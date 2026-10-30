/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The verification record of a machine or project profile (PM-CLIENT-5, Profile Verification): the closed document the
 * profile verification writes beside the profile, which binds the profile to the digest it was measured as and to the
 * harness build it was measured on.
 * <p>
 * The reader accepts the closed shape of the record and refuses anything else: no unknown member, every required
 * member present, every stage a closed status. It keeps what the profile loader decides on — the profile digest, the
 * harness build, the platform and the stages — and refuses a record that breaks the shape of any part, so a record
 * written elsewhere never becomes an empty promise. The per-kind results of {@code events} and {@code usage} are kept
 * as they are recorded; the stage {@code usage} compares them with the parsed figures.
 *
 * @param formatVersion   the format version of the record
 * @param clientId        the client id of the verified profile
 * @param profileDigest   the canonical-JSON digest the profile had when it was verified
 * @param verifiedAt      when the verification ran
 * @param tool            the build of the tool whose stages measured the profile
 * @param harness         the harness build the record was measured on
 * @param platform        the platform of the verification
 * @param stages          the stage results by stage name
 * @since 0.1
 */
public record VerificationRecord(int formatVersion, String clientId, String profileDigest, Instant verifiedAt,
Tool tool, HarnessBuild harness, Platform platform, Map<String, Stage> stages) {

    /** The one format version of a verification record. */
    public static final int FORMAT_VERSION = 1;

    /** The closed stage names of a verification. */
    public static final Set<String> STAGE_NAMES = Set.of("identity", "detection", "launch", "events", "egress",
            "isolation", "classes", "limits", "paths", "resumability", "faults", "usage");

    /**
     * @param formatVersion the format version
     * @param clientId      the client id
     * @param profileDigest the canonical digest
     * @param verifiedAt    the verification instant
     * @param harness       the harness build
     * @param platform      the platform
     * @param stages        the stage results
     */
    public VerificationRecord {
        stages = Collections.unmodifiableMap(new LinkedHashMap<>(stages));
    }

    /**
     * One stage of the record.
     *
     * @param status       {@code passed}, {@code failed} or {@code not_run}
     * @param results      the per-kind results of the stages {@code events} and {@code usage}; empty for another stage
     * @param trials       the trials of the stage {@code faults}; {@code null} for another stage
     * @param startContext the start context of the stage {@code usage}; empty for another stage
     * @since 0.1
     */
    public record Stage(String status, Map<String, String> results, Integer trials,
    List<ProfileStages.StartContext> startContext) {

        /** The closed statuses of a stage. */
        public static final Set<String> STATUSES = Set.of("passed", "failed", "not_run");

        /** Copies the results and the start context and refuses an unknown status. */
        public Stage {
            if (!STATUSES.contains(status)) {
                throw new IllegalArgumentException("Not a stage status: " + status);
            }
            results = Collections.unmodifiableMap(new LinkedHashMap<>(results));
            startContext = List.copyOf(startContext);
        }

        /**
         * @param status  the status
         * @param results the per-kind results
         * @param trials  the trials of the stage {@code faults}
         */
        public Stage(String status, Map<String, String> results, Integer trials) {
            this(status, results, trials, List.of());
        }
    }

    /**
     * The harness build a record was measured on.
     *
     * @param executable       the bare executable name
     * @param version          the exact version the version probe printed
     * @param executableSha256 the digest of the executable file the run started
     * @param models           the models a stage ran with
     * @since 0.1
     */
    public record HarnessBuild(String executable, String version, String executableSha256, List<String> models) {

        /** Copies the models. */
        public HarnessBuild {
            models = List.copyOf(models);
        }
    }

    /**
     * The platform of a verification.
     *
     * @param os          the operating system
     * @param arch        the architecture
     * @param confinement the confinement, {@code landlock}, {@code sandbox} or {@code none}
     * @since 0.1
     */
    public record Platform(String os, String arch, String confinement) {
    }

    /**
     * The build of the tool whose stages measured the profile, as the record binds it: the verifying-binary control
     * compares {@code version} and {@code commit} with the identity of the installed binary and refuses a mismatch.
     *
     * @param version the exact version of the tool
     * @param commit  the commit of the tool
     * @since 0.1
     */
    public record Tool(String version, String commit) {
    }

    /**
     * @param document the record, UTF-8 encoded
     * @param name     the record, as a refusal names it
     * @return the record
     * @throws ProfileRefusal if the document is unreadable or does not have the closed shape of a record
     */
    public static VerificationRecord read(byte[] document, String name) {
        var root = JsonDocument.parse(document, name);
        var checker = new RecordChecker(name);
        return checker.read(root);
    }

    /** Reads one record with the closed shape of its kind. */
    private static final class RecordChecker {

        // The member names and pointer segments of the record, named once each (ADR core-profile/005).
        private static final String NAME_VERSION = "version";
        private static final String NAME_CONFINEMENT = "confinement";
        private static final String NAME_LANDLOCK_ABI = "landlock_abi";
        private static final String NAME_STATUS = "status";
        private static final String NAME_TRIALS = "trials";
        private static final String NAME_RESULTS = "results";
        private static final String NAME_POINTER = "pointer";
        private static final String PTR_STATUS = "/status";
        private static final String PTR_TOKENS = "/tokens";

        private final String document;

        private RecordChecker(String document) {
            this.document = document;
        }

        private VerificationRecord read(JsonNode root) {
            object(root, "", "record", Set.of("format_version", "client_id", "profile_schema_version", "profile_digest",
                    "verified_at", "tool", "harness", "platform", "stages", "values"));
            var format = integer(root, "/format_version", "format_version");
            if (format != FORMAT_VERSION) {
                throw ProfileRefusal.recordInvalid(document, "/format_version",
                        "the format version is %d".formatted(FORMAT_VERSION));
            }
            var schema = integer(root, "/profile_schema_version", "profile_schema_version");
            if (schema != ProfileValidator.SCHEMA_VERSION) {
                throw ProfileRefusal.recordInvalid(document, "/profile_schema_version",
                        "the profile schema version is %d".formatted(ProfileValidator.SCHEMA_VERSION));
            }
            var clientId = string(root, "/client_id", "client_id");
            var digest = string(root, "/profile_digest", "profile_digest");
            if (!digest.matches("sha256:[0-9a-f]{64}")) {
                throw ProfileRefusal.recordInvalid(document, "/profile_digest", "not a sha256: digest");
            }
            var verifiedAt = instant(root, "/verified_at");
            var tool = tool(required(root, "/tool"));
            var harness = harness(required(root, "/harness"));
            var platform = platform(required(root, "/platform"));
            var stages = stages(required(root, "/stages"), harness.models());
            values(required(root, "/values"));
            return new VerificationRecord((int) format, clientId, digest, verifiedAt, tool, harness, platform, stages);
        }

        private Tool tool(JsonNode tool) {
            object(tool, "/tool", "tool", Set.of(NAME_VERSION, "commit"));
            var version = string(tool, "/tool/version", NAME_VERSION);
            var commit = string(tool, "/tool/commit", "commit");
            return new Tool(version, commit);
        }

        private HarnessBuild harness(JsonNode harness) {
            object(harness, "/harness", "harness", Set.of("executable", NAME_VERSION, "executable_sha256", "models"));
            var executable = string(harness, "/harness/executable", "executable");
            var version = string(harness, "/harness/version", NAME_VERSION);
            var sha = string(harness, "/harness/executable_sha256", "executable_sha256");
            if (!sha.matches("sha256:[0-9a-f]{64}")) {
                throw ProfileRefusal.recordInvalid(document, "/harness/executable_sha256", "not a sha256: digest");
            }
            var models = required(harness, "/harness/models");
            if (!models.isArray()) {
                throw ProfileRefusal.recordInvalid(document, "/harness/models", "the models must be an array");
            }
            var modelIds = new ArrayList<String>();
            for (var index = 0; index < models.elements().size(); index++) {
                var model = models.elements().get(index);
                if (!model.isString()) {
                    throw ProfileRefusal.recordInvalid(document, "/harness/models/" + index,
                            "a model id is a string");
                }
                modelIds.add(model.text().orElseThrow());
            }
            return new HarnessBuild(executable, version, sha, modelIds);
        }

        private Platform platform(JsonNode platform) {
            object(platform, "/platform", "platform", Set.of("os", "arch", NAME_CONFINEMENT),
                    Set.of("os", "arch", NAME_CONFINEMENT, NAME_LANDLOCK_ABI));
            var os = string(platform, "/platform/os", "os");
            var arch = string(platform, "/platform/arch", "arch");
            var confinement = string(platform, "/platform/confinement", NAME_CONFINEMENT);
            var abi = platform.member(NAME_LANDLOCK_ABI);
            if (abi.isPresent() && !abi.get().isNull()) {
                integer(platform, "/platform/landlock_abi", NAME_LANDLOCK_ABI);
            }
            return new Platform(os, arch, confinement);
        }

        private Map<String, Stage> stages(JsonNode stages, List<String> harnessModels) {
            object(stages, "/stages", "stages", Set.of(), STAGE_NAMES);
            var read = new LinkedHashMap<String, Stage>();
            for (var name : stages.members().keySet()) {
                var pointer = "/stages/" + name;
                var stage = stages.members().get(name);
                object(stage, pointer, name, Set.of(NAME_STATUS),
                        Set.of(NAME_STATUS, NAME_RESULTS, NAME_TRIALS, "start_context"));
                var status = string(stage, pointer + PTR_STATUS, NAME_STATUS);
                if (!Stage.STATUSES.contains(status)) {
                    throw ProfileRefusal.recordInvalid(document, pointer + PTR_STATUS,
                            "not a stage status: '%s'".formatted(status));
                }
                var results = results(stage.member(NAME_RESULTS).orElse(null), pointer + "/results");
                @Nullable Integer trials = null;
                var trialNode = stage.member(NAME_TRIALS).orElse(null);
                if (trialNode != null) {
                    trials = (int) integer(stage, pointer + "/trials", NAME_TRIALS);
                }
                var startContext = startContext(stage.member("start_context").orElse(null),
                        pointer + "/start_context", harnessModels);
                if (!startContext.isEmpty() && !"usage".equals(name)) {
                    throw ProfileRefusal.recordInvalid(document, pointer + "/start_context",
                            "'start_context' belongs to the stage 'usage'");
                }
                read.put(name, new Stage(status, results, trials, startContext));
            }
            return read;
        }

        private List<ProfileStages.StartContext> startContext(JsonNode startContext, String pointer,
                List<String> harnessModels) {
            if (startContext == null) {
                return List.of();
            }
            if (!startContext.isArray()) {
                throw ProfileRefusal.recordInvalid(document, pointer, "'start_context' must be an array");
            }
            var contexts = new ArrayList<ProfileStages.StartContext>();
            var elements = startContext.elements();
            for (var index = 0; index < elements.size(); index++) {
                var entryPointer = pointer + "/" + index;
                var entry = elements.get(index);
                object(entry, entryPointer, "start_context entry", Set.of("class", "model", "tokens"));
                var workerClass = string(entry, entryPointer + "/class", "class");
                var model = string(entry, entryPointer + "/model", "model");
                requireListedModel(model, harnessModels, entryPointer);
                try {
                    contexts.add(new ProfileStages.StartContext(workerClass, model,
                            tokens(entry, entryPointer)));
                } catch (IllegalArgumentException e) {
                    throw ProfileRefusal.recordInvalid(document, entryPointer, e.getMessage());
                }
            }
            return List.copyOf(contexts);
        }

        private void requireListedModel(String model, List<String> harnessModels, String entryPointer) {
            if (!harnessModels.contains(model)) {
                throw ProfileRefusal.recordInvalid(document, entryPointer + "/model",
                        "the model is not listed in '/harness/models'");
            }
        }

        private OptionalLong tokens(JsonNode entry, String entryPointer) {
            var tokens = entry.member("tokens").orElse(null);
            if (tokens == null) {
                throw ProfileRefusal.recordInvalid(document, entryPointer + PTR_TOKENS,
                        "the member 'tokens' of a start_context entry is missing");
            }
            if (tokens.isInteger()) {
                if (tokens.integerValue().orElseThrow() < 0) {
                    throw ProfileRefusal.recordInvalid(document, entryPointer + PTR_TOKENS,
                            "'tokens' must not be negative");
                }
                return OptionalLong.of(tokens.integerValue().orElseThrow());
            }
            if (tokens.isString() && "unmeasured".equals(tokens.text().orElseThrow())) {
                return OptionalLong.empty();
            }
            throw ProfileRefusal.recordInvalid(document, entryPointer + PTR_TOKENS,
                    "'tokens' must be an integer or 'unmeasured'");
        }

        private Map<String, String> results(JsonNode results, String pointer) {
            if (results == null) {
                return Map.of();
            }
            object(results, pointer, NAME_RESULTS);
            var read = new LinkedHashMap<String, String>();
            for (var entry : results.members().entrySet()) {
                var value = entry.getValue();
                if (!value.isString()) {
                    throw ProfileRefusal.recordInvalid(document, pointer + "/" + entry.getKey(),
                            "a stage result is a string");
                }
                read.put(entry.getKey(), value.text().orElseThrow());
            }
            return read;
        }

        private void values(JsonNode values) {
            if (!values.isArray()) {
                throw ProfileRefusal.recordInvalid(document, "/values", "the values must be an array");
            }
            for (var index = 0; index < values.elements().size(); index++) {
                var pointer = "/values/" + index;
                var value = values.elements().get(index);
                object(value, pointer, "value", Set.of(NAME_POINTER, NAME_STATUS),
                        Set.of(NAME_POINTER, NAME_STATUS, "code", "figures"));
                string(value, pointer + "/pointer", NAME_POINTER);
                var status = string(value, pointer + PTR_STATUS, NAME_STATUS);
                if (!Set.of("measured", "confirmed", "declared", "not_measured").contains(status)) {
                    throw ProfileRefusal.recordInvalid(document, pointer + PTR_STATUS,
                            "not a value status: '%s'".formatted(status));
                }
            }
        }

        private void object(JsonNode node, String pointer, String name, Set<String> required) {
            object(node, pointer, name, required, required);
        }

        private void object(JsonNode node, String pointer, String name, Set<String> required, Set<String> allowed) {
            if (!node.isObject()) {
                throw ProfileRefusal.recordInvalid(document, pointer, "'%s' must be an object".formatted(name));
            }
            for (var member : node.members().keySet()) {
                if (!allowed.contains(member)) {
                    throw ProfileRefusal.recordInvalid(document, pointer + "/" + escape(member),
                            "the member '%s' is not declared by '%s'".formatted(member, name));
                }
            }
            for (var member : required) {
                if (!node.members().containsKey(member)) {
                    throw ProfileRefusal.recordInvalid(document, pointer + "/" + escape(member),
                            "the member '%s' of '%s' is missing".formatted(member, name));
                }
            }
        }

        private void object(JsonNode node, String pointer, String name) {
            if (!node.isObject()) {
                throw ProfileRefusal.recordInvalid(document, pointer, "'%s' must be an object".formatted(name));
            }
        }

        private JsonNode required(JsonNode node, String pointer) {
            var name = pointer.substring(pointer.lastIndexOf('/') + 1);
            return node.member(name).orElseThrow(() -> ProfileRefusal.recordInvalid(document, pointer,
                    "the member '%s' is missing".formatted(name)));
        }

        private String string(JsonNode node, String pointer, String name) {
            var value = node.member(pointer.substring(pointer.lastIndexOf('/') + 1));
            if (value.isEmpty() || !value.get().isString()) {
                throw ProfileRefusal.recordInvalid(document, pointer, "'%s' must be a string".formatted(name));
            }
            return value.get().text().orElseThrow();
        }

        private long integer(JsonNode node, String pointer, String name) {
            var value = node.member(pointer.substring(pointer.lastIndexOf('/') + 1));
            if (value.isEmpty() || !value.get().isInteger()) {
                throw ProfileRefusal.recordInvalid(document, pointer, "'%s' must be an integer".formatted(name));
            }
            return value.get().integerValue().orElseThrow();
        }

        private Instant instant(JsonNode root, String pointer) {
            var text = string(root, pointer, "verified_at");
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException _) {
                throw ProfileRefusal.recordInvalid(document, pointer, "not an instant: '%s'".formatted(text));
            }
        }

        private static String escape(String member) {
            return member.replace("~", "~0").replace("/", "~1");
        }
    }
}
