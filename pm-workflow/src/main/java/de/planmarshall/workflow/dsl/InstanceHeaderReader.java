/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.workflow.ast.InstanceHeader;
import de.planmarshall.workflow.ast.InstanceInputs;

/**
 * Reads the one-line JSON of the {@code instance} line of a plan instance version into an {@link InstanceHeader}
 * (PM-IMPL-5), with the streaming parser of jackson-core, by the structure of the {@code PlanInstanceHeader} schema
 * (store schemas, Plan Instance).
 * <p>
 * The line must be exactly one JSON object with the members {@code plan_id} (string), {@code version} (integer),
 * {@code event} (string) and {@code inputs} (object). The {@code inputs} object needs {@code config_hash} and
 * {@code plan_facts_digest} (strings), {@code templates} and {@code sub_units} (arrays of unit references, each an
 * object with the strings {@code unit}, {@code digest} and {@code origin} and an optional {@code file}, a string or
 * {@code null}) and {@code composition_rules} (an object with the strings {@code revision} and {@code digest}); its
 * members {@code realized_footprint_digest}, {@code change_type} and {@code providers_digest} may be absent, a string
 * or {@code null}. No object may name a member twice. Further members are kept in the header's JSON and not
 * interpreted: the checks of the schema beyond this structure (constants, enumerations, patterns, members it does not
 * allow) belong to the composition of the plan instance, not to the parser.
 * <p>
 * Anything else is refused with an {@link IllegalArgumentException} whose message names the defect and the member it
 * lies in; a jackson-core {@link IOException} is mapped to that refusal and never rethrown.
 */
final class InstanceHeaderReader {

    private static final String FIELD_PLAN_ID = "plan_id";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_EVENT = "event";
    private static final String FIELD_INPUTS = "inputs";

    private static final String FIELD_CONFIG_HASH = "config_hash";
    private static final String FIELD_TEMPLATES = "templates";
    private static final String FIELD_SUB_UNITS = "sub_units";
    private static final String FIELD_COMPOSITION_RULES = "composition_rules";
    private static final String FIELD_PLAN_FACTS_DIGEST = "plan_facts_digest";
    private static final String FIELD_REALIZED_FOOTPRINT_DIGEST = "realized_footprint_digest";
    private static final String FIELD_CHANGE_TYPE = "change_type";
    private static final String FIELD_PROVIDERS_DIGEST = "providers_digest";

    private static final String FIELD_UNIT = "unit";
    private static final String FIELD_FILE = "file";
    private static final String FIELD_DIGEST = "digest";
    private static final String FIELD_ORIGIN = "origin";
    private static final String FIELD_REVISION = "revision";

    private static final String ROOT = "";

    private static final JsonFactory JSON = new JsonFactory();

    private InstanceHeaderReader() {
    }

    /**
     * Reads an instance header.
     *
     * @param json the JSON of the {@code instance} line
     * @return the header, holding {@code json} as read
     * @throws IllegalArgumentException if the line is no JSON object or lacks or mistypes a required member
     */
    static InstanceHeader read(String json) {
        try (var parser = JSON.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("the instance header is not a JSON object");
            }
            String planId = null;
            var version = 0;
            String event = null;
            InstanceInputs inputs = null;
            var seen = new HashSet<String>();
            for (var field = nextMember(parser, ROOT, seen); field != null; field = nextMember(parser, ROOT, seen)) {
                switch (field) {
                    case FIELD_PLAN_ID -> planId = string(parser, field);
                    case FIELD_VERSION -> version = integer(parser, field);
                    case FIELD_EVENT -> event = string(parser, field);
                    case FIELD_INPUTS -> inputs = inputs(parser, field);
                    default -> parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("content after the instance header object");
            }
            requireMembers(ROOT, seen, FIELD_PLAN_ID, FIELD_VERSION, FIELD_EVENT, FIELD_INPUTS);
            return new InstanceHeader(planId, version, event, inputs, json);
        } catch (IOException e) {
            throw new IllegalArgumentException("the instance header is not valid JSON: " + describe(e), e);
        }
    }

    private static InstanceInputs inputs(JsonParser parser, String path) throws IOException {
        requireToken(parser, JsonToken.START_OBJECT, path, "an object");
        String configHash = null;
        List<InstanceInputs.UnitRef> templates = List.of();
        List<InstanceInputs.UnitRef> subUnits = List.of();
        InstanceInputs.CompositionRules compositionRules = null;
        String planFactsDigest = null;
        Optional<String> realizedFootprintDigest = Optional.empty();
        Optional<String> changeType = Optional.empty();
        Optional<String> providersDigest = Optional.empty();
        var seen = new HashSet<String>();
        for (var field = nextMember(parser, path, seen); field != null; field = nextMember(parser, path, seen)) {
            var member = qualify(path, field);
            switch (field) {
                case FIELD_CONFIG_HASH -> configHash = string(parser, member);
                case FIELD_TEMPLATES -> templates = unitRefs(parser, member);
                case FIELD_SUB_UNITS -> subUnits = unitRefs(parser, member);
                case FIELD_COMPOSITION_RULES -> compositionRules = compositionRules(parser, member);
                case FIELD_PLAN_FACTS_DIGEST -> planFactsDigest = string(parser, member);
                case FIELD_REALIZED_FOOTPRINT_DIGEST -> realizedFootprintDigest = optionalString(parser, member);
                case FIELD_CHANGE_TYPE -> changeType = optionalString(parser, member);
                case FIELD_PROVIDERS_DIGEST -> providersDigest = optionalString(parser, member);
                default -> parser.skipChildren();
            }
        }
        requireMembers(path, seen, FIELD_CONFIG_HASH, FIELD_TEMPLATES, FIELD_SUB_UNITS, FIELD_COMPOSITION_RULES,
                FIELD_PLAN_FACTS_DIGEST);
        return new InstanceInputs(configHash, templates, subUnits, compositionRules, planFactsDigest,
                realizedFootprintDigest, changeType, providersDigest);
    }

