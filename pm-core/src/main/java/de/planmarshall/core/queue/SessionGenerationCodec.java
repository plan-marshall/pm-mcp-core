/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.map;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableInstant;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.queue.SessionGeneration.DeliveredSkill;
import de.planmarshall.core.queue.SessionGeneration.Scope;
import de.planmarshall.core.queue.SessionGeneration.Workdir;
import de.planmarshall.core.store.ScopeSessionRow;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes the session generation record {@code queue/session-generations.json} (PM-WORK-5, PM-IMPL-2):
 * {@code {"format_version": 1, "generations": {<generation_id>: <entry>}}}, an entry in the closed schema of the
 * Session Generation Record of the specification.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}: exactly the current {@code format_version}, only
 * the declared fields, each at most once, every required field, and an entry key that is a generation id. A document
 * in another format version is refused as outdated or newer; anything else that does not parse is unreadable, never an
 * empty record. Every refusal is a {@link StoreRefusal} that names the file.
 * <p>
 * The record holds the instant a generation was fenced, not what fenced it. Whether a {@code pm-mcp enter} fenced it
 * ({@link SessionGeneration.Fence#byEnter()}) is derived when the record is read: an issue fences every earlier
 * generation of its session key in the same write, at the instant of the issue, so a generation fenced by an
 * {@code enter} is one for which another generation of the same key was issued by {@code enter} at its
 * {@code fenced_at}. The writer drops the flag; the reader derives it again, so a record the store wrote reads back
 * equal.
 *
 * @since 0.1
 */
@UtilityClass
public class SessionGenerationCodec {

    /** The format version of a session generation record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_GENERATIONS = "generations";
    private static final String FIELD_SESSION_KEY = "session_key";
    private static final String FIELD_CLIENT = "client";
    private static final String FIELD_CLIENT_SESSION_ID = "client_session_id";
    private static final String FIELD_SCOPE = "scope";
    private static final String FIELD_WORKDIR = "workdir";
    private static final String FIELD_ISSUED_AT = "issued_at";
    private static final String FIELD_ISSUED_BY = "issued_by";
    private static final String FIELD_SESSION_TASKS = "session_tasks";
    private static final String FIELD_OFFERS_SUSPENDED = "offers_suspended";
    private static final String FIELD_DELIVERED_SKILLS = "delivered_skills";
    private static final String FIELD_FENCED_AT = "fenced_at";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_ID = "id";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_PLAN_ID = "plan_id";
    private static final String FIELD_URI = "uri";
    private static final String FIELD_DIGEST = "digest";
    private static final String FIELD_DELIVERED_AT = "delivered_at";

    /**
     * @param generations the entries of the record, in the order they are written
     * @return the document, UTF-8 encoded
     * @throws IllegalArgumentException if two entries have the same generation id
     */
    public static byte[] write(Collection<SessionGeneration> generations) {
        Objects.requireNonNull(generations, FIELD_GENERATIONS);
        var ids = new HashSet<GenerationId>();
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeObjectFieldStart(FIELD_GENERATIONS);
            for (var generation : generations) {
                if (!ids.add(generation.id())) {
                    throw new IllegalArgumentException("Two entries of the generation " + generation.id());
                }
                generator.writeFieldName(generation.id().value());
                writeGeneration(generator, generation);
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Session generation record cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the record file the document was read from, named by a refusal
     * @param document a session generation record, UTF-8 encoded
     * @return the entries of the record, in its order, each fence with what fenced it derived
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static List<SessionGeneration> read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return withFenceCauses(StrictJson.read(file, document, FORMAT_VERSION, SessionGenerationCodec::readDocument));
    }

    private static void writeGeneration(JsonGenerator generator, SessionGeneration generation) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_SESSION_KEY, generation.sessionKey());
        generator.writeStringField(FIELD_CLIENT, generation.client().wireName());
        writeNullableString(generator, FIELD_CLIENT_SESSION_ID, generation.clientSessionId());
        writeScope(generator, generation.scope());
        writeWorkdir(generator, generation.workdir());
        writeInstant(generator, FIELD_ISSUED_AT, generation.issuedAt());
        generator.writeStringField(FIELD_ISSUED_BY, generation.cause().wireName());
        generator.writeBooleanField(FIELD_SESSION_TASKS, generation.sessionTasks());
        generator.writeBooleanField(FIELD_OFFERS_SUSPENDED, generation.offersSuspended());
        generator.writeArrayFieldStart(FIELD_DELIVERED_SKILLS);
        for (var skill : generation.deliveredSkills()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_URI, skill.uri());
            generator.writeStringField(FIELD_DIGEST, skill.digest());
            writeInstant(generator, FIELD_DELIVERED_AT, skill.deliveredAt());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        writeInstant(generator, FIELD_FENCED_AT, generation.isFenced() ? generation.fence().at() : null);
        generator.writeEndObject();
    }

    private static void writeScope(JsonGenerator generator, Scope scope) throws IOException {
        if (scope == null) {
            generator.writeNullField(FIELD_SCOPE);
            return;
        }
        generator.writeObjectFieldStart(FIELD_SCOPE);
        generator.writeStringField(FIELD_TYPE, scope.type().wireName());
        writeNullableString(generator, FIELD_ID, scope.id());
        generator.writeEndObject();
    }

    private static void writeWorkdir(JsonGenerator generator, Workdir workdir) throws IOException {
        if (workdir == null) {
            generator.writeNullField(FIELD_WORKDIR);
            return;
        }
        generator.writeObjectFieldStart(FIELD_WORKDIR);
        generator.writeStringField(FIELD_KIND, workdir.kind().wireName());
        writeNullableString(generator, FIELD_PLAN_ID, workdir.planId());
        generator.writeEndObject();
    }

    private static void writeNullableString(JsonGenerator generator, String field, String value)
            throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static List<SessionGeneration> readDocument(JsonParser parser) throws IOException {
        List<SessionGeneration> generations = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_GENERATIONS -> generations = List.copyOf(
                        map(parser, field, SessionGenerationCodec::readGeneration).values());
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "record", FIELD_GENERATIONS);
        return generations;
    }

    private static SessionGeneration readGeneration(JsonParser parser, String key) throws IOException {
        var id = GenerationId.parse(key)
                .orElseThrow(() -> unreadable("'%s' is not a session generation id".formatted(key)));
        requireObject(parser, key);
        var builder = SessionGeneration.builder().id(id);
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_SESSION_KEY -> builder.sessionKey(string(parser, field));
                case FIELD_CLIENT -> builder.client(wireValue(string(parser, field), field,
                        ScopeSessionRow.Client::fromWireName));
                case FIELD_CLIENT_SESSION_ID -> builder.clientSessionId(nullableString(parser, field));
                case FIELD_SCOPE -> builder.scope(readScope(parser));
                case FIELD_WORKDIR -> builder.workdir(readWorkdir(parser));
                case FIELD_ISSUED_AT -> builder.issuedAt(instant(parser, field));
                case FIELD_ISSUED_BY -> builder.cause(wireValue(string(parser, field), field,
                        GenerationCause::fromWireName));
                case FIELD_SESSION_TASKS -> builder.sessionTasks(bool(parser, field));
                case FIELD_OFFERS_SUSPENDED -> builder.offersSuspended(bool(parser, field));
                case FIELD_DELIVERED_SKILLS -> builder.deliveredSkills(
                        array(parser, field, SessionGenerationCodec::readDeliveredSkill));
                case FIELD_FENCED_AT -> {
                    var fencedAt = nullableInstant(parser, field);
                    builder.fence(fencedAt == null ? null : new SessionGeneration.Fence(fencedAt, false));
                }
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "generation " + key, FIELD_SESSION_KEY, FIELD_CLIENT, FIELD_CLIENT_SESSION_ID,
                FIELD_SCOPE, FIELD_WORKDIR, FIELD_ISSUED_AT, FIELD_ISSUED_BY, FIELD_SESSION_TASKS,
                FIELD_OFFERS_SUSPENDED, FIELD_DELIVERED_SKILLS, FIELD_FENCED_AT);
        return construct(builder::build);
    }

    private static Scope readScope(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        requireObject(parser, FIELD_SCOPE);
        Scope.Type type = null;
        String id = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_TYPE -> type = wireValue(string(parser, field), field, Scope.Type::fromWireName);
                case FIELD_ID -> id = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_SCOPE, FIELD_TYPE, FIELD_ID);
        var scopeType = type;
        var scopeId = id;
        return construct(() -> new Scope(scopeType, scopeId));
    }

    private static Workdir readWorkdir(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        requireObject(parser, FIELD_WORKDIR);
        Workdir.Kind kind = null;
        String planId = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KIND -> kind = wireValue(string(parser, field), field, Workdir.Kind::fromWireName);
                case FIELD_PLAN_ID -> planId = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_WORKDIR, FIELD_KIND, FIELD_PLAN_ID);
        var workdirKind = kind;
        var workdirPlan = planId;
        return construct(() -> new Workdir(workdirKind, workdirPlan));
    }

    private static DeliveredSkill readDeliveredSkill(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String uri = null;
        String digest = null;
        Instant deliveredAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_URI -> uri = string(parser, field);
                case FIELD_DIGEST -> digest = string(parser, field);
                case FIELD_DELIVERED_AT -> deliveredAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_URI, FIELD_DIGEST, FIELD_DELIVERED_AT);
        var skillUri = uri;
        var skillDigest = digest;
        var skillDeliveredAt = deliveredAt;
        return construct(() -> new DeliveredSkill(skillUri, skillDigest, skillDeliveredAt));
    }

    /**
     * Derives for every fenced entry whether a {@code pm-mcp enter} fenced it: another entry of the same session key
     * issued by {@code enter} at the instant of the fence.
     */
    private static List<SessionGeneration> withFenceCauses(List<SessionGeneration> generations) {
        var derived = new ArrayList<SessionGeneration>(generations.size());
        for (var generation : generations) {
            if (!generation.isFenced()) {
                derived.add(generation);
                continue;
            }
            var fencedAt = generation.fence().at();
            var byEnter = generations.stream().anyMatch(other -> !other.id().equals(generation.id())
                    && other.sessionKey().equals(generation.sessionKey()) && other.cause() == GenerationCause.ENTER
                    && other.issuedAt().equals(fencedAt));
            derived.add(generation.toBuilder().fence(new SessionGeneration.Fence(fencedAt, byEnter)).build());
        }
        return List.copyOf(derived);
    }

    private static <V> V wireValue(String wireName, String field, Function<String, Optional<V>> lookup) {
        return lookup.apply(wireName).orElseThrow(
                () -> unreadable("'%s' has the undeclared value '%s'".formatted(field, wireName)));
    }

    /** Turns a value the record refuses into the refusal of the document. */
    private static <V> V construct(Supplier<V> constructor) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }
}
