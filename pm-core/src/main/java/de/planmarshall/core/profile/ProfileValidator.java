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

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a client profile as a whole against the client profile schema and the rules stated with its blocks
 * (PM-CLIENT-5).
 * <p>
 * The validator accepts every mechanism the schema names and no other: every object is closed, every enumeration is
 * closed, every array has its bound, every string is NFC, single-line, control-character-free and bounded, and the
 * hardening rules of <em>A Profile Is Untrusted Input</em> are checked before any value of the profile is used. A value
 * that breaks a rule refuses the profile as a whole with a {@link ProfileRefusal} naming the document and the JSON
 * Pointer of the failing value; the runtime never runs on a part of a profile.
 * <p>
 * The validator names no harness: what a harness is, is the document. The paths a bundle manages and the harness homes
 * of an installation are data of the installation ({@link ProfileRules}), never a literal of this class.
 *
 * @since 0.1
 */
public final class ProfileValidator {

    /** The one schema version a profile declares. */
    public static final int SCHEMA_VERSION = 1;

    // The member names of the client profile schema, named once each (ADR core-profile/005).
    private static final String NAME_SCHEMA_VERSION = "schema_version";
    private static final String NAME_CLIENT_ID = "client_id";
    private static final String NAME_DISPLAY_NAME = "display_name";
    private static final String NAME_ICON = "icon";
    private static final String NAME_MIN_DAEMON_VERSION = "min_daemon_version";
    private static final String NAME_TOOLS = "tools";
    private static final String NAME_SKILL_LOADING = "skill_loading";
    private static final String NAME_HEADLESS = "headless";
    private static final String NAME_CONNECTION = "connection";
    private static final String NAME_WORKER = "worker";
    private static final String NAME_DETECTION = "detection";
    private static final String NAME_PROFILE_KIND = "profile_kind";
    private static final String NAME_ENDPOINT = "endpoint";
    private static final String NAME_PROTOCOL = "protocol";
    private static final String NAME_LOCALITY = "locality";
    private static final String NAME_BASE_URL = "base_url";
    private static final String NAME_CREDENTIAL_KEY = "credential_key";
    private static final String NAME_READINESS_PATH = "readiness_path";
    private static final String NAME_MODELS_PATH = "models_path";
    private static final String NAME_MODELS_POINTER = "models_pointer";
    private static final String NAME_CONTEXT_TOKENS = "context_tokens";
    private static final String NAME_ANSWER_RESERVE_TOKENS = "answer_reserve_tokens";
    private static final String NAME_REQUEST_TIMEOUT_SECONDS = "request_timeout_seconds";
    private static final String NAME_IDENTITY = "identity";
    private static final String NAME_EXECUTABLE = "executable";
    private static final String NAME_INSTALL_LOCATIONS = "install_locations";
    private static final String NAME_INSTALL_HINT = "install_hint";
    private static final String NAME_AUTH_PROBE = "auth_probe";
    private static final String NAME_MODELS_PROBE = "models_probe";
    private static final String NAME_WEIGHTS_PROBE = "weights_probe";
    private static final String NAME_MODEL_TIERS = "model_tiers";
    private static final String NAME_SUPPORTED_VERSIONS = "supported_versions";
    private static final String NAME_UPDATE_EVIDENCE = "update_evidence";
    private static final String NAME_AGENT_MODE = "agent_mode";
    private static final String NAME_MCP_CONFIG = "mcp_config";
    private static final String NAME_CLASSES = "classes";
    private static final String NAME_CAPABILITIES = "capabilities";
    private static final String NAME_OUTPUT = "output";
    private static final String NAME_USAGE = "usage";
    private static final String NAME_EVENTS = "events";
    private static final String NAME_ENDPOINTS = "endpoints";
    private static final String NAME_WRITABLE_PATHS = "writable_paths";
    private static final String NAME_DATA_HOME = "data_home";
    private static final String NAME_TOOL_RESULT_LIMIT_CHARS = "tool_result_limit_chars";
    private static final String NAME_RESOURCE_TOOLS = "resource_tools";
    private static final String NAME_SESSION_RESIDUE = "session_residue";
    private static final String NAME_RESUMABILITY = "resumability";
    private static final String NAME_MODEL_EFFORTS = "model_efforts";
    private static final String NAME_CONTEXT_BUDGET_TOKENS = "context_budget_tokens";
    private static final String NAME_EPHEMERAL = "ephemeral";
    private static final String NAME_RESULT_SCHEMA = "result_schema";
    private static final String NAME_SESSION_ID = "session_id";
    private static final String NAME_BUNDLED = "bundled";
    private static final String NAME_SOURCE = "source";
    private static final String NAME_EDIT_FILE = "edit_file";
    private static final String NAME_RUN_SHELL = "run_shell";
    private static final String NAME_READ_FILE = "read_file";
    private static final String NAME_FIND_FILES = "find_files";
    private static final String NAME_WRITE_FILE = "write_file";
    private static final String NAME_SEARCH_CODE = "search_code";
    private static final String NAME_ASK_USER = "ask_user";
    private static final String NAME_IDIOMS = "idioms";
    private static final String NAME_REPLACEMENT = "replacement";
    private static final String NAME_DISPOSITION = "disposition";
    private static final String NAME_SKILLS_EXTENSION = "skills_extension";
    private static final String NAME_SCHEMA_RESIDENCY = "schema_residency";
    private static final String NAME_LOAD_DIRECTIVE_TEMPLATE = "load_directive_template";
    private static final String NAME_TOOL_CALL = "tool_call";
    private static final String NAME_LOGIN_COMMAND = "login_command";
    private static final String NAME_LOGGED_IN_EXIT_CODES = "logged_in_exit_codes";
    private static final String NAME_LOGGED_OUT_EXIT_CODES = "logged_out_exit_codes";
    private static final String NAME_POINTER = "pointer";
    private static final String NAME_FORMAT = "format";
    private static final String NAME_PATTERN = "pattern";
    private static final String NAME_SIGNER = "signer";
    private static final String NAME_METHOD = "method";
    private static final String NAME_EXCLUSIVE = "exclusive";
    private static final String NAME_DELIVERY = "delivery";
    private static final String NAME_READ_ONLY = "read_only";
    private static final String NAME_ZERO_TOOL = "zero_tool";
    private static final String NAME_WRITER = "writer";
    private static final String NAME_TOOL_SET = "tool_set";
    private static final String NAME_RESULT_POINTER = "result_pointer";
    private static final String NAME_KIND_POINTER = "kind_pointer";
    private static final String NAME_KINDS = "kinds";
    private static final String NAME_MATCH = "match";
    private static final String NAME_TARGET = "target";
    private static final String NAME_LOGIN_FILE = "login_file";
    private static final String NAME_VARIABLE = "variable";
    private static final String NAME_RESUME = "resume";
    private static final String NAME_MAX_HOLD_SECONDS = "max_hold_seconds";
    private static final String NAME_MAX_AGE_SECONDS = "max_age_seconds";
    private static final String NAME_BOUNDED_WAIT_SECONDS = "bounded_wait_seconds";
    private static final String NAME_IDLE_TIMEOUT_SECONDS = "idle_timeout_seconds";
    private static final String NAME_TRANSPORT = "transport";
    private static final String NAME_PROGRESS_RESETS_TIMEOUT = "progress_resets_timeout";
    private static final String NAME_ACK_INFORMATIONAL = "ack_informational";
    private static final String NAME_RESTRICTED_MODELS = "restricted_models";
    private static final String NAME_ACK_DEADLINE_SECONDS = "ack_deadline_seconds";
    private static final String NAME_ELIGIBLE = "eligible";
    private static final String NAME_SILENCE_GRACE_SECONDS = "silence_grace_seconds";
    private static final String NAME_IDLE_WAKEUPS = "idle_wakeups";
    private static final String NAME_PROCESS_MARKERS = "process_markers";
    private static final String NAME_PRIMARY_SIGNALS = "primary_signals";
    private static final String NAME_SECONDARY_SIGNALS = "secondary_signals";
    private static final String NAME_PRECEDENCE = "precedence";
    private static final String NAME_REACHES = "reaches";
    private static final String NAME_AI_AGENT_PREFIXES = "ai_agent_prefixes";
    private static final String NAME_EQUALS = "equals";
    private static final String NAME_VERSION_PATTERN = "version_pattern";
    private static final String NAME_VERSION_PROBE = "version_probe";

    // The pointer segments a refusal names, with their leading slash.
    private static final String PTR_DISPLAY_NAME = "/display_name";
    private static final String PTR_SCHEMA_VERSION = "/schema_version";
    private static final String PTR_CLIENT_ID = "/client_id";
    private static final String PTR_MIN_DAEMON_VERSION = "/min_daemon_version";
    private static final String PTR_IDENTITY = "/identity";
    private static final String PTR_EXECUTABLE = "/executable";
    private static final String PTR_UPDATE_EVIDENCE = "/update_evidence";
    private static final String PTR_BASE_URL = "/endpoint/base_url";
    private static final String PTR_SOURCE = "/source";
    private static final String PTR_BUNDLED = "/bundled";
    private static final String PTR_ARGV = "/argv";
    private static final String PTR_POINTER = "/pointer";
    private static final String PTR_PATTERN = "/pattern";
    private static final String PTR_SIGNER = "/signer";
    private static final String PTR_PATH = "/path";
    private static final String PTR_ZERO_TOOL = "/zero_tool";
    private static final String PTR_VARIABLE = "/variable";
    private static final String PTR_LOGIN_FILE = "/login_file";
    private static final String PTR_RESTRICTED_MODELS = "/restricted_models";
    private static final String PTR_ACK_DEADLINE_SECONDS = "/ack_deadline_seconds";

    /** The one refusal detail that names a JSON Pointer, used wherever a pointer is read from the document. */
    private static final String MSG_NOT_A_POINTER = "not a JSON Pointer: '%s'";

    private static final Set<String> TOP_LEVEL = Set.of(NAME_SCHEMA_VERSION, NAME_CLIENT_ID, NAME_DISPLAY_NAME, NAME_ICON,
            NAME_MIN_DAEMON_VERSION, NAME_TOOLS, NAME_SKILL_LOADING, NAME_HEADLESS, NAME_CONNECTION, NAME_WORKER, NAME_DETECTION,
            NAME_IDENTITY);

