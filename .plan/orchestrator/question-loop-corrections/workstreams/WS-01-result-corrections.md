# WS-01: Corrections of the published question-loop results

epic: question-loop-corrections

## Charter

Bring the published results of the question-loop smoke run in line with what the runs support: the statements in the
evaluation reference, the developer document and the engine specification that are stronger than the evidence, the
measurement figures that sit in normative specification text, the limits that are missing (the deleted evidence among
them), and the open decisions the results raise. Closed when PLAN-01 has landed in `pm-mcp-core` and
`plan-marshall-documentation`.

## Scope

- In scope: `pm-mcp-core` `doc/specification/client-profiles/01-profile-schema.adoc`, `02-placeholders.adoc`,
  `doc/specification/model-work/01-roles-and-configuration.adoc`, `06-envelopes-questions-deadlines.adoc`;
  `plan-marshall-documentation` `doc/specification/evaluation.adoc`, `doc/developer/question-loop-experiment.adoc`,
  `doc/discussions/open-questions.adoc`.
- Out of scope: the work packages (WS-02), any new measurement (WS-02), code.

## Plans

| Plan | Status | Notes |
|------|--------|-------|
| PLAN-01-correct-question-loop-results | staged | correct the overstated statements, move figures out of normative text, record the evidence loss and open decisions |

## Sequencing and Surface Notes

- Disjoint from WS-02 by file. PLAN-03 (WS-02) reads the corrected evaluation rows; it runs after PLAN-01 has landed.
