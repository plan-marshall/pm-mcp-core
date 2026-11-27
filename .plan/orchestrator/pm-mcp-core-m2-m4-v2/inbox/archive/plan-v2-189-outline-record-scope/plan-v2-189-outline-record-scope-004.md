envelope_version=1
sender_type=plan
sender_id=plan-v2-189-outline-record-scope
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T16:08:38Z

component=plan-marshall:plan-retrospective
category=bug
confidence=high
source_aspects=artifact-consistency,manifest-decisions,outline-vs-shipped

# Stop check-artifact-consistency reporting realized files as declared-but-unrealized

## Context

In plan-v2-189-outline-record-scope, check-artifact-consistency reported recall 80.8% (declared 26, found 21) with five "missing" files: OutlineCodec.java, OutlineRecord.java, OutlineRenderer.java, RemovedSchemaMembersTest.java, outline-record-v1.schema.json. All five are in the realized footprint (outline-vs-shipped lists them with full paths), and the 21 unique declared paths equal the 21 realized paths exactly. check-manifest-consistency rule M6 then failed declared_vs_realized_set with a warning on the same five.

## Root cause

The declared count 26 is a multiset: the same paths are declared under several deliverables, and the comparison appears to count declarations (or bare basenames taken from "Change per file" bullets) instead of the de-duplicated, path-normalized set, so repeated declarations surface as unrealized.

## Proposed action

De-duplicate the declared set by normalized repo-relative path before computing recall and outline_only, and add a regression test with one path declared in two deliverables.

## Evidence

- fragment artifact-consistency: declared 26, found 21, missing five basenames
- fragment outline-vs-shipped: all five present in realized_footprint_paths with full paths
- fragment manifest-decisions: M6 fail "5 declared-but-unrealized and 0 realized-but-undeclared"
