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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads and writes {@code <PM_MCP_BASE>/machine-config.json} (Store Schemas § Machine Configuration Store).
 * <p>
 * The document belongs to the operator and holds far more than the machine's provider defaults, so this codec never
 * rewrites the whole file from its own model: it reads {@code providers}, {@code sources.documents_mode} and
 * {@code cleanup} in place, and a write replaces that one member, keeps every other member — {@code credentials}, the
 * concurrency limits, the languages, the plan defaults — copied through untouched, and writes the revision the caller
 * names. The members the file requires and this codec does not own ({@code max_build_slots},
 * {@code lsp_idle_timeout_seconds}) are written at their schema defaults when the document is created.
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
    private static final String FIELD_VERIFICATION = "verification";
    private static final String FIELD_PASSED = "passed";
    private static final String FIELD_AT = "at";
    private static final String FIELD_CHECKS = "checks";
    private static final String FIELD_CHECK = "check";
    private static final String FIELD_OK = "ok";
    private static final String FIELD_SOURCES = "sources";
    private static final String FIELD_DOCUMENTS_MODE = "documents_mode";
    private static final String FIELD_CLEANUP = "cleanup";
    private static final String FIELD_PLAN_DEFAULTS = "plan_defaults";
    private static final String FIELD_LANDING = "landing";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_ASKING = "asking";
    private static final String FIELD_POSTURE = "posture";
    private static final String FIELD_PLANS_AFTER_DAYS = "plans_after_days";
    private static final String FIELD_EPICS_AFTER_DAYS = "epics_after_days";
    private static final String FIELD_LOGS_AFTER_DAYS = "logs_after_days";
    private static final String FIELD_REPORTS_AFTER_DAYS = "reports_after_days";
    private static final String FIELD_UNENROLLED_AFTER_DAYS = "unenrolled_after_days";
    private static final String FIELD_REMIND_AFTER_DAYS = "remind_after_days";
    private static final String NEVER = "never";

    /** The lowest and highest {@code max_build_slots} the schema admits. */
    static final int MIN_BUILD_SLOTS = 1;
    static final int MAX_BUILD_SLOTS = 16;

    /** The lowest {@code lsp_idle_timeout_seconds} the schema admits. */
    static final int MIN_LSP_IDLE_TIMEOUT_SECONDS = 60;

    /** The parameter name of a document's file, in a refusal. */
    private static final String ARG_FILE = "file";

    /** The parameter name of a document's bytes, in a refusal. */
    private static final String ARG_DOCUMENT = "document";

    /** The form of a refusal that names the member and the reason. */
    private static final String DETAIL_FORMAT = "%s: %s";

    /** The schema default of {@code max_build_slots}. */
    static final int DEFAULT_MAX_BUILD_SLOTS = 2;

    /** The schema default of {@code lsp_idle_timeout_seconds}. */
    static final int DEFAULT_LSP_IDLE_TIMEOUT_SECONDS = 900;

    /**
     * What a read of the document found.
     *
     * @param providers     the machine's provider defaults; none where the document records none
     * @param documentsMode how a start takes its documents; {@code null} where the document records no mode and the
     *                      schema default ({@link DocumentsMode#DEFAULT}) applies
     * @param cleanup       the durations of the one cleanup, each member at the value the document sets or at the schema
     *                      default where it does not; {@code null} where the document records no {@code cleanup} member
     *                      and {@link CleanupDurations#defaults()} applies
     * @param planDefaults  the machine's default of each plan behaviour axis; {@code null} where the document records no
     *                      {@code plan_defaults} member and {@link PlanDefaults#shipped()} applies
     * @param revision      the {@code revision} of the document
     * @since 0.1
     */
    @Builder
    public record Document(ProviderDefaults providers, @Nullable DocumentsMode documentsMode,
    @Nullable CleanupDurations cleanup, @Nullable PlanDefaults planDefaults, long revision) {
    }

    /**
     * @param file     the document's file, named by a refusal
     * @param document the document, UTF-8 encoded
     * @return the provider defaults, the settings this codec owns and the revision
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static Document read(Path file, byte[] document) {
        Objects.requireNonNull(file, ARG_FILE);
        Objects.requireNonNull(document, ARG_DOCUMENT);
        StrictJson.requireCurrentVersion(file, document, FORMAT_VERSION);
        var pass = new Pass(file, Replacement.NONE);
        pass.run(document);
        return new Document(pass.providers, pass.documentsMode, pass.cleanup, pass.planDefaults,
                pass.readRevision);
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
        Objects.requireNonNull(file, ARG_FILE);
        Objects.requireNonNull(document, ARG_DOCUMENT);
        Objects.requireNonNull(providers, FIELD_PROVIDERS);
        StrictJson.requireCurrentVersion(file, document, FORMAT_VERSION);
        var pass = new Pass(file, new Replacement(providers, null, null, null, revision));
        pass.run(document);
        return pass.rewritten;
    }

    /**
     * Replaces the machine's documents mode, keeps every other member of the operator's document, and writes the given
     * revision.
     *
     * @param file      the document's file, named by a refusal
     * @param document  the document, UTF-8 encoded
     * @param mode      the mode to record
     * @param revision  the revision to write
     * @return the rewritten document, UTF-8 encoded
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static byte[] writeDocumentsMode(Path file, byte[] document, DocumentsMode mode, long revision) {
        Objects.requireNonNull(file, ARG_FILE);
        Objects.requireNonNull(document, ARG_DOCUMENT);
        Objects.requireNonNull(mode, FIELD_DOCUMENTS_MODE);
        StrictJson.requireCurrentVersion(file, document, FORMAT_VERSION);
        var pass = new Pass(file, new Replacement(null, mode, null, null, revision));
        pass.run(document);
        return pass.rewritten;
    }

    /**
     * Replaces the machine's cleanup durations, keeps every other member and writes the given revision.
     *
     * @param file      the document's file, named by a refusal
     * @param document  the document, UTF-8 encoded
     * @param durations the durations to record
     * @param revision  the revision to write
     * @return the rewritten document, UTF-8 encoded
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static byte[] writeCleanup(Path file, byte[] document, CleanupDurations durations, long revision) {
        Objects.requireNonNull(file, ARG_FILE);
        Objects.requireNonNull(document, ARG_DOCUMENT);
        Objects.requireNonNull(durations, FIELD_CLEANUP);
        StrictJson.requireCurrentVersion(file, document, FORMAT_VERSION);
        var pass = new Pass(file, new Replacement(null, null, durations, null, revision));
        pass.run(document);
        return pass.rewritten;
    }

    /**
     * Replaces the machine's default of each plan behaviour axis, keeps every other member and writes the given
     * revision.
     *
     * @param file      the document's file, named by a refusal
     * @param document  the document, UTF-8 encoded
     * @param defaults  the plan defaults to record
     * @param revision  the revision to write
     * @return the rewritten document, UTF-8 encoded
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static byte[] writePlanDefaults(Path file, byte[] document, PlanDefaults defaults, long revision) {
        Objects.requireNonNull(file, ARG_FILE);
        Objects.requireNonNull(document, ARG_DOCUMENT);
        Objects.requireNonNull(defaults, FIELD_PLAN_DEFAULTS);
        StrictJson.requireCurrentVersion(file, document, FORMAT_VERSION);
        var pass = new Pass(file, new Replacement(null, null, null, defaults, revision));
        pass.run(document);
        return pass.rewritten;
    }

    /**
     * @param providers the provider defaults of the new document
     * @return a machine configuration document at revision 1 holding only the required members and the defaults
     */
    public static byte[] create(ProviderDefaults providers) {
        Objects.requireNonNull(providers, FIELD_PROVIDERS);
        return create(new Replacement(providers, null, null, null, 1));
    }

    /**
     * @param mode the documents mode of the new document
     * @return a machine configuration document at revision 1 holding only the required members and the mode
     */
    public static byte[] createDocumentsMode(DocumentsMode mode) {
        Objects.requireNonNull(mode, FIELD_DOCUMENTS_MODE);
        return create(new Replacement(null, mode, null, null, 1));
    }

    /**
     * @param durations the cleanup durations of the new document
     * @return a machine configuration document at revision 1 holding only the required members and the durations
     */
    public static byte[] createCleanup(CleanupDurations durations) {
        Objects.requireNonNull(durations, FIELD_CLEANUP);
        return create(new Replacement(null, null, durations, null, 1));
    }

    /**
     * @param defaults the plan defaults of the new document
     * @return a machine configuration document at revision 1 holding only the required members and the plan defaults
     */
    public static byte[] createPlanDefaults(PlanDefaults defaults) {
        Objects.requireNonNull(defaults, FIELD_PLAN_DEFAULTS);
        return create(new Replacement(null, null, null, defaults, 1));
    }

    private static byte[] create(Replacement replacement) {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeNumberField(FIELD_REVISION, 1);
            generator.writeNumberField(FIELD_MAX_BUILD_SLOTS, DEFAULT_MAX_BUILD_SLOTS);
            generator.writeNumberField(FIELD_LSP_IDLE_TIMEOUT_SECONDS, DEFAULT_LSP_IDLE_TIMEOUT_SECONDS);
            if (replacement.providers() != null) {
                writeProviders(generator, replacement.providers());
            }
            if (replacement.mode() != null) {
                writeSources(generator, replacement.mode());
            }
            if (replacement.cleanup() != null) {
                writeCleanup(generator, replacement.cleanup());
            }
            if (replacement.planDefaults() != null) {
                writePlanDefaults(generator, replacement.planDefaults());
            }
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

    private static void writeSources(JsonGenerator generator, DocumentsMode mode) throws IOException {
        generator.writeObjectFieldStart(FIELD_SOURCES);
        generator.writeStringField(FIELD_DOCUMENTS_MODE, mode.wireName());
        generator.writeEndObject();
    }

    private static void writeCleanup(JsonGenerator generator, CleanupDurations durations) throws IOException {
        generator.writeObjectFieldStart(FIELD_CLEANUP);
        writeCleanupDuration(generator, FIELD_PLANS_AFTER_DAYS, durations.plansAfterDays());
        writeCleanupDuration(generator, FIELD_EPICS_AFTER_DAYS, durations.epicsAfterDays());
        writeCleanupDuration(generator, FIELD_LOGS_AFTER_DAYS, durations.logsAfterDays());
        writeCleanupDuration(generator, FIELD_REPORTS_AFTER_DAYS, durations.reportsAfterDays());
        writeCleanupDuration(generator, FIELD_UNENROLLED_AFTER_DAYS, durations.unenrolledAfterDays());
        generator.writeNumberField(FIELD_REMIND_AFTER_DAYS, durations.remindAfterDays());
        generator.writeEndObject();
    }

    private static void writeCleanupDuration(JsonGenerator generator, String field, CleanupDuration duration)
            throws IOException {
        var days = duration.days();
        if (days == null) {
            generator.writeStringField(field, NEVER);
        } else {
            generator.writeNumberField(field, days);
        }
    }

    private static void writePlanDefaults(JsonGenerator generator, PlanDefaults defaults) throws IOException {
        generator.writeObjectFieldStart(FIELD_PLAN_DEFAULTS);
        generator.writeStringField(FIELD_LANDING, defaults.landing());
        generator.writeStringField(FIELD_METHOD, defaults.method());
        generator.writeStringField(FIELD_ASKING, defaults.asking());
        if (defaults.posture() != null) {
            generator.writeStringField(FIELD_POSTURE, defaults.posture());
        }
        generator.writeEndObject();
    }

    private static void writeProvider(JsonGenerator generator, Provider provider, ProviderDefault def)
            throws IOException {
        generator.writeObjectFieldStart(provider.wireName());
        generator.writeBooleanField(FIELD_AVAILABLE, def.available());
        if (def.variant() != null) {
            generator.writeStringField(FIELD_VARIANT, def.variant().wireName());
        }
        if (def.credentialKey() != null) {
            generator.writeStringField(FIELD_CREDENTIAL_KEY, def.credentialKey());
        }
        if (def.verification() != null) {
            writeVerification(generator, def.verification());
        }
        generator.writeEndObject();
    }

    private static void writeVerification(JsonGenerator generator, VerificationResult verification)
            throws IOException {
        generator.writeObjectFieldStart(FIELD_VERIFICATION);
        generator.writeBooleanField(FIELD_PASSED, verification.passed());
        generator.writeStringField(FIELD_AT, verification.at().toString());
        generator.writeArrayFieldStart(FIELD_CHECKS);
        for (var check : verification.checks()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_CHECK, check.check());
            generator.writeBooleanField(FIELD_OK, check.ok());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    /**
     * The one member a write replaces, and the revision it writes; exactly one of the members is set.
     *
     * @param providers    the provider defaults to write, or {@code null}
     * @param mode         the documents mode to write, or {@code null}
     * @param cleanup      the cleanup durations to write, or {@code null}
     * @param planDefaults the plan defaults to write, or {@code null}
     * @param revision     the revision to write
     */
    @Builder
    private record Replacement(@Nullable ProviderDefaults providers, @Nullable DocumentsMode mode,
    @Nullable CleanupDurations cleanup, @Nullable PlanDefaults planDefaults, long revision) {

        static final Replacement NONE = new Replacement(null, null, null, null, -1);
    }

    /** One pass over the document: it reads what this codec owns and, when a write is asked for, composes the new one. */
    private static final class Pass {

        private final Path file;
        private final @Nullable ProviderDefaults replacement;
        private final @Nullable DocumentsMode replacementMode;
        private final @Nullable CleanupDurations replacementCleanup;
        private final @Nullable PlanDefaults replacementPlanDefaults;
        private final long writeRevision;
        private ProviderDefaults providers = ProviderDefaults.none();
        private @Nullable DocumentsMode documentsMode;
        private @Nullable CleanupDurations cleanup;
        private @Nullable PlanDefaults planDefaults;
        private long readRevision;
        private byte @Nullable [] rewritten = null;

        Pass(Path file, Replacement replacement) {
            this.file = file;
            this.replacement = replacement.providers();
            this.replacementMode = replacement.mode();
            this.replacementCleanup = replacement.cleanup();
            this.replacementPlanDefaults = replacement.planDefaults();
            this.writeRevision = replacement.revision();
        }

        private boolean writing() {
            return replacement != null || replacementMode != null || replacementCleanup != null
                    || replacementPlanDefaults != null;
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
                            case FIELD_SOURCES -> copySources(parser, generator);
                            case FIELD_CLEANUP -> copyCleanup(parser, generator);
                            case FIELD_PLAN_DEFAULTS -> copyPlanDefaults(parser, generator);
                            case FIELD_MAX_BUILD_SLOTS -> copyBoundedInteger(parser, generator, field,
                                    MIN_BUILD_SLOTS, MAX_BUILD_SLOTS);
                            case FIELD_LSP_IDLE_TIMEOUT_SECONDS -> copyBoundedInteger(parser, generator, field,
                                    MIN_LSP_IDLE_TIMEOUT_SECONDS, Integer.MAX_VALUE);
                            default -> copyMember(parser, generator, field);
                        }
                    }
                    requireFields(seen, "machine configuration", FIELD_FORMAT_VERSION, FIELD_REVISION,
                            FIELD_MAX_BUILD_SLOTS, FIELD_LSP_IDLE_TIMEOUT_SECONDS);
                    writeMissing(generator, seen);
                    generator.writeEndObject();
                }
                if (parser.nextToken() != null) {
                    throw unreadable("content after the document");
                }
                if (writing()) {
                    rewritten = out.toByteArray();
                }
            } catch (IOException | StrictJson.Malformed e) {
                throw StoreRefusal.unreadable(file, FORMAT_VERSION, String.valueOf(e.getMessage()), e);
            }
        }

        /** Writes the members a write replaces that the document did not hold yet, after the members it holds. */
        private void writeMissing(JsonGenerator generator, HashSet<String> seen) throws IOException {
            if (replacement != null && !seen.contains(FIELD_PROVIDERS)) {
                writeProviders(generator, replacement);
            }
            if (replacementMode != null && !seen.contains(FIELD_SOURCES)) {
                writeSources(generator, replacementMode);
            }
            if (replacementCleanup != null && !seen.contains(FIELD_CLEANUP)) {
                writeCleanup(generator, replacementCleanup);
            }
            if (replacementPlanDefaults != null && !seen.contains(FIELD_PLAN_DEFAULTS)) {
                writePlanDefaults(generator, replacementPlanDefaults);
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
            generator.writeNumberField(FIELD_REVISION, writing() ? writeRevision : documentRevision);
        }

        /** Copies one required setting through, refusing a value that is not an integer inside the schema's bounds. */
        private static void copyBoundedInteger(JsonParser parser, JsonGenerator generator, String field, int minimum,
                int maximum) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                throw unreadable("'%s' must be an integer".formatted(field));
            }
            var value = parser.getIntValue();
            if (value < minimum || value > maximum) {
                throw unreadable("'%s' must be %d to %d: %d".formatted(field, minimum, maximum, value));
            }
            generator.writeNumberField(field, value);
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

        private void copySources(JsonParser parser, JsonGenerator generator) throws IOException {
            requireObject(parser, FIELD_SOURCES);
            var seen = new HashSet<String>();
            @Nullable DocumentsMode read = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                if (!FIELD_DOCUMENTS_MODE.equals(field)) {
                    throw undeclared(field);
                }
                read = documentsMode(parser);
            }
            documentsMode = read;
            var written = replacementMode != null ? replacementMode : read;
            if (written == null) {
                generator.writeObjectFieldStart(FIELD_SOURCES);
                generator.writeEndObject();
            } else {
                writeSources(generator, written);
            }
        }

        private void copyCleanup(JsonParser parser, JsonGenerator generator) throws IOException {
            requireObject(parser, FIELD_CLEANUP);
            var seen = new HashSet<String>();
            @Nullable CleanupDuration plans = null;
            @Nullable CleanupDuration epics = null;
            @Nullable CleanupDuration logs = null;
            @Nullable CleanupDuration reports = null;
            @Nullable CleanupDuration unenrolled = null;
            @Nullable Integer remind = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_PLANS_AFTER_DAYS -> plans = cleanupDuration(parser, field);
                    case FIELD_EPICS_AFTER_DAYS -> epics = cleanupDuration(parser, field);
                    case FIELD_LOGS_AFTER_DAYS -> logs = cleanupDuration(parser, field);
                    case FIELD_REPORTS_AFTER_DAYS -> reports = cleanupDuration(parser, field);
                    case FIELD_UNENROLLED_AFTER_DAYS -> unenrolled = cleanupDuration(parser, field);
                    case FIELD_REMIND_AFTER_DAYS -> remind = remindAfterDays(parser);
                    default -> throw undeclared(field);
                }
            }
            cleanup = cleanupWithDefaults(plans, epics, logs, reports, unenrolled, remind);
            if (replacementCleanup != null) {
                writeCleanup(generator, replacementCleanup);
                return;
            }
            generator.writeObjectFieldStart(FIELD_CLEANUP);
            if (plans != null) {
                writeCleanupDuration(generator, FIELD_PLANS_AFTER_DAYS, plans);
            }
            if (epics != null) {
                writeCleanupDuration(generator, FIELD_EPICS_AFTER_DAYS, epics);
            }
            if (logs != null) {
                writeCleanupDuration(generator, FIELD_LOGS_AFTER_DAYS, logs);
            }
            if (reports != null) {
                writeCleanupDuration(generator, FIELD_REPORTS_AFTER_DAYS, reports);
            }
            if (unenrolled != null) {
                writeCleanupDuration(generator, FIELD_UNENROLLED_AFTER_DAYS, unenrolled);
            }
            if (remind != null) {
                generator.writeNumberField(FIELD_REMIND_AFTER_DAYS, remind);
            }
            generator.writeEndObject();
        }

        /**
         * Reads the machine's plan defaults and writes the member back: the replacement where a write asks for one, the
         * members read otherwise — a member the document does not set stays unset, so the schema default keeps applying.
         * A document without the member reads as none, and the schema default applies.
         */
        private void copyPlanDefaults(JsonParser parser, JsonGenerator generator) throws IOException {
            requireObject(parser, FIELD_PLAN_DEFAULTS);
            var seen = new HashSet<String>();
            @Nullable String landing = null;
            @Nullable String method = null;
            @Nullable String asking = null;
            @Nullable String posture = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_LANDING -> landing = planDefaultValue(parser, field, PlanDefaults.LANDING_VARIANTS);
                    case FIELD_METHOD -> method = planDefaultValue(parser, field, PlanDefaults.METHOD_VARIANTS);
                    case FIELD_ASKING -> asking = planDefaultValue(parser, field, PlanDefaults.ASKING_VARIANTS);
                    case FIELD_POSTURE -> posture = planDefaultValue(parser, field, PlanDefaults.POSTURES);
                    default -> throw undeclared(field);
                }
            }
            planDefaults = PlanDefaults.of(landing, method, asking, posture);
            if (replacementPlanDefaults != null) {
                writePlanDefaults(generator, replacementPlanDefaults);
                return;
            }
            generator.writeObjectFieldStart(FIELD_PLAN_DEFAULTS);
            if (landing != null) {
                generator.writeStringField(FIELD_LANDING, landing);
            }
            if (method != null) {
                generator.writeStringField(FIELD_METHOD, method);
            }
            if (asking != null) {
                generator.writeStringField(FIELD_ASKING, asking);
            }
            if (posture != null) {
                generator.writeStringField(FIELD_POSTURE, posture);
            }
            generator.writeEndObject();
        }

        private static String planDefaultValue(JsonParser parser, String field, List<String> admitted)
                throws IOException {
            var value = string(parser, field);
            if (!admitted.contains(value)) {
                throw unreadable("'%s' is no value of '%s', one of %s".formatted(value, field, admitted));
            }
            return value;
        }

        /**
         * Reads a {@code cleanup} member as a complete value: each member the document sets keeps its value, and each
         * member it does not set reads at the schema default, so a partial member never reports a longer retention than
         * the operator recorded. The pass writes the members back as it read them, so a partial member survives a write
         * of another setting unchanged.
         */
        private static CleanupDurations cleanupWithDefaults(@Nullable CleanupDuration plans,
                @Nullable CleanupDuration epics, @Nullable CleanupDuration logs, @Nullable CleanupDuration reports,
                @Nullable CleanupDuration unenrolled, @Nullable Integer remind) {
            var defaultDays = CleanupDuration.ofDays(CleanupDuration.DEFAULT_DAYS);
            return new CleanupDurations(
                    plans == null ? defaultDays : plans,
                    epics == null ? defaultDays : epics,
                    logs == null ? defaultDays : logs,
                    reports == null ? defaultDays : reports,
                    unenrolled == null ? defaultDays : unenrolled,
                    remind == null ? CleanupDurations.DEFAULT_REMIND_DAYS : remind);
        }

        private static CleanupDuration cleanupDuration(JsonParser parser, String field) throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                var text = string(parser, field);
                if (!NEVER.equals(text)) {
                    throw unreadable("'%s' is a number of days or 'never': %s".formatted(field, text));
                }
                return CleanupDuration.never();
            }
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                throw unreadable("'%s' is a number of days or 'never'".formatted(field));
            }
            var days = parser.getIntValue();
            try {
                return CleanupDuration.ofDays(days);
            } catch (IllegalArgumentException e) {
                throw unreadable(DETAIL_FORMAT.formatted(field, e.getMessage()), e);
            }
        }

        private static int remindAfterDays(JsonParser parser) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                throw unreadable("'%s' must be an integer".formatted(FIELD_REMIND_AFTER_DAYS));
            }
            var days = parser.getIntValue();
            if (days < CleanupDurations.MIN_REMIND_DAYS) {
                throw unreadable("'%s' must be at least %d: %d"
                        .formatted(FIELD_REMIND_AFTER_DAYS, CleanupDurations.MIN_REMIND_DAYS, days));
            }
            return days;
        }

        private static DocumentsMode documentsMode(JsonParser parser) throws IOException {
            var wireName = string(parser, FIELD_DOCUMENTS_MODE);
            return DocumentsMode.of(wireName)
                    .orElseThrow(() -> unreadable("not a documents mode: " + wireName));
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
            try {
                return new ProviderDefaults(defaults);
            } catch (IllegalArgumentException e) {
                throw unreadable(DETAIL_FORMAT.formatted(FIELD_PROVIDERS, e.getMessage()), e);
            }
        }

        private static ProviderDefault providerDefault(JsonParser parser, Provider provider) throws IOException {
            requireObject(parser, provider.wireName());
            var seen = new HashSet<String>();
            @Nullable Boolean available = null;
            Optional<ProviderVariant> variant = Optional.empty();
            Optional<String> credentialKey = Optional.empty();
            @Nullable VerificationResult verification = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_AVAILABLE -> available = bool(parser, field);
                    case FIELD_VARIANT -> variant = Optional.of(variant(parser));
                    case FIELD_CREDENTIAL_KEY -> credentialKey = Optional.of(string(parser, field));
                    case FIELD_VERIFICATION -> verification = verification(parser);
                    default -> throw undeclared(field);
                }
            }
            requireFields(seen, "provider default", FIELD_AVAILABLE);
            try {
                return new ProviderDefault(available, variant.orElse(null), credentialKey.orElse(null),
                        verification);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw unreadable(DETAIL_FORMAT.formatted(provider.wireName(), e.getMessage()), e);
            }
        }

        private static VerificationResult verification(JsonParser parser) throws IOException {
            requireObject(parser, FIELD_VERIFICATION);
            var seen = new HashSet<String>();
            @Nullable Boolean passed = null;
            @Nullable Instant at = null;
            var checks = new ArrayList<VerificationResult.Check>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_PASSED -> passed = bool(parser, field);
                    case FIELD_AT -> at = instant(parser, field);
                    case FIELD_CHECKS -> checks(parser, checks);
                    default -> throw undeclared(field);
                }
            }
            requireFields(seen, FIELD_VERIFICATION, FIELD_PASSED, FIELD_AT, FIELD_CHECKS);
            try {
                return new VerificationResult(passed, at, checks);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw unreadable(DETAIL_FORMAT.formatted(FIELD_VERIFICATION, e.getMessage()), e);
            }
        }

        private static void checks(JsonParser parser, List<VerificationResult.Check> checks) throws IOException {
            if (parser.currentToken() != JsonToken.START_ARRAY) {
                throw unreadable("'%s' must be an array".formatted(FIELD_CHECKS));
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    throw unreadable("a check of '%s' must be an object".formatted(FIELD_CHECKS));
                }
                checks.add(check(parser));
            }
        }

        private static VerificationResult.Check check(JsonParser parser) throws IOException {
            var seen = new HashSet<String>();
            @Nullable String check = null;
            @Nullable Boolean ok = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_CHECK -> check = string(parser, field);
                    case FIELD_OK -> ok = bool(parser, field);
                    default -> throw undeclared(field);
                }
            }
            requireFields(seen, "verification check", FIELD_CHECK, FIELD_OK);
            return new VerificationResult.Check(check, ok);
        }

        private static Instant instant(JsonParser parser, String field) throws IOException {
            var text = string(parser, field);
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                throw unreadable("'%s' is not an instant: %s".formatted(field, text), e);
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
