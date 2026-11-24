# Questions for maintainers and implementers

Please help falsify the need for additional specification work. References to existing mechanisms, corrected examples and independent applications are more useful than general agreement.

1. **Can existing formats do all of this?** Which native fields, reusable definitions, profiles or tools already make purpose, applicability, required order and allowable composition discoverable to an authoring agent?
2. **What is the smallest missing piece?** Identify a requirement in either example that cannot be represented or independently validated. Is the gap documentation, an application adapter, a vocabulary/profile, a validator feature or genuinely a specification gap?
3. **Where should domain semantics live?** How should a catalog connect executable definitions to prerequisite state, effects, alternative methods and clarification questions without maintaining contradictory copies?
4. **What does ordering mean across implementations?** Can we distinguish task hierarchy, data-flow dependencies, prerequisite state, sequencing, permitted interleaving and permitted parallelism? What unsupported cases should be refused?
5. **How should versioned catalogs be resolved?** Which existing mechanisms bind the preview and execution to the same definitions and prevent silent changes after approval?
6. **Would a profile be sufficient?** What minimal feature subset and refusal rules would make the examples portable without inventing a new language?
7. **Can someone reproduce the interpretation independently?** A second domain and runtime are welcome. Show both a valid work order and a structurally valid but semantically forbidden one.
8. **Is the author/executor boundary practical?** Can your stack accept native declarations from an untrusted authoring process with no apply permissions, then validate, preview and execute them independently?

For Arazzo contributors: the example intentionally uses 1.0.1 sequencing and output expressions. Would 1.1, reusable workflows, or existing extensions eliminate any apparent metadata gap? We are not requesting an inference step or relying on unreleased actor-in-the-loop fields.

For Open Workflow contributors: the [customer mapping](examples/customer-service/README.md#open-workflow-mapping) uses existing task/function and data-flow constructs. Is a catalog/profile enough to expose the application rules to authors while retaining ordinary runtime execution?

Please include the exact specification/tool version, the relevant declaration, whether it was parsed/validated/executed, and what remains application policy. “No new standard is needed” is explicitly welcome.
