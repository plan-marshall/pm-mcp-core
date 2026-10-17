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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.plan.HeaderJson.ArrayNode;
import de.planmarshall.workflow.plan.HeaderJson.Node;
import de.planmarshall.workflow.plan.HeaderJson.NullNode;
import de.planmarshall.workflow.plan.HeaderJson.NumberNode;
import de.planmarshall.workflow.plan.HeaderJson.ObjectNode;
import de.planmarshall.workflow.plan.HeaderJson.StringNode;

/**
 * Checks a plan instance header against every constraint of the {@code PlanInstanceHeader} schema (PM-IMPL-5,
 * PM-EXT-7) and reports every constraint it breaks as a {@link HeaderViolation}; it never throws for a bad header.
 * <p>
 * The constraints are those of the schema, checked in code over the streaming API of jackson-core: the required
 * members of every object, no member the schema does not declare at any level ({@code additionalProperties: false}),
 * the one JSON type of each member, the constant {@code format_version} {@value PlanInstanceHeader#FORMAT_VERSION},
 * {@code version} from {@code 1}, the closed enumerations (composition event, lane, posture, element kind, prune
 * reason, policy unit, argument source, unit origin), the patterns of the plan id, of every digest (64 lowercase
 * hexadecimal characters), of the Phase 6 step ids and of the waiver ids, and {@code composed_at} as an RFC 3339
 * date-time that the reader can parse. A JSON text that is no single JSON value, or names a member of an object
 * twice, is one violation of the header as a whole or of that member.
 * <p>
 * A violation names the JSON pointer of the member or element that breaks the constraint and what is wrong. A member
 * of the wrong type is reported once, and what lies inside it is not checked further.
 *
 * @since 0.1
 */
public final class HeaderSchemaCheck {

    static final String MISSING = "required member is missing";
    static final String NOT_ALLOWED = "member is not declared by the schema";
    static final String NOT_OBJECT = "must be an object";
    static final String NOT_ARRAY = "must be an array";
    static final String NOT_STRING = "must be a string";
    static final String NOT_STRING_OR_NULL = "must be a string or null";
    static final String NOT_INTEGER = "must be an integer";
    static final String NOT_FORMAT_VERSION = "must be " + PlanInstanceHeader.FORMAT_VERSION;
    static final String BELOW_ONE = "must be at least 1";
    static final String NOT_DATE_TIME = "must be an RFC 3339 date-time the header reader can parse "
            + "(an offset within +-18:00, no leap second)";

    /** The remediation policy units a policy call may name. */
    static final List<String> POLICY_UNITS = List.of("remediation.ci-failure", "remediation.review-findings",
            "remediation.build-failure", "remediation.merge-conflict", "remediation.qgate-findings",
            "remediation.analysis");

    private static final Pattern PLAN_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");
    private static final Pattern DIGEST = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern PHASE_6_STEP = Pattern.compile("^(default|project):[a-z][a-z0-9-]{0,63}$");
    private static final Pattern WAIVER_ID = Pattern.compile("^W-\\d+$");
    /** The date of an RFC 3339 date-time, the ten characters before its {@code T}. */
    private static final Pattern DATE = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$");
    /** The time with its offset of an RFC 3339 date-time, everything after its {@code T}. */
    private static final Pattern TIME_WITH_OFFSET = Pattern.compile(
            "^([01]\\d|2[0-3]):[0-5]\\d:([0-5]\\d|60)(\\.\\d+)?([Zz]|[+-]([01]\\d|2[0-3]):[0-5]\\d)$");
    /** The index of the {@code T} that separates the date from the time. */
    private static final int TIME_SEPARATOR = 10;

