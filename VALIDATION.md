# Validation scope

This repository contains documents and illustrative native declarations, not a runtime or a new validator. Checks are static and do not call the fictional API, CloudFormation, a planner or an execution engine.

## Declared checks

- Parse every JSON example.
- Validate the Arazzo examples against the official [Arazzo 1.0 schema, iteration 2025-10-15](https://spec.openapis.org/arazzo/1.0/schema/2025-10-15), applicable to patch version 1.0.1. Fetch the schema into memory only.
- Check example workflow inputs and request/result shapes with the existing Python `jsonschema` library, resolving only the sample's known output types/fixtures.
- Check operation IDs against the fictional OpenAPI document; check that step output references point to preceding steps in the valid workflow and fail in the intentionally invalid order.
- Check CloudFormation references/dependency cycles statically and inspect native resource property names against official documentation.
- Check relative document/file links and inspect public files for private infrastructure/source details, credentials and misleading maturity claims.

These checks are deliberately narrower than proving a specification implementation. Arazzo's own [schema publication notes](https://spec.openapis.org/arazzo/) explain that its schema cannot catch every specification violation.

## Results

Author static checks on 2026-11-24 passed:

- All six JSON files parsed.
- Both Arazzo documents matched the pinned official schema. Schema SHA-256: `b8715bd824fffcb2accf5077977d37c9e7a15be60d785e7a3a51cf600fd46ad4`.
- Concrete inputs and three operations' request/result fixture shapes matched their schemas; referenced operation IDs resolved locally.
- The valid sequence had no forward output references; the invalid sequence referenced activation's unavailable output.
- The valid CloudFormation resource graph was acyclic; the intentionally invalid variant had a cycle.
- Relative links/anchors and a named private-pattern scan passed. Native property names were checked against official documentation.

These were in-memory/static checks, not a shipped validator. No CloudFormation template-validation service, live API, executor, planner, application-state check, authorization integration, deployment, cross-runtime equivalence test or independent human review was performed. Schema validity does not validate the illustrative application's policy.

The three remaining Mermaid diagrams were rendered with the browser-free `beautiful-mermaid` 1.1.3 renderer, rasterized with `rsvg-convert`, and independently inspected as images on 2026-11-24. Equivalent literal presentation colors were used for rasterizer compatibility; diagram text and geometry were preserved during rasterization. They were legible and consistent with their text equivalents. This is static-diagram review, not visual verification of GitHub's embedded renderer or page layout. Public HTML and commit-pinned source availability were checked separately. The review was AI-assisted; no independent human expert assessment is claimed.

The main workflow uses the owner-selected phase-grid SVG, with its review-only option title replaced by "Agent-assembled work order" and its geometry, stage headings, responsibility rows and steps preserved. The public asset was checked for valid XML, scripts and external references, rendered with `rsvg-convert`, and independently inspected as an image before publication. The README preserves an accessible text equivalent. This is static asset verification, not a browser check of GitHub's page layout.

## Reproduction boundary

The check procedure uses ordinary JSON parsing and schema validation, plus inspection of local references and dependency edges. It is not shipped as an AAPI conformance suite. For an independent review, report the exact files/spec versions inspected and distinguish parser, schema, semantic and runtime results.

CloudFormation's `AWSTemplateFormatVersion: 2010-09-09` identifies its template format, not a frozen release of every resource provider. The target account, region, provider behavior and allowed actions would require a separately reviewed real environment.
