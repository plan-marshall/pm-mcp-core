envelope_version=1
sender_type=plan
sender_id=http-baseline
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T14:44:40Z

Candidate lesson (source: Q-Gate findings 8c2f11, 1f696c, 70e90c, 927d7d, phase 5-execute, plan http-baseline).

Running the quality gate `verify -Ppre-commit` rewrote files, leaving uncommitted changes, including two production classes outside the plan's declared scope (GitLabClient, GlabCommands: blank line removed from the import block) and an out-of-scope test (GlabCommandsTest), plus an OpenRewrite change in CiHttpClientTest (seven test methods now declare `throws Exception`). Each was raised as a lint-issue finding and resolved by operator decision to commit the rewrite as is (TASK-8).

Observation: the pre-commit profile of pm-mcp-core is a rewriting gate (licence headers, OpenRewrite recipes, import order), so its diff routinely crosses the plan's declared surface. The outline and plan phases should expect it: either plan an explicit "commit the pre-commit rewrite" step, or have the verification step classify whitespace-only and recipe-driven rewrites outside scope as an expected, pre-approved delta instead of a finding requiring an operator decision. Also worth noting: the OpenRewrite recipe that widens `throws` to `Exception` in tests conflicts with no project rule here, but should be checked against the parent POM's recipe set.

Component candidates: plan-marshall:phase-5-execute (verification step), pm-mcp-core build configuration (pre-commit profile from pm-mcp-parent).
