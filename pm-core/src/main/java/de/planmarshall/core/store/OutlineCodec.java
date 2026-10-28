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
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.map;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.OutlineRecord.AddedDependency;
import de.planmarshall.core.store.OutlineRecord.AffectedFile;
import de.planmarshall.core.store.OutlineRecord.ChangeType;
import de.planmarshall.core.store.OutlineRecord.Deliverable;
import de.planmarshall.core.store.OutlineRecord.Direction;
import de.planmarshall.core.store.OutlineRecord.ExecutionMode;
import de.planmarshall.core.store.OutlineRecord.FileIntent;
import de.planmarshall.core.store.OutlineRecord.Interface;
import de.planmarshall.core.store.OutlineRecord.KeyLink;
import de.planmarshall.core.store.OutlineRecord.Profile;
import de.planmarshall.core.store.OutlineRecord.ScopeEstimate;
import de.planmarshall.core.store.OutlineRecord.Verification;
import de.planmarshall.core.store.OutlineStore.DeliveryStatus;
import de.planmarshall.core.store.OutlineStore.Revision;

/**
 * Reads and writes {@code outline.json} (Store Schemas § Outline Record).
 * <p>
 * The reader accepts exactly one shape: the current format version, only the declared fields, each at most once,
 * every required field, and a record whose rules hold. Anything else is unreadable, never an empty store, and the
 * refusal names the file.
 *
 * @since 0.1
 */
@UtilityClass
public class OutlineCodec {

    private static final String FIELD_CURRENT = "current";
    private static final String FIELD_HISTORY = "history";
    private static final String FIELD_DELIVERABLE_STATUS = "deliverable_status";
    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_SUBMITTED_AT = "submitted_at";
    private static final String FIELD_TASK_ID = "task_id";
    private static final String FIELD_RECORD = "record";
    private static final String FIELD_SUMMARY = "summary";
    private static final String FIELD_OVERVIEW = "overview";
    private static final String FIELD_SCOPE_ESTIMATE = "scope_estimate";
    private static final String FIELD_DELIVERABLES = "deliverables";
    private static final String FIELD_NUMBER = "number";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_INTENT_GLOSS = "intent_gloss";
    private static final String FIELD_CHANGE_TYPE = "change_type";
    private static final String FIELD_EXECUTION_MODE = "execution_mode";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_MODULE = "module";
    private static final String FIELD_DEPENDS = "depends";
    private static final String FIELD_DESIGN_NOTES = "design_notes";
    private static final String FIELD_PROFILES = "profiles";
    private static final String FIELD_AFFECTED_FILES = "affected_files";
    private static final String FIELD_CHANGE_PER_FILE = "change_per_file";
    private static final String FIELD_VERIFICATION = "verification";
    private static final String FIELD_SATISFIES = "satisfies";
    private static final String FIELD_ADDED_DEPENDENCIES = "added_dependencies";
    private static final String FIELD_INTERFACES = "interfaces";
    private static final String FIELD_KEY_LINKS = "key_links";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_INTENT = "intent";
    private static final String FIELD_GATE = "gate";
    private static final String FIELD_COMMAND = "command";
    private static final String FIELD_CRITERION = "criterion";
    private static final String FIELD_COORDINATE = "coordinate";
    private static final String FIELD_WHY = "why";
    private static final String FIELD_REJECTED_ALTERNATIVE = "rejected_alternative";
    private static final String FIELD_DIRECTION = "direction";
    private static final String FIELD_SYMBOL = "symbol";
    private static final String FIELD_SIGNATURE = "signature";
    private static final String FIELD_FROM = "from";
    private static final String FIELD_TO = "to";
    private static final String FIELD_PATTERN = "pattern";