    /** The one value of {@code profile_kind} a document of the profile sets declares. */
    public static final String PROFILE_KIND_ENDPOINT = "endpoint";

    /** The tool-set value that makes a harness limit a worker to its role's tools. */
    private static final String ENFORCED = "enforced";

    private static final Set<String> ENDPOINT_TOP_LEVEL = Set.of(NAME_PROFILE_KIND, NAME_SCHEMA_VERSION, NAME_CLIENT_ID,
            NAME_DISPLAY_NAME, NAME_MIN_DAEMON_VERSION, NAME_ICON, NAME_EXECUTABLE, NAME_IDENTITY, NAME_UPDATE_EVIDENCE,
            NAME_ENDPOINT, NAME_MODEL_TIERS, NAME_WEIGHTS_PROBE);

    private static final Set<String> ENDPOINT_BLOCK = Set.of(NAME_PROTOCOL, NAME_LOCALITY, NAME_BASE_URL,
            NAME_CREDENTIAL_KEY, NAME_READINESS_PATH, NAME_MODELS_PATH, NAME_MODELS_POINTER, NAME_RESULT_SCHEMA,
            NAME_CONTEXT_TOKENS, NAME_ANSWER_RESERVE_TOKENS, NAME_REQUEST_TIMEOUT_SECONDS);

    private static final Set<String> HEADLESS = Set.of(NAME_EXECUTABLE, NAME_INSTALL_LOCATIONS, NAME_INSTALL_HINT, NAME_AUTH_PROBE,
            NAME_MODELS_PROBE, NAME_WEIGHTS_PROBE, NAME_MODEL_TIERS, NAME_SUPPORTED_VERSIONS, NAME_UPDATE_EVIDENCE, NAME_AGENT_MODE,
            "argv", NAME_MCP_CONFIG, NAME_CLASSES, "env", NAME_CAPABILITIES, NAME_OUTPUT, NAME_USAGE, NAME_EVENTS, NAME_ENDPOINTS,
            NAME_WRITABLE_PATHS, NAME_DATA_HOME, NAME_TOOL_RESULT_LIMIT_CHARS, NAME_RESOURCE_TOOLS, NAME_SESSION_RESIDUE,
            NAME_RESUMABILITY);

    private static final Set<String> HEADLESS_OPTIONAL = Set.of(NAME_MODEL_EFFORTS, NAME_CONTEXT_BUDGET_TOKENS);

    private static final Set<String> CAPABILITIES = Set.of(NAME_RESULT_SCHEMA, NAME_EPHEMERAL, "usage_fields", "cost_field",
            "exit_reliable", "project_settings_control", "extension_isolation");

    private static final Set<String> SLOTS = Set.of("prompt_file", "model", "effort", NAME_MCP_CONFIG, NAME_RESULT_SCHEMA,
            NAME_TOOLS, "cwd", "agent");

    /** The launch slots plus the session slot a resume names; derived, so a new slot reaches a resume as well. */
    private static final Set<String> SESSION_SLOTS = sessionSlots();

    /** The tool declarations a profile must carry; {@code idioms} is the block's one optional member. */
    private static final Set<String> TOOL_NAMES = Set.of(NAME_READ_FILE, NAME_WRITE_FILE, NAME_EDIT_FILE,
            NAME_SEARCH_CODE, NAME_FIND_FILES, NAME_RUN_SHELL, NAME_ASK_USER);

    private static Set<String> sessionSlots() {
        var slots = new LinkedHashSet<>(SLOTS);
        slots.add(NAME_SESSION_ID);
        return Collections.unmodifiableSet(slots);
    }

    private static final Set<String> JOB_ENVIRONMENT = Set.of("PATH", "HOME", "NODE_OPTIONS", "JAVA_TOOL_OPTIONS");

    private static final List<String> JOB_ENVIRONMENT_PREFIXES = List.of("LD_", "DYLD_", "PYTHON", "PM_MCP_");

    private static final List<String> CREDENTIAL_MARKERS = List.of("TOKEN", "SECRET", "PASSWORD", "PASSWD", "APIKEY",
            "API_KEY", "CREDENTIAL", "AUTH");

    private static final String MODEL_READABLE_CHARACTERS = " _.:/-$=,()\"{}<>";

    private static final Pattern CLIENT_ID = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");

