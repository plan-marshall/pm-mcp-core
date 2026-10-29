envelope_version=1
sender_type=plan
sender_id=runtime-lifecycle
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T20:12:57Z

component=pm-runtime
category=bug
signal=automated-review
evidence=pr-comment findings 29de97 (sourcery, RelayVersionCheck.java:129), 38041d (sourcery, RelayVersionCheck.java:149), b34435 (coderabbit review body nitpick), all resolution fixed by TASK-25; sonar java:S6213 5a2d18 fixed in the same task

# State keyed by values from a request header must be bounded, rate-limited and updated atomically

## Observation

`RelayVersionCheck` kept every distinct relay-version header value in a set and appended one audit event per value, without bound, so a client sending many distinct values could grow runtime memory and the audit log. On a refused audit append it removed the version again, so a persistently failing sink got a retry and a warning per request. Add, append and remove were not serialized per version, so a concurrent observer could lose a refused record. The method name `record` also matched a restricted identifier (Sonar S6213).

## Rule (pm-mcp-core)

Data derived from a header crosses a trust boundary: cap the number and length of retained values, rate-limit retries and warnings per key (keeping a later retry possible), and make check-append-rollback a single atomic step per key (for example `ConcurrentHashMap.compute`). Do not name methods `record`, `var`, `yield` or other restricted identifiers.
