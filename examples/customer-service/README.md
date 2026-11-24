# Example: activate a customer with reporting

**Fictional API; native Arazzo 1.0.1 declaration; no service has been called.**

## Human request and clarification

Fictional request: “Activate the standard service with monthly billing and reporting for this customer.”

The agent asks which customer, when activation should begin, and whether this includes charging money or historical invoices. Fictional answers: `customer-example`, immediate activation after approval, monthly configuration only, reporting enabled, no payment collection or historical invoicing. [inputs.json](inputs.json) carries the confirmed customer. No unresolved customer or billing choice may be guessed.

## Approved components and method

The fictional application's contract is described by [openapi.json](openapi.json), OpenAPI 3.1.1. Its server is `https://service.example.invalid`, deliberately unusable. The bearer authorization scheme describes executor access; there is no token and the authoring agent must not receive one.

| Operation | Purpose | Required state | Effect/output |
|---|---|---|---|
| `configureBilling` | Select monthly billing | Customer exists and is eligible | Billing configured; string `billingRecordId` |
| `activateService` | Activate the standard service | Billing configured for this customer | Service active; string `serviceId` |
| `enableReporting` | Enable the reporting add-on | That customer's service is active | Reporting enabled |

“Activate with reporting” is the approved method: all three operations in that order. “Activate without reporting” would stop after activation; it is an alternative to discuss, not an extra implemented workflow here.

The descriptions explain application rules. A real application must enforce them; OpenAPI descriptions are not executable preconditions. Machine-readable sharing of those rules is the question under review, not a capability silently supplied by this sample.

## Native work order and ordering

[work-order.arazzo.json](work-order.arazzo.json), together with [inputs.json](inputs.json) and the pinned source API description, is the illustrative approval package.

It uses ordinary Arazzo `steps` sequencing, `operationId`, path parameters, request bodies, success criteria and output expressions:

```mermaid
flowchart LR
  B["Configure billing"] -->|"billingRecordId"| A["Activate service"]
  A -->|"serviceId"| R["Enable reporting"]
```

Text equivalent: billing configuration precedes activation, which precedes reporting. The arrows require that order; their labels identify output-to-input bindings. The prerequisite state—billing configured, then service active—is a separate application condition, even though this method aligns all three relationships.

Version 1.0.1 was chosen for a small documented subset and tooling compatibility, not because it is the latest Arazzo release: 1.1.0 exists. In 1.0.1 `steps` is ordered and `dependsOn` is workflow-level. This sample does not use 1.1 step-level dependencies, parallelism or unreleased actor-in-the-loop fields.

The declaration has no automatic retries. A real executor must handle uncertain effects through the application's idempotency/reconciliation contract rather than blindly repeat a failed request.

## Invalid case

[invalid-order.arazzo.json](invalid-order.arazzo.json) moves reporting before activation. It still passes the document schema, but refers to an output that is not available yet and violates the approved method. The expected preflight refusal is a semantic/order check, not evidence that JSON Schema proves execution safety. No request should be sent.

## Human preview

For `customer-example`: configure monthly billing, activate the standard service using the billing record returned for this customer, then enable reporting on the returned service. No payment will be collected and no historical invoice created by these example operations. All three operations must occur in order; none is approved for parallel execution. Preview inputs, API/workflow versions, target and all late-bound relationships before asking for approval.

A real executor verifies customer eligibility, permissions, definitions and ordering before effects; resolves runtime outputs only from this approved chain; rechecks application state; and records outcomes. Failures can leave billing configured without an active service. That is a reported partial result, not an automatic rollback promise.

## Open Workflow mapping

The same method can be expressed using the existing [Open Workflow v1.0.0 constructs](https://github.com/open-workflow-specification/specification/blob/v1.0.0/dsl.md):

| Arazzo example | Open Workflow mapping |
|---|---|
| Ordered three-step workflow | A `do` task list with three sequential named tasks |
| API operation call | A `call: openapi` task bound to the approved API operation, or `call: http` with explicit method/endpoint |
| Request body and output expressions | Native task `input`, `output` and `export` data-flow mappings; explicitly preserve customer and earlier IDs |
| Reusable approved method | A reusable function in `use.functions` or a pinned external catalog, with schema-constrained inputs/outputs |
| No parallel permission | Do not introduce a `fork`; if a later method permits one, validate its resource/state conflicts |
| Human authorization | Application gate before starting the resolved workflow instance; no inference task required |

This is a construct-level mapping, not an additional executable file or a claim of conversion equivalence. Implementers should supply an exact native document and runtime result before asserting equivalence. The [Java reference runtime](https://github.com/open-workflow-specification/sdk-java/tree/main/impl) demonstrates that independent execution machinery exists; no runtime was invoked here.

## What is already covered?

Arazzo covers sequence, operation references, input/output bindings and step-success criteria. OpenAPI covers typed requests/results. The remaining research question concerns shared discovery and enforcement of domain-specific applicability, prerequisite/effect vocabulary, permitted methods and clarification needs. Existing descriptions, profiles and adapters may be sufficient. No `x-aapi-*` semantics are invented in these files.

[Validation limits](../../VALIDATION.md) · [Feedback questions](../../FEEDBACK.md)
