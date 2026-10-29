envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:22:30Z

component=pm-runtime
category=anti-pattern
source=pr-comment 1976a4 (PR #90, sourcery-ai Medium, fixed TASK-27)

# Test checked the key file in the wrong state directory

## Observation

`EnrolmentKeyTest.recordedFileStore` built the `EnrolmentKey` over the test temp directory `state`, while the machine base was `state/pm-base`. It then asserted `key.keyFile()` exists, so the test passed even when the key was written outside the machine's state directory (EnrolmentKeyTest.java:802-806). Fixed by TASK-27.

## Rule

A test that asserts a file location must derive the expected path independently from the production layout (here `base.resolve("state")`), never from the object under test. `assertExists(sut.path())` is vacuous about location.
