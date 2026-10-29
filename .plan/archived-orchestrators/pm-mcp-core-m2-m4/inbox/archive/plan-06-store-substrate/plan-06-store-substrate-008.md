envelope_version=1
sender_type=plan
sender_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T07:10:56Z

component=pm-core
category=improvement
source_plan=plan-06-store-substrate
source_record=sonar-issue 27e8b1, a7ce75, 9dde77, 4f5705, a4c6ab, a8763d, 8a3b4b, 56420c, 6e55b3, 17ccea, c83510, 8b11db, cabd3e, a8372f, 8d5a9b, 48c430, f02a79, be07c6, 8de587, df369c, c6ef57 (round 1); 491da2, db9af6, 08a3d9, 8315dd (round 2); all fixed

# Recurring Sonar rules reached the PR, and a round-1 fix created a round-2 issue

## What happened

SonarCloud reported 21 issues on the first analysis of PR 26 and 4 more on the second; the first
analysis failed the quality gate (Reliability rating C) because of one BUG.

Round 1, by rule:
- `java:S5778` x11 (tests): more than one call that can throw inside an `assertThrows` lambda.
- `java:S1130` x2 (tests): `throws Exception` on a test method that cannot throw it.
- `java:S9391` x2: loop where a stream was expected.
- `java:S1192` x3: duplicated literals (`"store"`, `"scope"`).
- `java:S6878`: pattern variable where a record pattern fits.
- `java:S2326`: unused type parameter (a phantom type on `JsonlStoreFile.Entry`).
- `java:S6218` (BUG, failed the gate): a record with an array component (`PendingTarget`) without
  content-aware `equals` / `hashCode` / `toString`.

Round 2 (after the fix commits):
- `java:S1845` (BLOCKER) in `StoreSnapshot`: the round-1 fix of `S1192` introduced a constant `STORE`
  that clashed with the existing field `store`.
- `java:S9391`, `java:S1130`, `java:S6213` (a local record named with a restricted identifier).

## Rule

- Before the PR, self-review the change against the Sonar rules this repository keeps hitting:
  `S5778` (one throwing call per `assertThrows` lambda), `S1130`, `S1192`, `S9391`, and `S6218` for
  every record that has an array component.
- A fix commit for a static-analysis finding is checked against the same rules before it is pushed;
  a constant introduced to remove a duplicated literal must not share its name (ignoring case) with
  a field of the class.
