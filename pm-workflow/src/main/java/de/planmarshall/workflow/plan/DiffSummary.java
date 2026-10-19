/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.plan.HeaderJson.ArrayNode;
import de.planmarshall.workflow.plan.HeaderJson.Node;
import de.planmarshall.workflow.plan.HeaderJson.NullNode;
import de.planmarshall.workflow.plan.HeaderJson.ObjectNode;
import de.planmarshall.workflow.plan.HeaderJson.StringNode;

/**
 * The {@code diff_summary} of a line of the composition log (PM-IMPL-5, PM-WF-1): a composed version relative to the
 * version before it, as five flat arrays. A pruned element, a waived gate and a bound argument value are compared as
 * the header writes them; a pruned element whose reason or source fact changed is removed and added. Each array keeps
 * the order of the header it comes from, the ceilings the order of the call sites, first those of the previous
 * version, and within a call site the order of the argument names.
 *
 * @param prunedAdded     pruned in this version, not in the previous one
 * @param prunedRemoved   pruned in the previous version, not in this one
 * @param ceilingsChanged the bound arguments of a policy call whose value differs between the two versions
 * @param gatesWaived     waived in this version, not in the previous one
 * @param gatesUnwaived   waived in the previous version, not in this one
 * @since 0.1
 */
public record DiffSummary(List<PrunedElement> prunedAdded, List<PrunedElement> prunedRemoved,
List<CeilingChange> ceilingsChanged, List<WaivedGate> gatesWaived, List<WaivedGate> gatesUnwaived) {

    /**
     * Copies every list into an unmodifiable list.
     *
     * @param prunedAdded     pruned in this version, not in the previous one
     * @param prunedRemoved   pruned in the previous version, not in this one
     * @param ceilingsChanged the bound arguments whose value changed
     * @param gatesWaived     waived in this version, not in the previous one
     * @param gatesUnwaived   waived in the previous version, not in this one
     * @throws NullPointerException if a list or an element is {@code null}
     */
    public DiffSummary {
        prunedAdded = List.copyOf(prunedAdded);
        prunedRemoved = List.copyOf(prunedRemoved);
        ceilingsChanged = List.copyOf(ceilingsChanged);
        gatesWaived = List.copyOf(gatesWaived);
        gatesUnwaived = List.copyOf(gatesUnwaived);
    }

    /**
     * @param previous the header of the version before
     * @param current  the header of the composed version
     * @return the composed version relative to the version before
     * @throws NullPointerException if a header is {@code null}
     */
    public static DiffSummary between(PlanInstanceHeader previous, PlanInstanceHeader current) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        return new DiffSummary(missingFrom(current.pruned(), previous.pruned()),
                missingFrom(previous.pruned(), current.pruned()), ceilings(previous, current),
                missingFrom(current.waivedGates(), previous.waivedGates()),
                missingFrom(previous.waivedGates(), current.waivedGates()));
    }

    /**
     * @return the tree of the {@code diff_summary} member, every array present
     */
    Node toNode() {
        var members = new LinkedHashMap<String, Node>();
        members.put("pruned_added", new ArrayNode(prunedAdded.stream().map(HeaderJson::pruned).toList()));
        members.put("pruned_removed", new ArrayNode(prunedRemoved.stream().map(HeaderJson::pruned).toList()));
        members.put("ceilings_changed", new ArrayNode(ceilingsChanged.stream().map(CeilingChange::toNode).toList()));
        members.put("gates_waived", new ArrayNode(gatesWaived.stream().map(HeaderJson::waivedGate).toList()));
        members.put("gates_unwaived", new ArrayNode(gatesUnwaived.stream().map(HeaderJson::waivedGate).toList()));
        return new ObjectNode(members);
    }

    private static <T> List<T> missingFrom(List<T> elements, List<T> other) {
        return elements.stream().filter(element -> !other.contains(element)).toList();
    }

    private static List<CeilingChange> ceilings(PlanInstanceHeader previous, PlanInstanceHeader current) {
        var before = byCallSite(previous);
        var after = byCallSite(current);
        var callSites = new LinkedHashSet<>(before.keySet());
        callSites.addAll(after.keySet());
        var changes = new ArrayList<CeilingChange>();
        for (var callSite : callSites) {
            var previousArgs = before.getOrDefault(callSite, Map.of());
            var currentArgs = after.getOrDefault(callSite, Map.of());
            var names = new TreeSet<>(previousArgs.keySet());
            names.addAll(currentArgs.keySet());
            for (var name : names) {
                var was = value(previousArgs, name);
                var is = value(currentArgs, name);
                if (!was.equals(is)) {
                    changes.add(new CeilingChange(callSite, name, was, is));
                }
            }
        }
        return changes;
    }

    /** @return the arguments of each policy call of the header by its call site, in the order of the header */
    private static Map<String, Map<String, BoundArgument>> byCallSite(PlanInstanceHeader header) {
        var calls = new LinkedHashMap<String, Map<String, BoundArgument>>();
        header.policyCalls().forEach(call -> calls.putIfAbsent(call.callSite(), call.args()));
        return calls;
    }

    private static Optional<CallValue> value(Map<String, BoundArgument> args, String name) {
        return Optional.ofNullable(args.get(name)).map(BoundArgument::value);
    }

    /**
     * A bound argument of a policy call whose value differs between two versions.
     *
     * @param callSite the calling state of the policy call
     * @param argument the bound argument ({@code retry_budget}, {@code fix_budget}, {@code actions})
     * @param previous the value in the previous version; empty where it did not bind the argument
     * @param value    the value in this version; empty where it does not bind the argument
     * @since 0.1
     */
    public record CeilingChange(String callSite, String argument, Optional<CallValue> previous,
    Optional<CallValue> value) {

        /**
         * Refuses a missing component and a change that changes nothing.
         *
         * @param callSite the calling state of the policy call
         * @param argument the bound argument
         * @param previous the value in the previous version
         * @param value    the value in this version
         * @throws IllegalArgumentException if the two values are equal
         */
        public CeilingChange {
            Objects.requireNonNull(callSite, "callSite");
            Objects.requireNonNull(argument, "argument");
            Objects.requireNonNull(previous, "previous");
            Objects.requireNonNull(value, "value");
            if (previous.equals(value)) {
                throw new IllegalArgumentException("the argument '%s' of '%s' did not change".formatted(argument,
                        callSite));
            }
        }

        private Node toNode() {
            var members = new LinkedHashMap<String, Node>();
            members.put("call_site", new StringNode(callSite));
            members.put("argument", new StringNode(argument));
            members.put("previous", previous.map(HeaderJson::callValue).orElse(NullNode.INSTANCE));
            members.put("value", value.map(HeaderJson::callValue).orElse(NullNode.INSTANCE));
            return new ObjectNode(members);
        }
    }
}
