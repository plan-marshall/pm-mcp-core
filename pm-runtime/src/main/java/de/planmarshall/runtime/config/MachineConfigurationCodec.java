/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.bool;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes {@code <PM_MCP_BASE>/machine-config.json} (Store Schemas § Machine Configuration Store).
 * <p>
 * The document belongs to the operator and holds far more than the machine's provider defaults, so this codec never
 * rewrites the whole file from its own model: it reads {@code providers} in place, and a write replaces that one member,
 * keeps every other member — {@code credentials}, the concurrency limits, the languages, the plan defaults — copied
 * through untouched, and writes the revision the caller names. The members the file requires and this codec does not own
 * ({@code max_build_slots}, {@code lsp_idle_timeout_seconds}) are written at their schema defaults when the document is
 * created.
 *
 * @since 0.1
 */
@UtilityClass
public class MachineConfigurationCodec {

    /** The format version of the machine configuration document. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_MAX_BUILD_SLOTS = "max_build_slots";
    private static final String FIELD_LSP_IDLE_TIMEOUT_SECONDS = "lsp_idle_timeout_seconds";
    private static final String FIELD_PROVIDERS = "providers";
    private static final String FIELD_AVAILABLE = "available";
    private static final String FIELD_VARIANT = "variant";
    private static final String FIELD_CREDENTIAL_KEY = "credential_key";

    /** The schema default of {@code max_build_slots}. */
    static final int DEFAULT_MAX_BUILD_SLOTS = 2;

    /** The schema default of {@code lsp_idle_timeout_seconds}. */
    static final int DEFAULT_LSP_IDLE_TIMEOUT_SECONDS = 900;

    /**
     * What a read of the document found.
     *
     * @param providers the machine's provider defaults; none where the document records none
     * @param revision  the {@code revision} of the document
     * @since 0.1
     */
    public record Document(ProviderDefaults providers, long revision) {
    }

    /**
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the provider defaults and the revision
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static Document read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        StrictJson.requireCurrentVersion(file, document, FORMAT_VERSION);
        var pass = new Pass(file, null, -1);
        pass.run(document);
        return new Document(pass.providers, pass.readRevision);
    }

    /**
     * Replaces the provider defaults, keeps every other member and writes the given revision.
     *
     * @param file      the document's file, named by a refusal
     * @param document  the document, UTF-8 encoded
     * @param providers the provider defaults to write
     * @param revision  the revision to write
     * @return the rewritten document, UTF-8 encoded
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static byte[] write(Path file, byte[] document, ProviderDefaults providers, long revision) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(providers, FIELD_PROVIDERS);
        StrictJson.requireCurrentVersion(file, document, FORMAT_VERSION);
        var pass = new Pass(file, providers, revision);
        pass.run(document);
        return pass.rewritten;
    }

    /**
     * @param providers the provider defaults of the new document
     * @return a machine configuration document at revision 1 holding only the required members and the defaults
     */
    public static byte[] create(ProviderDefaults providers) {
        Objects.requireNonNull(providers, "providers");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeNumberField(FIELD_REVISION, 1);
            generator.writeNumberField(FIELD_MAX_BUILD_SLOTS, DEFAULT_MAX_BUILD_SLOTS);
            generator.writeNumberField(FIELD_LSP_IDLE_TIMEOUT_SECONDS, DEFAULT_LSP_IDLE_TIMEOUT_SECONDS);
            writeProviders(generator, providers);
            generator.writeEndObject();
        } catch (IOException e) {
            throw new IllegalStateException("The machine configuration cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeProviders(JsonGenerator generator, ProviderDefaults providers) throws IOException {
        if (providers.providers().isEmpty()) {
            return;
        }
        generator.writeObjectFieldStart(FIELD_PROVIDERS);
        for (var entry : providers.providers().entrySet()) {
            writeProvider(generator, entry.getKey(), entry.getValue());
        }
        generator.writeEndObject();
    }

    private static void writeProvider(JsonGenerator generator, Provider provider, ProviderDefault def)
            throws IOException {
        generator.writeObjectFieldStart(provider.wireName());
        generator.writeBooleanField(FIELD_AVAILABLE, def.available());
        if (def.variant().isPresent()) {
            generator.writeStringField(FIELD_VARIANT, def.variant().get().wireName());
        }
        if (def.credentialKey().isPresent()) {
            generator.writeStringField(FIELD_CREDENTIAL_KEY, def.credentialKey().get());
        }
        generator.writeEndObject();
    }

    /** One pass over the document: it reads the provider defaults and, when a write is asked for, composes the new one. */
    private static final class Pass {

        private final Path file;
        private final @Nullable ProviderDefaults replacement;
        private final long writeRevision;
        private ProviderDefaults providers = ProviderDefaults.none();
        private long readRevision;
        private @Nullable byte[] rewritten;

        Pass(Path file, @Nullable ProviderDefaults replacement, long writeRevision) {
            this.file = file;
            this.replacement = replacement;
            this.writeRevision = writeRevision;
        }

        void run(byte[] document) {
            try (var parser = StrictJson.factory().createParser(document)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    throw unreadable("the machine configuration is not a JSON object");
                }
                var out = new ByteArrayOutputStream();
                try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
                    generator.writeStartObject();
                    var seen = new HashSet<String>();
                    while (parser.nextToken() == JsonToken.FIELD_NAME) {
                        var field = nextField(parser, seen);
                        switch (field) {
                            case FIELD_FORMAT_VERSION -> copyFormatVersion(parser, generator);
                            case FIELD_REVISION -> copyRevision(parser, generator);
                            case FIELD_PROVIDERS -> copyProviders(parser, generator);
                            default -> copyMember(parser, generator, field);
                        }
                    }
                    requireFields(seen, "machine configuration", FIELD_FORMAT_VERSION, FIELD_REVISION,
                            FIELD_MAX_BUILD_SLOTS, FIELD_LSP_IDLE_TIMEOUT_SECONDS);
                    if (replacement != null && !seen.contains(FIELD_PROVIDERS)) {
                        writeProviders(generator, replacement);
                    }
                    generator.writeEndObject();
                }
                if (replacement != null) {
                    rewritten = out.toByteArray();
                }
            } catch (IOException | StrictJson.Malformed e) {
                throw StoreRefusal.unreadable(file, FORMAT_VERSION, String.valueOf(e.getMessage()), e);
            }
        }

