envelope_version=1
sender_type=plan
sender_id=plan-44-static-verifier-rule-gaps
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-12T17:14:38Z

# Candidate lesson: no unconditional SUCCESS fallback in outcomeClassOf QuestionWait arm (Rule 6)

Source: PR #38 coderabbit inline comment 0a1369 on WorkflowValidator.java:1328, triaged to TASK-14 and fixed on HEAD 8d8a0ad.

Defect shape: the QuestionWait arm returned OutcomeClass SUCCESS for every transition no link primitive resolved, hiding unregistered primitives, undeclared outcomes, and misclassified revise/returned/assumed resolutions behind a passing class.

Corrective rule: resolve the outcome class only for question-class resolution outcomes and return empty for unmatched link transitions so the validator emits a visible STEP_COMPLETION refusal.

Classification deferred to orchestrator pickup.
