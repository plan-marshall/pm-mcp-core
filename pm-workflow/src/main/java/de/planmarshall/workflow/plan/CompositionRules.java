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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.plan.HeaderJson.ArrayNode;
import de.planmarshall.workflow.plan.HeaderJson.BooleanNode;
import de.planmarshall.workflow.plan.HeaderJson.Node;
import de.planmarshall.workflow.plan.HeaderJson.NumberNode;
import de.planmarshall.workflow.plan.HeaderJson.ObjectNode;
import de.planmarshall.workflow.plan.HeaderJson.StringNode;
import de.planmarshall.workflow.verify.CompiledInvariants;

/**
 * The composition rule file (PM-IMPL-5, PM-EXT-7): the declarative rules that map a plan fact or an effective
 * configuration value to prunes and policy bindings, with the revision and the SHA-256 digest of the file, both inputs
 * of every instance header.
 * <p>
 * The shipped file is the classpath resource {@value #RESOURCE} of this module; it has no project copy and nothing
 * overrides it. Its closed shape: an object with {@code format_version} {@value #FORMAT_VERSION}, a non-empty
 * {@code revision} and the list {@code rules}; each rule an object with a unique {@code id}, a {@code when} naming one
 * registered {@code fact} and exactly one of {@code equals} (a string), {@code in} (a list of strings), {@code empty}
 * ({@code true}), {@code unset} ({@code true}) or {@code disjoint} (a list of globs), and a {@code then} with the lists
 * {@code prune} (entries of {@code element}, {@code id} and {@code reason} from the closed enumerations) and
 * {@code bind} (entries of {@code call_site}, {@code argument} and a literal {@code value}). A registered fact is one of
 * the plan facts of {@link PlanFacts}, an axis fact of {@link PlanFacts} (the variant bound on a plan behaviour axis,
 * {@code axes.<axis>}, and a declaration of it, {@code axes.<axis>.<declaration>}, the names of the compiled
 * declaration schemas of the axis's variants), or a configuration key ({@code derived.push_remote},
 * {@code integrations.<provider>}).
 * <p>
 * A file that breaks this shape is refused as a value, never by an exception, and so is a rule that would add an
 * element ({@code then.add}), raise a budget (a binding of a budget parameter of the remediation schema,
 * {@code retry_budget}, {@code fix_budget} or {@code waiver_wait_seconds}, read from its one definition
 * {@link CompiledInvariants#remediationBudgetParameters()}) or name a gate (a {@code gate} element or member); each of these three is refused by its
 * own {@link Refusal}, so the compiled invariants and the operator's waivers stay the only authority over gates and
 * budgets.
 *
 * @param revision the revision of the rule file
 * @param digest   the SHA-256 of the file bytes, 64 lowercase hexadecimal characters
 * @param rules    the rules in file order
 * @since 0.1
 */
public record CompositionRules(String revision, String digest, List<CompositionRule> rules) {

    /** The classpath location of the shipped rule file. */
    public static final String RESOURCE = "/de/planmarshall/workflow/plan/composition-rules.json";

    /** The only format version of the rule file. */
    public static final int FORMAT_VERSION = 1;

    private static final String REVISION_MEMBER = "revision";
    private static final String PRUNE_MEMBER = "prune";
    private static final String ELEMENT_MEMBER = "element";
    private static final Set<String> CONFIGURATION_FACTS = Set.of("derived.push_remote");
    private static final String PROVIDER_FACT_PREFIX = "integrations.";
    private static final Pattern RULE_ID = Pattern.compile("^[a-z][a-z0-9-]{0,63}$");
    private static final List<String> OPERATORS = List.of("equals", "in", "empty", "unset", "disjoint");
    private static final String GATE = "gate";

    /**
     * Refuses a missing component and copies the rules into an unmodifiable list that keeps their order.
     *
     * @param revision the revision of the rule file
     * @param digest   the SHA-256 of the file bytes
     * @param rules    the rules in file order
     */
    public CompositionRules {
        Objects.requireNonNull(revision, REVISION_MEMBER);
        Objects.requireNonNull(digest, "digest");
        rules = List.copyOf(rules);
    }

