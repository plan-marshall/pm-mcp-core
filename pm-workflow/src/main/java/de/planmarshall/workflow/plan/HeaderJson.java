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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.UnitOrigin;

/**
 * The canonical JSON form of a {@link PlanInstanceHeader} (PM-IMPL-5), over the streaming API of jackson-core.
 * <p>
 * The writer produces exactly one spelling: one line of JSON, UTF-8, the members of every object sorted by name, no
 * whitespace between tokens, every member of the schema present, an absent optional value written as {@code null},
 * {@code composed_at} written as {@link Instant#toString()} writes it. The same header therefore always gives the same
 * bytes, and so the same digest.
 * <p>
 * The reader parses a line into a {@link Node} tree, refusing a line that is no single JSON value or names a member of
 * an object twice, checks the tree with {@link HeaderSchemaCheck}, and maps a tree without violations to the header.
 */
final class HeaderJson {

    static final String FORMAT_VERSION = "format_version";
    static final String PLAN_ID = "plan_id";
    static final String VERSION = "version";
    static final String EVENT = "event";
    static final String COMPOSED_AT = "composed_at";
    static final String INPUTS = "inputs";
    static final String LANE = "lane";
    static final String POSTURE = "posture";
    static final String STEPS = "steps";
    static final String PRUNED = "pruned";
    static final String POLICY_CALLS = "policy_calls";
    static final String WAIVED_GATES = "waived_gates";

    static final String CONFIG_HASH = "config_hash";
    static final String TEMPLATES = "templates";
    static final String SUB_UNITS = "sub_units";
    static final String COMPOSITION_RULES = "composition_rules";
    static final String PLAN_FACTS_DIGEST = "plan_facts_digest";
    static final String REALIZED_FOOTPRINT_DIGEST = "realized_footprint_digest";
    static final String CHANGE_TYPE = "change_type";
    static final String PROVIDERS_DIGEST = "providers_digest";
    static final String REVISION = "revision";

    static final String UNIT = "unit";
    static final String FILE = "file";
    static final String DIGEST = "digest";
    static final String ORIGIN = "origin";

    static final String PHASE_5_VERIFICATION = "phase_5_verification";
    static final String PHASE_6 = "phase_6";

    static final String ELEMENT = "element";
    static final String ID = "id";
    static final String REASON = "reason";
    static final String SOURCE_FACT = "source_fact";

    static final String CALL_SITE = "call_site";
    static final String ARGS = "args";
    static final String VALUE = "value";
    static final String SOURCE = "source";
    static final String WAIVER_ID = "waiver_id";

    static final String GATE = "gate";

    private static final JsonFactory JSON = new JsonFactory();

    private HeaderJson() {
    }

    /**
     * @param header the header to write
     * @return its canonical JSON line, without a line break
     */
    static String write(PlanInstanceHeader header) {
        return canonical(toNode(header));
    }

