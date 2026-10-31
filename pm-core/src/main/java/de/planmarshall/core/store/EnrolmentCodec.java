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
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.map;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableInstant;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireArray;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.stringList;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.ProjectEnrolment.ArgumentSchema;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.Binding;
import de.planmarshall.core.store.ProjectEnrolment.BooleanValue;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;
import de.planmarshall.core.store.ProjectEnrolment.ContainerEngine;
import de.planmarshall.core.store.ProjectEnrolment.EnumValue;
import de.planmarshall.core.store.ProjectEnrolment.HostTool;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.Interpreter;
import de.planmarshall.core.store.ProjectEnrolment.LanguageServer;
import de.planmarshall.core.store.ProjectEnrolment.NumberValue;
import de.planmarshall.core.store.ProjectEnrolment.PinnedFile;
import de.planmarshall.core.store.ProjectEnrolment.ProjectScript;
import de.planmarshall.core.store.ProjectEnrolment.Provider;
import de.planmarshall.core.store.ProjectEnrolment.ProviderVerification;
import de.planmarshall.core.store.ProjectEnrolment.SchemaType;
import de.planmarshall.core.store.ProjectEnrolment.TextValue;
import de.planmarshall.core.store.ProjectEnrolment.WorkerKind;
import de.planmarshall.core.store.ProjectEnrolment.Wrapper;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;

/**
 * Reads and writes the enrolment file of a project (PM-EXEC-2, PM-MIG-2, PM-IMPL-2):
 * {@code enrolments/<project_id>.json}, a {@link ProjectEnrolment}.
 * <p>
 * The reader accepts exactly one shape and never tolerates another: the {@code format_version} first (another version
 * is refused as {@code format_outdated} or {@code format_newer} naming the file), then every field at most once, only
 * the declared fields at every level of nesting, every required field, and one JSON type per field. Whatever does not
 * parse is a {@link StoreRefusal} naming the file. The optional fields {@code pilot}, {@code container_engine},
 * {@code released_at} and the {@code interpreter} of a host tool read as their defaults when absent.
 * <p>
 * The writer writes the canonical bytes of an enrolment: every field, defaults included, in the order of the schema,
 * and the entries of every map in the stable order of {@link ProjectEnrolment}. The same enrolment always gives the
 * same bytes, the bytes the integrity HMAC of the enrolment is computed over (PM-EXEC-2, PM-SEC-5).
 *
 * @since 0.1
 */
@UtilityClass
public class EnrolmentCodec {

    private static final String FIELD_PROJECT_ID = "project_id";
    private static final String FIELD_PROJECT_ROOT = "project_root";
    private static final String FIELD_ENROLLED_AT = "enrolled_at";
    private static final String FIELD_PILOT = "pilot";
    private static final String FIELD_CONTAINER_ENGINE = "container_engine";
    private static final String FIELD_RELEASED_AT = "released_at";
    private static final String FIELD_WRAPPERS = "wrappers";
    private static final String FIELD_HOST_TOOLS = "host_tools";
    private static final String FIELD_BUILD = "build";
    private static final String FIELD_PROVIDERS = "providers";
    private static final String FIELD_PROJECT_SCRIPTS = "project_scripts";
    private static final String FIELD_LANGUAGE_SERVERS = "language_servers";
    private static final String FIELD_COMMAND_BUDGETS = "command_budgets";
    private static final String FIELD_WRITE_PATHS = "write_paths";
    private static final String FIELD_BUILD_ENVIRONMENT = "build_environment";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_RELATIVE_PATH = "relative_path";
    private static final String FIELD_SHA256 = "sha256";
    private static final String FIELD_PINNED_FILES = "pinned_files";
    private static final String FIELD_INTERPRETER = "interpreter";
    private static final String FIELD_EXECUTABLE_PATH = "executable_path";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_CANONICAL_TEMPLATES = "canonical_templates";
    private static final String FIELD_DISCOVERY_TEMPLATES = "discovery_templates";
    private static final String FIELD_VERIFY_ARGV = "verify_argv";
    private static final String FIELD_SCRIPT_PATH = "script_path";
    private static final String FIELD_BINDING = "binding";
    private static final String FIELD_WORKER_KIND = "worker_kind";
    private static final String FIELD_TARGET = "target";
    private static final String FIELD_ARGUMENT_SCHEMA = "argument_schema";
    private static final String FIELD_MAX_TIMEOUT_SECONDS = "max_timeout_seconds";
    private static final String FIELD_ARGS = "args";
    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";
    private static final String SCHEMA_ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String SCHEMA_ENUM = "enum";
    private static final String SCHEMA_PATTERN = "pattern";
    private static final String SCHEMA_MINIMUM = "minimum";
    private static final String SCHEMA_MAXIMUM = "maximum";
    private static final String SCHEMA_MAX_LENGTH = "maxLength";

