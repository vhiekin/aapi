# Pinned upstream schemas

These are unmodified upstream schemas vendored solely for offline example checks. They are not AAPI schemas.

| File | Source |
|---|---|
| `arazzo-1.0-schema.json` | [OAI Arazzo 1.0, iteration 2025-10-15](https://spec.openapis.org/arazzo/1.0/schema/2025-10-15) |
| `open-workflow-1.0.1-schema.yaml` | [Open Workflow specification tag v1.0.1](https://github.com/open-workflow-specification/specification/blob/v1.0.1/schema/workflow.yaml) |

[manifest.json](manifest.json) records exact source URLs and SHA-256 checksums. The upstream YAML schema retains its original `serverlessworkflow.io` identifier. The Open Workflow schema uses local-fragment references. The Arazzo schema also references the official JSON Schema 2020-12 meta-schema; the pinned installed `jsonschema-specifications` package supplies that resource and its dialect dependencies. The checker refuses other external schema references and configures no network retrieval.

Both upstream projects license these materials under Apache-2.0: [OAI license](https://github.com/OAI/Arazzo-Specification/blob/main/LICENSE), [Open Workflow v1.0.1 license](https://github.com/open-workflow-specification/specification/blob/v1.0.1/LICENSE). A copy is included as [LICENSE-APACHE-2.0.txt](LICENSE-APACHE-2.0.txt). Upstream copyright and source identifiers are retained. These files are excluded from the original examples' MIT licensing claim.

To update, select an explicit upstream revision, review changes and references, record new hashes, and rerun all bounded checks. Do not silently replace a pinned schema with a moving latest URL.
