envelope_version=1
sender_type=plan
sender_id=plan-v2-200-composed-vector-check-parser-limits
epic=pm-mcp-core-m2-m4-v2
kind=candidate-lesson
created=2026-11-27T21:26:44Z

component=plan-marshall:phase-4-plan
category=anti-pattern
title=Fix phase-4-plan Step 8 qgate add flags to the accepted --type/--severity set

# Fix phase-4-plan Step 8 qgate add flags to the accepted --type/--severity set

## Context

In plan-v2-200-composed-vector-check-parser-limits the 4-plan leaf followed the phase-4-plan Step 8 text and called manage-findings qgate add with --type warning. The script rejected it with an argparse usage error (exit 2) at 2026-11-27T14:24:58Z; the leaf retried with --type triage --severity warning, which succeeded, and reported the drift in its hand-back.

## Root cause

phase-4-plan Step 8 documents a --type value (warning) that is a severity, not a member of the qgate add --type choices.

## Proposed action

Correct the Step 8 invocation to --type triage --severity warning (or the intended type) and cover it with the canonical-invocation check.

## Evidence

- script-failure-analysis: manage-findings qgate, argparse_other, exit 2, 2026-11-27T14:24:58Z
- 4-plan hand-back: "its Step 8 text uses qgate add --type warning, but the script only accepts triage (and other types) with --severity warning"
