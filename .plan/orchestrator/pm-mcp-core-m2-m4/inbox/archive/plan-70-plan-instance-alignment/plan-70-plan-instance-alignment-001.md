envelope_version=1
sender_type=plan
sender_id=plan-70-plan-instance-alignment
epic=pm-mcp-core-m2-m4
kind=candidate-lesson
created=2026-10-19T18:14:07Z

component=pm-workflow
category=improvement

# Lane-reachability prune must exclude the shared states ahead of the lane fork

Source: Q-Gate 3-outline finding ece97b (resolved in run, taken_into_account), plan plan-70-plan-instance-alignment.

The outline defined the `other_lane` prune as "every state not reachable from the chosen lane's entry". With the lane entries sitting after the fork at `init.ready`, the shared pre-fork states (`init.begin` ... `init.ready`, the unit start, the refine states ahead of the deep entry) are reachable from neither entry, so both lanes would have pruned them as `other_lane` - including the start state - and the INV-INIT checks would have broken on the composed version.

## Rule

When a prune is defined by reachability from a lane entry, define it as "reachable from the other lane's entry AND NOT reachable from the chosen lane's entry", never as "not reachable from the chosen entry". Add a test per lane that the shared pre-fork states survive.

## Where it applies

Outline authoring of any graph-pruning deliverable (store-schemas/14, workflow-dsl/09, InstancePruner in pm-workflow).
