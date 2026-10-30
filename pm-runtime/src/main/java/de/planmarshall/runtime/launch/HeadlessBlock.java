/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.SupportedVersions;
import de.planmarshall.core.queue.ActivityEventMapping;
import de.planmarshall.core.role.RoleClass;

/**
 * The launch contract of one client profile, projected out of its {@code headless} block (PM-CLIENT-2, PM-CLIENT-5): the
 * executable and the argument-vector template with its typed slots, the per-class argument templates, the agent mode,
 * the delivery of the MCP configuration, the launch environment, the model tiers and efforts, the supported version
 * range, the update evidence, the resource tools, the per-worker data home, the session residue and the resumability.
 * <p>
 * The projection is the one place the launch package reads the shape of a profile document; the composer fills the
 * typed slots {@code {{prompt_file}}}, {@code {{model}}}, {@code {{effort}}}, {@code {{mcp_config}}},
 * {@code {{result_schema}}}, {@code {{tools}}}, {@code {{cwd}}} and {@code {{agent}}} only where the template declares
 * them.
 *
 * @param executable         the bare executable name of the harness
 * @param argv               the argument-vector template of the {@code writer}, {@code read_only} and generic launch
 * @param classArgs          the argument template of each runnable class; a class absent is not runnable
 * @param agentMode          how the composed agent reaches the harness
 * @param mcpConfig          how the MCP configuration is delivered
 * @param env                the launch environment of the harness
 * @param outputFormat       the shape of the output stream
 * @param modelTiers         the model of each tier of the default assignment
 * @param modelEfforts       the effort values accepted per model; the key {@code *} applies to the other models
 * @param resourceTools      the harness's MCP resource-reading tools
 * @param dataHome           the per-worker data home; {@code null} when the harness shares one store
 * @param sessionResidue     whether a worker session is ephemeral or persisted
 * @param resumeArgv         the argument template that resumes a stored session; empty when the harness cannot resume
 * @param capabilities       the capabilities the profile was verified for
 * @param updateEvidence     the evidence a changed executable must pass
 * @param supportedVersions  the supported version range of the harness CLI
 * @param versionProbeArgv   the argument vector of the version probe; empty when the profile declares none
 * @param versionPattern     the pattern that reads the version out of the probe's output; {@code null} when none
 * @param writablePaths      the sub-paths the harness must write to run, as the profile declares them
 * @param usagePointers      the JSON pointer of each usage column; a column absent is not reported
 * @param events             the mapping of an output line onto the activity kinds; {@code null} for a harness without
 *                           an event stream
 * @since 0.1
 */
