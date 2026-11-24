#!/usr/bin/env python3
"""Offline checks for these fixed examples; not an AAPI or workflow runtime."""
import argparse
import copy
import hashlib
import importlib.metadata
import json
from pathlib import Path
import re
import sys

import yaml
from jsonschema import Draft202012Validator
from jsonschema_specifications import REGISTRY as DIALECTS

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "examples/customer-service"
# These IDs map to the fixed application predicates in guard(), not an expression DSL.
GUARD_IDS = {
    "configureBilling": ["customer-eligible"],
    "activateService": ["customer-eligible", "billing-same-customer", "billing-monthly"],
    "enableReporting": ["customer-eligible", "service-same-customer", "service-active"],
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, f"duplicate JSON key: {key}")
        result[key] = value
    return result


def read(path):
    return json.loads(path.read_text(), object_pairs_hook=pairs)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def local(base, name):
    path = (base / name).resolve()
    require(path.is_relative_to(base.resolve()), "reference leaves its fixture directory")
    require(path.is_file(), f"missing fixture: {name}")
    return path


def validate(schema, value):
    # Pinned package supplies official dialect resources; unknown references fail.
    Draft202012Validator(schema, registry=DIALECTS).validate(value)


def refs(value):
    if isinstance(value, dict):
        for key, item in value.items():
            if key in ("$ref", "$dynamicRef"):
                yield item
            yield from refs(item)
    elif isinstance(value, list):
        for item in value:
            yield from refs(item)


def setup():
    for line in (ROOT / "requirements-checks.txt").read_text().splitlines():
        name, version = line.split("==")
        require(importlib.metadata.version(name) == version, f"install pinned dependency {line}")
    schemas = {}
    for entry in read(ROOT / "schemas/manifest.json")["files"]:
        path = local(ROOT / "schemas", entry["file"])
        require(digest(path) == entry["sha256"], f"schema/license checksum mismatch: {path.name}")
        if path.suffix not in (".json", ".yaml"):
            continue
        schema = read(path) if path.suffix == ".json" else yaml.safe_load(path.read_text())
        require(all(ref.startswith("#") or ref == "https://json-schema.org/draft/2020-12/schema" for ref in refs(schema)), "unsupported external schema reference")
        Draft202012Validator.check_schema(schema)
        schemas[path.name] = schema
    for folder in (ROOT / "examples", ROOT / "schemas"):
        for path in folder.rglob("*.json"):
            read(path)
    return schemas


def graph(document):
    resources = document["Resources"]
    edges = {name: set() for name in resources}

    def resource_refs(value):
        found = set()
        if isinstance(value, dict):
            if "Ref" in value and value["Ref"] in resources:
                found.add(value["Ref"])
            if "Fn::GetAtt" in value:
                found.add(value["Fn::GetAtt"][0])
            if "Fn::Sub" in value:
                found.update(part.split(".")[0] for part in re.findall(r"\$\{([^}]+)\}", value["Fn::Sub"]))
            for child in value.values():
                found.update(resource_refs(child))
        elif isinstance(value, list):
            for child in value:
                found.update(resource_refs(child))
        return found

    for name, value in resources.items():
        edges[name] = resource_refs(value)
        dep = value.get("DependsOn", [])
        edges[name].update([dep] if isinstance(dep, str) else dep)
        require(edges[name] <= resources.keys(), "unknown resource dependency")
    return edges


def acyclic(edges):
    done = set()
    while len(done) != len(edges):
        ready = {name for name, parents in edges.items() if name not in done and parents <= done}
        if not ready:
            return False
        done.update(ready)
    return True


def forward_refs(document):
    seen, failures = set(), []
    for step in document["workflows"][0]["steps"]:
        for parent in re.findall(r"\$steps\.([A-Za-z0-9_-]+)\.outputs\.", json.dumps(step)):
            if parent not in seen:
                failures.append((step["stepId"], parent))
        seen.add(step["stepId"])
    return failures


def api_operations(api):
    require(api["openapi"] == "3.1.1", "unexpected OpenAPI version")
    require(api["servers"] == [{"url": "https://service.example.invalid"}], "unexpected API target")
    return {item["put"]["operationId"]: (path, item["put"]) for path, item in api["paths"].items()}


