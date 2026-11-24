# Native catalog/runtime experiment

**Author and independent AI-assisted reruns completed on 2026-11-24.** The authoring agent is outside execution. A trusted Java harness runs real native catalog functions against its own localhost business mock. No real business/cloud API or approval ceremony is involved.

## Declared venue and command

Run on a designated Linux build/test host with JDK 17, not on the author's workstation. Use a task-owned directory and task-local Maven/repository; do not install globally or use containers. Host-specific access and directory ownership are operational evidence, not public configuration.

From this directory, with Apache Maven 3.9.11 available as `mvn`, prepare dependencies **before** the test:

```sh
mvn -B -Dmaven.repo.local=.m2 compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

Before running, compare every resolved runtime JAR with the recorded [dependency hashes](dependency-lock.json):

```sh
python3 - <<'PYLOCK'
import hashlib, json, os
from pathlib import Path
expected = {r["file"]: r["sha256"] for r in json.loads(Path("dependency-lock.json").read_text())["jars"]}
paths = [Path(p) for p in Path("target/classpath.txt").read_text().strip().split(os.pathsep)]
actual = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
assert len(paths) == len(expected) and actual == expected, "runtime dependency mismatch"
PYLOCK
```

Then the focused runtime command is:

```sh
timeout 150 java -cp "target/classes:$(cat target/classpath.txt)" example.NativeCatalogExperiment catalog target/results.json
```

The Java command starts/stops only its own loopback servers. Runtime declarations reference only those servers. This is not an OS-level outbound-network sandbox. The optional third Java argument selects one case, such as `with-reporting`, for an incremental first proof. Capture stdout, results, resolved dependency hashes and runtime/source versions; compare every expected versus actual case. Maven downloads occur only in preparation.

## Native reuse being exercised

The release is [sdk-java 7.35.4.Final](https://github.com/open-workflow-specification/sdk-java/tree/7.35.4.Final), using `io.serverlessworkflow` APIs and Java 17. Current main has different APIs/requirements and is not used. Direct dependencies/plugins are pinned in [pom.xml](pom.xml).

The native catalog endpoint serves frozen function bytes at `main/functions/<name>/<version>/function.yaml`, the released resolver's path. The harness records each fetch and SHA-256. This records provenance; it does not mean the native runtime authenticates catalog hashes or enforces immutability of a production catalog. Top-level work orders call `with-reporting:1.0.0@local` or `without-reporting:1.0.0@local`; those functions reuse cataloged billing, activation and reporting functions. No custom assembly engine resolves the calls.

## Expected cases and ownership of each check

| Case | Expected observation | Responsible mechanism |
|---|---|---|
| With reporting | Billing → activation → reporting; correct ID bindings and final output | Native catalog resolution, jq/data flow and HTTP runtime; mock records effects |
| Without reporting | Billing → activation; no reporting call | Second native catalog method |
| Wrong billing owner | Billing succeeds but returns another customer's record; activation returns 409 before any activation effect | Authoritative mock application ownership guard, not native runtime policy |
| Eligibility drift | Eligibility is revoked after billing; activation returns 409 before effects | Fresh mock application state guard |
| Unapproved endpoint observation | An owned loopback capture path outside the fictional application allowlist is called by the default runtime | Observe absence of an automatically enforced endpoint allowlist; no external destination |
| Top-level shell | Refused before attempting the harmless nonexistent sentinel | Native empty command allowlist |
| Top-level container | Unsupported; no container provider available | Capability omitted, not a universal container sandbox |
| Nested shell | Same refusal inside a cataloged function | Native command policy applied to nested task |
| Unknown function version | Catalog 404; no business calls | Native resource lookup failure |

Before the container probe the harness inspects loaded runnable providers and aborts if any container provider is present. No Docker executable, image pull or container runtime is invoked. A shell-policy regression would at worst try a deliberately nonexistent harmless executable; success requires the native “not allowed” refusal, not merely an OS error.

## Measured results

The [independent review record](review.json) and [rerun result](results-reviewer.json) confirm matching source, all 49 runtime JAR hashes and all nine expected/actual case records after normalizing only ephemeral loopback ports. Both compile and runtime commands exited zero; no experiment process remained. This is AI-assisted review, not an external human expert assessment.

The [machine-readable author result](results.json) records expected versus actual behavior, failure phase, catalog paths/digests, request payloads and effects. Java 17.0.21 with runtime 7.35.4.Final met all nine case expectations. Both native methods completed with correct output bindings. Ownership and eligibility refusals occurred while awaiting the activation call, with only billing effects recorded; no activation or reporting effect followed. Shell refusals were native `SecurityException`s, including inside a fetched function. The container task failed during definition with no runnable provider; unknown version failed during definition with a `FileNotFoundException` and recorded catalog 404.

**The endpoint probe was allowed, not blocked:** the native default called the owned localhost capture path once. A catalog function being loadable does not automatically restrict its HTTP destination to the fictional application allowlist. This is a useful observed limitation, not a security test passed by denial. No external destination was contacted by the probe.

The [source lock](source-lock.json) pins release tag 7.35.4.Final to commit `8e31544f88d7d4d290deef186bef979c5118a6aa` and records inspected source hashes. [dependency-lock.json](dependency-lock.json) records the 49 resolved runtime JARs. Maven bootstrap used 3.9.11 with official SHA-512 `bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978` from the [Apache archive](https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.tar.gz.sha512). Runtime logs reported no SLF4J provider and an injection-less Jersey client; the measured calls completed, but logging/injection integrations were not exercised.

## Scope and limits

The mock's guards are explicitly application code. Runtime metadata or a native catalog is not automatically approval authority. This experiment does not implement production catalog signatures, exact-plan human authorization, an endpoint allowlist, a general policy profile, an LLM benchmark or a production executor. It tests these specific native functions and cases, not arbitrary workflow equivalence. Script-engine permissions, file resources, redirects and other endpoint policies remain untested. A controlled server and an omitted module are not a production sandbox.

The harness is original MIT-licensed example code. The external SDK is Apache-2.0; source references and existing [upstream license copy](../../schemas/LICENSE-APACHE-2.0.txt) are retained. Upstream API/test patterns informed the harness; no SDK implementation is copied.