    private static final List<String> EVENTS = Arrays.stream(CompositionEvent.values())
            .map(CompositionEvent::wireName).toList();
    private static final List<String> LANES = Arrays.stream(PlanInstanceHeader.Lane.values())
            .map(PlanInstanceHeader.Lane::wireName).toList();
    private static final List<String> POSTURES = Arrays.stream(PlanInstanceHeader.Posture.values())
            .map(PlanInstanceHeader.Posture::wireName).toList();
    private static final List<String> ELEMENTS = Arrays.stream(PrunedElement.Kind.values())
            .map(PrunedElement.Kind::wireName).toList();
    private static final List<String> REASONS = Arrays.stream(PruneReason.values()).map(PruneReason::wireName)
            .toList();
    private static final List<String> SOURCES = Arrays.stream(BoundArgument.Source.values())
            .map(BoundArgument.Source::wireName).toList();
    private static final List<String> ORIGINS = Arrays.stream(UnitOrigin.values()).map(UnitReference::wireName)
            .toList();

    private static final List<String> ROOT_REQUIRED = List.of(HeaderJson.FORMAT_VERSION, HeaderJson.PLAN_ID,
            HeaderJson.VERSION, HeaderJson.EVENT, HeaderJson.COMPOSED_AT, HeaderJson.INPUTS, HeaderJson.LANE,
            HeaderJson.STEPS, HeaderJson.PRUNED, HeaderJson.POLICY_CALLS, HeaderJson.WAIVED_GATES);
    private static final List<String> ROOT_OPTIONAL = List.of(HeaderJson.POSTURE);
    private static final List<String> INPUTS_REQUIRED = List.of(HeaderJson.CONFIG_HASH, HeaderJson.TEMPLATES,
            HeaderJson.SUB_UNITS, HeaderJson.COMPOSITION_RULES, HeaderJson.PLAN_FACTS_DIGEST);
    private static final List<String> INPUTS_OPTIONAL = List.of(HeaderJson.REALIZED_FOOTPRINT_DIGEST,
            HeaderJson.CHANGE_TYPE, HeaderJson.PROVIDERS_DIGEST);
    private static final List<String> RULES_REQUIRED = List.of(HeaderJson.REVISION, HeaderJson.DIGEST);
    private static final List<String> STEPS_REQUIRED = List.of(HeaderJson.PHASE_5_VERIFICATION, HeaderJson.PHASE_6);
    private static final List<String> PRUNED_REQUIRED = List.of(HeaderJson.ELEMENT, HeaderJson.ID,
            HeaderJson.REASON, HeaderJson.SOURCE_FACT);
    private static final List<String> CALL_REQUIRED = List.of(HeaderJson.CALL_SITE, HeaderJson.UNIT,
            HeaderJson.ARGS);
    private static final List<String> ARGUMENT_REQUIRED = List.of(HeaderJson.VALUE, HeaderJson.SOURCE);
    private static final List<String> ARGUMENT_OPTIONAL = List.of(HeaderJson.FILE, HeaderJson.WAIVER_ID);
    private static final List<String> GATE_REQUIRED = List.of(HeaderJson.GATE, HeaderJson.WAIVER_ID);
    private static final List<String> UNIT_REF_REQUIRED = List.of(HeaderJson.UNIT, HeaderJson.DIGEST,
            HeaderJson.ORIGIN);
    private static final List<String> UNIT_REF_OPTIONAL = List.of(HeaderJson.FILE);

    private HeaderSchemaCheck() {
    }

    /**
     * Checks the JSON of an {@code instance} line.
     *
     * @param json the header JSON
     * @return every violation, in the order the header is walked; empty when the header satisfies the schema
     */
    public static List<HeaderViolation> check(String json) {
        Objects.requireNonNull(json, "json");
        try {
            return check(HeaderJson.parse(json));
        } catch (HeaderJson.Malformed e) {
            return List.of(new HeaderViolation(e.pointer(), e.reason()));
        }
    }

    /**
     * Checks a header as it would be written.
     *
     * @param header the header
     * @return every violation; empty when the header satisfies the schema
     */
    public static List<HeaderViolation> check(PlanInstanceHeader header) {
        Objects.requireNonNull(header, "header");
        return check(HeaderJson.toNode(header));
    }

