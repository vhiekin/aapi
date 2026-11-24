# AAPI: agent-authored application work orders

**Agentic Application Programming Interface — exploratory discussion draft, 2026-11-24.**

How should an application describe its approved building blocks so an agent can assemble work from a conversation, while the application retains execution authority?

This repository is a request for input, not a released protocol, a new industry standard, or a working general-purpose framework. We want to reuse existing declarations, catalogs and runtimes. **“Existing formats already cover this; here is how” is a successful outcome.**

The pattern under discussion is:

```mermaid
flowchart TB
  H["Human request"] --> A
  C["Approved catalog"] --> A
  subgraph Authoring["Authoring: no mutation permission"]
    A["Agent assembles"] --> D["Native declaration"]
  end
  A -. "Clarifies missing choices" .-> H
  D --> V["Independent validation + preview"]
  V --> P["Human approves exact work"]
  subgraph Execution["Execution: application authority"]
    R["Trusted runtime"] --> O["Effects + recorded results"]
  end
  P --> R
  classDef author fill:#eef4fa,stroke:#536b86,color:#182533
  classDef executor fill:#edf5ef,stroke:#55745e,color:#182533
  class A,D author
  class R,O executor
```

Text equivalent: the human and agent clarify the request using an approved catalog. The agent emits a declaration. The application validates and previews it; human approval precedes trusted execution and recorded results.

The authoring agent has no apply or domain-mutation permissions. It cannot grant approval or invent executable components. A second agent may critique intent, but cannot waive deterministic checks.

## Read and challenge

- [Proposal and candidate semantics](PROPOSAL.md)
- [Infrastructure example: native CloudFormation](examples/cloudformation/README.md)
- [Customer activation: native Arazzo plus an Open Workflow mapping](examples/customer-service/README.md)
- [Prior art, overlap and compatibility](PRIOR_ART.md)
- [Questions for maintainers and implementers](FEEDBACK.md)
- [What has and has not been validated](VALIDATION.md)
- [Contributing](CONTRIBUTING.md)

The examples are fictional and illustrative. No application endpoints, credentials, deploy commands or runtime are supplied. Native schemas can establish structural validity; they do not establish authorization or prove application effects.

## License and authorship

Original documents and examples are available under the [MIT License](LICENSE). Linked specifications retain their respective licenses.

This draft was prepared with AI assistance for research, writing and static checks. Maintainers remain responsible for its claims. No independent human review or standards-body endorsement is claimed.
