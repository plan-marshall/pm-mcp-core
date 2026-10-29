envelope_version=1
sender_type=plan
sender_id=plan-09-audit-trail
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T18:57:57Z

# Hash chain across a rotation contradicted the append order (empty log yields genesis)

- Source: Q-Gate record 2007e0 (3-outline, warning, pm-core/.../store/AuditLog.java), resolution `taken_into_account`.
- Suggested component: project-local (pm-mcp-core core.store / runtime.audit, ADR core-store/026).

## Observation

D4 said the first record after a rotation chains to the checkpoint hash, but D1's append read the predecessor from the (now empty) log and fell back to genesis, so verification would flag every post-rotation record. The outline also named no API through which runtime.audit writes a chained record into core.store. Settled with one predecessor rule (last line, else `emptyLogPredecessor()` = last index checkpoint_hash, else genesis) and a public `appendCheckpoint`.

## Candidate rule

For chained logs with rotation, the outline must state the predecessor rule for the empty-after-rotation case and the exact cross-package write API; a later functional review (f12251) showed the "only through appendCheckpoint" claim this produced was still not true in code.
