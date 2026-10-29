envelope_version=1
sender_type=plan
sender_id=lock-manager-and-leases
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-10T11:51:29Z

# Candidate lesson: verify -Ppre-commit (pm-mcp-parent) is not idempotent and mangles wrapped record headers

- Component: pm-mcp-parent quality-gate recipes (consumed by pm-mcp-core `verify -Ppre-commit`)
- Signal: Q-Gate finding 6291ff (5-execute, lint-issue, fixed); TASK-15 BLOCKED
- Evidence: qgate finding 6291ff; work log WARNING 3d9faa (2026-10-10T06:23:23Z); decision log 1f7a46, 56508d, c1663b, a88af0

## What happened

- A green `verify -Ppre-commit` rewrote 43 tracked files: 16 in the plan's diff and 27 pm-runtime files the
  branch never touched (import order, `throws IOException` -> `throws Exception`, array-initialiser spacing,
  try-with-resources continuation indent).
- Record headers wrapped one component per line (`LeaseOwner`, `StaleLeaseSweep.Result`) were re-indented to
  column 0 / 4 on every run; reshaping them did not stabilise the output. The operator accepted the formatter's
  shape and deferred a recipe fix in pm-mcp-parent.
- On pm-runtime the import layout flips between two forms from one run to the next, so the gate never
  converges; the operator decided to commit every rewrite anyway.

## Suggested direction

- Fix the formatter recipe for multi-line record headers and the import-layout flip in pm-mcp-parent.
- Until then, a plan in pm-mcp-core should expect `verify -Ppre-commit` to touch files outside its footprint and
  plan a separate style-only commit for them (this is what feeds the scope_creep_check noise).
