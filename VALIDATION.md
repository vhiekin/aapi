# Reproduce the bounded example checks

The local artifact-check venue uses Python 3.10+ with pinned dependencies. No service, account, secret, cloud permission, model or workflow runtime is needed. The checker reads only tracked examples/schemas and writes no files. It does not use the network.

## Exact commands

From a fresh checkout, install the pinned dependencies once (this installation may use the package index):

```sh
python3 -m venv .venv
.venv/bin/python -m pip install --no-cache-dir -r requirements-checks.txt
.venv/bin/python tools/check_examples.py
```

**The single verification command is `.venv/bin/python tools/check_examples.py`.** Once installed, it runs offline. An existing interpreter with the exact versions may instead use `python3 tools/check_examples.py`. The checker verifies installed versions and vendored schema checksums. A mismatch or unexpected positive/negative result exits nonzero.

[requirements-checks.txt](requirements-checks.txt) pins all six check dependencies. [schemas/manifest.json](schemas/manifest.json) pins official schema bytes/source URLs; [upstream notices](schemas/README.md) identify licenses and offline reference closure. No hidden Git files, user-home inputs or downloaded schema cache is required.

## Declared checks

- Parse JSON and validate both approved Arazzo 1.0.1 methods and both Open Workflow 1.0.1 declarations against the pinned official schemas.
- Verify application-owned declaration/API hashes, method orders, allowed author fields and input schemas.
- Resolve operation references, compare the two native formats' exact call/binding projection, and validate fixture request/response shapes against OpenAPI's schemas.
- Evaluate actual controlled customer/record state at initial preflight and immediately before each hypothetical operation.
- Compare seven derived positive/negative case decisions with [tracked expected outcomes](examples/customer-service/fixtures/cases.json): both methods, wrong-customer billing, initial ineligibility, eligibility drift, unknown method and author self-approval.
- Detect the separately tracked forward-output-order negative and CloudFormation dependency-cycle negative; accept the positive resource graph.
- Verify local Markdown links/anchors. Separately inspect changed public files for privacy and claim accuracy.

## Results for this revision

Author run on 2026-11-24: `python3 tools/check_examples.py` passed all seven cases, both native formats for both methods, schema/definition hashes, call/binding projections, ordering/cycle negatives and 38 local links. The wrong-customer case was refused at the activation guard with `billing-owner-mismatch`; the drift case was refused before activation, while initial ineligibility was refused during preflight. Independent persona reviews and rerun are pending.

## What this does not prove

This is a bounded example checker, not a general AAPI validator, a standard conformance suite, a workflow engine or a security boundary for untrusted code. Catalog/admission metadata is custom to the fictional application. A trusted checkout is assumed; production catalog authentication and policy enforcement are not implemented.

The Open Workflow expression recognizer supports only these fixtures; it is not a jq interpreter. Matching the projected calls/bindings does not prove general runtime equivalence. The OpenAPI document is checked through the referenced operation/request/result shapes, not a complete OpenAPI conformance implementation.

Schema checks cannot establish live state, approval, execution effects, safe retries or rollback. No actual HTTP/AWS calls, CloudFormation validation service, agent inference or runtime execution are performed. A schema-valid native declaration can still violate the application's ownership rule. Fixtures demonstrate that distinction, not production correctness.

CloudFormation's `AWSTemplateFormatVersion: 2010-09-09` does not pin every provider implementation. Its existing sample is checked as a resource-reference graph, not deployed.

## Existing visual evidence

The phase-grid SVG and three Mermaid diagrams are unchanged by this example-checking addition. Their earlier AI-assisted static rendering/image inspections remain the evidence for those exact assets. They are not a browser check of GitHub's page layout or an independent human expert assessment. No private review material is required to run the tracked checks.
