/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One capability spec, {@code .marshall/capabilities/<capability>.json} (Store Schemas § Capability Spec): a durable
 * behaviour named for what it does, its purpose and its requirements.
 * <p>
 * A requirement's fingerprint is the SHA-256 of the RFC 8785 canonical JSON of its object without {@code plan_id} and
 * {@code verified_by}, so that following a renamed test changes no fingerprint. The intent record cites the
 * fingerprint its criterion changes, which {@link IntentAcceptance} checks against the tree the plan started from.
 *
 * @param capability   the capability, equal to the file's path below the capabilities directory
 * @param purpose      what the capability is for, 20 to 1000 characters
 * @param requirements the requirements of the capability
 * @since 0.1
 */
public record CapabilitySpec(String capability, String purpose, List<Requirement> requirements) {

    /** The format version of a capability spec that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_CAPABILITY = "capability";

    private static final String FIELD_PURPOSE = "purpose";

    private static final String FIELD_REQUIREMENTS = "requirements";

    private static final String FIELD_NAME = "name";

    private static final String FIELD_TEXT = "text";

    private static final String FIELD_SCENARIOS = "scenarios";

    private static final String FIELD_GIVEN = "given";

    private static final String FIELD_WHEN = "when";

    private static final String FIELD_THEN = "then";

    private static final String FIELD_PLAN_ID = "plan_id";

    private static final String FIELD_VERIFIED_BY = "verified_by";

    /**
     * Copies the requirements into an immutable list and checks the spec's rules.
     *
     * @throws IllegalArgumentException if a member is out of its bounds or a requirement name repeats
     * @throws NullPointerException     if a value is {@code null}
     */
    public CapabilitySpec {
        Objects.requireNonNull(capability, FIELD_CAPABILITY);
        Objects.requireNonNull(purpose, FIELD_PURPOSE);
        if (purpose.length() < 20 || purpose.length() > 1000) {
            throw new IllegalArgumentException("a purpose is 20 to 1000 characters: " + purpose.length());
        }
        Objects.requireNonNull(requirements, FIELD_REQUIREMENTS);
        requirements = List.copyOf(requirements);
        var names = new HashSet<String>();
        for (var requirement : requirements) {
            if (!names.add(requirement.name())) {
                throw new IllegalArgumentException(
                        "the requirement '%s' is named twice in '%s'".formatted(requirement.name(), capability));
            }
        }
    }

    /**
     * @param name a requirement name
     * @return the requirement of that name; empty when the capability does not hold it
     */
    public Optional<Requirement> requirement(String name) {
        return requirements.stream().filter(item -> item.name().equals(name)).findFirst();
    }

    /**
     * @param name a requirement name
     * @return the requirement's fingerprint; empty when the capability does not hold it
     */
    public Optional<String> fingerprint(String name) {
        return requirement(name).map(Requirement::fingerprint);
    }

    /**
     * @param file the file the document was read from, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the spec
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static CapabilitySpec read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, CapabilitySpec::readDocument);
    }

    /**
     * @param spec the spec
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(CapabilitySpec spec) {
        Objects.requireNonNull(spec, "spec");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeStringField(FIELD_CAPABILITY, spec.capability());
            generator.writeStringField(FIELD_PURPOSE, spec.purpose());
            generator.writeArrayFieldStart(FIELD_REQUIREMENTS);
            for (var requirement : spec.requirements()) {
                writeRequirement(generator, requirement);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Capability spec cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeRequirement(JsonGenerator generator, Requirement requirement) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_NAME, requirement.name());
        generator.writeStringField(FIELD_TEXT, requirement.text());
        generator.writeArrayFieldStart(FIELD_SCENARIOS);
        for (var scenario : requirement.scenarios()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_NAME, scenario.name());
            if (scenario.given() == null) {
                generator.writeNullField(FIELD_GIVEN);
            } else {
                generator.writeStringField(FIELD_GIVEN, scenario.given());
            }
            generator.writeStringField(FIELD_WHEN, scenario.when());
            generator.writeStringField(FIELD_THEN, scenario.then());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeStringField(FIELD_PLAN_ID, requirement.planId());
        generator.writeArrayFieldStart(FIELD_VERIFIED_BY);
        for (var test : requirement.verifiedBy()) {
            generator.writeString(test);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private static CapabilitySpec readDocument(JsonParser parser) throws IOException {
        @Nullable String capability = null;
        @Nullable String purpose = null;
        @Nullable List<Requirement> requirements = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_CAPABILITY -> capability = string(parser, field);
                case FIELD_PURPOSE -> purpose = string(parser, field);
                case FIELD_REQUIREMENTS -> requirements = array(parser, field, CapabilitySpec::readRequirement);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "document", FIELD_CAPABILITY, FIELD_PURPOSE, FIELD_REQUIREMENTS);
        try {
            return new CapabilitySpec(capability, purpose, requirements);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Requirement readRequirement(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String requirementName = null;
        @Nullable String text = null;
        @Nullable List<Scenario> scenarios = null;
        @Nullable String planId = null;
        List<String> verifiedBy = List.of();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_NAME -> requirementName = string(parser, field);
                case FIELD_TEXT -> text = string(parser, field);
                case FIELD_SCENARIOS -> scenarios = array(parser, field, CapabilitySpec::readScenario);
                case FIELD_PLAN_ID -> planId = string(parser, field);
                case FIELD_VERIFIED_BY -> verifiedBy = stringList(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_NAME, FIELD_TEXT, FIELD_SCENARIOS, FIELD_PLAN_ID);
        try {
            return new Requirement(requirementName, text, scenarios, planId, verifiedBy);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Scenario readScenario(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String scenarioName = null;
        @Nullable String given = null;
        @Nullable String when = null;
        @Nullable String then = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_NAME -> scenarioName = string(parser, field);
                case FIELD_GIVEN -> given = StrictJson.nullableString(parser, field);
                case FIELD_WHEN -> when = string(parser, field);
                case FIELD_THEN -> then = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_NAME, FIELD_GIVEN, FIELD_WHEN, FIELD_THEN);
        try {
            return new Scenario(scenarioName, given, when, then);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    /**
     * One requirement of a capability.
     *
     * @param name       the name, unique within the capability, 3 to 120 characters
     * @param text       the statement in EARS form
     * @param scenarios  at least one scenario
     * @param planId     the plan whose merge last changed the requirement
     * @param verifiedBy the test files the self-review named as evidence; empty for demonstration or inspection
     * @since 0.1
     */
    public record Requirement(String name, String text, List<Scenario> scenarios, String planId,
    List<String> verifiedBy) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds or the text is not in EARS form
         * @throws NullPointerException     if a value is {@code null}
         */
        public Requirement {
            Objects.requireNonNull(name, "name");
            if (name.length() < 3 || name.length() > 120) {
                throw new IllegalArgumentException("a requirement name is 3 to 120 characters: " + name.length());
            }
            Objects.requireNonNull(text, "text");
            if (text.length() < 10 || text.length() > 500) {
                throw new IllegalArgumentException("a requirement text is 10 to 500 characters: " + text.length());
            }
            EarsForm.violation(text, false)
                    .ifPresent(reason -> {
                        throw new IllegalArgumentException(
                                "the requirement '%s' is not in EARS form: %s".formatted(name, reason));
                    });
            Objects.requireNonNull(scenarios, FIELD_SCENARIOS);
            scenarios = List.copyOf(scenarios);
            if (scenarios.isEmpty()) {
                throw new IllegalArgumentException("the requirement '%s' holds no scenario".formatted(name));
            }
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(verifiedBy, "verifiedBy");
            verifiedBy = List.copyOf(verifiedBy);
        }