def normalized_openworkflow(document, api_ops):
    """Recognize only the three literal HTTP tasks and expressions in this fixture.
    No jq evaluation, HTTP call, or general Open Workflow interpretation occurs.
    """
    require(document["document"]["dsl"] == "1.0.1", "unexpected Open Workflow version")
    previous = None
    result = []
    for entry in document["do"]:
        require(len(entry) == 1, "one named task required")
        name, task = next(iter(entry.items()))
        require(name in api_ops and task["call"] == "http", "unsupported example task")
        path, op = api_ops[name]
        suffix = path.rsplit("/", 1)[-1]
        endpoint = '${ "https://service.example.invalid/customers/" + $workflow.input.customerId + "/' + suffix + '" }'
        require(task["with"]["endpoint"] == endpoint, "unexpected native endpoint binding")
        require(task["with"]["method"] == "put", "unexpected HTTP method")
        require(task["with"]["headers"] == {"Content-Type": "application/json"}, "unexpected headers")
        require(task["with"]["output"] == "content", "unexpected HTTP output mode")
        require(task["output"]["schema"]["document"] == op["responses"]["200"]["content"]["application/json"]["schema"], "result schema drift")
        payload = copy.deepcopy(task["with"]["body"])
        for key, value in payload.items():
            if isinstance(value, str) and value.startswith("${"):
                match = re.fullmatch(r"\$\{ \.([A-Za-z]+) \}", value)
                require(match is not None and previous is not None, "unsupported native expression")
                payload[key] = "$steps." + previous + ".outputs." + match.group(1)
        result.append((name, payload))
        previous = name
    return result


def check_definitions(schemas, catalog, api_ops):
    require(set(catalog["components"]) == set(GUARD_IDS), "unknown catalog component")
    for name, ids in GUARD_IDS.items():
        require(catalog["components"][name]["guards"] == ids, "catalog/guard implementation mismatch")
    for method in catalog["methods"].values():
        definitions = {}
        for fmt, source in method["declarations"].items():
            path = local(BASE, source["file"])
            require(digest(path) == source["sha256"], "unapproved definition bytes")
            definitions[fmt] = read(path)
        ar = definitions["arazzo"]
        ow = definitions["openWorkflow"]
        validate(schemas["arazzo-1.0-schema.json"], ar)
        validate(schemas["open-workflow-1.0.1-schema.yaml"], ow)
        require(ar["arazzo"] == "1.0.1", "unexpected Arazzo version")
        require(ar["sourceDescriptions"] == [{"name": "customerService", "url": "openapi.json", "type": "openapi"}], "unapproved source description")
        require(len(ar["workflows"]) == 1, "one selected native method required")
        workflow = ar["workflows"][0]
        require(workflow["inputs"] == method["inputSchema"], "author input schema drift")
        require(ow["input"]["schema"]["document"] == method["inputSchema"], "Open Workflow input schema drift")
        require([s["operationId"] for s in workflow["steps"]] == method["operationOrder"], "method order mismatch")
        require(not forward_refs(ar), "forward output reference")
        for step in workflow["steps"]:
            require(step["operationId"] in api_ops and step["stepId"] == step["operationId"], "unknown operation")
            require(step["parameters"] == [{"name": "customerId", "in": "path", "value": "$inputs.customerId"}], "customer binding drift")
        ar_calls = [(s["operationId"], s["requestBody"]["payload"]) for s in workflow["steps"]]
        require(normalized_openworkflow(ow, api_ops) == ar_calls, "native call/binding projection mismatch")
    invalid = read(BASE / "invalid-order.arazzo.json")
    validate(schemas["arazzo-1.0-schema.json"], invalid)
    require(bool(forward_refs(invalid)), "negative order unexpectedly accepted")
    require(digest(BASE / "invalid-order.arazzo.json") not in {
        v["sha256"] for m in catalog["methods"].values() for v in m["declarations"].values()
    }, "negative declaration admitted by catalog")
    cf = ROOT / "examples/cloudformation"
    require(acyclic(graph(read(cf / "template.json"))), "valid resource graph refused")
    require(not acyclic(graph(read(cf / "invalid-cycle.json"))), "negative resource cycle accepted")


class Refusal(Exception):
    def __init__(self, stage, code):
        self.stage, self.code = stage, code


def guard(state, customer, operation, payload, stage):
    if not state["customers"].get(customer, {}).get("eligible", False):
        raise Refusal(stage, "customer-ineligible")
    if operation == "activateService":
        record = state["billingRecords"].get(payload["billingRecordId"], {})
        if record.get("customerId") != customer:
            raise Refusal(stage, "billing-owner-mismatch")
        if record.get("cycle") != "monthly":
            raise Refusal(stage, "billing-not-monthly")
    if operation == "enableReporting":
        record = state["services"].get(payload["serviceId"], {})
        if record.get("customerId") != customer:
            raise Refusal(stage, "service-owner-mismatch")
        if record.get("active") is not True:
            raise Refusal(stage, "service-not-active")


