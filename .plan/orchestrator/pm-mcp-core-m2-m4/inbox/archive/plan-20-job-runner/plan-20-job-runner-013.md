envelope_version=1
sender_type=plan
sender_id=plan-20-job-runner
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-18T10:19:10Z

component=pm-runtime
category=improvement
created=2026-10-18

# Project facts about pm-runtime learned in plan-20-job-runner (knowledge, not a defect)

## Context

Three findings of plan-20-job-runner rest on facts about this repository that a later plan touching pm-runtime will need again. They are knowledge, not lessons with a corrective action; the orchestrator may route them as architecture hints for pm-runtime.

- FFM downcall descriptors (MacSecurity, Posix, and now the kill(2) descriptor of KillBinding / ProcessGroupSignals) are registered in pm-runtime/src/main/resources/META-INF/native-image/de.planmarshall/pm-runtime-credentials/reachability-metadata.json, not in the plan-marshall-mcp assembly. The MacSecurity Javadoc said otherwise before this plan. (qgate 1a85eb, 6a1367)
- ProjectRecovery is public and is wired by the pm-mcp-server assembly in plan-marshall-mcp. A change to its factory methods (withEnrolment) needs a source-compatible overload or a named follow-up in plan-marshall-mcp after the SNAPSHOT deploy; the outline should say which. (qgate 3f46fc)
- Signal numbers differ between Linux and macOS; a table of signal names used for a job killed by a signal must be chosen per OS, not taken from the Linux table. (qgate 0546e6)

## Proposed action

Record the three facts as pm-runtime architecture hints (tip / insight), so an outline touching native-image metadata, ProjectRecovery or signal handling finds them before the Q-Gate does.

## Evidence

- qgate 3-outline 1a85eb, 3f46fc
- qgate 6-finalize 6a1367, 0546e6
