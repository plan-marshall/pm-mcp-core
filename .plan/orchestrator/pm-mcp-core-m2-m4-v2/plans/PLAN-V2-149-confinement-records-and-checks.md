# PLAN-V2-149: The rest of the confinement package: the egress proxy's name and address rules, the job record's confinement, seccomp and egress members, the launch report shape and the self-check

epic: pm-mcp-core-m2-m4-v2
workstream: WS-06
origin: the grounding of 2026-11-14 of the open package `job-confinement-and-environment` (a read-only leaf, `origin/main` `77f2a83d`; issues pass Q14, answer 1): after PLAN-V2-146 (the listed worker read set, `readable_paths`, the socket list, the probe residue) the package's Tasks 1 (`partial(abi=2)`), 9 (the proxy), 10 (the record members), 11 (the self-check) and 12 (the CLI git identity) have no owner; **a contract mismatch needs an owner too**: the real `pm-exec` report is `{"pm_exec":"launched",...,"no_new_privs":b}` (`pm-mcp-clients` `Launcher.java:79-81`) while `LaunchReport.parse` refuses undeclared members (`:164`) and the fixture launcher omits them, so the runtime cannot read a real report today
lane: runbook

> Staged plan spec — one shippable unit of work, ready for `/plan-marshall-runbook-lane` hand-off.
> Lives at `plans/PLAN-V2-149-confinement-records-and-checks.md` and is queued as one row file, `queue/PLAN-V2-149.json`,
> in the epic ledger. The orchestrator EMITS the command below; it never launches the plan inline.
> This spec is SELF-SUFFICIENT together with the standing rules its First Instruction names:
> the emitted command is a one-line pointer and carries no brief, so every per-plan carry is
> authored here and the epic-wide rules are in `standing-rules.md`, read first.
> See `persona-plan-orchestrator/standards/orchestration-model.md` for the tier and
> hand-off contract.

## First Instruction: Read The Standing Rules

**Before you read the rest of this spec, read the standing rules of the epic completely:** `.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md` (from the root of the `pm-mcp-core` checkout; beside this spec's `plans/` directory it is `../standing-rules.md`; from another repository: `/home/oliver/git/pm-mcp-core/.plan/orchestrator/pm-mcp-core-m2-m4-v2/standing-rules.md`). They bind this plan exactly as if they were written below, and a rule that is not named here does not apply. Say in your first progress note that you read them, with their revision (`r7`; R21 binds the orchestrator's analysis and not this plan), and repeat the revision and the rule ids in your landing message.