def resolve(value, outputs):
    if isinstance(value, dict):
        return {key: resolve(child, outputs) for key, child in value.items()}
    if isinstance(value, str) and value.startswith("$steps."):
        match = re.fullmatch(r"\$steps\.([A-Za-z]+)\.outputs\.([A-Za-z]+)", value)
        require(match is not None, "unsupported fixture expression")
        return outputs[match[1]][match[2]]
    return value


def evaluate(submission, scenario, catalog, api_ops):
    passed = []
    try:
        if set(submission) != set(catalog["allowedAuthorFields"]):
            raise Refusal("admission", "author-fields")
        method = catalog["methods"].get(submission["method"])
        if method is None:
            raise Refusal("admission", "unknown-method")
        try:
            validate(method["inputSchema"], submission["inputs"])
        except Exception:
            raise Refusal("admission", "inputs-invalid")
        customer = submission["inputs"]["customerId"]
        # Preflight checks only facts required initially, not effects of future steps.
        guard(scenario["initial"], customer, "configureBilling", {}, "preflight")
        ar = read(local(BASE, method["declarations"]["arazzo"]["file"]))
        outputs = {}
        for i, step in enumerate(ar["workflows"][0]["steps"]):
            name = step["operationId"]
            observation = scenario["observations"][i]
            require(observation["operationId"] == name, "fixture observation order mismatch")
            payload = resolve(step["requestBody"]["payload"], outputs)
            _, op = api_ops[name]
            validate(op["parameters"][0]["schema"], customer)
            validate(op["requestBody"]["content"]["application/json"]["schema"], payload)
            # The scenario supplies a fresh authoritative snapshot for each hypothetical call.
            guard(observation["before"], customer, name, payload, "operation-guard")
            passed.append(name)
            require(observation["status"] == 200, "unexpected fixture HTTP status")
            response = observation["response"]
            validate(op["responses"]["200"]["content"]["application/json"]["schema"], response)
            outputs[name] = {}
            for key, expr in step.get("outputs", {}).items():
                require(expr.startswith("$response.body#/"), "unsupported response expression")
                outputs[name][key] = response[expr.split("#/", 1)[1]]
        return {"stage": "complete", "code": "accepted-fixture", "guardPassed": passed}
    except Refusal as error:
        return {"stage": error.stage, "code": error.code, "guardPassed": passed}


def check_links():
    count = 0
    for path in ROOT.rglob("*.md"):
        if any(part.startswith(".") for part in path.relative_to(ROOT).parts):
            continue
        for dest in re.findall(r"\]\(([^)]+)\)", path.read_text()):
            if "://" in dest:
                continue
            name, _, anchor = dest.partition("#")
            target = (path.parent / name).resolve() if name else path
            require(target.is_relative_to(ROOT) and target.exists(), f"missing local link: {path.name}: {dest}")
            if anchor:
                headings = re.findall(r"^#{1,6}\s+(.+)$", target.read_text(), re.M)
                slugs = [re.sub(r"[^\w\- ]", "", h.lower()).replace(" ", "-") for h in headings]
                require(anchor in slugs, f"missing anchor: {dest}")
            count += 1
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", help="Inspect one named controlled-fixture case.")
    args = parser.parse_args()
    schemas = setup()
    catalog = read(BASE / "approved-catalog.json")
    source = catalog["api"]
    require(digest(local(BASE, source["file"])) == source["sha256"], "unapproved API bytes")
    ops = api_operations(read(BASE / source["file"]))
    check_definitions(schemas, catalog, ops)
    scenarios = read(BASE / "fixtures/state.json")["scenarios"]
    cases = read(BASE / "fixtures/cases.json")["cases"]
    if args.case:
        cases = [case for case in cases if case["id"] == args.case]
        require(bool(cases), "unknown case")
    for case in cases:
        actual = evaluate(read(local(BASE, case["submission"])), scenarios[case["scenario"]], catalog, ops)
        require(actual == case["expected"], f"{case['id']}: expected {case['expected']}, got {actual}")
        print("PASS", case["id"], json.dumps(actual, sort_keys=True))
    print(f"PASS: {len(cases)} cases; native schemas, pinned definitions, call/binding projections, ordering negatives and {check_links()} local links.")
    print("Offline fixture inspection only: no requests, LLM calls, workflow execution or authorization ceremony.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("FAIL:", error, file=sys.stderr)
        sys.exit(1)
