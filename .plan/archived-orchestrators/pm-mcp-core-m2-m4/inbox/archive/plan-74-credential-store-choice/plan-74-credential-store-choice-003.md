envelope_version=1
sender_type=plan
sender_id=plan-74-credential-store-choice
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T09:21:26Z

component=pm-runtime
category=bug
source=qgate 6-finalize a24ecb (code-review, error)

# Enumeration must not filter legal names with the temp-file prefix

## Observation

`FileSecretStore.entries()` skipped every entry whose name begins with `.tmp-` to hide in-flight atomic-write temp files, but `.tmp-` is a legal credential name, so a store move left those credentials behind (FileSecretStore.java:155-158). Fixed by TASK-020 (9b41ea8).

## Rule

When a store hides its own temp files from enumeration, the temp namespace must be disjoint from the legal entry-name namespace (separate directory or a character the name grammar forbids). A prefix filter over the same namespace silently drops data, and an enumeration consumer such as a move loses it.