    /**
     * @param enrolment the enrolment of a project
     * @return its canonical bytes, UTF-8 encoded
     */
    public static byte[] write(ProjectEnrolment enrolment) {
        Objects.requireNonNull(enrolment, "enrolment");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, ProjectEnrolment.FORMAT_VERSION);
            generator.writeStringField(FIELD_PROJECT_ID, enrolment.projectId());
            generator.writeStringField(FIELD_PROJECT_ROOT, enrolment.projectRoot());
            writeInstant(generator, FIELD_ENROLLED_AT, enrolment.enrolledAt());
            generator.writeBooleanField(FIELD_PILOT, enrolment.pilot());
            writeNullableString(generator, FIELD_CONTAINER_ENGINE,
                    enrolment.containerEngine() == null ? null : enrolment.containerEngine().wireName());
            writeInstant(generator, FIELD_RELEASED_AT, enrolment.releasedAt());
            generator.writeArrayFieldStart(FIELD_WRAPPERS);
            for (var wrapper : enrolment.wrappers()) {
                writeWrapper(generator, wrapper);
            }
            generator.writeEndArray();
            generator.writeArrayFieldStart(FIELD_HOST_TOOLS);
            for (var tool : enrolment.hostTools()) {
                writeHostTool(generator, tool);
            }
            generator.writeEndArray();
            generator.writeObjectFieldStart(FIELD_BUILD);
            writeTemplates(generator, FIELD_CANONICAL_TEMPLATES, enrolment.build().canonicalTemplates());
            writeTemplates(generator, FIELD_DISCOVERY_TEMPLATES, enrolment.build().discoveryTemplates());
            generator.writeEndObject();
            generator.writeObjectFieldStart(FIELD_PROVIDERS);
            for (var provider : enrolment.providers().entrySet()) {
                generator.writeObjectFieldStart(provider.getKey().wireName());
                generator.writeFieldName(FIELD_VERIFY_ARGV);
                writeArgv(generator, provider.getValue().verifyArgv());
                generator.writeEndObject();
            }
            generator.writeEndObject();
            generator.writeObjectFieldStart(FIELD_PROJECT_SCRIPTS);
            for (var script : enrolment.projectScripts().entrySet()) {
                generator.writeFieldName(script.getKey());
                writeProjectScript(generator, script.getValue());
            }
            generator.writeEndObject();
            generator.writeObjectFieldStart(FIELD_LANGUAGE_SERVERS);
            for (var server : enrolment.languageServers().entrySet()) {
                generator.writeFieldName(server.getKey());
                writeLanguageServer(generator, server.getValue());
            }
            generator.writeEndObject();
            generator.writeObjectFieldStart(FIELD_COMMAND_BUDGETS);
            for (var budget : enrolment.commandBudgets().entrySet()) {
                generator.writeNumberField(budget.getKey(), budget.getValue());
            }
            generator.writeEndObject();
            writeStrings(generator, FIELD_WRITE_PATHS, enrolment.writePaths());
            generator.writeObjectFieldStart(FIELD_BUILD_ENVIRONMENT);
            for (var variable : enrolment.buildEnvironment().entrySet()) {
                generator.writeStringField(variable.getKey(), variable.getValue());
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Enrolment file cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the enrolment file the document was read from, named by a refusal
     * @param document an enrolment document, UTF-8 encoded
     * @return the enrolment
     * @throws StoreRefusal if the document declares another format version or does not have the one shape of an
     *                      enrolment
     */
    public static ProjectEnrolment read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, ProjectEnrolment.FORMAT_VERSION, EnrolmentCodec::readDocument);
    }

    private static void writeWrapper(JsonGenerator generator, Wrapper wrapper) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_NAME, wrapper.name().wireName());
        generator.writeStringField(FIELD_RELATIVE_PATH, wrapper.relativePath());
        generator.writeStringField(FIELD_SHA256, wrapper.sha256());
        generator.writeArrayFieldStart(FIELD_PINNED_FILES);
        for (var pinned : wrapper.pinnedFiles()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_RELATIVE_PATH, pinned.relativePath());
            writeNullableString(generator, FIELD_SHA256, pinned.sha256());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        writeNullableString(generator, FIELD_INTERPRETER, interpreter(wrapper.interpreter()).orElse(null));
        generator.writeEndObject();
    }

    private static void writeHostTool(JsonGenerator generator, HostTool tool) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_NAME, tool.name().wireName());
        generator.writeStringField(FIELD_EXECUTABLE_PATH, tool.executablePath());
        generator.writeStringField(FIELD_SHA256, tool.sha256());
        writeNullableString(generator, FIELD_INTERPRETER, interpreter(tool.interpreter()).orElse(null));
        writeNullableString(generator, FIELD_VERSION, tool.version());
        generator.writeEndObject();
    }

    private static void writeTemplates(JsonGenerator generator, String field,
            Map<BuildSystem, SortedMap<String, Argv>> templates) throws IOException {
        generator.writeObjectFieldStart(field);
        for (var system : templates.entrySet()) {
            generator.writeObjectFieldStart(system.getKey().wireName());
            for (var template : system.getValue().entrySet()) {
                generator.writeFieldName(template.getKey());
                writeArgv(generator, template.getValue());
            }
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    private static void writeArgv(JsonGenerator generator, Argv argv) throws IOException {
        generator.writeStartArray();
        for (var element : argv.elements()) {
            switch (element) {
                case Argv.Literal(var value) -> generator.writeString(value);
                case Argv.ModuleGroup(var arguments) -> {
                    generator.writeStartArray();
                    for (var argument : arguments) {
                        generator.writeString(argument);
                    }
                    generator.writeEndArray();
                }
            }
        }
        generator.writeEndArray();
    }

    private static void writeProjectScript(JsonGenerator generator, ProjectScript script) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_SCRIPT_PATH, script.scriptPath());
        generator.writeStringField(FIELD_SHA256, script.sha256());
        generator.writeStringField(FIELD_INTERPRETER, script.interpreter().wireName());
        generator.writeObjectFieldStart(FIELD_BINDING);
        generator.writeStringField(FIELD_WORKER_KIND, script.binding().workerKind().wireName());
        generator.writeStringField(FIELD_TARGET, script.binding().target());
        generator.writeEndObject();
        generator.writeFieldName(FIELD_ARGUMENT_SCHEMA);
        writeSchema(generator, script.argumentSchema());
        generator.writeNumberField(FIELD_MAX_TIMEOUT_SECONDS, script.maxTimeoutSeconds());
        generator.writeEndObject();
    }

    private static void writeSchema(JsonGenerator generator, ArgumentSchema schema) throws IOException {
        generator.writeStartObject();
        if (schema.type() != null) {
            generator.writeStringField(SCHEMA_TYPE, schema.type().wireName());
        }
        if (schema.properties() != null) {
            generator.writeObjectFieldStart(SCHEMA_PROPERTIES);
            for (var property : schema.properties().entrySet()) {
                generator.writeFieldName(property.getKey());
                writeSchema(generator, property.getValue());
            }
            generator.writeEndObject();
        }
        if (schema.required() != null) {
            writeStrings(generator, SCHEMA_REQUIRED, schema.required());
        }
        if (schema.closed()) {
            generator.writeBooleanField(SCHEMA_ADDITIONAL_PROPERTIES, false);
        }
        if (schema.enumValues() != null) {
            writeEnumValues(generator, schema.enumValues());
        }
        if (schema.pattern() != null) {
            generator.writeStringField(SCHEMA_PATTERN, schema.pattern());
        }
        if (schema.minimum() != null) {
            generator.writeNumberField(SCHEMA_MINIMUM, schema.minimum());
        }
        if (schema.maximum() != null) {
            generator.writeNumberField(SCHEMA_MAXIMUM, schema.maximum());
        }
        if (schema.maxLength() != null) {
            generator.writeNumberField(SCHEMA_MAX_LENGTH, schema.maxLength());
        }
        generator.writeEndObject();
    }

    private static void writeEnumValues(JsonGenerator generator, List<EnumValue> values) throws IOException {
        generator.writeArrayFieldStart(SCHEMA_ENUM);
        for (var value : values) {
            switch (value) {
                case TextValue(var text) -> generator.writeString(text);
                case NumberValue(var number) -> generator.writeNumber(number);
                case BooleanValue(var flag) -> generator.writeBoolean(flag);
            }
        }
        generator.writeEndArray();
    }

    private static void writeLanguageServer(JsonGenerator generator, LanguageServer server) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_EXECUTABLE_PATH, server.executablePath());
        generator.writeStringField(FIELD_SHA256, server.sha256());
        writeStrings(generator, FIELD_ARGS, server.args());
        generator.writeEndObject();
    }

    private static void writeStrings(JsonGenerator generator, String field, List<String> values) throws IOException {
        generator.writeArrayFieldStart(field);
        for (var value : values) {
            generator.writeString(value);
        }
        generator.writeEndArray();
    }

    private static void writeNullableString(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static Optional<String> interpreter(@Nullable Interpreter interpreter) {
        return interpreter == null ? Optional.empty() : Optional.of(interpreter.wireName());
    }

    private static ProjectEnrolment readDocument(JsonParser parser) throws IOException {
        var root = new Root();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_PROJECT_ID -> root.projectId = string(parser, field);
                case FIELD_PROJECT_ROOT -> root.projectRoot = string(parser, field);
                case FIELD_ENROLLED_AT -> root.enrolledAt = instant(parser, field);
                case FIELD_PILOT -> root.pilot = bool(parser, field);
                case FIELD_CONTAINER_ENGINE -> root.containerEngine = nullableEnum(parser, field,
                        ContainerEngine::fromWireName);
                case FIELD_RELEASED_AT -> root.releasedAt = nullableInstant(parser, field);
                case FIELD_WRAPPERS -> root.wrappers = array(parser, field, EnrolmentCodec::readWrapper);
                case FIELD_HOST_TOOLS -> root.hostTools = array(parser, field, EnrolmentCodec::readHostTool);
                case FIELD_BUILD -> root.build = readBuild(parser);
                case FIELD_PROVIDERS -> root.providers = enumKeys(map(parser, field, EnrolmentCodec::readProvider),
                        Provider.class, Provider::fromWireName, field);
                case FIELD_PROJECT_SCRIPTS -> root.projectScripts = new TreeMap<>(
                        map(parser, field, EnrolmentCodec::readProjectScript));
                case FIELD_LANGUAGE_SERVERS -> root.languageServers = new TreeMap<>(
                        map(parser, field, EnrolmentCodec::readLanguageServer));
                case FIELD_COMMAND_BUDGETS -> root.commandBudgets = new TreeMap<>(
                        map(parser, field, StrictJson::integer));
                case FIELD_WRITE_PATHS -> root.writePaths = stringList(parser, field);
                case FIELD_BUILD_ENVIRONMENT -> root.buildEnvironment = new TreeMap<>(
                        map(parser, field, StrictJson::string));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "enrolment", FIELD_PROJECT_ID, FIELD_PROJECT_ROOT, FIELD_ENROLLED_AT, FIELD_WRAPPERS,
                FIELD_HOST_TOOLS, FIELD_BUILD, FIELD_PROVIDERS, FIELD_PROJECT_SCRIPTS, FIELD_LANGUAGE_SERVERS,
                FIELD_COMMAND_BUDGETS, FIELD_WRITE_PATHS, FIELD_BUILD_ENVIRONMENT);
        try {
            return new ProjectEnrolment(root.projectId, root.projectRoot, root.enrolledAt, root.pilot,
                    root.containerEngine, root.releasedAt, root.wrappers, root.hostTools, root.build, root.providers,
                    root.projectScripts, root.languageServers, root.commandBudgets, root.writePaths,
                    root.buildEnvironment);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Wrapper readWrapper(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable WrapperName wrapperName = null;
        @Nullable String relativePath = null;
        @Nullable String sha256 = null;
        @Nullable List<PinnedFile> pinnedFiles = null;
        @Nullable Interpreter interpreter = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_NAME -> wrapperName = enumValue(string(parser, field), WrapperName::fromWireName, field);
                case FIELD_RELATIVE_PATH -> relativePath = string(parser, field);
                case FIELD_SHA256 -> sha256 = string(parser, field);
                case FIELD_PINNED_FILES -> pinnedFiles = array(parser, field, EnrolmentCodec::readPinnedFile);
                case FIELD_INTERPRETER -> interpreter = nullableEnum(parser, field, Interpreter::fromWireName);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_NAME, FIELD_RELATIVE_PATH, FIELD_SHA256, FIELD_PINNED_FILES,
                FIELD_INTERPRETER);
        try {
            return new Wrapper(wrapperName, relativePath, sha256, pinnedFiles, interpreter);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static PinnedFile readPinnedFile(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String relativePath = null;
        @Nullable String sha256 = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_RELATIVE_PATH -> relativePath = string(parser, field);
                case FIELD_SHA256 -> sha256 = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_RELATIVE_PATH, FIELD_SHA256);
        try {
            return new PinnedFile(relativePath, sha256);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static HostTool readHostTool(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable HostToolName toolName = null;
        @Nullable String executablePath = null;
        @Nullable String sha256 = null;
        @Nullable Interpreter interpreter = null;
        @Nullable String version = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_NAME -> toolName = enumValue(string(parser, field), HostToolName::fromWireName, field);
                case FIELD_EXECUTABLE_PATH -> executablePath = string(parser, field);
                case FIELD_SHA256 -> sha256 = string(parser, field);
                case FIELD_INTERPRETER -> interpreter = nullableEnum(parser, field, Interpreter::fromWireName);
                case FIELD_VERSION -> version = nullableString(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_NAME, FIELD_EXECUTABLE_PATH, FIELD_SHA256, FIELD_VERSION);
        try {
            return new HostTool(toolName, executablePath, sha256, interpreter, version);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Build readBuild(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_BUILD);
        @Nullable Map<BuildSystem, SortedMap<String, Argv>> canonical = null;
        @Nullable Map<BuildSystem, SortedMap<String, Argv>> discovery = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_CANONICAL_TEMPLATES -> canonical = readTemplates(parser, field);
                case FIELD_DISCOVERY_TEMPLATES -> discovery = readTemplates(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_BUILD, FIELD_CANONICAL_TEMPLATES, FIELD_DISCOVERY_TEMPLATES);
        try {
            return new Build(canonical, discovery);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Map<BuildSystem, SortedMap<String, Argv>> readTemplates(JsonParser parser, String field)
            throws IOException {
        var systems = map(parser, field, EnrolmentCodec::readTemplateSet);
        return enumKeys(systems, BuildSystem.class, BuildSystem::fromWireName, field);
    }

    private static SortedMap<String, Argv> readTemplateSet(JsonParser parser, String system) throws IOException {
        return new TreeMap<>(map(parser, system, EnrolmentCodec::readArgv));
    }

    private static ProviderVerification readProvider(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable Argv verifyArgv = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            if (!FIELD_VERIFY_ARGV.equals(field)) {
                throw undeclared(field);
            }
            verifyArgv = readArgv(parser, field);
        }
        requireFields(seen, name, FIELD_VERIFY_ARGV);
        return new ProviderVerification(verifyArgv);
    }

    private static Argv readArgv(JsonParser parser, String name) throws IOException {
        requireArray(parser, name);
        var elements = new ArrayList<Argv.Element>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == JsonToken.START_ARRAY) {
                elements.add(argvElement(() -> new Argv.ModuleGroup(stringList(parser, name))));
            } else {
                elements.add(argvElement(() -> new Argv.Literal(string(parser, name))));
            }
        }
        try {
            return new Argv(elements);
        } catch (IllegalArgumentException e) {
            throw unreadable("'%s': %s".formatted(name, e.getMessage()), e);
        }
    }

    private static ProjectScript readProjectScript(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String scriptPath = null;
        @Nullable String sha256 = null;
        @Nullable Interpreter interpreter = null;
        @Nullable Binding binding = null;
        @Nullable ArgumentSchema argumentSchema = null;
        long maxTimeoutSeconds = 0;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_SCRIPT_PATH -> scriptPath = string(parser, field);
                case FIELD_SHA256 -> sha256 = string(parser, field);
                case FIELD_INTERPRETER -> interpreter = enumValue(string(parser, field), Interpreter::fromWireName,
                        field);
                case FIELD_BINDING -> binding = readBinding(parser);
                case FIELD_ARGUMENT_SCHEMA -> argumentSchema = readSchema(parser, field);
                case FIELD_MAX_TIMEOUT_SECONDS -> maxTimeoutSeconds = integer(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_SCRIPT_PATH, FIELD_SHA256, FIELD_INTERPRETER, FIELD_BINDING,
                FIELD_ARGUMENT_SCHEMA, FIELD_MAX_TIMEOUT_SECONDS);
        try {
            return new ProjectScript(scriptPath, sha256, interpreter, binding, argumentSchema, maxTimeoutSeconds);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Binding readBinding(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_BINDING);
        @Nullable WorkerKind workerKind = null;
        @Nullable String target = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_WORKER_KIND -> workerKind = enumValue(string(parser, field), WorkerKind::fromWireName,
                        field);
                case FIELD_TARGET -> target = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_BINDING, FIELD_WORKER_KIND, FIELD_TARGET);
        try {
            return new Binding(workerKind, target);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static ArgumentSchema readSchema(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable SchemaType type = null;
        @Nullable SortedMap<String, ArgumentSchema> properties = null;
        @Nullable List<String> required = null;
        var closed = false;
        @Nullable List<EnumValue> enumValues = null;
        @Nullable String pattern = null;
        @Nullable BigDecimal minimum = null;
        @Nullable BigDecimal maximum = null;
        @Nullable Long maxLength = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case SCHEMA_TYPE -> type = enumValue(string(parser, field), SchemaType::fromWireName, field);
                case SCHEMA_PROPERTIES -> properties = new TreeMap<>(map(parser, field, EnrolmentCodec::readSchema));
                case SCHEMA_REQUIRED -> required = stringList(parser, field);
                case SCHEMA_ADDITIONAL_PROPERTIES -> closed = readClosed(parser, field);
                case SCHEMA_ENUM -> enumValues = array(parser, field, EnrolmentCodec::readEnumValue);
                case SCHEMA_PATTERN -> pattern = string(parser, field);
                case SCHEMA_MINIMUM -> minimum = number(parser, field);
                case SCHEMA_MAXIMUM -> maximum = number(parser, field);
                case SCHEMA_MAX_LENGTH -> maxLength = integer(parser, field);
                default -> throw unreadable("'%s' is not a keyword of the argument schema subset in '%s'"
                        .formatted(field, name));
            }
        }
        try {
            return new ArgumentSchema(type, properties, required, closed, enumValues, pattern, minimum, maximum,
                    maxLength);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static boolean readClosed(JsonParser parser, String field) {
        if (bool(parser, field)) {
            throw unreadable("'%s' admits only false".formatted(field));
        }
        return true;
    }

    private static EnumValue readEnumValue(JsonParser parser, String name) throws IOException {
        return switch (parser.currentToken()) {
            case VALUE_STRING -> new TextValue(parser.getText());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new NumberValue(parser.getDecimalValue());
            case VALUE_TRUE -> new BooleanValue(true);
            case VALUE_FALSE -> new BooleanValue(false);
            case null, default -> throw unreadable("'%s' must be a string, a number or a boolean".formatted(name));
        };
    }

    private static BigDecimal number(JsonParser parser, String field) throws IOException {
        var token = parser.currentToken();
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            throw unreadable("'%s' must be a number".formatted(field));
        }
        return parser.getDecimalValue();
    }

    private static LanguageServer readLanguageServer(JsonParser parser, String name) throws IOException {
        requireObject(parser, name);
        @Nullable String executablePath = null;
        @Nullable String sha256 = null;
        @Nullable List<String> args = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_EXECUTABLE_PATH -> executablePath = string(parser, field);
                case FIELD_SHA256 -> sha256 = string(parser, field);
                case FIELD_ARGS -> args = stringList(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_EXECUTABLE_PATH, FIELD_SHA256, FIELD_ARGS);
        try {
            return new LanguageServer(executablePath, sha256, args);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E nullableEnum(JsonParser parser, String field,
            Function<String, Optional<E>> byWireName) throws IOException {
        var text = nullableString(parser, field);
        return text == null ? null : enumValue(text, byWireName, field);
    }

    private static <E extends Enum<E>> E enumValue(String text, Function<String, Optional<E>> byWireName,
            String field) {
        return byWireName.apply(text)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, text)));
    }

    private static <E extends Enum<E>, V> Map<E, V> enumKeys(Map<String, V> values, Class<E> type,
            Function<String, Optional<E>> byWireName, String field) {
        var keyed = new EnumMap<E, V>(type);
        for (var entry : values.entrySet()) {
            keyed.put(enumValue(entry.getKey(), byWireName, field), entry.getValue());
        }
        return keyed;
    }

    private static Argv.Element argvElement(ElementSupplier element) throws IOException {
        try {
            return element.get();
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface ElementSupplier {
        Argv.Element get() throws IOException;
    }

    /** The fields of the root object while it is read. */
    private static final class Root {
        private String projectId;
        private String projectRoot;
        private Instant enrolledAt;
        private boolean pilot;
        private ContainerEngine containerEngine;
        private Instant releasedAt;
        private List<Wrapper> wrappers;
        private List<HostTool> hostTools;
        private Build build;
        private Map<Provider, ProviderVerification> providers;
        private SortedMap<String, ProjectScript> projectScripts;
        private SortedMap<String, LanguageServer> languageServers;
        private SortedMap<String, Long> commandBudgets;
        private List<String> writePaths;
        private SortedMap<String, String> buildEnvironment;
    }
}
