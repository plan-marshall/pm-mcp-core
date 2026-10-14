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

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.workflow.ast.CallValue;

/**
 * One rule of the composition rule file (PM-IMPL-5, PM-EXT-7): a {@code when} condition over one fact and a
 * {@code then} of prunes and bindings. A rule never adds an element, never raises a budget and never names a gate;
 * {@link CompositionRules} refuses a file whose rule would.
 * <p>
 * A prune names its target as a selector the composer resolves against the template: an exact id, a policy action of
 * every call site as {@code <call_site>/<action>} with the call site written as a star, or every step below an order
 * as {@code order<<n>}.
 *
 * @param id       the id of the rule, unique in its file
 * @param when     the condition over one fact
 * @param prunes   the template elements the rule leaves out when it holds
 * @param bindings the policy call arguments the rule fixes when it holds
 * @since 0.1
 */
public record CompositionRule(String id, Condition when, List<Prune> prunes, List<Binding> bindings) {

    /**
     * Refuses a missing component and copies the lists into unmodifiable lists that keep their order.
     *
     * @param id       the id of the rule
     * @param when     the condition over one fact
     * @param prunes   the template elements the rule leaves out
     * @param bindings the policy call arguments the rule fixes
     */
    public CompositionRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(when, "when");
        prunes = List.copyOf(prunes);
        bindings = List.copyOf(bindings);
    }

    /**
     * The condition of a rule over one fact of {@link PlanFacts}. An unknown fact makes every condition false except
     * {@link Unset}.
     *
     * @since 0.1
     */
    public sealed interface Condition permits Equals, In, Empty, Unset, Disjoint {

        /**
         * @return the name of the fact the condition reads
         */
        String fact();

        /**
         * @param facts the facts of the plan
         * @return whether the condition holds
         */
        boolean holds(PlanFacts facts);
    }

    /**
     * The fact equals a value.
     *
     * @param fact  the fact name
     * @param value the value
     * @since 0.1
     */
    public record Equals(String fact, String value) implements Condition {

        @Override
        public boolean holds(PlanFacts facts) {
            return facts.text(fact).filter(value::equals).isPresent();
        }
    }

    /**
     * The fact is one of several values.
     *
     * @param fact   the fact name
     * @param values the values
     * @since 0.1
     */
    public record In(String fact, List<String> values) implements Condition {

        /**
         * Copies the values into an unmodifiable list.
         *
         * @param fact   the fact name
         * @param values the values
         */
        public In {
            values = List.copyOf(values);
        }

        @Override
        public boolean holds(PlanFacts facts) {
            return facts.text(fact).filter(values::contains).isPresent();
        }
    }

    /**
     * The fact is known and empty: an empty text or an empty list.
     *
     * @param fact the fact name
     * @since 0.1
     */
    public record Empty(String fact) implements Condition {

        @Override
        public boolean holds(PlanFacts facts) {
            return facts.text(fact).filter(String::isEmpty).isPresent()
                    || facts.list(fact).filter(List::isEmpty).isPresent();
        }
    }

    /**
     * The fact is not set: the configuration does not set the key, or the plan fact is unknown.
     *
     * @param fact the fact name
     * @since 0.1
     */
    public record Unset(String fact) implements Condition {

        @Override
        public boolean holds(PlanFacts facts) {
            return facts.text(fact).isEmpty() && facts.list(fact).isEmpty();
        }
    }

    /**
     * The fact is a known, non-empty list of paths none of which matches any of the globs. An unknown or empty list
     * is indeterminate and never disjoint: absence of a footprint never means disjoint from everything.
     * <p>
     * A glob matches a whole path: {@code **} matches any sequence of characters, {@code *} any sequence without a
     * {@code /}, {@code ?} one character other than {@code /}; every other character matches itself.
     *
     * @param fact  the fact name
     * @param globs the globs
     * @since 0.1
     */
    public record Disjoint(String fact, List<String> globs) implements Condition {

        /**
         * Copies the globs into an unmodifiable list.
         *
         * @param fact  the fact name
         * @param globs the globs
         */
        public Disjoint {
            globs = List.copyOf(globs);
        }

        @Override
        public boolean holds(PlanFacts facts) {
            var patterns = globs.stream().map(Disjoint::compile).toList();
            return facts.list(fact).filter(paths -> !paths.isEmpty())
                    .filter(paths -> paths.stream().noneMatch(
                            path -> patterns.stream().anyMatch(pattern -> pattern.matcher(path).matches())))
                    .isPresent();
        }

        static Pattern compile(String glob) {
            var regex = new StringBuilder();
            for (var index = 0; index < glob.length(); index++) {
                var character = glob.charAt(index);
                if (character == '*' && index + 1 < glob.length() && glob.charAt(index + 1) == '*') {
                    regex.append(".*");
                    index++;
                } else if (character == '*') {
                    regex.append("[^/]*");
                } else if (character == '?') {
                    regex.append("[^/]");
                } else {
                    regex.append(Pattern.quote(String.valueOf(character)));
                }
            }
            return Pattern.compile(regex.toString());
        }
    }

    /**
     * A template element a rule leaves out.
     *
     * @param element the kind of the element
     * @param id      the selector of the element: an exact id, a policy action of every call site (call site written
     *                as a star), or {@code order<<n>}
     * @param reason  the prune reason
     * @since 0.1
     */
    public record Prune(PrunedElement.Kind element, String id, PruneReason reason) {

        /**
         * Refuses a missing component.
         *
         * @param element the kind of the element
         * @param id      the selector of the element
         * @param reason  the prune reason
         */
        public Prune {
            Objects.requireNonNull(element, "element");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * An argument a rule fixes at a policy call site.
     *
     * @param callSite the state id of the call site, or {@code *} for every call site
     * @param argument the argument name
     * @param value    the value the rule binds
     * @since 0.1
     */
    public record Binding(String callSite, String argument, CallValue value) {

        /**
         * Refuses a missing component.
         *
         * @param callSite the call site
         * @param argument the argument name
         * @param value    the value the rule binds
         */
        public Binding {
            Objects.requireNonNull(callSite, "callSite");
            Objects.requireNonNull(argument, "argument");
            Objects.requireNonNull(value, "value");
        }
    }
}