Rules that apply to this plan: R02, R05, R06, R07, R08, R09, R11, R12, R13, R15, R16, R17, R18, R19, R20. (R01: the package file keeps its row `open` with what is left; R03, R04: the proxy is the existing `WorkerEgressProxy` and its resolver is an injected seam, the recording-resolver test the specification asks for; R09: the proxy is an egress boundary, the address checks fail closed; R10: the specifications already state the rules; R12: log records through `RuntimeLogMessages`; **R13: the job record gains members: its schema file `job-record.schema.json` (the specification document includes it since #404) is the authority, edited with its parity test**; R14: no protocol change.)

Plan parameters:

- R06 — developer documentation: `pm-runtime/doc/developer/runtime-job.adoc`; ADR directory `pm-runtime/doc/adr/runtime-job/` (the next free numbers); layout: workstream WS-06.

**Read the skills first (R20).** `java-core`, `java-lombok`, `java-null-safety`, `javadoc`, `junit-core`, `cui-testing` and `plan-marshall:persona-security-expert` (for the confinement and the untrusted-input reading) of `/home/oliver/git/plan-marshall/marketplace/bundles/{pm-dev-java,pm-dev-java-cui}/skills/`; say in the first progress note which you read.

## Objective

Four slices of the confinement package remain after PLAN-V2-146, and they share files with PLAN-V2-59 (`egress-record`) and PLAN-V2-134 (`git-metadata-out-of-workers-reach`), so this plan runs after both. **(1) The egress proxy:** `WorkerEgressProxy` matches lower-cased text (`:158-159`) and refuses others, but handles neither a trailing dot nor an IP literal, has no `address_not_public` check (loopback, private, CGNAT, IPv6-embedded IPv4: the merged specification #419), and connects with `new InetSocketAddress(host, port)` (`:205`) instead of to the address it checked; `AuditEvent.egressRefused` carries no reason. **(2) The records and the report:** `Confinement` has `PARTIAL_ABI_1` and no `partial(abi=2)` (`JobRecord.java:238`, the pattern at `job-record.schema.json:84`), the runtime does not check that `confined` implies ABI 3 or more, the job record has no `seccomp`, `egress` or `git_metadata` member, and the launch report cannot read the real `pm-exec` output. **(3) The self-check:** absent. **(4) The CLI git identity:** the JGit variant exists (`pm-provider-git` `GitIdentity`, `GitOperations`), the CLI variant has no class here. Like PLAN-V2-146 this plan composes, checks and records; **the kernel enforcement is `pm-exec`'s**.

## Deliverables

Five deliverables, one commit each, in this order.

1. **The proxy's name and address rules (the tests first).** In `WorkerEgressProxy` (`pm-runtime`): match the name **before any resolution** (case-folded, one trailing dot removed; an IP literal is never a name that matches a list of names); resolve through an **injected resolver seam** and refuse an address that is not public (loopback, link-local, private, CGNAT, unspecified, multicast, an IPv6 address that embeds a non-public IPv4) with the reason `address_not_public`; **connect to the address that was checked**, never to the name again; `AuditEvent.egressRefused` carries the closed reason. **Test order:** the recording-resolver fixture the specification asks for: a name that resolves first to a public and then to a private address connects to the checked one; each refusal class with a matched control; an IP literal; a trailing dot.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
2. **The record and the report.** `partial(abi=2)` in `Confinement` and the schema pattern (the file `job-record.schema.json` and its parity test move together); a consistency check (`confined` implies ABI 3 or more; below it the level is `partial`); the members `seccomp`, `egress` and `git_metadata` in the job record and its codec (the specification, `store-schemas/10`; coordinate with PLAN-V2-59, which writes `egress: unenforced` and with 134, which carries `git_metadata`: the outline reads their landing state and **does not redo what they delivered**); `LaunchReport` accepts the real `pm-exec` members `pm_exec` and `no_new_privs` and the specification's `seccomp` and `git_metadata`: **ask-at-refine:** who aligns the shape, the runtime (accept and ignore the two members as optional) or `pm-mcp-clients` (add the specification's members and drop the others); recommendation: the runtime accepts them as optional and the clients are told, about 55%; `FakePmExec` carries the real shape. **Test order:** the parity test and the reader refuse the old shapes; the real report from a recorded `pm-exec` output parses.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-lombok, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
3. **The self-check runner and its state.** The runtime's self-check of the confinement (a launcher mode that reports what it could establish) and its result seam: the parsing of the result, its effect on the job's recorded level, and the refusal when the check reports less than the profile needs; the check itself is `pm-exec`'s mode (`exec-hardening`, open): built here as a port over the fixture launcher, with the on-host run owed.
   - Skills: pm-dev-java:java-core, pm-dev-java:java-null-safety, pm-dev-java:javadoc, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
4. **The CLI git identity (Task 12), or its removal from the package.** **Ask-at-refine:** build the CLI variant beside the JGit one (`pm-provider-git`: no JGit in `pm-core` or `pm-workflow`, the module rules of `CLAUDE.md`), or move the task to the plan that needs it (`PLAN-V2-20`, `git-commit-primitives`); recommendation: move it to PLAN-V2-20 and record that in the package row, about 65%.
   - Skills: pm-dev-java:java-core, pm-dev-java:junit-core, pm-dev-java-cui:cui-testing
   - Persona: implementer
5. **Documentation and bookkeeping (R05, R06).** The guides, the ADRs, the watch items, `doc/Traceability.adoc`, and the package file `doc/plans/job-confinement-and-environment.adoc` (the row `done` if nothing remains, else its Remaining list; the concept document owed to the documentation repository, Task 13).
   - Skills: pm-documents:ref-asciidoc, pm-dev-java:javadoc
   - Persona: documenter

**Done when:** the proxy refuses by name before resolution and by non-public address with the recording-resolver fixture proving it connects to the checked address; `partial(abi=2)` and the consistency check are in the code, the schema file and its parity test; the job record carries the new members as far as 59 and 134 have not delivered them; the launch report parses a recorded real `pm-exec` output; the self-check seam is proven against the fixture launcher; every refusal has a test that fails on the unguarded input; `OutboundCallsArchTest` and its exception map are unchanged; the guides, the ADRs and the package file are written; `verify`, `verify -Ppre-commit` (its rewrites reviewed and committed), `verify -Pcoverage` and `verify -Pintegration-tests -pl pm-runtime -am` are green. **No claim that a kernel enforced anything or that a real self-check ran:** those are owed on a host with Landlock, `pm-exec` built and an operator machine. **Sonar:** the SonarCloud search of the pull request (`pullRequest=<N>&resolved=false`) lists no open issue other than one dismissed with its reason, and its quality gate is green, before the pull request is reported ready (the lane resolves `sonar: configured` since 2026-11-14).

## Standing Constraints

- **Fail closed at the egress boundary:** an address that cannot be classified is refused; the checked address is the one connected to.
- **The schema file is the authority** for the job record (`job-record.schema.json`); a member is added to the file, the codec and the parity test in one commit.
- **Do not redo PLAN-V2-59 and 134:** read their landing state first; the proxy has no production caller and this plan does not wire one (59's spec forbids it).
- Accepted residual risks (below Landlock ABI 3 truncation, below 6 signals, below 9 pathname sockets) are recorded, not claimed.
- No dependency or plugin is added; no hand-written test double (EasyMock only); where the project analyses its pull requests with Sonar, read it over plain HTTP (`pullRequest=N`, never `branch=<feature>`): an open issue is fixed in a pushed commit or dismissed with a stated reason `{issue-key}: {reason}` through `plan-marshall:workflow-integration-sonar:sonar_rest transition --issue-key {key} --transition {falsepositive|wontfix}`.
- **Review lens:** `plan-marshall:persona-security-expert` is the lens of the deliverables that parse or confine untrusted input (read it as the R20 paragraph says); the `Persona:` lines name the work identity only (the persona grammar takes a plain name).

## Claim Labels

All lines read at `origin/main` `77f2a83d` through the grounding of 2026-11-14; the outline re-reads each before it acts.

- OBSERVED: `WorkerEgressProxy` matches lower-cased text (`pm-runtime/.../job/WorkerEgressProxy.java:158-159`) and connects with `new InetSocketAddress(host, port)` (`:205`); `AuditEvent.egressRefused` has no reason.
- OBSERVED: `Confinement` has `PARTIAL_ABI_1` only (`JobRecord.java:238`); `job-record.schema.json:84` has no `abi=2`; the record has no `seccomp`, `egress` or `git_metadata` member.
- OBSERVED: the real `pm-exec` report `{"pm_exec":"launched",...,"no_new_privs":b}` (`pm-mcp-clients` `Launcher.java:79-81`) against `LaunchReport.parse` (`:164`, refuses undeclared members) and `FakePmExec`.
- OBSERVED: no self-check code (no grep hit); the JGit identity exists in `pm-provider-git` (`GitIdentity`, `GitOperations`), the CLI variant does not.
- HYPOTHESIS: PLAN-V2-59 and PLAN-V2-134 will have delivered the `egress` and `git_metadata` members before this plan is emitted; verify-first by reading their landing records; deliver only the difference.

## Expected Surface

- OBSERVED: `pm-runtime/src/main/java/de/planmarshall/runtime/job/WorkerEgressProxy.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobRecord.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/JobRecordCodec.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/LaunchReport.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/PmExecLauncher.java`, `pm-runtime/src/main/java/de/planmarshall/runtime/job/SubprocessJobRunner.java` — D1 to D3 (shared with PLAN-V2-59, 134, 146)
- OBSERVED: `pm-core/src/main/resources/de/planmarshall/core/store/job-record.schema.json` (the path is found with `git ls-files | grep job-record.schema.json`) and its parity test — D2
- OBSERVED: `pm-runtime/src/test/java/de/planmarshall/runtime/job/FakePmExec.java`, `pm-runtime/src/test/java/de/planmarshall/runtime/job/WorkerEgressProxyTest.java` — the proofs
- OBSERVED: `doc/plans/job-confinement-and-environment.adoc`, `doc/implementation-watch/job-runtime.adoc`, `doc/Traceability.adoc`, `pm-runtime/doc/developer/runtime-job.adoc`; HYPOTHESIS: ADRs in `pm-runtime/doc/adr/runtime-job/`

Not surface (R19): `doc/plans/README.adoc`, `doc/adr/README.adoc`. No `pom.xml`.

## Dependencies and Sequencing

- Depends on: PLAN-V2-146 (the first slice of the package), PLAN-V2-59 and PLAN-V2-134 (**run first**: they edit `JobRecord`, `JobRecordCodec`, `SubprocessJobRunner`, `PmExecLauncher`, `LaunchReport` and `FakePmExec`; PLAN-V2-59 is stale against the schema files since #404 and needs a re-ground before its emit), PLAN-V2-27, 29, 30, 53 (shipped), PLAN-V2-83.
- Overlaps at class level, decided by the gate at emit: 59, 134, 146 (above), 148 (the `egress` stage reads the proxy and the record), 126 and 141 (the job test trees), 114 and 125.
- Not here: the kernel enforcement and the self-check mode itself (`pm-exec` in `pm-mcp-clients`: `exec-hardening`), macOS Seatbelt (Task 8), the production wiring of the proxy, the concept document (the documentation repository).

**Corrections of 2026-11-14 after the impact analysis of the specification pull requests #414, #417 and #419 (`landings/SPEC-PRS-2026-11-14.md`; BINDING over any contradicting older text of this spec).** **#419 (`77f2a83d`) extended the rule D1 implements** (`doc/specification/job-runtime/02-confinement-and-environment.adoc:181`, § Name Resolution, "The proxy checks what it reached", and the watch item `PM-WATCH-JOB-40`, `doc/implementation-watch/job-runtime.adoc` about `:250-267`): **multicast is a class of `address_not_public`**, and **an IPv6 address that carries an IPv4 address is checked as that IPv4 address**: IPv4-mapped `::ffff:0:0/96`, IPv4-compatible `::/96`, NAT64 `64:ff9b::/96` and `64:ff9b:1::/48`, 6to4 `2002::/16`, Teredo `2001::/32` with the client address inverted. **Test vectors of the watch item:** `::ffff:127.0.0.1`, `64:ff9b::a00:1`, `2002:a00:1::`, a Teredo address whose client is `10.0.0.1`, and a multicast address (each refused, with a matched public control). Java maps only the IPv4-mapped form to `Inet4Address` itself: the other prefixes need explicit checks in the outline. `WorkerEgressProxy.java` has none of this at the head (`toLowerCase` matching at `:73,:158-159`, no resolver seam, `new InetSocketAddress(host, port)` at `:205`).

## Hand-Off Command

```text
/plan-marshall-runbook-lane plan=.plan/orchestrator/pm-mcp-core-m2-m4-v2/plans/PLAN-V2-149-confinement-records-and-checks.md
```

## Write-Boundary

The plan implementing this spec touches only its own repository source and tests. It creates
and edits NO file under `.plan/orchestrator/` other than its own
`inbox/{sender}-{seq}` message — the orchestrator owns every other ledger write — and reports
its outcome through its PR and its inbox message. The inbox exception's qualifiers and the
sole sanctioned write mechanism are stated in
`persona-plan-orchestrator/standards/orchestration-model.md` § Ledger Write-Boundary.