        private static void copyFormatVersion(JsonParser parser, JsonGenerator generator) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT || parser.getIntValue() != FORMAT_VERSION) {
                throw unreadable("'format_version' is not %d".formatted(FORMAT_VERSION));
            }
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
        }

        private void copyRevision(JsonParser parser, JsonGenerator generator) throws IOException {
            var documentRevision = integer(parser, FIELD_REVISION);
            if (documentRevision < 0) {
                throw unreadable("'revision' must not be negative: " + documentRevision);
            }
            readRevision = documentRevision;
            generator.writeNumberField(FIELD_REVISION, replacement == null ? documentRevision : writeRevision);
        }

        private void copyProviders(JsonParser parser, JsonGenerator generator) throws IOException {
            var read = providers(parser);
            providers = read;
            if (replacement != null) {
                writeProviders(generator, replacement);
            } else {
                generator.writeFieldName(FIELD_PROVIDERS);
                generator.writeStartObject();
                for (var entry : read.providers().entrySet()) {
                    writeProvider(generator, entry.getKey(), entry.getValue());
                }
                generator.writeEndObject();
            }
        }

        private static ProviderDefaults providers(JsonParser parser) throws IOException {
            requireObject(parser, FIELD_PROVIDERS);
            var defaults = new EnumMap<Provider, ProviderDefault>(Provider.class);
            var seen = new HashSet<String>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var key = nextField(parser, seen);
                var provider = Provider.of(key)
                        .orElseThrow(() -> unreadable("not a provider of the machine: " + key));
                defaults.put(provider, providerDefault(parser, provider));
            }
            return new ProviderDefaults(defaults);
        }

        private static ProviderDefault providerDefault(JsonParser parser, Provider provider) throws IOException {
            requireObject(parser, provider.wireName());
            var seen = new HashSet<String>();
            @Nullable Boolean available = null;
            Optional<ProviderVariant> variant = Optional.empty();
            Optional<String> credentialKey = Optional.empty();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_AVAILABLE -> available = bool(parser, field);
                    case FIELD_VARIANT -> variant = Optional.of(variant(parser));
                    case FIELD_CREDENTIAL_KEY -> credentialKey = Optional.of(string(parser, field));
                    default -> throw undeclared(field);
                }
            }
            requireFields(seen, "provider default", FIELD_AVAILABLE);
            try {
                return new ProviderDefault(available, variant, credentialKey);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw unreadable("%s: %s".formatted(provider.wireName(), e.getMessage()), e);
            }
        }

        private static ProviderVariant variant(JsonParser parser) throws IOException {
            var wireName = string(parser, FIELD_VARIANT);
            return ProviderVariant.of(wireName)
                    .orElseThrow(() -> unreadable("not a provider variant: " + wireName));
        }

        private static void copyMember(JsonParser parser, JsonGenerator generator, String field) throws IOException {
            generator.writeFieldName(field);
            generator.copyCurrentStructure(parser);
        }
    }
}
