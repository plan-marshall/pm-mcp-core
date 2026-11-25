envelope_version=1
sender_type=orchestrator
sender_id=orchestrator-pm-mcp-core-m2-m4-v2
epic=pm-mcp-core-m2-m4-v2
kind=finding
created=2026-11-25T20:47:36Z

# Third doc-review round: two more derived-fact kinds leave in your deliverable 2

From the orchestrator, for PLAN-V2-188. Source: `doc-review-2026-11-25-002.md` §1, merged as core #617 (`515c1fa3`); the numbers and lines below are the reviewer's, on `origin/main` `3ba79c4f`. Re-read each at your head first and name what a merge since then settled. Your spec stays your scope; this adds one item to deliverable 2 and one wording item to deliverable 1, and nothing else.

## Deliverable 2 gains two kinds and the count changes

The merged specification (SS/09; phase-workflows/04 `ci-verify`, /06 merge path) reads whether a CI check is required, and whether a merge must go through the queue, from the pull request at the act. The cached forge facts go:

- `DerivedFactKind.java:44-45` `REQUIRED_CHECKS` and `:50-51` `CHECKS_VISIBLE` are deleted, with `derived-facts.schema.json:25` dropping both enum values, in the same commit (parity pin `DerivedFactsSchemaParityTest:96-98`).
- **The count is thirteen, not fifteen** (the review's correction of the earlier round): sixteen minus `branch_up_to_date_required` (your item) minus these two. Javadoc `DerivedFactKind.java:19` and `pm-core/doc/developer/core-store.adoc:1424` say thirteen; ADR core-store/053 is amended with the stamp, as for your first removal.
- `MERGE_QUEUE_ENFORCED` stays and keeps `{available, check_response_timeout}`; its Javadoc (`:40`) drops "enforces its merge queue".
- One negative control per removed kind in `DerivedFactsCodecTest`, like the one you already write for `branch_up_to_date_required`: a stored entry keyed `required_checks` is refused UNREADABLE and the key is named, and likewise `checks_visible`. Red first, then the schema and the constants together.

## Deliverable 1 gains one wording item

- `finding-record.schema.json:40` (`required_check`): the description reads "from the provider's read of the check at that moment", not "the derived required_checks"; `Finding.java:237` Javadoc likewise. The schema file is already on your surface (`:102`); `Finding.java` is a new entry.

## What this does not ask

- The other schema members of §1 (`decision-envelope-v1` `blocks`, `plan-instance-header` `:30`, `status.schema` `evidence_class`) are staged elsewhere (PLAN-V2-23, PLAN-V2-190, the refine package); do not take them.
- If your deliverable 2 is already merged to your branch with the count fifteen, correct it in the next commit rather than leaving two counts.

Say in your landing message that you read this, and name each item as delivered or not.
