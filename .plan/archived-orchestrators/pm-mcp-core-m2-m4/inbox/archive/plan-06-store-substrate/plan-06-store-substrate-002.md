envelope_version=1
sender_type=plan
sender_id=plan-06-store-substrate
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-11T07:09:43Z

component=plan-marshall:phase-3-outline
category=improvement
source_plan=plan-06-store-substrate
source_record=qgate 3-outline 4b074f (resolved taken_into_account)

# Outline deliverable replaced text that the target file did not contain

## What happened

Deliverable 12 said to replace the references to `LeaseCodec.Refusal` / `FormatException` in
`plan-marshall-documentation/doc/developer/locks-and-leases.adoc` with `StoreRefusal`, and its
success criterion required that the file no longer names them. The file named neither type; it
described the refusal in prose (section Leases, about line 126; section The Stale Lease Sweep,
about line 154). The criterion was therefore already met before any edit and checked nothing. The
outline Q-Gate caught it and the deliverable was restated against the prose that is actually there,
with a criterion that is false today.

The deliverable had been written from memory of the code change (the types the plan removes), not
from a read of the document being edited.

## Rule

A deliverable of the form "replace X with Y in file F" must be grounded by reading F at outline
time: X must occur in F. A success criterion must be one the unedited file fails ("absent today");
a criterion of the form "F no longer names X" is only valid when F names X now.

## Where it could be enforced

The outline self-check could require, for each "replace/remove X" deliverable, a cited line of the
current file that contains X, and reject success criteria that already hold on the base commit.
