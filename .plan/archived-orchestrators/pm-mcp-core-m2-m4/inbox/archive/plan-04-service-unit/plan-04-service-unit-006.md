envelope_version=1
sender_type=plan
sender_id=plan-04-service-unit
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T06:41:36Z

# Candidate lesson: a "missing input is ignored" test must also assert that nothing was created

Source: PR #25 review comment by sourcery-ai (finding 07d50f, resolved fixed in-run), `pm-runtime/.../runtime/start/ServiceUnitInstallerTest.java:282`.

`shouldIgnoreMissingUnit` asserted only that no manager commands ran and that the expected logs appeared. A regression in which uninstall creates the missing unit file without contacting the manager would still pass and leave a phantom unit behind.

Fix applied (TASK-10): `assertFalse(unit.exists())` after `uninstall()`.

Candidate rule: a test for "absent input is a no-op" asserts the absence of side effects on the filesystem (the path is still absent), not only the absence of calls and the presence of logs.