    /**
     * Reads a header line that satisfies every constraint of the header schema.
     *
     * @param json the JSON of the {@code instance} line
     * @return the header
     * @throws IllegalArgumentException if the line is no JSON value, names a member twice, breaks a constraint of the
     *                                  schema (the message lists every violation), or holds a bound argument value
     *                                  that is no literal of the DSL
     */
    static PlanInstanceHeader read(String json) {
        Node node;
        try {
            node = parse(json);
        } catch (Malformed e) {
            throw new IllegalArgumentException("Instance header refused: %s at '%s'".formatted(e.reason(),
                    e.pointer()), e);
        }
        var violations = HeaderSchemaCheck.check(node);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Instance header refused: " + violations);
        }
        return header((ObjectNode) node);
    }

    // ------------------------------------------------------------------------------------------------- the tree

    /**
     * A JSON value as the reader parsed it, or as the writer is about to write it.
     */
    sealed interface Node permits ObjectNode, ArrayNode, StringNode, NumberNode, BooleanNode, NullNode {
    }

    /**
     * An object; its members in the order they were read or added.
     *
     * @param members the members by name
     */
    record ObjectNode(Map<String, Node> members) implements Node {

        ObjectNode {
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }
    }

    /**
     * An array.
     *
     * @param elements the elements in order
     */
    record ArrayNode(List<Node> elements) implements Node {

        ArrayNode {
            elements = List.copyOf(elements);
        }
    }

    /**
     * A string.
     *
     * @param value the string
     */
    record StringNode(String value) implements Node {

        StringNode {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A number, integral or not.
     *
     * @param value the number
     */
    record NumberNode(BigDecimal value) implements Node {

        NumberNode {
            Objects.requireNonNull(value, "value");
        }

        /**
         * @return whether the number has no fractional part, so it is an integer in the sense of JSON Schema
         */
        boolean integral() {
            return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
        }
    }

    /**
     * {@code true} or {@code false}.
     *
     * @param value the boolean
     */
    record BooleanNode(boolean value) implements Node {
    }

    /** {@code null}. */
    enum NullNode implements Node {
        /** The one {@code null}. */
        INSTANCE
    }

    /**
     * A line that is no single JSON value, or names a member of an object twice: the pointer names where.
     */
    static final class Malformed extends Exception {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String pointer;
        private final String reason;

        Malformed(String pointer, String reason) {
            super(reason + " at '" + pointer + "'");
            this.pointer = pointer;
            this.reason = reason;
        }

        /**
         * @return the JSON pointer of the defect, the empty string for the whole line
         */
        String pointer() {
            return pointer;
        }

        /**
         * @return what is wrong
         */
        String reason() {
            return reason;
        }
    }

    /**
     * @param pointer the JSON pointer of an object or array
     * @param token   a member name or an array index
     * @return the JSON pointer of the member or element, with {@code ~} and {@code /} escaped as RFC 6901 demands
     */
    static String child(String pointer, String token) {
        return pointer + "/" + token.replace("~", "~0").replace("/", "~1");
    }

    // ------------------------------------------------------------------------------------------------- parsing

    /**
     * @param json one JSON value
     * @return its tree
     * @throws Malformed if the text is no single JSON value, or an object in it names a member twice
     */
    static Node parse(String json) throws Malformed {
        try (var parser = JSON.createParser(json)) {
            if (parser.nextToken() == null) {
                throw new Malformed("", "the header is empty");
            }
            var node = value(parser, "");
            if (parser.nextToken() != null) {
                throw new Malformed("", "content after the header value");
            }
            return node;
        } catch (IOException e) {
            throw new Malformed("", "the header is not valid JSON: " + e.getMessage());
        }
    }

    private static Node value(JsonParser parser, String pointer) throws IOException, Malformed {
        return switch (parser.currentToken()) {
            case START_OBJECT -> object(parser, pointer);
            case START_ARRAY -> array(parser, pointer);
            case VALUE_STRING -> new StringNode(parser.getText());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new NumberNode(parser.getDecimalValue());
            case VALUE_TRUE -> new BooleanNode(true);
            case VALUE_FALSE -> new BooleanNode(false);
            case VALUE_NULL -> NullNode.INSTANCE;
            default -> throw new Malformed(pointer, "unexpected token " + parser.currentToken());
        };
    }

    private static ObjectNode object(JsonParser parser, String pointer) throws IOException, Malformed {
        var members = new LinkedHashMap<String, Node>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var name = parser.currentName();
            var member = child(pointer, name);
            if (members.containsKey(name)) {
                throw new Malformed(member, "member named twice");
            }
            parser.nextToken();
            members.put(name, value(parser, member));
        }
        return new ObjectNode(members);
    }

    private static ArrayNode array(JsonParser parser, String pointer) throws IOException, Malformed {
        var elements = new ArrayList<Node>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            elements.add(value(parser, child(pointer, Integer.toString(elements.size()))));
        }
        return new ArrayNode(elements);
    }

    // ------------------------------------------------------------------------------------------------- writing

    /**
     * @param node a tree
     * @return its canonical JSON: members sorted by name, no whitespace between tokens
     */
    static String canonical(Node node) {
        var out = new ByteArrayOutputStream();
        try (var generator = JSON.createGenerator(out, JsonEncoding.UTF8)) {
            writeNode(generator, node);
        } catch (IOException e) {
            throw new UncheckedIOException("Instance header cannot be written", e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void writeNode(JsonGenerator generator, Node node) throws IOException {
        switch (node) {
            case ObjectNode object -> {
                generator.writeStartObject();
                for (var member : new TreeMap<>(object.members()).entrySet()) {
                    generator.writeFieldName(member.getKey());
                    writeNode(generator, member.getValue());
                }
                generator.writeEndObject();
            }
            case ArrayNode array -> {
                generator.writeStartArray();
                for (var element : array.elements()) {
                    writeNode(generator, element);
                }
                generator.writeEndArray();
            }
            case StringNode string -> generator.writeString(string.value());
            case NumberNode number -> writeNumber(generator, number);
            case BooleanNode bool -> generator.writeBoolean(bool.value());
            case NullNode ignored -> generator.writeNull();
        }
    }

    private static void writeNumber(JsonGenerator generator, NumberNode number) throws IOException {
        if (number.integral()) {
            generator.writeNumber(number.value().toBigIntegerExact());
        } else {
            generator.writeNumber(number.value().stripTrailingZeros());
        }
    }

    // ------------------------------------------------------------------------------------------------- header -> tree

    /**
     * @param header a header
     * @return its tree, every member of the schema present
     */
    static ObjectNode toNode(PlanInstanceHeader header) {
        var root = new LinkedHashMap<String, Node>();
        root.put(FORMAT_VERSION, number(PlanInstanceHeader.FORMAT_VERSION));
        root.put(PLAN_ID, new StringNode(header.planId()));
        root.put(VERSION, number(header.version()));
        root.put(EVENT, new StringNode(header.event().wireName()));
        root.put(COMPOSED_AT, new StringNode(header.composedAt().toString()));
        root.put(INPUTS, inputs(header.inputs()));
        root.put(LANE, optional(header.lane().map(PlanInstanceHeader.Lane::wireName)));
        root.put(POSTURE, optional(header.posture().map(PlanInstanceHeader.Posture::wireName)));
        root.put(STEPS, new ObjectNode(Map.of(PHASE_5_VERIFICATION, strings(header.steps().phase5Verification()),
                PHASE_6, strings(header.steps().phase6()))));
        root.put(PRUNED, new ArrayNode(header.pruned().stream().map(HeaderJson::pruned).toList()));
        root.put(POLICY_CALLS, new ArrayNode(header.policyCalls().stream().map(HeaderJson::policyCall).toList()));
        root.put(WAIVED_GATES, new ArrayNode(header.waivedGates().stream()
                .map(gate -> (Node) new ObjectNode(Map.of(GATE, new StringNode(gate.gate()), WAIVER_ID,
                        new StringNode(gate.waiverId()))))
                .toList()));
        return new ObjectNode(root);
    }

    private static ObjectNode inputs(CompositionInputs inputs) {
        var members = new LinkedHashMap<String, Node>();
        members.put(CONFIG_HASH, new StringNode(inputs.configHash()));
        members.put(TEMPLATES, unitRefs(inputs.templates()));
        members.put(SUB_UNITS, unitRefs(inputs.subUnits()));
        members.put(COMPOSITION_RULES, new ObjectNode(Map.of(REVISION, new StringNode(
                inputs.compositionRulesRevision()), DIGEST, new StringNode(inputs.compositionRulesDigest()))));
        members.put(PLAN_FACTS_DIGEST, new StringNode(inputs.planFactsDigest()));
        members.put(REALIZED_FOOTPRINT_DIGEST, optional(inputs.realizedFootprintDigest()));
        members.put(CHANGE_TYPE, optional(inputs.changeType()));
        members.put(PROVIDERS_DIGEST, optional(inputs.providersDigest()));
        return new ObjectNode(members);
    }

    private static ArrayNode unitRefs(List<UnitReference> refs) {
        return new ArrayNode(refs.stream().map(ref -> {
            var members = new LinkedHashMap<String, Node>();
            members.put(UNIT, new StringNode(ref.unit()));
            members.put(FILE, optional(ref.file()));
            members.put(DIGEST, new StringNode(ref.digest()));
            members.put(ORIGIN, new StringNode(UnitReference.wireName(ref.origin())));
            return (Node) new ObjectNode(members);
        }).toList());
    }

    private static Node pruned(PrunedElement element) {
        return new ObjectNode(Map.of(ELEMENT, new StringNode(element.element().wireName()), ID,
                new StringNode(element.id()), REASON, new StringNode(element.reason().wireName()), SOURCE_FACT,
                new StringNode(element.sourceFact())));
    }

    private static Node policyCall(PolicyCall call) {
        var args = new LinkedHashMap<String, Node>();
        call.args().forEach((name, argument) -> {
            var members = new LinkedHashMap<String, Node>();
            members.put(VALUE, callValue(argument.value()));
            members.put(SOURCE, new StringNode(argument.source().wireName()));
            members.put(FILE, optional(argument.file()));
            members.put(WAIVER_ID, optional(argument.waiverId()));
            args.put(name, new ObjectNode(members));
        });
        return new ObjectNode(Map.of(CALL_SITE, new StringNode(call.callSite()), UNIT, new StringNode(call.unit()),
                ARGS, new ObjectNode(args)));
    }

    private static Node callValue(CallValue value) {
        return switch (value) {
            case CallValue.EnumValue enumValue -> new StringNode(enumValue.value());
            case CallValue.IntValue intValue -> number(intValue.value());
            case CallValue.EnumList list -> strings(list.values());
        };
    }

    private static NumberNode number(long value) {
        return new NumberNode(BigDecimal.valueOf(value));
    }

    private static ArrayNode strings(List<String> values) {
        return new ArrayNode(values.stream().map(value -> (Node) new StringNode(value)).toList());
    }

    private static Node optional(Optional<String> value) {
        return value.<Node>map(StringNode::new).orElse(NullNode.INSTANCE);
    }

    // ------------------------------------------------------------------------------------------------- tree -> header

    /** Maps a tree that {@link HeaderSchemaCheck} found free of violations. */
    private static PlanInstanceHeader header(ObjectNode root) {
        var members = root.members();
        var steps = (ObjectNode) members.get(STEPS);
        return new PlanInstanceHeader(text(members.get(PLAN_ID)), intValue(members.get(VERSION), VERSION),
                wire(members.get(EVENT), CompositionEvent::fromWireName),
                instant(text(members.get(COMPOSED_AT))),
                inputs((ObjectNode) members.get(INPUTS)),
                optionalText(members.get(LANE)).flatMap(PlanInstanceHeader.Lane::fromWireName),
                optionalText(members.get(POSTURE)).flatMap(PlanInstanceHeader.Posture::fromWireName),
                new PlanInstanceHeader.Steps(texts(steps.members().get(PHASE_5_VERIFICATION)),
                        texts(steps.members().get(PHASE_6))),
                elements(members.get(PRUNED)).stream().map(HeaderJson::prunedElement).toList(),
                elements(members.get(POLICY_CALLS)).stream().map(HeaderJson::policyCall).toList(),
                elements(members.get(WAIVED_GATES)).stream().map(node -> {
                    var gate = ((ObjectNode) node).members();
                    return new WaivedGate(text(gate.get(GATE)), text(gate.get(WAIVER_ID)));
                }).toList());
    }

    /**
     * Parses a {@code composed_at} value; the schema check and the reader both go through here, so the check refuses
     * exactly what the reader cannot parse.
     *
     * @param text the RFC 3339 date-time
     * @return the instant it denotes
     * @throws IllegalArgumentException if the text is no date-time the reader can parse
     */
    static Instant instant(String text) {
        try {
            return OffsetDateTime.parse(text.toUpperCase(Locale.ROOT), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("not a date-time the header reader can parse: " + text, e);
        }
    }

    private static CompositionInputs inputs(ObjectNode node) {
        var members = node.members();
        var rules = ((ObjectNode) members.get(COMPOSITION_RULES)).members();
        return new CompositionInputs(text(members.get(CONFIG_HASH)), unitRefs(members.get(TEMPLATES)),
                unitRefs(members.get(SUB_UNITS)), text(rules.get(REVISION)), text(rules.get(DIGEST)),
                text(members.get(PLAN_FACTS_DIGEST)), optionalText(members.get(REALIZED_FOOTPRINT_DIGEST)),
                optionalText(members.get(CHANGE_TYPE)), optionalText(members.get(PROVIDERS_DIGEST)));
    }

    private static List<UnitReference> unitRefs(Node node) {
        return elements(node).stream().map(element -> {
            var ref = ((ObjectNode) element).members();
            var origin = "project".equals(text(ref.get(ORIGIN))) ? UnitOrigin.PROJECT : UnitOrigin.DEFAULT;
            return new UnitReference(text(ref.get(UNIT)), optionalText(ref.get(FILE)), text(ref.get(DIGEST)),
                    origin);
        }).toList();
    }

    private static PrunedElement prunedElement(Node node) {
        var members = ((ObjectNode) node).members();
        return new PrunedElement(wire(members.get(ELEMENT), PrunedElement.Kind::fromWireName),
                text(members.get(ID)), wire(members.get(REASON), PruneReason::fromWireName),
                text(members.get(SOURCE_FACT)));
    }

    private static PolicyCall policyCall(Node node) {
        var members = ((ObjectNode) node).members();
        var args = new TreeMap<String, BoundArgument>();
        ((ObjectNode) members.get(ARGS)).members().forEach((name, value) -> {
            var argument = ((ObjectNode) value).members();
            args.put(name, new BoundArgument(callValue(argument.get(VALUE), name),
                    wire(argument.get(SOURCE), BoundArgument.Source::fromWireName),
                    optionalText(argument.get(FILE)), optionalText(argument.get(WAIVER_ID))));
        });
        return new PolicyCall(text(members.get(CALL_SITE)), text(members.get(UNIT)), args);
    }

    private static CallValue callValue(Node node, String name) {
        return switch (node) {
            case StringNode string -> new CallValue.EnumValue(string.value());
            case NumberNode number when number.integral() -> new CallValue.IntValue(intValue(number, name));
            case ArrayNode array when array.elements().stream().allMatch(StringNode.class::isInstance) ->
                new CallValue.EnumList(texts(array));
            default -> throw new IllegalArgumentException(
                    "Instance header refused: the value of argument '%s' is no literal of the DSL".formatted(name));
        };
    }

    private static List<Node> elements(Node node) {
        return ((ArrayNode) node).elements();
    }

    private static String text(Node node) {
        return ((StringNode) node).value();
    }

    private static List<String> texts(Node node) {
        return elements(node).stream().map(HeaderJson::text).toList();
    }

    private static Optional<String> optionalText(Node node) {
        return node instanceof StringNode string ? Optional.of(string.value()) : Optional.empty();
    }

    private static int intValue(Node node, String name) {
        var value = ((NumberNode) node).value();
        try {
            return value.toBigIntegerExact().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Instance header refused: '%s' is out of range: %s".formatted(name,
                    value), e);
        }
    }

    private static <E> E wire(Node node, Function<String, Optional<E>> lookup) {
        var wireName = text(node);
        return lookup.apply(wireName).orElseThrow(() -> new IllegalArgumentException(
                "Instance header refused: undeclared value '%s'".formatted(wireName)));
    }
}