    /** The credential-key grammar of the endpoint profile's {@code credential_key}. */
    private static final Pattern CREDENTIAL_KEY = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern VARIABLE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.-]+)?$");
    private static final Pattern EXECUTABLE = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final Pattern INSTALL_LOCATION = Pattern.compile("^(~|\\{app_dir\\}|/)[A-Za-z0-9_./ -]*$");
    private static final Pattern INSTALL_HINT = Pattern.compile("^[a-z][a-z0-9._-]*$");
    private static final Pattern TOOL_NAME = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");
    private static final Pattern ICON_SOURCE = Pattern.compile("^https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+\\.(svg|png)$");
    private static final Pattern BUNDLED_ICON = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private static final Pattern LOGIN_FILE = Pattern.compile("^\\w[\\w./-]*$");
    private static final Pattern HOME_PATH = Pattern.compile("^~/[A-Za-z0-9_./-]+$");
    private static final Pattern ENDPOINT_HOST = Pattern.compile("^[a-z0-9.-]+$");
    private static final Pattern ENDPOINT_PATH = Pattern.compile("^/[A-Za-z0-9._/-]{0,127}$");
    private static final Pattern LOOPBACK_URL = Pattern.compile("^http://(127\\.0\\.0\\.1|\\[::1\\]):(\\d{1,5})$");
    private static final Pattern HTTPS_URL = Pattern.compile("^https://[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:\\d{1,5})?$");

    /** The port of an {@code https} URL, for the range check the pattern cannot express. */
    private static final Pattern HTTPS_PORT = Pattern.compile(":(\\d{1,5})$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)\\}\\}");

    private ProfileValidator() {
    }

    /**
     * @param document the profile document, UTF-8 encoded
     * @param name     the document, as a refusal names it
     * @param rules    what the installation knows beyond the document
     * @return the validated tree
     * @throws ProfileRefusal if the document is unreadable or breaks a rule of the schema
     */
    public static JsonNode validate(byte[] document, String name, ProfileRules rules) {
        return validate(JsonDocument.parse(document, name), name, rules);
    }

    /**
     * @param profile  the profile tree
     * @param document the document, as a refusal names it
     * @param rules    what the installation knows beyond the document
     * @return the validated tree
     * @throws ProfileRefusal if the profile breaks a rule of the schema
     */
    public static JsonNode validate(JsonNode profile, String document, ProfileRules rules) {
        var checker = new Checker(document, rules);
        checker.checkProfile(profile);
        return profile;
    }

    /**
     * @param profile a validated profile
     * @return its {@code client_id}
     */
    public static String clientId(JsonNode profile) {
        return profile.member(NAME_CLIENT_ID).flatMap(JsonNode::text).orElse("");
    }

    /**
     * @param profile a profile document
     * @return whether the document declares {@code profile_kind: "endpoint"}
     */
    public static boolean isEndpoint(JsonNode profile) {
        return profile.member(NAME_PROFILE_KIND).flatMap(JsonNode::text)
                .filter(PROFILE_KIND_ENDPOINT::equals).isPresent();
    }

    /** The checks of one document; it carries the document name and the installation's rules. */
    private static final class Checker {

        private final String document;
        private final ProfileRules rules;

        private Checker(String document, ProfileRules rules) {
            this.document = document;
            this.rules = rules;
        }

        private void checkProfile(JsonNode profile) {
            var declaredKind = profile.member(NAME_PROFILE_KIND);
            if (declaredKind.isPresent()) {
                var kind = declaredKind.get().text().orElse("");
                if (PROFILE_KIND_ENDPOINT.equals(kind)) {
                    checkEndpointProfile(profile);
                    return;
                }
                throw invalid("/profile_kind",
                        "'profile_kind' has the undeclared value '%s'".formatted(kind));
            }
            object(profile, "", "profile", TOP_LEVEL, TOP_LEVEL);
            if (integer(required(profile, "", NAME_SCHEMA_VERSION), PTR_SCHEMA_VERSION, NAME_SCHEMA_VERSION, 1,
                    SCHEMA_VERSION) != SCHEMA_VERSION) {
                throw invalid(PTR_SCHEMA_VERSION, "'schema_version' must be %d".formatted(SCHEMA_VERSION));
            }
            var clientId = text(required(profile, "", NAME_CLIENT_ID), PTR_CLIENT_ID, NAME_CLIENT_ID, 32);
            if (!CLIENT_ID.matcher(clientId).matches()) {
                throw invalid(PTR_CLIENT_ID, "not a client id: '%s'".formatted(clientId));
            }
            var displayName = text(required(profile, "", NAME_DISPLAY_NAME), PTR_DISPLAY_NAME, NAME_DISPLAY_NAME, 64);
            if (displayName.isEmpty()) {
                throw invalid(PTR_DISPLAY_NAME, "a display name is not empty");
            }
            stringRule(displayName, PTR_DISPLAY_NAME, true);
            var minDaemonVersion = text(required(profile, "", NAME_MIN_DAEMON_VERSION), PTR_MIN_DAEMON_VERSION,
                    NAME_MIN_DAEMON_VERSION, 256);
            if (!VERSION.matcher(minDaemonVersion).matches()) {
                throw invalid(PTR_MIN_DAEMON_VERSION, "not a version: '%s'".formatted(minDaemonVersion));
            }
            icon(required(profile, "", NAME_ICON), "/icon");
            tools(required(profile, "", NAME_TOOLS), "/tools");
            skillLoading(required(profile, "", NAME_SKILL_LOADING), "/skill_loading");
            headless(required(profile, "", NAME_HEADLESS), "/headless");
            connection(required(profile, "", NAME_CONNECTION), "/connection");
            worker(required(profile, "", NAME_WORKER), "/worker", profile);
            detection(required(profile, "", NAME_DETECTION), "/detection");
            identity(required(profile, "", NAME_IDENTITY), PTR_IDENTITY);
        }

        /**
         * Validates an endpoint profile (Client Profiles § Endpoint Profile Schema): the same sets and hardening rules as
         * a client profile, a closed document of its own members, and the locality combinations the schema cannot
         * express.
         */
        private void checkEndpointProfile(JsonNode profile) {
            object(profile, "", "endpoint profile", ENDPOINT_TOP_LEVEL, ENDPOINT_TOP_LEVEL);
            if (integer(required(profile, "", NAME_SCHEMA_VERSION), PTR_SCHEMA_VERSION, NAME_SCHEMA_VERSION, 1,
                    SCHEMA_VERSION) != SCHEMA_VERSION) {
                throw invalid(PTR_SCHEMA_VERSION, "'schema_version' must be %d".formatted(SCHEMA_VERSION));
            }
            var clientId = text(required(profile, "", NAME_CLIENT_ID), PTR_CLIENT_ID, NAME_CLIENT_ID, 32);
            if (!CLIENT_ID.matcher(clientId).matches()) {
                throw invalid(PTR_CLIENT_ID, "not a client id: '%s'".formatted(clientId));
            }
            var displayName = text(required(profile, "", NAME_DISPLAY_NAME), PTR_DISPLAY_NAME, NAME_DISPLAY_NAME, 64);
            if (displayName.isEmpty()) {
                throw invalid(PTR_DISPLAY_NAME, "a display name is not empty");
            }
            stringRule(displayName, PTR_DISPLAY_NAME, true);
            var minDaemonVersion = text(required(profile, "", NAME_MIN_DAEMON_VERSION), PTR_MIN_DAEMON_VERSION,
                    NAME_MIN_DAEMON_VERSION, 256);
            if (!VERSION.matcher(minDaemonVersion).matches()) {
                throw invalid(PTR_MIN_DAEMON_VERSION, "not a version: '%s'".formatted(minDaemonVersion));
            }
            icon(required(profile, "", NAME_ICON), "/icon");
            var executable = optionalText(required(profile, "", NAME_EXECUTABLE), PTR_EXECUTABLE, NAME_EXECUTABLE, 64);
            if (executable != null && !EXECUTABLE.matcher(executable).matches()) {
                throw invalid(PTR_EXECUTABLE, "not a bare executable name: '%s'".formatted(executable));
            }
            var identityNode = required(profile, "", NAME_IDENTITY);
            var hasIdentity = !identityNode.isNull();
            if (hasIdentity) {
                identity(identityNode, PTR_IDENTITY);
            }
            var evidenceNode = required(profile, "", NAME_UPDATE_EVIDENCE);
            if (!evidenceNode.isNull()) {
                updateEvidence(evidenceNode, PTR_UPDATE_EVIDENCE);
            }
            var block = endpointBlock(required(profile, "", NAME_ENDPOINT), "/endpoint");
            modelTiers(required(profile, "", NAME_MODEL_TIERS), "/model_tiers");
            var weightsNode = required(profile, "", NAME_WEIGHTS_PROBE);
            weightsProbe(weightsNode, "/weights_probe");
            localityRules(block, executable, hasIdentity, !evidenceNode.isNull(), usesArgvProbe(weightsNode));
        }

        /** @return whether the profile's weights probe runs a program of this machine */
        private static boolean usesArgvProbe(JsonNode probe) {
            return !probe.isNull() && "argv".equals(probe.member(NAME_SOURCE).flatMap(JsonNode::text).orElse(null));
        }

        /**
         * Validates the {@code endpoint} block and returns the values the locality rules read.
         */
        private EndpointBlock endpointBlock(JsonNode endpoint, String pointer) {
            object(endpoint, pointer, NAME_ENDPOINT, ENDPOINT_BLOCK, ENDPOINT_BLOCK);
            var protocol = enumValue(required(endpoint, pointer, NAME_PROTOCOL), pointer + "/protocol", NAME_PROTOCOL,
                    Set.of("openai_chat", "system_one"));
            var locality = enumValue(required(endpoint, pointer, NAME_LOCALITY), pointer + "/locality", NAME_LOCALITY,
                    Set.of("loopback", "remote"));
            var baseUrl = text(required(endpoint, pointer, NAME_BASE_URL), pointer + "/base_url", NAME_BASE_URL, 256);
            if (!LOOPBACK_URL.matcher(baseUrl).matches() && !HTTPS_URL.matcher(baseUrl).matches()) {
                throw invalid(PTR_BASE_URL, "not an endpoint URL: '%s'".formatted(baseUrl));
            }
            var credential = optionalText(optional(endpoint, NAME_CREDENTIAL_KEY), pointer + "/credential_key",
                    NAME_CREDENTIAL_KEY, 32);
            if (credential != null && !CREDENTIAL_KEY.matcher(credential).matches()) {
                throw invalid(pointer + "/credential_key", "not a credential key: '%s'".formatted(credential));
            }
            endpointPath(optional(endpoint, NAME_READINESS_PATH), pointer + "/readiness_path", NAME_READINESS_PATH);
            endpointPath(required(endpoint, pointer, NAME_MODELS_PATH), pointer + "/models_path", NAME_MODELS_PATH);
            var modelsPointer = text(required(endpoint, pointer, NAME_MODELS_POINTER), pointer + "/models_pointer",
                    NAME_MODELS_POINTER, 256);
            if (!modelsPointer.startsWith("/")) {
                throw invalid(pointer + "/models_pointer", MSG_NOT_A_POINTER.formatted(modelsPointer));
            }
            enumValue(required(endpoint, pointer, NAME_RESULT_SCHEMA), pointer + "/result_schema", NAME_RESULT_SCHEMA,
                    Set.of(ENFORCED, "advisory", "none"));
            var contextTokens = integer(required(endpoint, pointer, NAME_CONTEXT_TOKENS), pointer + "/context_tokens",
                    NAME_CONTEXT_TOKENS, 1024, Integer.MAX_VALUE);
            var answerReserve = integer(required(endpoint, pointer, NAME_ANSWER_RESERVE_TOKENS),
                    pointer + "/answer_reserve_tokens", NAME_ANSWER_RESERVE_TOKENS, 64, Integer.MAX_VALUE);
            if (answerReserve >= contextTokens) {
                throw invalid(pointer + "/answer_reserve_tokens",
                        "the answer reserve %d must stay below context_tokens %d".formatted(answerReserve,
                                contextTokens));
            }
            integer(required(endpoint, pointer, NAME_REQUEST_TIMEOUT_SECONDS), pointer + "/request_timeout_seconds",
                    NAME_REQUEST_TIMEOUT_SECONDS, 5, 3600);
            return new EndpointBlock(protocol, locality, baseUrl, credential);
        }

        /**
         * Refuses the combinations of {@code locality} the schema cannot express (Client Profiles § Endpoint Profile
         * Schema, § Remote Form). A loopback endpoint names a plain {@code http} loopback URL, no credential, and the
         * serving program and its identity. The remote form is the protocol {@code system_one}'s: it names one {@code
         * https} origin and the Token Lane credential entry it reads, and has no serving program, no identity and no
         * update evidence, because no owner check applies to a service.
         */
        private void localityRules(EndpointBlock block, String executable, boolean hasIdentity,
                boolean hasUpdateEvidence, boolean hasArgvWeightsProbe) {
            if ("remote".equals(block.locality())) {
                remoteRules(block, executable, hasIdentity, hasUpdateEvidence, hasArgvWeightsProbe);
            } else {
                loopbackRules(block, executable, hasIdentity);
            }
        }

        private void remoteRules(EndpointBlock block, String executable, boolean hasIdentity,
                boolean hasUpdateEvidence, boolean hasArgvWeightsProbe) {
            if (hasArgvWeightsProbe) {
                throw invalid("/weights_probe/source",
                        "a remote endpoint reads its weights identity from the models listing, never from a program "
                                + "of this machine");
            }
            if (!"system_one".equals(block.protocol())) {
                throw invalid("/endpoint/locality",
                        "the remote form is the protocol system_one's; an openai_chat endpoint is a loopback one");
            }
            if (!HTTPS_URL.matcher(block.baseUrl()).matches()) {
                throw invalid(PTR_BASE_URL,
                        "a remote endpoint names the https origin of one service: '%s'".formatted(block.baseUrl()));
            }
            var port = HTTPS_PORT.matcher(block.baseUrl());
            if (port.find()) {
                var value = Integer.parseInt(port.group(1));
                if (value < 1 || value > 65535) {
                    throw invalid(PTR_BASE_URL, "not a port: %d".formatted(value));
                }
            }
            if (block.credentialKey() == null) {
                throw invalid("/endpoint/credential_key",
                        "a remote endpoint names the Token Lane credential entry it reads");
            }
            if (executable != null) {
                throw invalid(PTR_EXECUTABLE, "a remote endpoint has a null executable");
            }
            if (hasIdentity) {
                throw invalid(PTR_IDENTITY, "a remote endpoint has a null identity");
            }
            if (hasUpdateEvidence) {
                throw invalid(PTR_UPDATE_EVIDENCE, "a remote endpoint has a null update_evidence");
            }
        }

        private void loopbackRules(EndpointBlock block, String executable, boolean hasIdentity) {
            var loopback = LOOPBACK_URL.matcher(block.baseUrl());
            if (!loopback.matches()) {
                throw invalid(PTR_BASE_URL,
                        "a loopback endpoint names http://127.0.0.1:<port> or http://[::1]:<port>: '%s'"
                                .formatted(block.baseUrl()));
            }
            var port = Integer.parseInt(loopback.group(2));
            if (port < 1 || port > 65535) {
                throw invalid(PTR_BASE_URL, "not a port: %d".formatted(port));
            }
            if (block.credentialKey() != null) {
                throw invalid("/endpoint/credential_key", "a loopback endpoint has a null credential_key");
            }
            if (executable == null) {
                throw invalid(PTR_EXECUTABLE, "a loopback endpoint names the program that serves it");
            }
            if (!hasIdentity) {
                throw invalid(PTR_IDENTITY, "a loopback endpoint names its identity");
            }
        }

        private void endpointPath(JsonNode path, String pointer, String name) {
            if (path == null || path.isNull()) {
                return;
            }
            var value = text(path, pointer, name, 128);
            if (!ENDPOINT_PATH.matcher(value).matches()) {
                throw invalid(pointer, "not an endpoint path: '%s'".formatted(value));
            }
        }

        /**
         * @param node the value of a member the schema types {@code [string, null]}
         * @return the string, or {@code null} for the JSON null
         */
        private String optionalText(JsonNode node, String pointer, String name, int max) {
            return node == null || node.isNull() ? null : text(node, pointer, name, max);
        }

        /** The values of the {@code endpoint} block the locality rules read. */
        private record EndpointBlock(String protocol, String locality, String baseUrl, String credentialKey) {
        }

        private void icon(JsonNode icon, String pointer) {
            if (icon.isNull()) {
                return;
            }
            object(icon, pointer, NAME_ICON, Set.of("form", NAME_SOURCE, NAME_BUNDLED), Set.of("form", NAME_SOURCE, NAME_BUNDLED));
            var form = enumValue(required(icon, pointer, "form"), pointer + "/form", "form", Set.of(NAME_SOURCE, NAME_BUNDLED));
            var source = optional(icon, NAME_SOURCE);
            var bundled = optional(icon, NAME_BUNDLED);
            if (NAME_SOURCE.equals(form)) {
                var url = string(source, pointer + PTR_SOURCE, NAME_SOURCE, 256);
                if (!ICON_SOURCE.matcher(url).matches()) {
                    throw invalid(pointer + PTR_SOURCE, "an icon source is an https URL of an SVG or PNG file: '%s'"
                            .formatted(url));
                }
                if (bundled != null && !bundled.isNull()) {
                    throw invalid(pointer + PTR_BUNDLED, "an icon of the form source has no bundled name");
                }
            } else {
                var name = string(bundled, pointer + PTR_BUNDLED, NAME_BUNDLED, 32);
                if (!BUNDLED_ICON.matcher(name).matches()) {
                    throw invalid(pointer + PTR_BUNDLED, "not a bundled icon name: '%s'".formatted(name));
                }
                if (source != null && !source.isNull()) {
                    throw invalid(pointer + PTR_SOURCE, "an icon of the form bundled has no source URL");
                }
            }
        }

        private void tools(JsonNode tools, String pointer) {
            var allowed = new LinkedHashSet<>(TOOL_NAMES);
            allowed.add(NAME_IDIOMS);
            object(tools, pointer, NAME_TOOLS, TOOL_NAMES, allowed);
            for (var name : TOOL_NAMES) {
                var value = text(required(tools, pointer, name), pointer + "/" + name, name, 256);
                toolName(value, pointer + "/" + name);
            }
            var idioms = optional(tools, NAME_IDIOMS);
            if (idioms == null) {
                return;
            }
            object(idioms, pointer + "/idioms", NAME_IDIOMS);
            for (var member : idioms.members().entrySet()) {
                var idiomPointer = pointer + "/idioms/" + escape(member.getKey());
                object(member.getValue(), idiomPointer, "idiom", Set.of(NAME_DISPOSITION, NAME_REPLACEMENT),
                        Set.of(NAME_DISPOSITION, NAME_REPLACEMENT));
                enumValue(required(member.getValue(), idiomPointer, NAME_DISPOSITION), idiomPointer + "/disposition",
                        NAME_DISPOSITION, Set.of("rewrite", "preserve", "omit"));
                var replacement = optional(member.getValue(), NAME_REPLACEMENT);
                if (replacement != null) {
                    var text = text(replacement, idiomPointer + "/replacement", NAME_REPLACEMENT, 256);
                    stringRule(text, idiomPointer + "/replacement", true);
                }
            }
        }

        private void skillLoading(JsonNode skill, String pointer) {
            var members = Set.of("mode", NAME_LOAD_DIRECTIVE_TEMPLATE, NAME_SCHEMA_RESIDENCY, NAME_SKILLS_EXTENSION);
            object(skill, pointer, NAME_SKILL_LOADING, members, members);
            enumValue(required(skill, pointer, "mode"), pointer + "/mode", "mode",
                    Set.of(NAME_TOOL_CALL, "mcp_resource", "view_file"));
            var template = text(required(skill, pointer, NAME_LOAD_DIRECTIVE_TEMPLATE),
                    pointer + "/load_directive_template", NAME_LOAD_DIRECTIVE_TEMPLATE, 256);
            stringRule(template, pointer + "/load_directive_template", true);
            enumValue(required(skill, pointer, NAME_SCHEMA_RESIDENCY), pointer + "/schema_residency", NAME_SCHEMA_RESIDENCY,
                    Set.of("resident", "on_demand"));
            bool(required(skill, pointer, NAME_SKILLS_EXTENSION), pointer + "/skills_extension", NAME_SKILLS_EXTENSION);
        }

        private void headless(JsonNode headless, String pointer) {
            if (headless.isNull()) {
                return;
            }
            var headlessMembers = new HashSet<>(HEADLESS);
            headlessMembers.addAll(HEADLESS_OPTIONAL);
            object(headless, pointer, NAME_HEADLESS, HEADLESS, headlessMembers);
            var executable = text(required(headless, pointer, NAME_EXECUTABLE), pointer + PTR_EXECUTABLE, NAME_EXECUTABLE, 64);
            if (!EXECUTABLE.matcher(executable).matches()) {
                throw invalid(pointer + PTR_EXECUTABLE, "not a bare executable name: '%s'".formatted(executable));
            }
            installLocations(required(headless, pointer, NAME_INSTALL_LOCATIONS), pointer + "/install_locations");
            var installHint = text(required(headless, pointer, NAME_INSTALL_HINT), pointer + "/install_hint", NAME_INSTALL_HINT,
                    64);
            if (!INSTALL_HINT.matcher(installHint).matches()) {
                throw invalid(pointer + "/install_hint", "not a message key: '%s'".formatted(installHint));
            }
            authProbe(required(headless, pointer, NAME_AUTH_PROBE), pointer + "/auth_probe");
            modelsProbe(required(headless, pointer, NAME_MODELS_PROBE), pointer + "/models_probe");
            weightsProbe(required(headless, pointer, NAME_WEIGHTS_PROBE), pointer + "/weights_probe");
            modelTiers(required(headless, pointer, NAME_MODEL_TIERS), pointer + "/model_tiers");
            var supported = text(required(headless, pointer, NAME_SUPPORTED_VERSIONS), pointer + "/supported_versions",
                    NAME_SUPPORTED_VERSIONS, 256);
            SupportedVersions.parse(supported, document, pointer + "/supported_versions");
            updateEvidence(required(headless, pointer, NAME_UPDATE_EVIDENCE), pointer + PTR_UPDATE_EVIDENCE);
            enumValue(required(headless, pointer, NAME_AGENT_MODE), pointer + "/agent_mode", NAME_AGENT_MODE,
                    Set.of("inline", "flags"));
            launchArguments(required(headless, pointer, "argv"), pointer + PTR_ARGV, "argv", SLOTS);
            var mcpConfig = required(headless, pointer, NAME_MCP_CONFIG);
            var exclusive = mcpConfig(mcpConfig, pointer + "/mcp_config");
            classes(required(headless, pointer, NAME_CLASSES), pointer + "/classes", exclusive, headless, pointer);
            environment(required(headless, pointer, "env"), pointer + "/env");
            capabilities(required(headless, pointer, NAME_CAPABILITIES), pointer + "/capabilities");
            var efforts = optional(headless, NAME_MODEL_EFFORTS);
            if (efforts != null) {
                modelEfforts(efforts, pointer + "/model_efforts");
            }
            output(required(headless, pointer, NAME_OUTPUT), pointer + "/output");
            usage(required(headless, pointer, NAME_USAGE), pointer + "/usage");
            events(required(headless, pointer, NAME_EVENTS), pointer + "/events");
            endpoints(required(headless, pointer, NAME_ENDPOINTS), pointer + "/endpoints");
            writablePaths(required(headless, pointer, NAME_WRITABLE_PATHS), pointer + "/writable_paths");
            dataHome(required(headless, pointer, NAME_DATA_HOME), pointer + "/data_home");
            var limit = optional(headless, NAME_TOOL_RESULT_LIMIT_CHARS);
            if (limit != null && !limit.isNull()) {
                integer(limit, pointer + "/tool_result_limit_chars", NAME_TOOL_RESULT_LIMIT_CHARS, 4000,
                        Integer.MAX_VALUE);
                var resources = required(headless, pointer, NAME_RESOURCE_TOOLS);
                stringArray(resources, pointer + "/resource_tools", NAME_RESOURCE_TOOLS, 0, 64, true);
                if (resources.elements().isEmpty()) {
                    throw invalid(pointer + "/tool_result_limit_chars",
                            "a tool-result limit requires a non-empty resource_tools");
                }
            }
            stringArray(required(headless, pointer, NAME_RESOURCE_TOOLS), pointer + "/resource_tools", NAME_RESOURCE_TOOLS, 0,
                    64, true);
            enumValue(required(headless, pointer, NAME_SESSION_RESIDUE), pointer + "/session_residue", NAME_SESSION_RESIDUE,
                    Set.of(NAME_EPHEMERAL, "persisted"));
            resumability(required(headless, pointer, NAME_RESUMABILITY), pointer + "/resumability",
                    enumValue(required(headless, pointer, NAME_SESSION_RESIDUE), pointer + "/session_residue",
                            NAME_SESSION_RESIDUE, Set.of(NAME_EPHEMERAL, "persisted")));
            var budget = optional(headless, NAME_CONTEXT_BUDGET_TOKENS);
            if (budget != null) {
                integer(budget, pointer + "/context_budget_tokens", NAME_CONTEXT_BUDGET_TOKENS, 10000, Integer.MAX_VALUE);
            }
        }

        private void installLocations(JsonNode locations, String pointer) {
            var elements = list(locations, pointer, NAME_INSTALL_LOCATIONS, 0, 16);
            for (var index = 0; index < elements.size(); index++) {
                var location = text(elements.get(index), pointer + "/" + index, "install_locations[" + index + "]", 256);
                if (!INSTALL_LOCATION.matcher(location).matches()) {
                    throw invalid(pointer + "/" + index, "not an install location: '%s'".formatted(location));
                }
            }
        }

        private void authProbe(JsonNode probe, String pointer) {
            if (probe.isNull()) {
                return;
            }
            var members = Set.of("argv", NAME_LOGGED_IN_EXIT_CODES, NAME_LOGGED_OUT_EXIT_CODES, NAME_LOGIN_COMMAND);
            object(probe, pointer, NAME_AUTH_PROBE, members, members);
            launchArguments(required(probe, pointer, "argv"), pointer + PTR_ARGV, "argv", SLOTS);
            exitCodes(required(probe, pointer, NAME_LOGGED_IN_EXIT_CODES), pointer + "/logged_in_exit_codes",
                    NAME_LOGGED_IN_EXIT_CODES);
            exitCodes(required(probe, pointer, NAME_LOGGED_OUT_EXIT_CODES), pointer + "/logged_out_exit_codes",
                    NAME_LOGGED_OUT_EXIT_CODES);
            launchArguments(required(probe, pointer, NAME_LOGIN_COMMAND), pointer + "/login_command", NAME_LOGIN_COMMAND,
                    SLOTS);
        }

        private void exitCodes(JsonNode codes, String pointer, String name) {
            var elements = list(codes, pointer, name, 1, 64);
            for (var index = 0; index < elements.size(); index++) {
                integer(elements.get(index), pointer + "/" + index, name + "[" + index + "]", 0, 255);
            }
        }

        private void modelsProbe(JsonNode probe, String pointer) {
            if (probe.isNull()) {
                return;
            }
            var members = Set.of("argv", NAME_FORMAT, NAME_POINTER);
            object(probe, pointer, NAME_MODELS_PROBE, members, members);
            launchArguments(required(probe, pointer, "argv"), pointer + PTR_ARGV, "argv", SLOTS);
            var format = enumValue(required(probe, pointer, NAME_FORMAT), pointer + "/format", NAME_FORMAT,
                    Set.of("lines", "json"));
            var modelPointer = optional(probe, NAME_POINTER);
            if ("json".equals(format)) {
                if (modelPointer == null || modelPointer.isNull()) {
                    throw invalid(pointer + PTR_POINTER, "a JSON models probe names its pointer");
                }
                string(modelPointer, pointer + PTR_POINTER, NAME_POINTER, 256);
            } else if (modelPointer != null && !modelPointer.isNull()) {
                throw invalid(pointer + PTR_POINTER, "a line-wise models probe has a null pointer");
            }
        }

        /**
         * The weights probe of the endpoint-profile definition, which a client profile references: null, or a closed
         * object that reads the weights identity from the models listing or from an argv probe with the model slot.
         */
        private void weightsProbe(JsonNode probe, String pointer) {
            if (probe.isNull()) {
                return;
            }
            var members = Set.of(NAME_SOURCE, NAME_POINTER, "argv", NAME_PATTERN);
            object(probe, pointer, NAME_WEIGHTS_PROBE, members, members);
            var source = enumValue(required(probe, pointer, NAME_SOURCE), pointer + PTR_SOURCE, NAME_SOURCE,
                    Set.of("models_listing", "argv"));
            var identityPointer = optional(probe, NAME_POINTER);
            var argv = optional(probe, "argv");
            var pattern = optional(probe, NAME_PATTERN);
            if ("models_listing".equals(source)) {
                modelsListingProbe(identityPointer, argv, pattern, pointer);
            } else {
                argvProbe(identityPointer, argv, pattern, pointer);
            }
        }

        private void modelsListingProbe(JsonNode identityPointer, JsonNode argv, JsonNode pattern, String pointer) {
            if (identityPointer == null || identityPointer.isNull()) {
                throw invalid(pointer + PTR_POINTER, "a models-listing weights probe names its pointer");
            }
            var value = text(identityPointer, pointer + PTR_POINTER, NAME_POINTER, 256);
            if (!value.startsWith("/")) {
                throw invalid(pointer + PTR_POINTER, MSG_NOT_A_POINTER.formatted(value));
            }
            if (argv != null && !argv.isNull()) {
                throw invalid(pointer + PTR_ARGV, "a models-listing weights probe has a null argv");
            }
            if (pattern != null && !pattern.isNull()) {
                throw invalid(pointer + PTR_PATTERN, "a models-listing weights probe has a null pattern");
            }
        }

        private void argvProbe(JsonNode identityPointer, JsonNode argv, JsonNode pattern, String pointer) {
            if (identityPointer != null && !identityPointer.isNull()) {
                throw invalid(pointer + PTR_POINTER, "an argv weights probe has a null pointer");
            }
            if (argv == null || argv.isNull()) {
                throw invalid(pointer + PTR_ARGV, "an argv weights probe names its argv");
            }
            launchArguments(argv, pointer + PTR_ARGV, "weights_probe.argv", SLOTS, 0);
            if (!argv.elements().isEmpty() && argv.elements().stream()
                    .noneMatch(item -> item.text().orElse("").contains("{{model}}"))) {
                throw invalid(pointer + PTR_ARGV, "an argv weights probe uses the {{model}} slot");
            }
            if (pattern == null || pattern.isNull()) {
                throw invalid(pointer + PTR_PATTERN, "an argv weights probe names its pattern");
            }
            patternRule(text(pattern, pointer + PTR_PATTERN, NAME_PATTERN, 128), pointer + PTR_PATTERN);
        }

        private void modelTiers(JsonNode tiers, String pointer) {
            var members = Set.of("frontier", "standard", "fast");
            object(tiers, pointer, NAME_MODEL_TIERS, members, members);
            for (var tier : members) {
                var value = required(tiers, pointer, tier);
                if (!value.isNull()) {
                    text(value, pointer + "/" + tier, tier, 128);
                }
            }
        }

        private void updateEvidence(JsonNode evidence, String pointer) {
            var members = Set.of(NAME_METHOD, NAME_SIGNER);
            object(evidence, pointer, NAME_UPDATE_EVIDENCE, members, members);
            var method = enumValue(required(evidence, pointer, NAME_METHOD), pointer + "/method", NAME_METHOD,
                    Set.of("code_signature", "package_provenance", "none"));
            var signer = optional(evidence, NAME_SIGNER);
            if ("none".equals(method)) {
                if (signer != null && !signer.isNull()) {
                    throw invalid(pointer + PTR_SIGNER, "the method none has a null signer");
                }
            } else {
                if (signer == null || signer.isNull()) {
                    throw invalid(pointer + PTR_SIGNER, "the method %s requires a signer".formatted(method));
                }
                var value = text(signer, pointer + PTR_SIGNER, NAME_SIGNER, 256);
                if (value.isEmpty()) {
                    throw invalid(pointer + PTR_SIGNER, "a signer is not empty");
                }
            }
        }

        /** @return whether the MCP configuration is exclusive */
        private boolean mcpConfig(JsonNode config, String pointer) {
            var members = Set.of(NAME_DELIVERY, "path", NAME_EXCLUSIVE);
            object(config, pointer, NAME_MCP_CONFIG, members, members);
            var delivery = enumValue(required(config, pointer, NAME_DELIVERY), pointer + "/delivery", NAME_DELIVERY,
                    Set.of("slot", "file"));
            var path = optional(config, "path");
            if ("file".equals(delivery)) {
                if (path == null || path.isNull()) {
                    throw invalid(pointer + PTR_PATH, "the delivery file names its path");
                }
                var value = text(path, pointer + PTR_PATH, "path", 256);
                relativePath(value, pointer + PTR_PATH, "mcp_config.path");
            } else if (path != null && !path.isNull()) {
                throw invalid(pointer + PTR_PATH, "the delivery slot has a null path");
            }
            return bool(required(config, pointer, NAME_EXCLUSIVE), pointer + "/exclusive", NAME_EXCLUSIVE);
        }

        private void classes(JsonNode classes, String pointer, boolean exclusive, JsonNode headless,
                String headlessPointer) {
            var members = Set.of(NAME_WRITER, NAME_READ_ONLY, NAME_ZERO_TOOL);
            object(classes, pointer, NAME_CLASSES, members, members);
            var writer = toolClass(required(classes, pointer, NAME_WRITER), pointer + "/writer", NAME_WRITER);
            var readOnly = toolClass(required(classes, pointer, NAME_READ_ONLY), pointer + "/read_only", NAME_READ_ONLY);
            var zeroTool = required(classes, pointer, NAME_ZERO_TOOL);
            var zeroToolRunnable = false;
            if (!zeroTool.isNull()) {
                object(zeroTool, pointer + PTR_ZERO_TOOL, NAME_ZERO_TOOL, Set.of("args"), Set.of("args"));
                launchArguments(required(zeroTool, pointer + PTR_ZERO_TOOL, "args"), pointer + "/zero_tool/args",
                        "zero_tool.args", SLOTS, 0);
                zeroToolRunnable = true;
            }
            if (!exclusive && (writer.isPresent() || readOnly.isPresent() || zeroToolRunnable)) {
                throw invalid(pointer,
                        "a profile whose MCP configuration is not exclusive declares writer, read_only and zero_tool "
                                + "all null");
            }
            var capabilities = required(headless, headlessPointer, NAME_CAPABILITIES);
            if (zeroToolRunnable && !hasCapability(capabilities, "project_settings_control")) {
                throw invalid(pointer + PTR_ZERO_TOOL,
                        "a runnable zero_tool class requires the capability project_settings_control");
            }
            if ((writer.isPresent() || readOnly.isPresent()) && !hasCapability(capabilities, "extension_isolation")) {
                throw invalid(pointer,
                        "a runnable writer or read_only class requires the capability extension_isolation");
            }
        }

        /** @return the tool_set of a runnable class, or empty for a class the harness cannot run */
        private Optional<String> toolClass(JsonNode declaration, String pointer, String name) {
            if (declaration.isNull()) {
                return Optional.empty();
            }
            object(declaration, pointer, name, Set.of("args", NAME_TOOL_SET), Set.of("args", NAME_TOOL_SET));
            launchArguments(required(declaration, pointer, "args"), pointer + "/args", name + ".args", SLOTS, 0);
            return Optional.of(enumValue(required(declaration, pointer, NAME_TOOL_SET), pointer + "/tool_set",
                    NAME_TOOL_SET, Set.of(ENFORCED, "advisory")));
        }

        private boolean hasCapability(JsonNode capabilities, String capability) {
            return capabilities.elements().stream().anyMatch(value -> value.text().orElse("").equals(capability));
        }

        private void environment(JsonNode environment, String pointer) {
            object(environment, pointer, "env");
            for (var member : environment.members().entrySet()) {
                var name = member.getKey();
                if (!VARIABLE.matcher(name).matches()) {
                    throw invalid(pointer + "/" + escape(name), "not an environment variable name: '%s'".formatted(name));
                }
                if (jobEnvironment(name)) {
                    throw invalid(pointer + "/" + escape(name),
                            "the launch may not set the job environment variable '%s'".formatted(name));
                }
                if (credentialName(name)) {
                    throw invalid(pointer + "/" + escape(name),
                            "the launch may not set the credential-pattern variable '%s'".formatted(name));
                }
                var value = text(member.getValue(), pointer + "/" + escape(name), name, 1024);
                launchItem(value, pointer + "/" + escape(name), SLOTS);
            }
        }

        private void capabilities(JsonNode capabilities, String pointer) {
            stringArray(capabilities, pointer, NAME_CAPABILITIES, 0, 64, true);
            for (var index = 0; index < capabilities.elements().size(); index++) {
                var value = capabilities.elements().get(index).text().orElse("");
                if (!CAPABILITIES.contains(value)) {
                    throw invalid(pointer + "/" + index, "not a capability: '%s'".formatted(value));
                }
            }
        }

        private void modelEfforts(JsonNode efforts, String pointer) {
            object(efforts, pointer, NAME_MODEL_EFFORTS);
            for (var member : efforts.members().entrySet()) {
                stringArray(member.getValue(), pointer + "/" + escape(member.getKey()), NAME_MODEL_EFFORTS,
                        0, 64, true);
            }
        }

        private void output(JsonNode output, String pointer) {
            var members = Set.of(NAME_FORMAT, NAME_RESULT_POINTER);
            object(output, pointer, NAME_OUTPUT, members, members);
            enumValue(required(output, pointer, NAME_FORMAT), pointer + "/format", NAME_FORMAT,
                    Set.of("json", "stream_json", "jsonl"));
            var resultPointer = required(output, pointer, NAME_RESULT_POINTER);
            if (!resultPointer.isNull()) {
                var value = text(resultPointer, pointer + "/result_pointer", NAME_RESULT_POINTER, 256);
                if (!value.startsWith("/")) {
                    throw invalid(pointer + "/result_pointer", MSG_NOT_A_POINTER.formatted(value));
                }
            }
        }

        /**
         * The usage block is {@code token-usage}'s, including the rule that {@code input_tokens} and
         * {@code output_tokens} are never {@code not_applicable}; this validator checks only its shape and leaves the
         * meaning of a figure to the stage {@code usage} of the profile verification.
         */
        private void usage(JsonNode usage, String pointer) {
            var members = Set.copyOf(ProfileStages.USAGE_FIGURES);
            object(usage, pointer, NAME_USAGE, members, members);
            for (var member : members) {
                var value = required(usage, pointer, member);
                if (!value.isNull()) {
                    text(value, pointer + "/" + member, member, 256);
                }
            }
        }

        private void events(JsonNode events, String pointer) {
            if (events.isNull()) {
                return;
            }
            object(events, pointer, NAME_EVENTS, Set.of(NAME_KIND_POINTER, NAME_KINDS), Set.of(NAME_KIND_POINTER, NAME_KINDS));
            var kindPointer = text(required(events, pointer, NAME_KIND_POINTER), pointer + "/kind_pointer", NAME_KIND_POINTER,
                    256);
            if (!kindPointer.startsWith("/")) {
                throw invalid(pointer + "/kind_pointer", MSG_NOT_A_POINTER.formatted(kindPointer));
            }
            var kinds = required(events, pointer, NAME_KINDS);
            var fields = Map.of(
                    "text", Set.of(NAME_MATCH, "text"),
                    NAME_TOOL_CALL, Set.of(NAME_MATCH, "tool", NAME_TARGET),
                    "tool_result", Set.of(NAME_MATCH, "size", "is_error"),
                    "file_changed", Set.of(NAME_MATCH, "path"),
                    "turn", Set.of(NAME_MATCH, "stop_reason"));
            var members = fields.keySet();
            object(kinds, pointer + "/kinds", NAME_KINDS, members, members);
            for (var member : members) {
                var value = required(kinds, pointer + "/kinds", member);
                if (value.isNull()) {
                    continue;
                }
                var kindPointerPath = pointer + "/kinds/" + member;
                var allowed = fields.get(member);
                object(value, kindPointerPath, member, allowed, allowed);
                stringArray(required(value, kindPointerPath, NAME_MATCH), kindPointerPath + "/match", NAME_MATCH, 0, 64,
                        false);
                for (var field : value.members().keySet()) {
                    checkKindField(field, value.members().get(field), kindPointerPath + "/" + field);
                }
            }
        }

        private void checkKindField(String field, JsonNode fieldValue, String fieldPointer) {
            if (NAME_MATCH.equals(field)) {
                return;
            }
            if (NAME_TARGET.equals(field)) {
                stringArray(fieldValue, fieldPointer, NAME_TARGET, 0, 4, false);
            } else if (!isNullOptionalField(field, fieldValue)) {
                text(fieldValue, fieldPointer, field, 256);
            }
        }

        private static boolean isNullOptionalField(String field, JsonNode fieldValue) {
            return ("is_error".equals(field) || "stop_reason".equals(field)) && fieldValue.isNull();
        }

        private void endpoints(JsonNode endpoints, String pointer) {
            var elements = list(endpoints, pointer, NAME_ENDPOINTS, 1, 64);
            for (var index = 0; index < elements.size(); index++) {
                var endpoint = text(elements.get(index), pointer + "/" + index, "endpoints[" + index + "]", 256);
                if (!validEndpoint(endpoint)) {
                    throw invalid(pointer + "/" + index, "not a host:port endpoint: '%s'".formatted(endpoint));
                }
            }
        }

        /**
         * @return whether the text is a lowercase host name and a decimal port from 1 to 65535 without a leading zero
         */
        private static boolean validEndpoint(String endpoint) {
            var separator = endpoint.indexOf(':');
            return separator > 0 && ENDPOINT_HOST.matcher(endpoint.substring(0, separator)).matches()
                    && validPort(endpoint.substring(separator + 1));
        }

        private static boolean validPort(String port) {
            if (port.isEmpty() || port.length() > 5 || port.charAt(0) == '0') {
                return false;
            }
            for (var index = 0; index < port.length(); index++) {
                var character = port.charAt(index);
                if (character < '0' || character > '9') {
                    return false;
                }
            }
            return Integer.parseInt(port) <= 65535;
        }

        private void writablePaths(JsonNode paths, String pointer) {
            var elements = list(paths, pointer, NAME_WRITABLE_PATHS, 0, 64);
            for (var index = 0; index < elements.size(); index++) {
                var path = text(elements.get(index), pointer + '/' + index, "writable_paths[" + index + "]", 256);
                if (!HOME_PATH.matcher(path).matches()) {
                    throw invalid(pointer + "/" + index, "not a home-relative path: '%s'".formatted(path));
                }
                noTraversal(path, pointer + "/" + index, NAME_WRITABLE_PATHS);
                if (rules.harnessHomes().contains(path)) {
                    throw invalid(pointer + "/" + index,
                            "a harness home is never a writable path as a whole: '%s'".formatted(path));
                }
                for (var managed : rules.managedBundlePaths()) {
                    if (covers(path, managed)) {
                        throw invalid(pointer + "/" + index,
                                "the writable path '%s' covers the managed file '%s'".formatted(path, managed));
                    }
                }
            }
        }

        private void dataHome(JsonNode home, String pointer) {
            if (home.isNull()) {
                return;
            }
            object(home, pointer, NAME_DATA_HOME, Set.of(NAME_VARIABLE, NAME_LOGIN_FILE), Set.of(NAME_VARIABLE, NAME_LOGIN_FILE));
            var variable = text(required(home, pointer, NAME_VARIABLE), pointer + PTR_VARIABLE, NAME_VARIABLE, 64);
            if (!VARIABLE.matcher(variable).matches()) {
                throw invalid(pointer + PTR_VARIABLE, "not an environment variable name: '%s'".formatted(variable));
            }
            if (jobEnvironment(variable) || credentialName(variable)) {
                throw invalid(pointer + PTR_VARIABLE,
                        "the data home may not relocate a job or credential variable: '%s'".formatted(variable));
            }
            var loginFile = text(required(home, pointer, NAME_LOGIN_FILE), pointer + PTR_LOGIN_FILE, NAME_LOGIN_FILE, 256);
            if (!LOGIN_FILE.matcher(loginFile).matches()) {
                throw invalid(pointer + PTR_LOGIN_FILE, "not a login file path: '%s'".formatted(loginFile));
            }
            noTraversal(loginFile, pointer + PTR_LOGIN_FILE, "data_home.login_file");
        }

        private void resumability(JsonNode resumability, String pointer, String residue) {
            object(resumability, pointer, NAME_RESUMABILITY, Set.of("hold", NAME_RESUME),
                    Set.of("hold", NAME_RESUME, NAME_MAX_HOLD_SECONDS));
            var hold = bool(required(resumability, pointer, "hold"), pointer + "/hold", "hold");
            var maxHold = optional(resumability, NAME_MAX_HOLD_SECONDS);
            if (maxHold != null) {
                if (maxHold.isNull()) {
                    if (hold) {
                        throw invalid(pointer + "/max_hold_seconds", "a hold names its max_hold_seconds");
                    }
                } else {
                    integer(maxHold, pointer + "/max_hold_seconds", NAME_MAX_HOLD_SECONDS, 60, 3600);
                }
            }
            var resume = optional(resumability, NAME_RESUME);
            if (resume == null || resume.isNull()) {
                return;
            }
            if (NAME_EPHEMERAL.equals(residue)) {
                throw invalid(pointer + "/resume",
                        "a resume needs persisted session residue, not ephemeral");
            }
            var resumePointer = pointer + "/resume";
            object(resume, resumePointer, NAME_RESUME, Set.of("argv", NAME_SESSION_ID, NAME_MAX_AGE_SECONDS),
                    Set.of("argv", NAME_SESSION_ID, NAME_MAX_AGE_SECONDS));
            var argv = required(resume, resumePointer, "argv");
            launchArguments(argv, resumePointer + PTR_ARGV, "resume.argv", SESSION_SLOTS);
            var occurrences = argv.elements().stream()
                    .filter(item -> item.text().orElse("").contains("{{session_id}}")).count();
            if (occurrences != 1) {
                throw invalid(resumePointer + PTR_ARGV,
                        "the resume argv holds {{session_id}} exactly once, not %d times".formatted(occurrences));
            }
            var sessionId = text(required(resume, resumePointer, NAME_SESSION_ID), resumePointer + "/session_id",
                    NAME_SESSION_ID, 256);
            if (!sessionId.startsWith("/")) {
                throw invalid(resumePointer + "/session_id", MSG_NOT_A_POINTER.formatted(sessionId));
            }
            integer(required(resume, resumePointer, NAME_MAX_AGE_SECONDS), resumePointer + "/max_age_seconds",
                    NAME_MAX_AGE_SECONDS, 600, 604800);
        }

        private void connection(JsonNode connection, String pointer) {
            var members = Set.of(NAME_TRANSPORT, NAME_IDLE_TIMEOUT_SECONDS, NAME_BOUNDED_WAIT_SECONDS,
                    NAME_PROGRESS_RESETS_TIMEOUT);
            object(connection, pointer, NAME_CONNECTION, members, members);
            var transport = text(required(connection, pointer, NAME_TRANSPORT), pointer + "/transport", NAME_TRANSPORT, 16);
            if (!"stdio".equals(transport)) {
                throw invalid(pointer + "/transport", "the transport must be 'stdio'");
            }
            var idle = integer(required(connection, pointer, NAME_IDLE_TIMEOUT_SECONDS),
                    pointer + "/idle_timeout_seconds", NAME_IDLE_TIMEOUT_SECONDS, 45, 7200);
            var bounded = integer(required(connection, pointer, NAME_BOUNDED_WAIT_SECONDS),
                    pointer + "/bounded_wait_seconds", NAME_BOUNDED_WAIT_SECONDS, 15, 3600);
            var progress = bool(required(connection, pointer, NAME_PROGRESS_RESETS_TIMEOUT),
                    pointer + "/progress_resets_timeout", NAME_PROGRESS_RESETS_TIMEOUT);
            if (!progress && bounded >= idle) {
                throw invalid(pointer + "/bounded_wait_seconds",
                        "the bounded wait %d stays below the idle timeout %d without progress frames"
                                .formatted(bounded, idle));
            }
        }

        private void worker(JsonNode worker, String pointer, JsonNode profile) {
            var members = Set.of(NAME_ELIGIBLE, NAME_RESTRICTED_MODELS, NAME_ACK_DEADLINE_SECONDS, NAME_ACK_INFORMATIONAL,
                    NAME_SILENCE_GRACE_SECONDS, NAME_IDLE_WAKEUPS);
            object(worker, pointer, NAME_WORKER, members, members);
            var eligible = bool(required(worker, pointer, NAME_ELIGIBLE), pointer + "/eligible", NAME_ELIGIBLE);
            var headless = profile.member(NAME_HEADLESS).orElse(JsonNode.nullValue());
            if (eligible && headless.isNull()) {
                throw invalid(pointer + "/eligible", "an eligible worker host has a headless block");
            }
            var restricted = optional(worker, NAME_RESTRICTED_MODELS);
            if (restricted != null && !restricted.isNull()) {
                stringArray(restricted, pointer + PTR_RESTRICTED_MODELS, NAME_RESTRICTED_MODELS, 0, 64, true);
            }
            var ackDeadline = optional(worker, NAME_ACK_DEADLINE_SECONDS);
            if (ackDeadline != null && !ackDeadline.isNull()) {
                integer(ackDeadline, pointer + PTR_ACK_DEADLINE_SECONDS, NAME_ACK_DEADLINE_SECONDS, 1, 600);
            }
            var informationalValue = informational(worker, pointer);
            var graceValue = grace(worker, pointer);
            var wakeups = optional(worker, NAME_IDLE_WAKEUPS);
            if (wakeups != null && !wakeups.isNull()) {
                integer(wakeups, pointer + "/idle_wakeups", NAME_IDLE_WAKEUPS, 1, 100);
            }
            if (informationalValue) {
                requireDeadlineAndGrace(ackDeadline, graceValue, profile, pointer);
            }
            if (!headless.isNull()) {
                enforcedClasses(headless, restricted, pointer);
            }
        }

        private boolean informational(JsonNode worker, String pointer) {
            var informational = optional(worker, NAME_ACK_INFORMATIONAL);
            return informational != null && !informational.isNull()
                    && bool(informational, pointer + "/ack_informational", NAME_ACK_INFORMATIONAL);
        }

        private Optional<Long> grace(JsonNode worker, String pointer) {
            var grace = optional(worker, NAME_SILENCE_GRACE_SECONDS);
            if (grace == null || grace.isNull()) {
                return Optional.empty();
            }
            return Optional.of(integer(grace, pointer + "/silence_grace_seconds", NAME_SILENCE_GRACE_SECONDS, 5, 600));
        }

        private void requireDeadlineAndGrace(JsonNode ackDeadline, Optional<Long> graceValue, JsonNode profile,
                String pointer) {
            if (ackDeadline == null || ackDeadline.isNull() || graceValue.isEmpty()) {
                throw invalid(pointer + PTR_ACK_DEADLINE_SECONDS,
                        "an informational acknowledgement names its deadline and silence grace");
            }
            var connection = profile.member(NAME_CONNECTION).orElseThrow();
            var boundedNode = connection.member(NAME_BOUNDED_WAIT_SECONDS).orElseThrow();
            var bounded = boundedNode.integerValue().orElseThrow();
            var deadline = ackDeadline.integerValue().orElseThrow();
            if (deadline != bounded + graceValue.get()) {
                throw invalid(pointer + PTR_ACK_DEADLINE_SECONDS,
                        ("with an informational acknowledgement the deadline is the bounded wait %d plus the grace "
                                + "%d, not %d").formatted(bounded, graceValue.get(), deadline));
            }
        }

        private void enforcedClasses(JsonNode headless, JsonNode restricted, String pointer) {
            var enforced = enforcedClass(headless);
            if (enforced && (restricted == null || restricted.isNull())) {
                throw invalid(pointer + PTR_RESTRICTED_MODELS,
                        "an enforced tool set names the models it accepts in restricted_models");
            }
            if (!enforced && restricted != null && !restricted.isNull()) {
                throw invalid(pointer + PTR_RESTRICTED_MODELS,
                        "restricted_models is non-null exactly when a class is enforced");
            }
            if (enforced) {
                var tiers = headless.member(NAME_MODEL_TIERS).orElseThrow();
                for (var tier : tiers.members().values()) {
                    tier.text().ifPresent(model -> {
                        if (!covered(model, restricted)) {
                            throw invalid(pointer + PTR_RESTRICTED_MODELS,
                                    "the tier model '%s' appears in no entry of restricted_models".formatted(model));
                        }
                    });
                }
            }
        }

        private boolean enforcedClass(JsonNode headless) {
            var classes = headless.member(NAME_CLASSES).orElse(JsonNode.nullValue());
            return classes.members().values().stream()
                    .filter(value -> !value.isNull())
                    .map(value -> value.member(NAME_TOOL_SET).flatMap(JsonNode::text).orElse(""))
                    .anyMatch(ENFORCED::equals);
        }

        private boolean covered(String model, JsonNode restricted) {
            if (restricted == null || restricted.isNull()) {
                return false;
            }
            for (var entry : restricted.elements()) {
                var pattern = entry.text().orElse("");
                if ("*".equals(pattern) || pattern.equals(model)) {
                    return true;
                }
                if (pattern.endsWith("/*") && model.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            }
            return false;
        }

        private void detection(JsonNode detection, String pointer) {
            if (detection.isNull()) {
                return;
            }
            var members = Set.of(NAME_PRECEDENCE, NAME_PRIMARY_SIGNALS, NAME_SECONDARY_SIGNALS, NAME_PROCESS_MARKERS,
                    NAME_AI_AGENT_PREFIXES, NAME_REACHES);
            object(detection, pointer, NAME_DETECTION, members, members);
            integer(required(detection, pointer, NAME_PRECEDENCE), pointer + "/precedence", NAME_PRECEDENCE, 1,
                    Integer.MAX_VALUE);
            signals(required(detection, pointer, NAME_PRIMARY_SIGNALS), pointer + "/primary_signals", NAME_PRIMARY_SIGNALS,
                    1);
            signals(required(detection, pointer, NAME_SECONDARY_SIGNALS), pointer + "/secondary_signals",
                    NAME_SECONDARY_SIGNALS, 0);
            var markers = required(detection, pointer, NAME_PROCESS_MARKERS);
            stringArray(markers, pointer + "/process_markers", NAME_PROCESS_MARKERS, 0, 64, true);
            for (var index = 0; index < markers.elements().size(); index++) {
                var marker = markers.elements().get(index).text().orElse("");
                if (!VARIABLE.matcher(marker).matches()) {
                    throw invalid(pointer + "/process_markers/" + index,
                            "not a process marker variable: '%s'".formatted(marker));
                }
            }
            stringArray(required(detection, pointer, NAME_AI_AGENT_PREFIXES), pointer + "/ai_agent_prefixes",
                    NAME_AI_AGENT_PREFIXES, 0, 64, true);
            var reaches = required(detection, pointer, NAME_REACHES);
            var values = list(reaches, pointer + "/reaches", NAME_REACHES, 1, 64);
            var seen = new HashSet<String>();
            for (var index = 0; index < values.size(); index++) {
                var value = enumValue(values.get(index), pointer + "/reaches/" + index, NAME_REACHES,
                        Set.of("command", "serve"));
                if (!seen.add(value)) {
                    throw invalid(pointer + "/reaches/" + index, "the value '%s' occurs twice".formatted(value));
                }
            }
        }

        private void signals(JsonNode signals, String pointer, String name, int min) {
            var elements = list(signals, pointer, name, min, 64);
            for (var index = 0; index < elements.size(); index++) {
                var signalPointer = pointer + "/" + index;
                object(elements.get(index), signalPointer, "signal", Set.of(NAME_VARIABLE, NAME_EQUALS),
                        Set.of(NAME_VARIABLE, NAME_EQUALS));
                var variable = text(required(elements.get(index), signalPointer, NAME_VARIABLE), signalPointer + PTR_VARIABLE,
                        NAME_VARIABLE, 64);
                if (!VARIABLE.matcher(variable).matches()) {
                    throw invalid(signalPointer + PTR_VARIABLE, "not a signal variable: '%s'".formatted(variable));
                }
                var equals = required(elements.get(index), signalPointer, NAME_EQUALS);
                if (!equals.isNull()) {
                    text(equals, signalPointer + "/equals", NAME_EQUALS, 256);
                }
            }
        }

        private void identity(JsonNode identity, String pointer) {
            var members = Set.of(NAME_VERSION_PROBE, NAME_VERSION_PATTERN);
            object(identity, pointer, NAME_IDENTITY, members, members);
            var probe = required(identity, pointer, NAME_VERSION_PROBE);
            var pattern = required(identity, pointer, NAME_VERSION_PATTERN);
            if (probe.isNull() != pattern.isNull()) {
                throw invalid(pointer, "version_probe and version_pattern are both null or both present");
            }
            if (!probe.isNull()) {
                launchArguments(probe, pointer + "/version_probe", NAME_VERSION_PROBE, SLOTS);
            }
            if (!pattern.isNull()) {
                var regex = text(pattern, pointer + "/version_pattern", NAME_VERSION_PATTERN, 128);
                patternRule(regex, pointer + "/version_pattern");
            }
        }

        // ---- hardening rules ---------------------------------------------------------------------------------

        private void stringRule(String text, String pointer, boolean modelReadable) {
            if (!Normalizer.normalize(text, Normalizer.Form.NFC).equals(text)) {
                throw invalid(pointer, "not in Unicode normal form NFC");
            }
            for (var index = 0; index < text.length(); index++) {
                var character = text.charAt(index);
                if (Character.isISOControl(character)) {
                    throw invalid(pointer, "a control character is not allowed");
                }
                if (character == '\n' || character == '\r') {
                    throw invalid(pointer, "a string is a single line");
                }
                if (modelReadable && !Character.isLetterOrDigit(character)
                        && MODEL_READABLE_CHARACTERS.indexOf(character) < 0) {
                    throw invalid(pointer, "the character '%s' cannot be read by a model".formatted(character));
                }
            }
            for (var placeholder = PLACEHOLDER.matcher(text); placeholder.find(); ) {
                if (!SLOTS.contains(placeholder.group(1)) && !NAME_SESSION_ID.equals(placeholder.group(1))
                        && !"uri".equals(placeholder.group(1))) {
                    throw invalid(pointer, "not a closed placeholder: '{{%s}}'".formatted(placeholder.group(1)));
                }
            }
        }

        private void toolName(String name, String pointer) {
            if (!TOOL_NAME.matcher(name).matches()) {
                throw invalid(pointer, "not a tool name: '%s'".formatted(name));
            }
        }

        private void launchArguments(JsonNode arguments, String pointer, String name, Set<String> slots) {
            launchArguments(arguments, pointer, name, slots, 1);
        }

        private void launchArguments(JsonNode arguments, String pointer, String name, Set<String> slots, int min) {
            var elements = list(arguments, pointer, name, min, 128);
            for (var index = 0; index < elements.size(); index++) {
                var item = text(elements.get(index), pointer + "/" + index, name + "[" + index + "]", 1024);
                if (item.isEmpty()) {
                    throw invalid(pointer + "/" + index, "an argument is not empty");
                }
                launchItem(item, pointer + "/" + index, slots);
            }
        }

        private void launchItem(String item, String pointer, Set<String> slots) {
            if (item.contains("://") || item.startsWith("http:") || item.startsWith("https:")) {
                throw invalid(pointer, "an argument names a URL: '%s'".formatted(item));
            }
            if (item.startsWith("/")) {
                throw invalid(pointer, "an argument names an absolute path: '%s'".formatted(item));
            }
            if (item.startsWith("~/")) {
                throw invalid(pointer, "an argument names a home path: '%s'".formatted(item));
            }
            for (var placeholder = PLACEHOLDER.matcher(item); placeholder.find(); ) {
                if (!slots.contains(placeholder.group(1))) {
                    throw invalid(pointer, "not a closed slot: '{{%s}}'".formatted(placeholder.group(1)));
                }
            }
        }

        private void relativePath(String path, String pointer, String name) {
            if (path.startsWith("/") || path.startsWith("~/")) {
                throw invalid(pointer, "%s is relative: '%s'".formatted(name, path));
            }
            noTraversal(path, pointer, name);
        }

        private void noTraversal(String path, String pointer, String name) {
            var segments = List.of(path.split("/", -1));
            if (segments.contains("..")) {
                throw invalid(pointer, "%s does not traverse with '..': '%s'".formatted(name, path));
            }
            if (segments.contains(".") || path.contains("//") || path.endsWith("/")) {
                throw invalid(pointer, "%s is normalized: '%s'".formatted(name, path));
            }
        }

        private boolean covers(String path, String managed) {
            var normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            var normalizedManaged = managed.endsWith("/") ? managed.substring(0, managed.length() - 1) : managed;
            return normalizedPath.equals(normalizedManaged) || normalizedManaged.startsWith(normalizedPath + "/")
                    || normalizedPath.startsWith(normalizedManaged + "/");
        }

        private void patternRule(String regex, String pointer) {
            if (regex.length() > 128) {
                throw invalid(pointer, "a pattern is at most 128 characters");
            }
            var groups = 0;
            var index = 0;
            while (index < regex.length()) {
                var character = regex.charAt(index);
                if (character == '\\') {
                    var escaped = index + 1;
                    if (escaped < regex.length() && Character.isDigit(regex.charAt(escaped))) {
                        throw invalid(pointer, "a pattern uses no backreference");
                    }
                    index += 2;
                } else {
                    if (character == '(') {
                        groups += captureGroup(regex.substring(index + 1), pointer);
                    }
                    index++;
                }
            }
            if (groups != 1) {
                throw invalid(pointer, "a pattern has exactly one capture group, not %d".formatted(groups));
            }
        }

        /** @return 1 for a capturing group, 0 for a non-capturing or look-around group */
        private int captureGroup(String rest, String pointer) {
            if (rest.startsWith("?=") || rest.startsWith("?!") || rest.startsWith("?<=") || rest.startsWith("?<!")) {
                throw invalid(pointer, "a pattern uses no look-around");
            }
            return rest.startsWith("?") ? 0 : 1;
        }

        private boolean jobEnvironment(String name) {
            return JOB_ENVIRONMENT.contains(name)
                    || JOB_ENVIRONMENT_PREFIXES.stream().anyMatch(name::startsWith);
        }

        private boolean credentialName(String name) {
            var upper = name.toUpperCase(Locale.ROOT);
            return CREDENTIAL_MARKERS.stream().anyMatch(upper::contains);
        }

        // ---- shape helpers -----------------------------------------------------------------------------------

        private void object(JsonNode node, String pointer, String name, Set<String> required, Set<String> allowed) {
            if (!node.isObject()) {
                throw invalid(pointer, "'%s' must be an object".formatted(name));
            }
            for (var member : node.members().keySet()) {
                if (!allowed.contains(member)) {
                    throw invalid(pointer + "/" + escape(member),
                            "the member '%s' is not declared by '%s'".formatted(member, name));
                }
            }
            for (var member : required) {
                if (!node.members().containsKey(member)) {
                    throw invalid(pointer + "/" + escape(member),
                            "the member '%s' of '%s' is missing".formatted(member, name));
                }
            }
        }

        private void object(JsonNode node, String pointer, String name) {
            if (!node.isObject()) {
                throw invalid(pointer, "'%s' must be an object".formatted(name));
            }
        }

        private JsonNode required(JsonNode node, String pointer, String member) {
            return node.member(member).orElseThrow(() -> invalid(pointer + "/" + escape(member),
                    "the member '%s' is missing".formatted(member)));
        }

        private JsonNode optional(JsonNode node, String member) {
            return node.member(member).orElse(null);
        }

        private String text(JsonNode node, String pointer, String name, int max) {
            var value = string(node, pointer, name, max);
            stringRule(value, pointer, false);
            return value;
        }

        private String string(JsonNode node, String pointer, String name, int max) {
            if (!node.isString()) {
                throw invalid(pointer, "'%s' must be a string".formatted(name));
            }
            var value = node.text().orElseThrow();
            if (value.length() > max) {
                throw invalid(pointer, "'%s' is at most %d characters".formatted(name, max));
            }
            return value;
        }

        private long integer(JsonNode node, String pointer, String name, long min, long max) {
            if (!node.isInteger()) {
                throw invalid(pointer, "'%s' must be an integer".formatted(name));
            }
            var value = node.integerValue().orElseThrow();
            if (value < min || value > max) {
                throw invalid(pointer, "'%s' must be between %d and %d, not %d".formatted(name, min, max, value));
            }
            return value;
        }

        private boolean bool(JsonNode node, String pointer, String name) {
            if (!node.isBoolean()) {
                throw invalid(pointer, "'%s' must be a boolean".formatted(name));
            }
            return node.booleanValue().orElseThrow();
        }

        private String enumValue(JsonNode node, String pointer, String name, Set<String> values) {
            var value = string(node, pointer, name, 256);
            if (!values.contains(value)) {
                throw invalid(pointer, "'%s' has the undeclared value '%s'".formatted(name, value));
            }
            return value;
        }

        private List<JsonNode> list(JsonNode node, String pointer, String name, int min, int max) {
            if (!node.isArray()) {
                throw invalid(pointer, "'%s' must be an array".formatted(name));
            }
            var elements = node.elements();
            if (elements.size() < min || elements.size() > max) {
                throw invalid(pointer, "'%s' has between %d and %d items, not %d".formatted(name, min, max,
                        elements.size()));
            }
            return elements;
        }

        private void stringArray(JsonNode node, String pointer, String name, int min, int max, boolean unique) {
            var elements = list(node, pointer, name, min, max);
            var seen = new HashSet<String>();
            for (var index = 0; index < elements.size(); index++) {
                var value = text(elements.get(index), pointer + "/" + index, name + "[" + index + "]", 1024);
                if (value.isEmpty()) {
                    throw invalid(pointer + "/" + index, "an item of '%s' is not empty".formatted(name));
                }
                if (unique && !seen.add(value)) {
                    throw invalid(pointer + "/" + index, "the item '%s' occurs twice".formatted(value));
                }
            }
        }

        private static String escape(String member) {
            return member.replace("~", "~0").replace("/", "~1");
        }

        private ProfileRefusal invalid(String pointer, String detail) {
            return ProfileRefusal.invalid(document, pointer, detail);
        }
    }
}
