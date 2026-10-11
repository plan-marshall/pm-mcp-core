envelope_version=1
sender_type=plan
sender_id=plan-04-service-unit
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T06:41:35Z

# Candidate lesson: Files.exists() returns false when existence cannot be determined

Source: PR #25 review comment by sourcery-ai (finding 34474e, resolved fixed in-run), `pm-runtime/.../runtime/start/ServiceUnitFile.java:166` and `ServiceUnitInstaller.java:79-80`.

`ServiceUnitInstaller.uninstall()` used `Files.exists`. That call returns false when a permission or I/O error prevents the check, so uninstall skipped removal and the manager cleanup, then logged success while the service could still be enabled.

Fix applied: `exists()` now answers true unless the unit is known to be absent (the `Files.notExists` semantics). Uninstall proceeds and reports a removal failure instead of reporting a false success.

Candidate rule: for a destructive or cleanup path, decide "absent" with `Files.notExists` (three-valued) rather than `!Files.exists`. An undetermined state must not take the "nothing to do" branch.
