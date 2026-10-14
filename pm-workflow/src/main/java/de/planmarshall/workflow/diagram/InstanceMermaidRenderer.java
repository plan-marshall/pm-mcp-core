/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.diagram;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * Renders a plan instance version as a Mermaid {@code stateDiagram-v2} (PM-IMPL-5, Workflow DSL Specification, Plan
 * Instance, Display).
 * <p>
 * The diagram opens with a front matter whose {@code title} is the title of the instance
 * ({@link InstanceAnnotations#title()}) written as a YAML double-quoted scalar, a backslash and a double quote escaped
 * with a backslash, followed by the diagram of the composed unit exactly as
 * {@link MermaidRenderer} draws it, the active state styled {@code active}. Below the unit come, each only when the
 * header lists one, a composite state {@code pruned} with one state per pruned element, described as
 * {@link InstanceAnnotations.Pruned#describe()}, and a composite state {@code waived gates} with one state per waived
 * gate, described as {@link InstanceAnnotations.Waived#describe()}; the pruned elements are absent from the unit, so
 * these states have no transition. Their Mermaid identifiers are {@code instance_pruned}, {@code instance_pruned_<n>},
 * {@code instance_waived} and {@code instance_waived_<n>}, numbered from {@code 1} in header order; an identifier the
 * unit could use itself (the alias of a state, a transition target or a phase composite, also with a numeric suffix)
 * is passed over by appending the next free {@code _<n>}, so no identifier of the annotations names a state of the
 * unit. The text of a label comes from the header and is escaped for the quotes that enclose it: a {@code #} and a
 * double quote become Mermaid entities and a line break a space.
 *
 * @since 0.1
 */
final class InstanceMermaidRenderer {

    private static final String INDENT = "    ";
    private static final String PHASE_PREFIX = "phase_";

    /** The most digits a numeric suffix of the unit's diagram can have; a longer suffix is no suffix of it. */
    private static final int MAX_SUFFIX_DIGITS = 9;

    private InstanceMermaidRenderer() {
    }

    /**
     * Renders an instance version.
     *
     * @param unit        the composed unit of the version
     * @param annotations the annotations of the version, the active state included
     * @return the diagram, every line ended by a line feed
     */
    static String render(WorkflowDefinition unit, InstanceAnnotations annotations) {
        var out = new StringBuilder();
        out.append("---\n").append("title: ").append(yamlQuoted(annotations.title())).append('\n').append("---\n");
        out.append(MermaidRenderer.render(unit, annotations.activeState()));
        var identifiers = Identifiers.of(unit);
        composite(out, identifiers, "pruned", "instance_pruned", annotations.pruned(),
                InstanceAnnotations.Pruned::describe);
        composite(out, identifiers, "waived gates", "instance_waived", annotations.waivedGates(),
                InstanceAnnotations.Waived::describe);
        return out.toString();
    }

    private static <T> void composite(StringBuilder out, Identifiers identifiers, String label, String prefix,
            List<T> entries, Function<T, String> describe) {
        if (entries.isEmpty()) {
            return;
        }
        out.append(INDENT).append("state \"").append(label).append("\" as ").append(identifiers.allocate(prefix))
                .append(" {\n");
        for (var index = 0; index < entries.size(); index++) {
            out.append(INDENT.repeat(2)).append("state \"").append(label(describe.apply(entries.get(index))))
                    .append("\" as ").append(identifiers.allocate(prefix + "_" + (index + 1))).append('\n');
        }
        out.append(INDENT).append("}\n");
    }

    /**
     * Makes free text safe inside the double quotes of a {@code state "..."} label: {@code #} becomes the entity
     * {@code #35;} (so text that looks like an entity stays text), a double quote the entity {@code #quot;}, and each
     * line break or control character, which would end the declaration, a single space
     * ({@link InstanceAnnotations#singleLine(String)}).
     */
    private static String label(String text) {
        return InstanceAnnotations.singleLine(text.replace("#", "#35;").replace("\"", "#quot;"));
    }

    /**
     * Writes the title as a YAML double-quoted scalar, so a {@code ": "} or a leading YAML indicator in it never
     * breaks the front matter: a backslash becomes {@code \\} and a double quote {@code \"}. The title is one line
     * already ({@link InstanceAnnotations#title()}), so no other character needs an escape.
     */
    static String yamlQuoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * The identifiers of the annotation states, none of which the unit's diagram may use.
     * <p>
     * The diagram of the unit gives each of its names (a state, the start state, a transition target, a phase
     * composite) the preferred identifier of that name or that identifier followed by {@code _<k>}; because every
     * name takes one identifier, {@code k} never exceeds the number of names plus one. Every identifier of that bounded
     * set counts as used, so an annotation never shares a node with the unit, and the allocation ends.
     *
     * @param preferredForms the preferred identifier of every name of the unit
     * @param maxSuffix      the largest numeric suffix the unit's diagram may append
     */
    private record Identifiers(Set<String> preferredForms, int maxSuffix, Set<String> taken) {

        static Identifiers of(WorkflowDefinition unit) {
            var transitions = StateTransitions.of(unit);
            var names = new HashSet<String>();
            Stream.concat(Stream.of(unit.startState()), unit.states().keySet().stream()).forEach(names::add);
            unit.states().keySet().stream().flatMap(id -> transitions.outgoing(id).stream())
                    .forEach(edge -> names.add(edge.target()));
            var preferredForms = new HashSet<String>();
            names.forEach(name -> preferredForms.add(preferred(name)));
            var phases = new HashSet<String>();
            unit.states().values().forEach(state -> phases.add(state.phase()));
            phases.forEach(phase -> preferredForms.add(PHASE_PREFIX + preferred(phase)));
            return new Identifiers(preferredForms, names.size() + phases.size() + 1, new HashSet<>());
        }

        /** The identifier the unit's diagram prefers for a name: dots and hyphens become underscores. */
        private static String preferred(String name) {
            return name.replace('.', '_').replace('-', '_');
        }

        String allocate(String preferred) {
            var candidate = preferred;
            for (var suffix = 2; isUsedByUnit(candidate) || !taken.add(candidate); suffix++) {
                candidate = preferred + "_" + suffix;
            }
            return candidate;
        }

        /**
         * Tells whether the unit's diagram may name a candidate: it is the preferred identifier of a name of the unit,
         * or one followed by a numeric suffix {@code _<k>} with {@code 2 <= k <= maxSuffix}.
         */
        private boolean isUsedByUnit(String candidate) {
            if (preferredForms.contains(candidate)) {
                return true;
            }
            var separator = candidate.lastIndexOf('_');
            if (separator <= 0) {
                return false;
            }
            var suffix = candidate.substring(separator + 1);
            if (suffix.isEmpty() || suffix.length() > MAX_SUFFIX_DIGITS
                    || !suffix.chars().allMatch(Character::isDigit)) {
                return false;
            }
            var value = Integer.parseInt(suffix);
            return value >= 2 && value <= maxSuffix && preferredForms.contains(candidate.substring(0, separator));
        }
    }
}
