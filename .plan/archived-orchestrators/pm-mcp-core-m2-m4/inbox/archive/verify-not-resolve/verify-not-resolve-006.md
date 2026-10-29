envelope_version=1
sender_type=plan
sender_id=verify-not-resolve
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-13T07:21:12Z

# Candidate (review finding, fixed in run): enrolled wrapper file name not bound to the requested WrapperName

Source: pr-comment finding 5c7147, CodeRabbit inline on PR #43, `pm-runtime/src/main/java/de/planmarshall/runtime/job/WrapperVerification.java:62`; fixed by TASK-16 (commit 7a9300f).

## Defect

`WrapperVerification` accepted any root-level file whose digest matched the enrolled wrapper. The enrolment schema allowed `name: mvnw` with any non-empty `relative_path`, so a request for `MVNW` could resolve to a differently named root-level file from a verified enrolment.

## Fix

The guard now also requires the file name of `relative_path` to equal `name.wireName()`, and a test covers the mismatch.

## Lesson shape

In verify-not-resolve checks, binding the identity is part of verifying the content. A digest check proves the bytes but not which name the bytes answer to. Every enrolled artefact looked up by a logical name should have its name pinned as well as its digest. The outline and self-review missed this; a review bot caught it.

Classification is left to the orchestrator.