    /**
     * @param store the store
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(OutlineStore store) {
        Objects.requireNonNull(store, "store");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, OutlineStore.FORMAT_VERSION);
            generator.writeFieldName(FIELD_CURRENT);
            writeRevision(generator, store.current());
            generator.writeObjectFieldStart(FIELD_DELIVERABLE_STATUS);
            for (var entry : store.deliverableStatus().entrySet()) {
                generator.writeStringField(Integer.toString(entry.getKey()), entry.getValue().wireName());
            }
            generator.writeEndObject();
            generator.writeArrayFieldStart(FIELD_HISTORY);
            for (var revision : store.history()) {
                writeRevision(generator, revision);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Outline store document cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the store file the document was read from, named by a refusal
     * @param document an outline store document, UTF-8 encoded
     * @return the store
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static OutlineStore read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, OutlineStore.FORMAT_VERSION, OutlineCodec::readDocument);
    }

    private static void writeRevision(JsonGenerator generator, Revision revision) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField(FIELD_REVISION, revision.revision());
        writeInstant(generator, FIELD_SUBMITTED_AT, revision.submittedAt());
        generator.writeStringField(FIELD_TASK_ID, revision.taskId());
        generator.writeFieldName(FIELD_RECORD);
        writeRecord(generator, revision.outlineRecord());
        generator.writeEndObject();
    }

    private static void writeRecord(JsonGenerator generator, OutlineRecord outlineRecord) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_SUMMARY, outlineRecord.summary());
        if (outlineRecord.overview() == null) {
            generator.writeNullField(FIELD_OVERVIEW);
        } else {
            generator.writeStringField(FIELD_OVERVIEW, outlineRecord.overview());
        }
        generator.writeStringField(FIELD_SCOPE_ESTIMATE, outlineRecord.scopeEstimate().wireName());
        generator.writeArrayFieldStart(FIELD_DELIVERABLES);
        for (var deliverable : outlineRecord.deliverables()) {
            writeDeliverable(generator, deliverable);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private static void writeDeliverable(JsonGenerator generator, Deliverable deliverable) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField(FIELD_NUMBER, deliverable.number());
        generator.writeStringField(FIELD_TITLE, deliverable.title());
        generator.writeStringField(FIELD_INTENT_GLOSS, deliverable.intentGloss());
        generator.writeStringField(FIELD_CHANGE_TYPE, deliverable.changeType().wireName());
        generator.writeStringField(FIELD_EXECUTION_MODE, deliverable.executionMode().wireName());
        generator.writeStringField(FIELD_DOMAIN, deliverable.domain());
        generator.writeStringField(FIELD_MODULE, deliverable.module());
        generator.writeArrayFieldStart(FIELD_DEPENDS);
        for (var dependency : deliverable.depends()) {
            generator.writeNumber(dependency);
        }
        generator.writeEndArray();
        if (deliverable.designNotes() == null) {
            generator.writeNullField(FIELD_DESIGN_NOTES);
        } else {
            generator.writeStringField(FIELD_DESIGN_NOTES, deliverable.designNotes());
        }
        generator.writeArrayFieldStart(FIELD_PROFILES);
        for (var profile : deliverable.profiles()) {
            generator.writeString(profile.wireName());
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_AFFECTED_FILES);
        for (var file : deliverable.affectedFiles()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_PATH, file.path());
            generator.writeStringField(FIELD_INTENT, file.intent().wireName());
            if (file.gate() == null) {
                generator.writeNullField(FIELD_GATE);
            } else {
                generator.writeStringField(FIELD_GATE, file.gate());
            }
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeStringField(FIELD_CHANGE_PER_FILE, deliverable.changePerFile());
        generator.writeArrayFieldStart(FIELD_VERIFICATION);
        for (var verification : deliverable.verification()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_COMMAND, verification.command());
            generator.writeStringField(FIELD_CRITERION, verification.criterion());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_SATISFIES);
        for (var entry : deliverable.satisfies()) {
            generator.writeString(entry);
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_ADDED_DEPENDENCIES);
        for (var dependency : deliverable.addedDependencies()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_COORDINATE, dependency.coordinate());
            generator.writeStringField(FIELD_WHY, dependency.why());
            generator.writeStringField(FIELD_REJECTED_ALTERNATIVE, dependency.rejectedAlternative());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_INTERFACES);
        for (var contract : deliverable.interfaces()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_DIRECTION, contract.direction().wireName());
            generator.writeStringField(FIELD_SYMBOL, contract.symbol());
            generator.writeStringField(FIELD_SIGNATURE, contract.signature());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(FIELD_KEY_LINKS);
        for (var link : deliverable.keyLinks()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_FROM, link.from());
            generator.writeStringField(FIELD_TO, link.to());
            generator.writeStringField(FIELD_PATTERN, link.pattern());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private static OutlineStore readDocument(JsonParser parser) throws IOException {
        Revision current = null;
        Map<Integer, DeliveryStatus> statuses = Map.of();
        List<Revision> history = List.of();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_CURRENT -> current = readRevision(parser, FIELD_CURRENT);
                case FIELD_DELIVERABLE_STATUS -> statuses = byNumber(map(parser, FIELD_DELIVERABLE_STATUS,
                        OutlineCodec::readStatus));
                case FIELD_HISTORY -> history = array(parser, FIELD_HISTORY, OutlineCodec::readRevision);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "document", FIELD_CURRENT, FIELD_DELIVERABLE_STATUS, FIELD_HISTORY);
        try {
            return new OutlineStore(current, statuses, history);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static DeliveryStatus readStatus(JsonParser parser, String name) throws IOException {
        var wireName = string(parser, name);
        return DeliveryStatus.fromWireName(wireName)
                .orElseThrow(() -> unreadable("'%s' names no delivery status".formatted(name)));
    }

    private static Revision readRevision(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        Long revision = null;
        Instant submittedAt = null;
        String taskId = null;
        OutlineRecord outlineRecord = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_REVISION -> revision = integer(parser, field);
                case FIELD_SUBMITTED_AT -> submittedAt = StrictJson.instant(parser, field);
                case FIELD_TASK_ID -> taskId = string(parser, field);
                case FIELD_RECORD -> outlineRecord = readRecord(parser, FIELD_RECORD);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_REVISION, FIELD_SUBMITTED_AT, FIELD_TASK_ID, FIELD_RECORD);
        try {
            return new Revision(Math.toIntExact(revision), submittedAt, taskId, outlineRecord);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static OutlineRecord readRecord(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String summary = null;
        String overview = null;
        ScopeEstimate scope = null;
        List<Deliverable> deliverables = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_SUMMARY -> summary = string(parser, field);
                case FIELD_OVERVIEW -> overview = nullableString(parser, field);
                case FIELD_SCOPE_ESTIMATE -> scope = required(ScopeEstimate.fromWireName(string(parser, field)), field);
                case FIELD_DELIVERABLES -> deliverables = array(parser, field, OutlineCodec::readDeliverable);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_SUMMARY, FIELD_OVERVIEW, FIELD_SCOPE_ESTIMATE, FIELD_DELIVERABLES);
        try {
            return new OutlineRecord(summary, overview, scope, deliverables);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Deliverable readDeliverable(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        Long number = null;
        String title = null;
        String intentGloss = null;
        ChangeType changeType = null;
        ExecutionMode executionMode = null;
        String domain = null;
        String module = null;
        List<Integer> depends = List.of();
        String designNotes = null;
        List<Profile> profiles = null;
        List<AffectedFile> affectedFiles = null;
        String changePerFile = null;
        List<Verification> verification = null;
        List<String> satisfies = null;
        List<AddedDependency> addedDependencies = null;
        List<Interface> interfaces = null;
        List<KeyLink> keyLinks = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_NUMBER -> number = integer(parser, field);
                case FIELD_TITLE -> title = string(parser, field);
                case FIELD_INTENT_GLOSS -> intentGloss = string(parser, field);
                case FIELD_CHANGE_TYPE -> changeType = required(ChangeType.fromWireName(string(parser, field)), field);
                case FIELD_EXECUTION_MODE ->
                    executionMode = required(ExecutionMode.fromWireName(string(parser, field)), field);
                case FIELD_DOMAIN -> domain = string(parser, field);
                case FIELD_MODULE -> module = string(parser, field);
                case FIELD_DEPENDS -> depends = array(parser, field, OutlineCodec::readNumber);
                case FIELD_DESIGN_NOTES -> designNotes = nullableString(parser, field);
                case FIELD_PROFILES -> profiles = array(parser, field, OutlineCodec::readProfile);
                case FIELD_AFFECTED_FILES -> affectedFiles = array(parser, field, OutlineCodec::readAffectedFile);
                case FIELD_CHANGE_PER_FILE -> changePerFile = string(parser, field);
                case FIELD_VERIFICATION -> verification = array(parser, field, OutlineCodec::readVerification);
                case FIELD_SATISFIES -> satisfies = stringList(parser, field);
                case FIELD_ADDED_DEPENDENCIES ->
                    addedDependencies = array(parser, field, OutlineCodec::readAddedDependency);
                case FIELD_INTERFACES -> interfaces = array(parser, field, OutlineCodec::readInterface);
                case FIELD_KEY_LINKS -> keyLinks = array(parser, field, OutlineCodec::readKeyLink);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_NUMBER, FIELD_TITLE, FIELD_INTENT_GLOSS, FIELD_CHANGE_TYPE,
                FIELD_EXECUTION_MODE, FIELD_DOMAIN, FIELD_MODULE, FIELD_DEPENDS, FIELD_DESIGN_NOTES, FIELD_PROFILES,
                FIELD_AFFECTED_FILES, FIELD_CHANGE_PER_FILE, FIELD_VERIFICATION, FIELD_SATISFIES,
                FIELD_ADDED_DEPENDENCIES, FIELD_INTERFACES, FIELD_KEY_LINKS);
        try {
            return new Deliverable(Math.toIntExact(number), title, intentGloss, changeType, executionMode, domain,
                    module, depends, designNotes, profiles, affectedFiles, changePerFile, verification, satisfies,
                    addedDependencies, interfaces, keyLinks);
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Integer readNumber(JsonParser parser, String name) throws IOException {
        try {
            return Math.toIntExact(integer(parser, name));
        } catch (ArithmeticException e) {
            throw unreadable("'%s' is out of the integer range".formatted(name), e);
        }
    }

    private static Profile readProfile(JsonParser parser, String name) throws IOException {
        return required(Profile.fromWireName(string(parser, name)), name);
    }

    private static AffectedFile readAffectedFile(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String path = null;
        FileIntent intent = null;
        String gate = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_PATH -> path = string(parser, field);
                case FIELD_INTENT -> intent = required(FileIntent.fromWireName(string(parser, field)), field);
                case FIELD_GATE -> gate = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_PATH, FIELD_INTENT, FIELD_GATE);
        try {
            return new AffectedFile(path, intent, gate);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Verification readVerification(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String command = null;
        String criterion = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_COMMAND -> command = string(parser, field);
                case FIELD_CRITERION -> criterion = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_COMMAND, FIELD_CRITERION);
        try {
            return new Verification(command, criterion);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static AddedDependency readAddedDependency(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String coordinate = null;
        String why = null;
        String rejected = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_COORDINATE -> coordinate = string(parser, field);
                case FIELD_WHY -> why = string(parser, field);
                case FIELD_REJECTED_ALTERNATIVE -> rejected = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_COORDINATE, FIELD_WHY, FIELD_REJECTED_ALTERNATIVE);
        try {
            return new AddedDependency(coordinate, why, rejected);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Interface readInterface(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        Direction direction = null;
        String symbol = null;
        String signature = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_DIRECTION -> direction = required(Direction.fromWireName(string(parser, field)), field);
                case FIELD_SYMBOL -> symbol = string(parser, field);
                case FIELD_SIGNATURE -> signature = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_DIRECTION, FIELD_SYMBOL, FIELD_SIGNATURE);
        try {
            return new Interface(direction, symbol, signature);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static KeyLink readKeyLink(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        String from = null;
        String to = null;
        String pattern = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FROM -> from = string(parser, field);
                case FIELD_TO -> to = string(parser, field);
                case FIELD_PATTERN -> pattern = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_FROM, FIELD_TO, FIELD_PATTERN);
        try {
            return new KeyLink(from, to, pattern);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static <T> T required(Optional<T> value, String field) {
        return value.orElseThrow(() -> unreadable("'%s' names no known value".formatted(field)));
    }

    private static Map<Integer, DeliveryStatus> byNumber(Map<String, DeliveryStatus> byKey) {
        var statuses = new LinkedHashMap<Integer, DeliveryStatus>();
        byKey.forEach((key, status) -> {
            try {
                statuses.put(Integer.valueOf(key), status);
            } catch (NumberFormatException e) {
                throw unreadable("'%s' is no deliverable number".formatted(key), e);
            }
        });
        return statuses;
    }
}
