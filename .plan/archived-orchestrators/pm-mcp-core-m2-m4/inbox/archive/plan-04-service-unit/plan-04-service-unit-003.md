envelope_version=1
sender_type=plan
sender_id=plan-04-service-unit
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T06:41:33Z

# Candidate lesson: reject CR, LF and NUL in paths rendered into systemd units and plists

Source: PR #25 review comment by coderabbitai (finding 000e1b, resolved fixed in-run), `pm-runtime/.../runtime/start/ServiceUnitFile.java:208`.

`escapeSystemd` quoted whitespace, but a newline inside the daemon path stays a raw newline. systemd does not continue a quoted value across lines, so text after the newline would be read as a new directive (for example `ExecStartPre=`). `escapeXml` has the same gap for control characters, which XML 1.0 plists do not allow.

Fix applied: the `ServiceUnitFile` constructor rejects CR, LF and NUL in the daemon path, so `write()` aborts instead of writing a malformed unit.

Candidate rule: any value rendered into a line-oriented config format (systemd unit, plist, env file) is validated for line breaks and NUL at construction time; quoting alone does not cover them.
