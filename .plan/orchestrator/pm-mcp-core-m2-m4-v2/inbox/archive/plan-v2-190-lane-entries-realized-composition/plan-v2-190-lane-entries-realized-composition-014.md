envelope_version=1
sender_type=plan
sender_id=plan-v2-190-lane-entries-realized-composition
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-28T12:16:49Z

# Candidate lesson: schema description promises a refusal no code in this repository implements

- Source: automatic review, PR #645, CodeRabbit inline comment, hash b4c4e8, resolution accepted
- File: pm-core/src/main/resources/de/planmarshall/core/store/references.schema.json:29
- Finding: the unplanned_changes description says declared_files never admits a protected path, but the declared_files item schema accepts any string and no Java/JSON source here writes, validates or refuses declared_files; ProtectedPaths.of no longer takes declarations.
- Disposition: accepted, unchanged - the clause restates the specification text of store-schemas/13 (ADR core-store/091); enforcement at the declared_files writer lives outside this repository.
- Observation: possible follow-up - confirm the writer of declared_files (outside pm-mcp-core) actually enforces the refusal the schema text states; otherwise the spec text has no enforcing implementation anywhere.