    static List<HeaderViolation> check(Node root) {
        var out = new ArrayList<HeaderViolation>();
        object(root, "", out).ifPresent(members -> {
            declared(members, "", ROOT_REQUIRED, ROOT_OPTIONAL, out);
            member(members, "", HeaderJson.FORMAT_VERSION, (node, pointer) -> integer(node, pointer, out)
                    .filter(value -> value.compareTo(BigDecimal.valueOf(PlanInstanceHeader.FORMAT_VERSION)) != 0)
                    .ifPresent(value -> out.add(new HeaderViolation(pointer, NOT_FORMAT_VERSION))));
            member(members, "", HeaderJson.PLAN_ID, (node, pointer) -> patterned(node, pointer, PLAN_ID, out));
            member(members, "", HeaderJson.VERSION, (node, pointer) -> integer(node, pointer, out)
                    .filter(value -> value.compareTo(BigDecimal.ONE) < 0)
                    .ifPresent(value -> out.add(new HeaderViolation(pointer, BELOW_ONE))));
            member(members, "", HeaderJson.EVENT, (node, pointer) -> enumerated(node, pointer, EVENTS, out));
            member(members, "", HeaderJson.COMPOSED_AT, (node, pointer) -> string(node, pointer, out)
                    .filter(text -> !isDateTime(text))
                    .ifPresent(text -> out.add(new HeaderViolation(pointer, NOT_DATE_TIME))));
            member(members, "", HeaderJson.INPUTS, (node, pointer) -> inputs(node, pointer, out));
            member(members, "", HeaderJson.LANE, (node, pointer) -> nullableEnumerated(node, pointer, LANES, out));
            member(members, "", HeaderJson.POSTURE,
                    (node, pointer) -> nullableEnumerated(node, pointer, POSTURES, out));
            member(members, "", HeaderJson.STEPS, (node, pointer) -> steps(node, pointer, out));
            member(members, "", HeaderJson.PRUNED,
                    (node, pointer) -> each(node, pointer, out, (element, at) -> pruned(element, at, out)));
            member(members, "", HeaderJson.POLICY_CALLS,
                    (node, pointer) -> each(node, pointer, out, (element, at) -> policyCall(element, at, out)));
            member(members, "", HeaderJson.WAIVED_GATES,
                    (node, pointer) -> each(node, pointer, out, (element, at) -> waivedGate(element, at, out)));
        });
        return List.copyOf(out);
    }