public record HeadlessBlock(String executable, List<String> argv, Map<RoleClass, List<String>> classArgs,
AgentMode agentMode, McpConfig mcpConfig, Map<String, String> env, OutputFormat outputFormat,
Map<String, String> modelTiers, Map<String, List<String>> modelEfforts, List<String> resourceTools,
DataHome dataHome, SessionResidue sessionResidue, List<String> resumeArgv, Set<String> capabilities,
UpdateEvidence updateEvidence, SupportedVersions supportedVersions, List<String> versionProbeArgv,
String versionPattern, List<String> writablePaths, Map<String, String> usagePointers,
ActivityEventMapping events) {

    /** Copies the collections. */
    public HeadlessBlock {
        Objects.requireNonNull(executable, "executable");
        argv = List.copyOf(Objects.requireNonNull(argv, "argv"));
        var arguments = new LinkedHashMap<RoleClass, List<String>>();
        classArgs.forEach((roleClass, template) -> arguments.put(roleClass, List.copyOf(template)));
        classArgs = Collections.unmodifiableMap(arguments);
        Objects.requireNonNull(agentMode, "agentMode");
        Objects.requireNonNull(mcpConfig, "mcpConfig");
        env = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(env, "env")));
        Objects.requireNonNull(outputFormat, "outputFormat");
        modelTiers = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(modelTiers, "modelTiers")));
        var efforts = new LinkedHashMap<String, List<String>>();
        modelEfforts.forEach((model, values) -> efforts.put(model, List.copyOf(values)));
        modelEfforts = Collections.unmodifiableMap(efforts);
        resourceTools = List.copyOf(Objects.requireNonNull(resourceTools, "resourceTools"));
        Objects.requireNonNull(sessionResidue, "sessionResidue");
        resumeArgv = List.copyOf(Objects.requireNonNull(resumeArgv, "resumeArgv"));
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        Objects.requireNonNull(updateEvidence, "updateEvidence");
        Objects.requireNonNull(supportedVersions, "supportedVersions");
        versionProbeArgv = List.copyOf(Objects.requireNonNull(versionProbeArgv, "versionProbeArgv"));
        writablePaths = List.copyOf(Objects.requireNonNull(writablePaths, "writablePaths"));
        usagePointers = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(usagePointers,
                "usagePointers")));
    }

    /**
     * @param profile a validated profile
     * @return the launch contract of its {@code headless} block
     * @throws IllegalArgumentException if the profile has no {@code headless} block
     */
    public static HeadlessBlock of(LoadedProfile profile) {
        Objects.requireNonNull(profile, "profile");
        var headless = profile.document().member("headless").orElseThrow(
                () -> new IllegalArgumentException("The profile '%s' has no headless block".formatted(profile.clientId())));
        if (headless.isNull()) {
            throw new IllegalArgumentException("The profile '%s' has no headless block".formatted(profile.clientId()));
        }
        return new HeadlessBlock(text(headless, "executable").orElseThrow(),
                strings(headless.member("argv").orElse(JsonNode.nullValue())),
                classArgs(headless.member("classes").orElse(JsonNode.nullValue())),
                agentMode(text(headless, "agent_mode").orElse("flags")),
                mcpConfig(headless.member("mcp_config").orElse(JsonNode.nullValue())),
                environment(headless.member("env").orElse(JsonNode.nullValue())),
                outputFormat(headless.member("output").orElse(JsonNode.nullValue())),
                tiers(headless.member("model_tiers").orElse(JsonNode.nullValue())),
                efforts(headless.member("model_efforts").orElse(JsonNode.nullValue())),
                strings(headless.member("resource_tools").orElse(JsonNode.nullValue())),
                dataHome(headless.member("data_home").orElse(JsonNode.nullValue())).orElse(null),
                residue(text(headless, "session_residue").orElse("persisted")),
                resumeArgv(headless.member("resumability").orElse(JsonNode.nullValue())),
                capabilities(headless.member("capabilities").orElse(JsonNode.nullValue())),
                updateEvidence(headless.member("update_evidence").orElse(JsonNode.nullValue())),
                SupportedVersions.parse(text(headless, "supported_versions").orElseThrow(), profile.clientId(),
                        "/headless/supported_versions"),
                versionProbeArgv(profile),
                text(profile.document().member("identity").orElse(JsonNode.nullValue()), "version_pattern")
                        .orElse(null),
                strings(headless.member("writable_paths").orElse(JsonNode.nullValue())),
                usagePointers(headless.member("usage").orElse(JsonNode.nullValue())),
                ActivityEventMapping.of(headless.member("events").orElse(JsonNode.nullValue())).orElse(null));
    }

    /**
     * @param home the user home the profile's {@code ~} stands for
     * @return the writable sub-paths as absolute paths
     */
    public List<Path> writablePaths(Path home) {
        return writablePaths.stream().map(path -> path.startsWith("~")
                ? home.resolve(path.substring(1).replaceFirst("^/", "")).toAbsolutePath().normalize()
                : Path.of(path).toAbsolutePath().normalize()).toList();
    }

    /** @return whether the harness runs the class */
    public boolean runs(RoleClass roleClass) {
        return classArgs.containsKey(roleClass);
    }

    /**
     * @param roleClass a role class
     * @return the base argument template with the class's arguments appended, or the base template when the class has
     *         none; the enrolled executable is prepended by the composer
     */
    public List<String> argumentsFor(RoleClass roleClass) {
        var classArguments = classArgs.get(roleClass);
        if (classArguments == null) {
            return argv;
        }
        var combined = new ArrayList<>(argv);
        combined.addAll(classArguments);
        return List.copyOf(combined);
    }

    /**
     * @param model a model identifier
     * @return the effort values the harness accepts for the model, in the profile's declared order
     */
    public List<String> efforts(String model) {
        var values = modelEfforts.get(model);
        return values != null ? values : modelEfforts.getOrDefault("*", List.of());
    }

    /**
     * @param tier a tier of the closed list {@code frontier}, {@code standard}, {@code fast}
     * @return the model the harness maps the tier to
     */
    public Optional<String> tierModel(String tier) {
        return Optional.ofNullable(modelTiers.get(tier));
    }

    /** @return whether the profile promises extension isolation for its worker classes */
    public boolean extensionIsolation() {
        return capabilities.contains("extension_isolation");
    }

    /** @return whether the profile can turn the project and user settings off */
    public boolean projectSettingsControl() {
        return capabilities.contains("project_settings_control");
    }

    /**
     * @param version a harness version
     * @return whether the version lies in the supported range
     */
    public boolean supports(String version) {
        return supportedVersions.contains(version);
    }

    private static Map<RoleClass, List<String>> classArgs(JsonNode classes) {
        var arguments = new LinkedHashMap<RoleClass, List<String>>();
        for (var roleClass : List.of(RoleClass.WRITER, RoleClass.READ_ONLY)) {
            classes.member(roleClass.wireName()).filter(value -> !value.isNull()).ifPresent(declaration -> arguments
                    .put(roleClass, strings(declaration.member("args").orElse(JsonNode.nullValue()))));
        }
        var zeroTool = classes.member(RoleClass.ZERO_TOOL.wireName()).orElse(JsonNode.nullValue());
        if (!zeroTool.isNull()) {
            arguments.put(RoleClass.ZERO_TOOL, strings(zeroTool.member("args").orElse(JsonNode.nullValue())));
        }
        return arguments;
    }

    private static AgentMode agentMode(String name) {
        return switch (name) {
            case "inline" -> AgentMode.INLINE;
            case "flags" -> AgentMode.FLAGS;
            default -> throw new IllegalArgumentException("Not an agent mode: " + name);
        };
    }

    private static McpConfig mcpConfig(JsonNode config) {
        var delivery = text(config, "delivery").orElse("slot");
        var path = text(config, "path").orElse(null);
        var exclusive = config.member("exclusive").flatMap(JsonNode::booleanValue).orElse(false);
        return new McpConfig(McpDelivery.fromWireName(delivery), path, exclusive);
    }

    private static Map<String, String> environment(JsonNode env) {
        var values = new LinkedHashMap<String, String>();
        env.members().forEach((name, value) -> {
            var text = value.text();
            if (text.isPresent()) {
                values.put(name, text.get());
            }
        });
        return values;
    }

    private static OutputFormat outputFormat(JsonNode output) {
        return OutputFormat.fromWireName(text(output, "format").orElseThrow(
                () -> new IllegalArgumentException("A headless block names its output format")));
    }

    private static Map<String, String> tiers(JsonNode tiers) {
        var values = new LinkedHashMap<String, String>();
        tiers.members().forEach((tier, model) -> model.text().ifPresent(value -> values.put(tier, value)));
        return values;
    }

    private static Map<String, List<String>> efforts(JsonNode efforts) {
        var values = new LinkedHashMap<String, List<String>>();
        efforts.members().forEach((model, list) -> values.put(model, strings(list)));
        return values;
    }

    private static Map<String, String> usagePointers(JsonNode usage) {
        var values = new LinkedHashMap<String, String>();
        usage.members().forEach((column, pointer) -> {
            var text = pointer.text();
            if (text.isPresent()) {
                values.put(column, text.get());
            }
        });
        return values;
    }

    /** @return the data home, or empty when the harness shares one store */
    private static Optional<DataHome> dataHome(JsonNode dataHome) {
        if (dataHome.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new DataHome(text(dataHome, "variable").orElseThrow(),
                text(dataHome, "login_file").orElseThrow()));
    }

    private static SessionResidue residue(String name) {
        return switch (name) {
            case "ephemeral" -> SessionResidue.EPHEMERAL;
            case "persisted" -> SessionResidue.PERSISTED;
            default -> throw new IllegalArgumentException("Not a session residue: " + name);
        };
    }

    private static List<String> resumeArgv(JsonNode resumability) {
        var resume = resumability.member("resume").orElse(JsonNode.nullValue());
        if (resume.isNull()) {
            return List.of();
        }
        return strings(resume.member("argv").orElse(JsonNode.nullValue()));
    }

    private static Set<String> capabilities(JsonNode capabilities) {
        var values = new LinkedHashSet<String>();
        for (var element : capabilities.elements()) {
            element.text().ifPresent(values::add);
        }
        return values;
    }

    private static UpdateEvidence updateEvidence(JsonNode evidence) {
        var method = text(evidence, "method").orElse("none");
        var signer = text(evidence, "signer").orElse(null);
        return new UpdateEvidence(EvidenceMethod.fromWireName(method), signer);
    }

    private static List<String> versionProbeArgv(LoadedProfile profile) {
        return strings(profile.document().member("identity").orElse(JsonNode.nullValue())
                .member("version_probe").orElse(JsonNode.nullValue()));
    }

    private static List<String> strings(JsonNode array) {
        var values = new ArrayList<String>();
        for (var element : array.elements()) {
            var text = element.text();
            text.ifPresent(values::add);
        }
        return values;
    }

    private static Optional<String> text(JsonNode node, String member) {
        return node.member(member).flatMap(JsonNode::text);
    }

    /** How the composed agent reaches the harness. */
    public enum AgentMode {
        /** The server renders a per-worker named agent into the {@code {{agent}}} slot. */
        INLINE,
        /** The same slots become per-call flags. */
        FLAGS
    }

    /** How the MCP configuration reaches the harness. */
    public enum McpDelivery {
        /** Rendered into a slot of the launch. */
        SLOT("slot"),
        /** Written to the file the harness reads in the worker's working directory. */
        FILE("file");

        private final String wireName;

        McpDelivery(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the delivery in a profile */
        public String wireName() {
            return wireName;
        }

        /**
         * @param name the name of a delivery
         * @return the delivery of that name
         */
        public static McpDelivery fromWireName(String name) {
            for (var value : values()) {
                if (value.wireName.equals(name)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Not a delivery: " + name);
        }
    }

    /** The shape of the harness's output stream. */
    public enum OutputFormat {
        /** One result at the end. */
        JSON("json"),
        /** One event per message, tool call, tool result and turn. */
        STREAM_JSON("stream_json"),
        /** One JSON value per line. */
        JSONL("jsonl");

        private final String wireName;

        OutputFormat(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @param name the name of a format
         * @return the format of that name
         */
        public static OutputFormat fromWireName(String name) {
            for (var value : values()) {
                if (value.wireName.equals(name)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Not an output format: " + name);
        }
    }

    /** Whether a worker session outlives its process. */
    public enum SessionResidue {
        /** The harness persists no session. */
        EPHEMERAL,
        /** The harness stores a session that a later generation can resume. */
        PERSISTED
    }

    /** The evidence a changed harness executable must pass. */
    public enum EvidenceMethod {
        /** The platform code signature, checked without running the file. */
        CODE_SIGNATURE("code_signature"),
        /** The installed package's signed provenance, checked by the package manager. */
        PACKAGE_PROVENANCE("package_provenance"),
        /** No evidence; every change waits for the operator. */
        NONE("none");

        private final String wireName;

        EvidenceMethod(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @param name the name of a method
         * @return the method of that name
         */
        public static EvidenceMethod fromWireName(String name) {
            for (var value : values()) {
                if (value.wireName.equals(name)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Not an update evidence method: " + name);
        }
    }

    /**
     * How the MCP configuration is delivered.
     *
     * @param delivery  the delivery
     * @param path      the relative path of the file delivery; {@code null} for the slot
     * @param exclusive whether the harness connects only the servers of the per-call configuration
     * @since 0.1
     */
    public record McpConfig(McpDelivery delivery, String path, boolean exclusive) {
    }

    /**
     * The per-worker data home of a harness that shares one store.
     *
     * @param variable  the environment variable that names the data directory
     * @param loginFile the harness's login file, relative to its user data directory
     * @since 0.1
     */
    public record DataHome(String variable, String loginFile) {
    }

    /**
     * The evidence a changed harness executable must pass.
     *
     * @param method the evidence method
     * @param signer the expected signer; {@code null} for the method {@code none}
     * @since 0.1
     */
    public record UpdateEvidence(EvidenceMethod method, String signer) {
    }
}
