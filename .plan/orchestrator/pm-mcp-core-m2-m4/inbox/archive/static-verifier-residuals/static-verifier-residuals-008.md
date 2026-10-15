envelope_version=1
sender_type=plan
sender_id=static-verifier-residuals
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-14T13:17:05Z

component=plan-marshall:phase-3-outline
category=improvement
title=Model cross-repository deliverables as executable steps instead of read-only tasks

# Model cross-repository deliverables as executable steps instead of read-only tasks

## Context

Deliverable 8 of static-verifier-residuals changes two files in the sibling repository plan-marshall-documentation (watch-item retirement, roadmap) in its own PR. The outline could only declare a read-only local file for it, so phase-4-plan's verification-only guard turned it into a verification-profile task that runs `verify -Ppre-commit` locally and does none of the work. After 4-plan the operator had to decide who does the work ("I do it after execute"), and the orchestrator later ran an ad-hoc inline-instruction dispatch for it; the functional review then graded D8 met_with_residuals until the operator merged PR #72.

## Root cause

The outline and task model have no shape for an external-repository deliverable: affected files must resolve inside the worktree, and a deliverable without write-intent paths degrades to a verification task.

## Proposed action

Give the outline a declared external-repository deliverable kind (repo path, files, PR expectation) that phase-4-plan turns into an operator-attended or dispatched step with its own completion evidence (PR URL, merge state), instead of a local verification task.

## Evidence

- aspect: chat-history-analysis — pivot after 3-outline: "None of the 13 tasks covers that repository; task 13 only runs a formatting check here"
- aspect: request-result-alignment — D8 has read-intent declarations only and is judged on task status; the real work happened outside the task model
- phase-4-plan report: "If you want that work done by an agent, give D8 a write-intent path or add a manual or operator step before landing"
