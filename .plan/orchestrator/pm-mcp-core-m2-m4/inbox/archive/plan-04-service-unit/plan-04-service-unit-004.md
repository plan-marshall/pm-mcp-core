envelope_version=1
sender_type=plan
sender_id=plan-04-service-unit
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T06:41:34Z

# Candidate lesson: a fix for a review-bot permission finding introduced Sonar java:S2612 and turned the PR quality gate red

Sources: PR #25 review comment by sourcery-ai (finding 942234, resolved fixed), and the SonarQube Cloud quality-gate comment (finding b2983e, resolved fixed). Both are on `pm-runtime/.../runtime/start/ServiceUnitFile.java`.

Sequence:
1. sourcery-ai flagged that the unit file and its parent directories took their permissions from the umask, so another local user could replace the service.
2. Round-1 fix (TASK-8) set explicit `rw-r--r--` file / `rwxr-xr-x` directory permissions.
3. Sonar flagged two java:S2612 vulnerabilities (permissions granted to "others") on that code. "Security Rating on New Code" dropped to C and the PR quality gate failed.
4. Round-2 fix (TASK-11) wrote the unit owner-only (0600 file, 0700 directories) through `PosixModes`, which cleared the gate.

This cost one extra review/Sonar loop-back out of three on the PR.

Candidate rule: when you fix a file-permission finding, go straight to owner-only modes (0600/0700) unless a non-owner reader is a stated requirement. Sonar java:S2612 rejects any explicit grant to group or others on new code, so a "safe" 0644/0755 still fails the quality gate.
