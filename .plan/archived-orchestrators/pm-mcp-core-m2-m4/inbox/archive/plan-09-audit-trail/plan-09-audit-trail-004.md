envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:57:38Z

# Signed/hashed content built with `String.formatted("%d")` is locale-dependent

- Source: review-bot record 080a48 (CodeRabbit, PR #69, inline at pm-runtime/.../audit/AuditRotation.java:346), resolution `fixed` by TASK-020 (commit 8cf3c94).
- Suggested component: project-local Java convention (pm-mcp-core `pm-runtime` audit / any HMAC or hash input); possibly `pm-dev-java:java-security` or `pm-dev-java:java-core`.

## Observation

`checkpointHmac` built the signed content with `"{\"record_count\":%d,...}".formatted(...)`. `Formatter` uses the default FORMAT locale, so under `ar-EG` or a `-u-nu-` locale the digits are non-ASCII and the HMAC no longer matches the documented content; a restart under another locale would make `completePending` refuse every append. Neither the plan's code-review rounds (five) nor the tests caught it; the review bot did.

## Candidate rule

Bytes that are signed, hashed or compared across processes must be built locale-independently: plain concatenation with `Long.toString`, or `String.format(Locale.ROOT, ...)`. Never `String.formatted` / `String.format` without a locale on such content. A regression test signing under a non-ASCII-digit locale (as TASK-020 added) pins it.