    /** A date-time is valid when it has the RFC 3339 shape and the header reader can parse it. */
    private static boolean isDateTime(String text) {
        if (!hasDateTimeShape(text)) {
            return false;
        }
        try {
            HeaderJson.instant(text);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    /**
     * The RFC 3339 shape of a date-time: a date, a {@code T} or {@code t} at index {@value #TIME_SEPARATOR}, and a time
     * with its offset. The two parts are matched apart, so neither pattern grows past a readable size.
     */
    private static boolean hasDateTimeShape(String text) {
        if (text.length() <= TIME_SEPARATOR) {
            return false;
        }
        var separator = text.charAt(TIME_SEPARATOR);
        return (separator == 'T' || separator == 't')
                && DATE.matcher(text.substring(0, TIME_SEPARATOR)).matches()
                && TIME_WITH_OFFSET.matcher(text.substring(TIME_SEPARATOR + 1)).matches();
    }

    private static void inputs(Node node, String pointer, List<HeaderViolation> out) {
        object(node, pointer, out).ifPresent(members -> {
            declared(members, pointer, INPUTS_REQUIRED, INPUTS_OPTIONAL, out);
            member(members, pointer, HeaderJson.CONFIG_HASH, (value, at) -> string(value, at, out));
            member(members, pointer, HeaderJson.TEMPLATES,
                    (value, at) -> each(value, at, out, (element, ref) -> unitRef(element, ref, out)));
            member(members, pointer, HeaderJson.SUB_UNITS,
                    (value, at) -> each(value, at, out, (element, ref) -> unitRef(element, ref, out)));
            member(members, pointer, HeaderJson.COMPOSITION_RULES, (value, at) -> object(value, at, out)
                    .ifPresent(rules -> {
                        declared(rules, at, RULES_REQUIRED, List.of(), out);
                        member(rules, at, HeaderJson.REVISION, (revision, path) -> string(revision, path, out));
                        member(rules, at, HeaderJson.DIGEST, (digest, path) -> patterned(digest, path, DIGEST, out));
                    }));
            member(members, pointer, HeaderJson.PLAN_FACTS_DIGEST,
                    (value, at) -> patterned(value, at, DIGEST, out));
            for (var optional : INPUTS_OPTIONAL) {
                member(members, pointer, optional, (value, at) -> nullableString(value, at, out));
            }
        });
    }

    private static void steps(Node node, String pointer, List<HeaderViolation> out) {
        object(node, pointer, out).ifPresent(members -> {
            declared(members, pointer, STEPS_REQUIRED, List.of(), out);
            member(members, pointer, HeaderJson.PHASE_5_VERIFICATION,
                    (value, at) -> each(value, at, out, (step, path) -> string(step, path, out)));
            member(members, pointer, HeaderJson.PHASE_6,
                    (value, at) -> each(value, at, out, (step, path) -> patterned(step, path, PHASE_6_STEP, out)));
        });
    }

    private static void pruned(Node node, String pointer, List<HeaderViolation> out) {
        object(node, pointer, out).ifPresent(members -> {
            declared(members, pointer, PRUNED_REQUIRED, List.of(), out);
            member(members, pointer, HeaderJson.ELEMENT, (value, at) -> enumerated(value, at, ELEMENTS, out));
            member(members, pointer, HeaderJson.ID, (value, at) -> string(value, at, out));
            member(members, pointer, HeaderJson.REASON, (value, at) -> enumerated(value, at, REASONS, out));
            member(members, pointer, HeaderJson.SOURCE_FACT, (value, at) -> string(value, at, out));
        });
    }

    private static void policyCall(Node node, String pointer, List<HeaderViolation> out) {
        object(node, pointer, out).ifPresent(members -> {
            declared(members, pointer, CALL_REQUIRED, List.of(), out);
            member(members, pointer, HeaderJson.CALL_SITE, (value, at) -> string(value, at, out));
            member(members, pointer, HeaderJson.UNIT, (value, at) -> enumerated(value, at, POLICY_UNITS, out));
            member(members, pointer, HeaderJson.ARGS, (value, at) -> object(value, at, out)
                    .ifPresent(args -> args.forEach(
                            (name, argument) -> argument(argument, HeaderJson.child(at, name), out))));
        });
    }

    private static void argument(Node node, String pointer, List<HeaderViolation> out) {
        object(node, pointer, out).ifPresent(members -> {
            declared(members, pointer, ARGUMENT_REQUIRED, ARGUMENT_OPTIONAL, out);
            member(members, pointer, HeaderJson.SOURCE, (value, at) -> enumerated(value, at, SOURCES, out));
            member(members, pointer, HeaderJson.FILE, (value, at) -> nullableString(value, at, out));
            member(members, pointer, HeaderJson.WAIVER_ID,
                    (value, at) -> nullableString(value, at, out).ifPresent(text -> match(text, at, WAIVER_ID, out)));
        });
    }

    private static void waivedGate(Node node, String pointer, List<HeaderViolation> out) {
        object(node, pointer, out).ifPresent(members -> {
            declared(members, pointer, GATE_REQUIRED, List.of(), out);
            member(members, pointer, HeaderJson.GATE, (value, at) -> string(value, at, out));
            member(members, pointer, HeaderJson.WAIVER_ID, (value, at) -> patterned(value, at, WAIVER_ID, out));
        });
    }

    private static void unitRef(Node node, String pointer, List<HeaderViolation> out) {
        object(node, pointer, out).ifPresent(members -> {
            declared(members, pointer, UNIT_REF_REQUIRED, UNIT_REF_OPTIONAL, out);
            member(members, pointer, HeaderJson.UNIT, (value, at) -> string(value, at, out));
            member(members, pointer, HeaderJson.FILE, (value, at) -> nullableString(value, at, out));
            member(members, pointer, HeaderJson.DIGEST, (value, at) -> patterned(value, at, DIGEST, out));
            member(members, pointer, HeaderJson.ORIGIN, (value, at) -> enumerated(value, at, ORIGINS, out));
        });
    }

    // ------------------------------------------------------------------------------------------------- building blocks

    /** Reports every required member that is missing and every member the schema does not declare. */
    private static void declared(Map<String, Node> members, String pointer, List<String> required,
            List<String> optional, List<HeaderViolation> out) {
        for (var name : required) {
            if (!members.containsKey(name)) {
                out.add(new HeaderViolation(HeaderJson.child(pointer, name), MISSING));
            }
        }
        for (var name : members.keySet()) {
            if (!required.contains(name) && !optional.contains(name)) {
                out.add(new HeaderViolation(HeaderJson.child(pointer, name), NOT_ALLOWED));
            }
        }
    }

    /** Checks a member if it is present; its absence is reported by {@link #declared}. */
    private static void member(Map<String, Node> members, String pointer, String name,
            BiConsumer<Node, String> check) {
        var node = members.get(name);
        if (node != null) {
            check.accept(node, HeaderJson.child(pointer, name));
        }
    }

    private static void each(Node node, String pointer, List<HeaderViolation> out, BiConsumer<Node, String> check) {
        if (node instanceof ArrayNode(var elements)) {
            for (var index = 0; index < elements.size(); index++) {
                check.accept(elements.get(index), HeaderJson.child(pointer, Integer.toString(index)));
            }
        } else {
            out.add(new HeaderViolation(pointer, NOT_ARRAY));
        }
    }

    private static Optional<Map<String, Node>> object(Node node, String pointer, List<HeaderViolation> out) {
        if (node instanceof ObjectNode(var members)) {
            return Optional.of(members);
        }
        out.add(new HeaderViolation(pointer, NOT_OBJECT));
        return Optional.empty();
    }

    private static Optional<String> string(Node node, String pointer, List<HeaderViolation> out) {
        if (node instanceof StringNode(var value)) {
            return Optional.of(value);
        }
        out.add(new HeaderViolation(pointer, NOT_STRING));
        return Optional.empty();
    }

    /** A string or {@code null}; returns the string, empty for {@code null} or a value of another type. */
    private static Optional<String> nullableString(Node node, String pointer, List<HeaderViolation> out) {
        if (node instanceof StringNode(var value)) {
            return Optional.of(value);
        }
        if (node != NullNode.INSTANCE) {
            out.add(new HeaderViolation(pointer, NOT_STRING_OR_NULL));
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> integer(Node node, String pointer, List<HeaderViolation> out) {
        if (node instanceof NumberNode number && number.integral()) {
            return Optional.of(number.value());
        }
        out.add(new HeaderViolation(pointer, NOT_INTEGER));
        return Optional.empty();
    }

    private static void patterned(Node node, String pointer, Pattern pattern, List<HeaderViolation> out) {
        string(node, pointer, out).ifPresent(text -> match(text, pointer, pattern, out));
    }

    private static void match(String text, String pointer, Pattern pattern, List<HeaderViolation> out) {
        if (!pattern.matcher(text).matches()) {
            out.add(new HeaderViolation(pointer, "must match " + pattern.pattern()));
        }
    }

    private static void enumerated(Node node, String pointer, List<String> values, List<HeaderViolation> out) {
        string(node, pointer, out).filter(text -> !values.contains(text))
                .ifPresent(text -> out.add(new HeaderViolation(pointer, "must be one of " + values)));
    }

    private static void nullableEnumerated(Node node, String pointer, List<String> values,
            List<HeaderViolation> out) {
        nullableString(node, pointer, out).filter(text -> !values.contains(text))
                .ifPresent(text -> out.add(new HeaderViolation(pointer, "must be one of " + values + " or null")));
    }
}