    private static List<InstanceInputs.UnitRef> unitRefs(JsonParser parser, String path) throws IOException {
        requireToken(parser, JsonToken.START_ARRAY, path, "an array");
        var refs = new ArrayList<InstanceInputs.UnitRef>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            refs.add(unitRef(parser, "%s[%d]".formatted(path, refs.size())));
        }
        return refs;
    }

    private static InstanceInputs.UnitRef unitRef(JsonParser parser, String path) throws IOException {
        requireToken(parser, JsonToken.START_OBJECT, path, "an object");
        String unit = null;
        Optional<String> file = Optional.empty();
        String digest = null;
        String origin = null;
        var seen = new HashSet<String>();
        for (var field = nextMember(parser, path, seen); field != null; field = nextMember(parser, path, seen)) {
            var member = qualify(path, field);
            switch (field) {
                case FIELD_UNIT -> unit = string(parser, member);
                case FIELD_FILE -> file = optionalString(parser, member);
                case FIELD_DIGEST -> digest = string(parser, member);
                case FIELD_ORIGIN -> origin = string(parser, member);
                default -> parser.skipChildren();
            }
        }
        requireMembers(path, seen, FIELD_UNIT, FIELD_DIGEST, FIELD_ORIGIN);
        return new InstanceInputs.UnitRef(unit, file, digest, origin);
    }

    private static InstanceInputs.CompositionRules compositionRules(JsonParser parser, String path)
            throws IOException {
        requireToken(parser, JsonToken.START_OBJECT, path, "an object");
        String revision = null;
        String digest = null;
        var seen = new HashSet<String>();
        for (var field = nextMember(parser, path, seen); field != null; field = nextMember(parser, path, seen)) {
            var member = qualify(path, field);
            switch (field) {
                case FIELD_REVISION -> revision = string(parser, member);
                case FIELD_DIGEST -> digest = string(parser, member);
                default -> parser.skipChildren();
            }
        }
        requireMembers(path, seen, FIELD_REVISION, FIELD_DIGEST);
        return new InstanceInputs.CompositionRules(revision, digest);
    }

    /**
     * Moves to the value of the next member of the current object and returns its name, or returns {@code null} at the
     * end of the object.
     */
    private static String nextMember(JsonParser parser, String path, Set<String> seen) throws IOException {
        if (parser.nextToken() != JsonToken.FIELD_NAME) {
            return null;
        }
        var field = parser.currentName();
        if (!seen.add(field)) {
            throw new IllegalArgumentException(
                    "member '%s' of the instance header twice".formatted(qualify(path, field)));
        }
        parser.nextToken();
        return field;
    }

    private static void requireMembers(String path, Set<String> seen, String... fields) {
        for (var field : fields) {
            if (!seen.contains(field)) {
                throw new IllegalArgumentException(
                        "member '%s' of the instance header is missing".formatted(qualify(path, field)));
            }
        }
    }

    private static void requireToken(JsonParser parser, JsonToken token, String path, String kind) {
        if (parser.currentToken() != token) {
            throw new IllegalArgumentException("member '%s' of the instance header must be %s".formatted(path, kind));
        }
    }

    private static String string(JsonParser parser, String path) throws IOException {
        requireToken(parser, JsonToken.VALUE_STRING, path, "a string");
        return parser.getText();
    }

    private static Optional<String> optionalString(JsonParser parser, String path) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return Optional.empty();
        }
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw new IllegalArgumentException(
                    "member '%s' of the instance header must be a string or null".formatted(path));
        }
        return Optional.of(parser.getText());
    }

    private static int integer(JsonParser parser, String path) throws IOException {
        requireToken(parser, JsonToken.VALUE_NUMBER_INT, path, "an integer");
        return parser.getIntValue();
    }

    private static String qualify(String path, String field) {
        return path.isEmpty() ? field : path + "." + field;
    }

    /** Names where the line stops being JSON, without the source excerpt the parser may append. */
    private static String describe(IOException e) {
        if (e instanceof JsonProcessingException malformed && malformed.getLocation() != null) {
            return "%s (column %d)".formatted(malformed.getOriginalMessage(), malformed.getLocation().getColumnNr());
        }
        return String.valueOf(e.getMessage());
    }
}