        /**
         * @return the SHA-256 of the RFC 8785 canonical JSON of the requirement without {@code plan_id} and
         *         {@code verified_by}
         */
        public String fingerprint() {
            var canonical = new LinkedHashMap<String, Object>();
            canonical.put(FIELD_NAME, name);
            var scenarioValues = new ArrayList<Map<String, Object>>();
            for (var scenario : scenarios) {
                var value = new LinkedHashMap<String, Object>();
                value.put(FIELD_GIVEN, scenario.given());
                value.put(FIELD_NAME, scenario.name());
                value.put(FIELD_THEN, scenario.then());
                value.put(FIELD_WHEN, scenario.when());
                scenarioValues.add(value);
            }
            canonical.put(FIELD_SCENARIOS, scenarioValues);
            canonical.put(FIELD_TEXT, text);
            try {
                var digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(canonical(canonical).getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is not available", e);
            }
        }
    }

    /**
     * One scenario of a requirement.
     *
     * @param name  the name, 3 to 120 characters
     * @param given the precondition; may be {@code null}
     * @param when  the trigger, 3 to 500 characters
     * @param then  the response, 3 to 500 characters
     * @since 0.1
     */
    public record Scenario(String name, String given, String when, String then) {

        /**
         * @throws IllegalArgumentException if a member is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public Scenario {
            Objects.requireNonNull(name, "name");
            if (name.length() < 3 || name.length() > 120) {
                throw new IllegalArgumentException("a scenario name is 3 to 120 characters: " + name.length());
            }
            if (given != null && given.length() > 500) {
                throw new IllegalArgumentException("a scenario's given is at most 500 characters");
            }
            Objects.requireNonNull(when, "when");
            if (when.length() < 3 || when.length() > 500) {
                throw new IllegalArgumentException("a scenario's when is 3 to 500 characters: " + when.length());
            }
            Objects.requireNonNull(then, "then");
            if (then.length() < 3 || then.length() > 500) {
                throw new IllegalArgumentException("a scenario's then is 3 to 500 characters: " + then.length());
            }
        }
    }

    /**
     * @param value a value of the canonical JSON: a string, a list or a map with string keys
     * @return the RFC 8785 canonical JSON text
     */
    static String canonical(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof List<?> list) {
            var out = new StringBuilder("[");
            for (var index = 0; index < list.size(); index++) {
                if (index > 0) {
                    out.append(',');
                }
                out.append(canonical(list.get(index)));
            }
            return out.append(']').toString();
        }
        if (value instanceof Map<?, ?> map) {
            var out = new StringBuilder("{");
            var first = true;
            for (var key : map.keySet().stream().map(String::valueOf).sorted().toList()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(quote(key)).append(':').append(canonical(map.get(key)));
            }
            return out.append('}').toString();
        }
        throw new IllegalArgumentException("not a canonical JSON value: " + value.getClass());
    }

    private static String quote(String text) {
        var out = new StringBuilder("\"");
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append("\\u%04x".formatted((int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