    /**
     * Loads the shipped rule file from the classpath.
     *
     * @return the loaded rules, or the refusal of the file
     */
    public static Loading shipped() {
        try (var in = CompositionRules.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new Refused(Refusal.UNREADABLE, "the resource " + RESOURCE + " is missing");
            }
            return parse(in.readAllBytes());
        } catch (IOException e) {
            return new Refused(Refusal.UNREADABLE, "the resource " + RESOURCE + " cannot be read: " + e.getMessage());
        }
    }

    /**
     * Parses and checks the bytes of a rule file.
     *
     * @param content the file bytes, UTF-8
     * @return the loaded rules, or the refusal of the file
     */
    public static Loading parse(byte[] content) {
        Objects.requireNonNull(content, "content");
        Node root;
        try {
            root = HeaderJson.parse(new String(content, StandardCharsets.UTF_8));
        } catch (HeaderJson.Malformed e) {
            return new Refused(Refusal.MALFORMED, e.getMessage());
        }
        var checker = new Checker();
        var rules = checker.file(root);
        return checker.refusal.<Loading>map(refusal -> refusal)
                .orElseGet(() -> new Loaded(new CompositionRules(checker.revision, PlanFacts.sha256(content),
                        rules)));
    }

    /**
     * The outcome of loading a rule file.
     *
     * @since 0.1
     */
    public sealed interface Loading permits Loaded, Refused {
    }

    /**
     * A rule file that satisfies its shape.
     *
     * @param rules the loaded rules
     * @since 0.1
     */
    public record Loaded(CompositionRules rules) implements Loading {
    }

    /**
     * A rule file that was refused.
     *
     * @param refusal why
     * @param detail  what is wrong, naming the rule or member
     * @since 0.1
     */
    public record Refused(Refusal refusal, String detail) implements Loading {
    }

    /**
     * Why a rule file is refused.
     *
     * @since 0.1
     */
    public enum Refusal {
        /** The file cannot be read. */
        UNREADABLE,
        /** The file is no JSON or breaks the closed shape of a rule file. */
        MALFORMED,
        /** A rule would add an element no template declares. */
        ADDS_ELEMENT,
        /** A rule would bind a budget. */
        RAISES_BUDGET,
        /** A rule names a gate. */
        NAMES_GATE
    }

    /** Walks a parsed rule file; the first refusal found ends the walk. */
    private static final class Checker {

        private Optional<Refused> refusal = Optional.empty();
        private String revision;

        List<CompositionRule> file(Node root) {
            var members = object(root, "the rule file");
            if (refusal.isPresent()
                    || !only(members, "the rule file", Set.of("format_version", REVISION_MEMBER, "rules"))) {
                return List.of();
            }
            if (!(members.get("format_version") instanceof NumberNode(var version))
                    || version.compareTo(BigDecimal.valueOf(FORMAT_VERSION)) != 0) {
                return refuse(Refusal.MALFORMED, "'format_version' must be " + FORMAT_VERSION);
            }
            var revisionText = text(members.get(REVISION_MEMBER), "'revision'");
            if (revisionText.isEmpty() || revisionText.get().isEmpty()) {
                return refuse(Refusal.MALFORMED, "'revision' must be a non-empty string");
            }
            revision = revisionText.get();
            if (!(members.get("rules") instanceof ArrayNode(var elements))) {
                return refuse(Refusal.MALFORMED, "'rules' must be an array");
            }
            var rules = new ArrayList<CompositionRule>();
            var ids = new HashSet<String>();
            for (var element : elements) {
                var rule = rule(element);
                if (rule.isEmpty()) {
                    return List.of();
                }
                if (!ids.add(rule.get().id())) {
                    return refuse(Refusal.MALFORMED, "rule '%s' is declared twice".formatted(rule.get().id()));
                }
                rules.add(rule.get());
            }
            return rules;
        }

        private Optional<CompositionRule> rule(Node node) {
            var members = object(node, "a rule");
            if (refusal.isPresent()) {
                return Optional.empty();
            }
            var id = text(members.get("id"), "the id of a rule");
            if (id.isEmpty() || !RULE_ID.matcher(id.get()).matches()) {
                refuse(Refusal.MALFORMED, "a rule id must match " + RULE_ID.pattern());
                return Optional.empty();
            }
            var name = "rule '%s'".formatted(id.get());
            if (!only(members, name, Set.of("id", "when", "then"))) {
                return Optional.empty();
            }
            var when = condition(members.get("when"), name);
            var then = object(members.get("then"), name + " 'then'");
            if (when.isEmpty() || refusal.isPresent()) {
                return Optional.empty();
            }
            if (then.containsKey("add")) {
                refuse(Refusal.ADDS_ELEMENT, name + " adds an element");
                return Optional.empty();
            }
            if (then.containsKey(GATE) || then.containsKey("waive")) {
                refuse(Refusal.NAMES_GATE, name + " names a gate");
                return Optional.empty();
            }
            if (!only(then, name + " 'then'", Set.of(PRUNE_MEMBER, "bind"))) {
                return Optional.empty();
            }
            var prunes = list(then.get(PRUNE_MEMBER), name + " 'prune'").stream()
                    .map(entry -> prune(entry, name)).flatMap(Optional::stream).toList();
            var bindings = list(then.get("bind"), name + " 'bind'").stream()
                    .map(entry -> binding(entry, name)).flatMap(Optional::stream).toList();
            return refusal.isPresent() ? Optional.empty()
                    : Optional.of(new CompositionRule(id.get(), when.get(), prunes, bindings));
        }

        private Optional<CompositionRule.Condition> condition(Node node, String name) {
            var members = object(node, name + " 'when'");
            if (refusal.isPresent()) {
                return Optional.empty();
            }
            var fact = text(members.get("fact"), name + " 'fact'");
            if (fact.isEmpty()) {
                return Optional.empty();
            }
            if (!registered(fact.get())) {
                refuse(Refusal.MALFORMED, "%s reads the unregistered fact '%s'".formatted(name, fact.get()));
                return Optional.empty();
            }
            var operators = members.keySet().stream().filter(key -> !"fact".equals(key)).toList();
            if (operators.size() != 1 || !OPERATORS.contains(operators.getFirst())) {
                refuse(Refusal.MALFORMED, "%s 'when' must name its fact and exactly one of %s".formatted(name,
                        OPERATORS));
                return Optional.empty();
            }
            var operand = members.get(operators.getFirst());
            return switch (operators.getFirst()) {
                case "equals" -> text(operand, name + " 'equals'")
                        .map(value -> (CompositionRule.Condition) new CompositionRule.Equals(fact.get(), value));
                case "in" -> Optional.of(new CompositionRule.In(fact.get(), texts(operand, name + " 'in'")));
                case "empty" -> flag(operand, name + " 'empty'")
                        ? Optional.of(new CompositionRule.Empty(fact.get())) : Optional.empty();
                case "unset" -> flag(operand, name + " 'unset'")
                        ? Optional.of(new CompositionRule.Unset(fact.get())) : Optional.empty();
                default -> Optional.of(new CompositionRule.Disjoint(fact.get(), texts(operand, name + " 'disjoint'")));
            };
        }

        private Optional<CompositionRule.Prune> prune(Node node, String name) {
            var members = object(node, name + " prune entry");
            if (refusal.isPresent()) {
                return Optional.empty();
            }
            if (members.get(ELEMENT_MEMBER) instanceof StringNode(var kind) && GATE.equals(kind)) {
                refuse(Refusal.NAMES_GATE, name + " prunes a gate");
                return Optional.empty();
            }
            if (!only(members, name + " prune entry", Set.of(ELEMENT_MEMBER, "id", "reason"))) {
                return Optional.empty();
            }
            var element = PrunedElement.Kind.fromWireName(text(members.get(ELEMENT_MEMBER),
                    name + " 'element'").orElse("null"));
            var id = text(members.get("id"), name + " prune 'id'");
            var reason = PruneReason.fromWireName(text(members.get("reason"), name + " 'reason'").orElse("null"));
            if (refusal.isPresent()) {
                return Optional.empty();
            }
            if (element.isEmpty() || reason.isEmpty() || id.isEmpty() || id.get().isEmpty()) {
                refuse(Refusal.MALFORMED, name + " has a prune entry with an undeclared element, reason or empty id");
                return Optional.empty();
            }
            return Optional.of(new CompositionRule.Prune(element.get(), id.get(), reason.get()));
        }

        private Optional<CompositionRule.Binding> binding(Node node, String name) {
            var members = object(node, name + " bind entry");
            if (refusal.isPresent()) {
                return Optional.empty();
            }
            var argument = text(members.get("argument"), name + " bind 'argument'");
            if (argument.isPresent()
                    && CompiledInvariants.remediationBudgetParameters().contains(argument.get())) {
                refuse(Refusal.RAISES_BUDGET, "%s binds the budget '%s'".formatted(name, argument.get()));
                return Optional.empty();
            }
            if (argument.filter(GATE::equals).isPresent()) {
                refuse(Refusal.NAMES_GATE, name + " binds a gate");
                return Optional.empty();
            }
            if (!only(members, name + " bind entry", Set.of("call_site", "argument", "value"))) {
                return Optional.empty();
            }
            var callSite = text(members.get("call_site"), name + " bind 'call_site'");
            var value = literal(members.get("value"), name + " bind 'value'");
            return refusal.isPresent() ? Optional.empty()
                    : Optional.of(new CompositionRule.Binding(callSite.get(), argument.get(), value.get()));
        }

        private Optional<CallValue> literal(Node node, String what) {
            return switch (node) {
                case StringNode(var text) -> Optional.of(new CallValue.EnumValue(text));
                case NumberNode number when number.integral() && number.value().abs().compareTo(
                        BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0 ->
                    Optional.of(new CallValue.IntValue(number.value().intValueExact()));
                case ArrayNode array when array.elements().stream().allMatch(StringNode.class::isInstance)
                        && array.elements().stream().distinct().count() == array.elements().size() ->
                    Optional.of(new CallValue.EnumList(texts(array, what)));
                case null, default -> {
                    refuse(Refusal.MALFORMED, what + " must be a string, an integer or a list of distinct strings");
                    yield Optional.empty();
                }
            };
        }

        private boolean registered(String fact) {
            return PlanFacts.PLAN_FACT_NAMES.contains(fact) || PlanFacts.AXIS_FACT_NAMES.contains(fact)
                    || CONFIGURATION_FACTS.contains(fact)
                    || fact.startsWith(PROVIDER_FACT_PREFIX) && fact.length() > PROVIDER_FACT_PREFIX.length();
        }

        /**
         * The members of an object; for any other node the refusal is recorded and the map is empty, so the caller
         * decides on {@link #refusal}, never on the map.
         */
        private Map<String, Node> object(Node node, String what) {
            if (node instanceof ObjectNode(var members)) {
                return members;
            }
            refuse(Refusal.MALFORMED, what + " must be an object");
            return Map.of();
        }

        private boolean only(Map<String, Node> members, String what, Set<String> allowed) {
            for (var key : members.keySet()) {
                if (!allowed.contains(key)) {
                    refuse(Refusal.MALFORMED, "%s has the undeclared member '%s'".formatted(what, key));
                    return false;
                }
            }
            for (var key : allowed) {
                if (!members.containsKey(key) && !PRUNE_MEMBER.equals(key) && !"bind".equals(key)) {
                    refuse(Refusal.MALFORMED, "%s lacks the member '%s'".formatted(what, key));
                    return false;
                }
            }
            return true;
        }

        private List<Node> list(Node node, String what) {
            if (node == null) {
                return List.of();
            }
            if (node instanceof ArrayNode(var elements)) {
                return elements;
            }
            refuse(Refusal.MALFORMED, what + " must be an array");
            return List.of();
        }

        private Optional<String> text(Node node, String what) {
            if (node instanceof StringNode(var value)) {
                return Optional.of(value);
            }
            refuse(Refusal.MALFORMED, what + " must be a string");
            return Optional.empty();
        }

        private List<String> texts(Node node, String what) {
            var values = list(node, what).stream().map(element -> text(element, what + " element"))
                    .flatMap(Optional::stream).toList();
            return refusal.isPresent() ? List.of() : values;
        }

        private boolean flag(Node node, String what) {
            if (node instanceof BooleanNode(var value) && value) {
                return true;
            }
            refuse(Refusal.MALFORMED, what + " must be true");
            return false;
        }

        private <T> List<T> refuse(Refusal reason, String detail) {
            if (refusal.isEmpty()) {
                refusal = Optional.of(new Refused(reason, detail));
            }
            return List.of();
        }
    }
}
