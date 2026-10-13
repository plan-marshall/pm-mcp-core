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

import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The enrolment of one project (PM-EXEC-2, PM-MIG-1, PM-MIG-2, PM-IMPL-2): the operator-authorized record of what the
 * runtime may run for the project, kept in {@code enrolments/<project_id>.json} and read and written by
 * {@link EnrolmentCodec}.
 * <p>
 * Every field of the closed schema is typed, every name with a fixed set of values is a closed enum, and every value
 * is validated here, so an instance that exists is one the schema admits. The record holds the project root, the build
 * wrappers with their pinned files, the pinned host tools, the canonical and discovery command templates, the provider
 * verification overrides, the approved project scripts, the language servers, the operator budgets, the write paths,
 * the captured build environment, the container-engine opt-in, the {@code pilot} flag the lifecycle gate reads
 * (PM-MIG-1) and the instant of a release.
 * <p>
 * The maps are kept in a stable order (the declaration order of an enum key, the natural order of a string key), so
 * {@link EnrolmentCodec#write(ProjectEnrolment)} writes the same enrolment as the same bytes; those bytes are what the
 * integrity HMAC is computed over (PM-EXEC-2, PM-SEC-5). What the schema only describes and the enrolment verb decides
 * (shell metacharacters in an argument vector, write paths below the base directory, refused environment names) is
 * not checked here. The runtime's enrolment policy ({@code EnrolmentPolicy} of pm-runtime) refuses, before an
 * enrolment is signed, a template or provider argument vector with a placeholder other than {@code {module}} or with
 * {@code {module}} outside its module-argument group, a write path that is, lies below or contains the base directory,
 * and a build environment name that is a credential variable or one of {@code DBUS_SESSION_BUS_ADDRESS},
 * {@code XDG_RUNTIME_DIR}, {@code MVND_DAEMON_STORAGE}. The shell-metacharacter and forbidden-flag rules apply to
 * what a caller supplies to a job and are applied at job time by the runtime's argument check, the last stage of its
 * verify-not-resolve pipeline.
 *
 * @param projectId         the enrolment identifier; equals the name of the file
 * @param projectRoot       the canonical absolute path of the repository main checkout
 * @param enrolledAt        the instant of the enrolment
 * @param pilot             whether the project is a pilot, admitted by a closed lifecycle gate
 * @param containerEngine   the container engine the project's jobs may use; {@code null} for none
 * @param releasedAt        the instant the project was released; {@code null} unless released
 * @param wrappers          the build wrappers with their pinned files
 * @param hostTools         the pinned host tools
 * @param build             the canonical and discovery command templates
 * @param providers         the verification override per CLI provider
 * @param projectScripts    the approved project scripts by their id
 * @param languageServers   the language servers by their language id
 * @param commandBudgets    the operator budget in seconds per budget key
 * @param writePaths        the build toolchain cache directories added to the write set of the project's jobs
 * @param buildEnvironment  the environment values captured at enrolment, by variable name
 * @since 0.1
 */
public record ProjectEnrolment(
String projectId,
String projectRoot,
Instant enrolledAt,
boolean pilot,
ContainerEngine containerEngine,
Instant releasedAt,
List<Wrapper> wrappers,
List<HostTool> hostTools,
Build build,
Map<Provider, ProviderVerification> providers,
SortedMap<String, ProjectScript> projectScripts,
SortedMap<String, LanguageServer> languageServers,
SortedMap<String, Long> commandBudgets,
List<String> writePaths,
SortedMap<String, String> buildEnvironment) {

    /** The format version of an enrolment file that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern VERB = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final Pattern PROJECT_SCRIPT_ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern LANGUAGE_ID = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    /** The grammar of a budget key; its longest match has the 155 characters the schema allows at most. */
    private static final Pattern BUDGET_KEY = Pattern
            .compile("[a-z][a-z0-9_]{0,31}:[a-z][a-z0-9.-]{0,63}(@[A-Za-z0-9][A-Za-z0-9_.-]{0,56})?");
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Z_][A-Z0-9_]{0,127}");
    private static final long MIN_SCRIPT_TIMEOUT_SECONDS = 10;
    private static final String FIELD_SHA256 = "sha256";

    /**
     * Copies every collection into its stable, unmodifiable form and validates every value against the schema.
     *
     * @throws NullPointerException     if a required component or an element is {@code null}
     * @throws IllegalArgumentException if a value violates the schema: an identifier, a key or a name outside its
     *                                  pattern, a project root that is not a normalized absolute path, an empty
     *                                  string where the schema requires one, a budget below 1
     */
    public ProjectEnrolment {
        LockKey.identifier(projectId, "project_id");
        requireAbsolutePath(projectRoot, "project_root");
        Objects.requireNonNull(enrolledAt, "enrolled_at");
        wrappers = List.copyOf(Objects.requireNonNull(wrappers, "wrappers"));
        hostTools = List.copyOf(Objects.requireNonNull(hostTools, "host_tools"));
        Objects.requireNonNull(build, "build");
        providers = enumMap(Provider.class, providers, "providers");
        projectScripts = sortedMap(projectScripts, "project_scripts", PROJECT_SCRIPT_ID);
        languageServers = sortedMap(languageServers, "language_servers", LANGUAGE_ID);
        commandBudgets = sortedMap(commandBudgets, "command_budgets", BUDGET_KEY);
        for (var budget : commandBudgets.entrySet()) {
            if (budget.getValue() < 1) {
                throw new IllegalArgumentException(
                        "A budget is at least 1 second: %s = %d".formatted(budget.getKey(), budget.getValue()));
            }
        }
        writePaths = nonEmptyStrings(writePaths, "write_paths");
        buildEnvironment = sortedMap(buildEnvironment, "build_environment", ENVIRONMENT_NAME);
    }

    /** @return whether the project was released and not enrolled again since */
    public boolean isReleased() {
        return releasedAt != null;
    }

    /**
     * The container engine a project may opt in to; {@code null} in the record means none.
     *
     * @since 0.1
     */
    public enum ContainerEngine {
        /** The Docker engine of the host. */
        DOCKER("docker");

        private final String wireName;

        ContainerEngine(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in an enrolment file */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in an enrolment file
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ContainerEngine> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The name of a build wrapper.
     *
     * @since 0.1
     */
    public enum WrapperName {
        /** The Maven wrapper. */
        MVNW("mvnw"),
        /** The Gradle wrapper. */
        GRADLEW("gradlew"),
        /** The Python project wrapper, run by an interpreter. */
        PW("pw");

        private final String wireName;

        WrapperName(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in an enrolment file */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in an enrolment file
         * @return the value of that name; empty if no value has it
         */
        public static Optional<WrapperName> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The name of a pinned host tool.
     *
     * @since 0.1
     */
    public enum HostToolName {
        /** git. */
        GIT("git"),
        /** The GitHub CLI. */
        GH("gh"),
        /** The GitLab CLI. */
        GLAB("glab"),
        /** The Python 3 interpreter. */
        PYTHON3("python3"),
        /** The Ruby interpreter. */
        RUBY("ruby"),
        /** npm. */
        NPM("npm"),
        /** npx. */
        NPX("npx"),
        /** Asciidoctor, a Ruby script. */
        ASCIIDOCTOR("asciidoctor");

        private final String wireName;

        HostToolName(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in an enrolment file */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in an enrolment file
         * @return the value of that name; empty if no value has it
         */
        public static Optional<HostToolName> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * An enrolled host tool that runs a script: an interpreter-based wrapper, a host tool that is a script, a project
     * script. Which interpreter a place admits is checked by the record of that place.
     *
     * @since 0.1
     */
    public enum Interpreter {
        /** The Python 3 interpreter. */
        PYTHON3("python3"),
        /** The Ruby interpreter. */
        RUBY("ruby");

        private final String wireName;

        Interpreter(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in an enrolment file */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in an enrolment file
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Interpreter> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * A build system with command templates.
     *
     * @since 0.1
     */
    public enum BuildSystem {
        /** Maven. */
        MAVEN("maven"),
        /** Gradle. */
        GRADLE("gradle"),
        /** npm. */
        NPM("npm"),
        /** A Python project. */
        PYPROJECT("pyproject");

        private final String wireName;

        BuildSystem(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in an enrolment file */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in an enrolment file
         * @return the value of that name; empty if no value has it
         */
        public static Optional<BuildSystem> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * A CLI provider whose compiled verification an enrolment may replace. A constant is named after the CLI the
     * provider runs, as {@link HostToolName} names it, so no member of {@code pm-core} names a provider (PM-IMPL-1);
     * the wire name in the enrolment file is the provider id.
     *
     * @since 0.1
     */
    public enum Provider {
        /** git. */
        GIT("git"),
        /** The provider of wire name {@code github}, run through the CLI {@code gh}. */
        GH("github"),
        /** The provider of wire name {@code gitlab}, run through the CLI {@code glab}. */
        GLAB("gitlab");

        private final String wireName;

        Provider(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in an enrolment file */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in an enrolment file
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Provider> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The kind of worker a project script serves.
     *
     * @since 0.1
     */
    public enum WorkerKind {
        /** A finalize step. */
        FINALIZE_STEP("finalize_step"),
        /** A recipe harvester. */
        RECIPE_HARVESTER("recipe_harvester"),
        /** A build log parser. */
        LOG_PARSER("log_parser"),
        /** A module discovery. */
        MODULE_DISCOVERY("module_discovery"),
        /** An edge resolver. */
        EDGE_RESOLVER("edge_resolver");

        private final String wireName;

        WorkerKind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in an enrolment file */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in an enrolment file
         * @return the value of that name; empty if no value has it
         */
        public static Optional<WorkerKind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * A build wrapper of the project and the files that decide which tool it runs with which flags.
     *
     * @param name         the wrapper
     * @param relativePath the path of the wrapper in the repository
     * @param sha256       the SHA-256 of the wrapper, lowercase hex
     * @param pinnedFiles  the pinned files of the wrapper
     * @param interpreter  the interpreter that runs the wrapper: {@link Interpreter#PYTHON3} for {@code pw},
     *                     {@code null} for every other wrapper
     * @since 0.1
     */
    public record Wrapper(WrapperName name, String relativePath, String sha256, List<PinnedFile> pinnedFiles,
    Interpreter interpreter) {

        /**
         * @throws NullPointerException     if a component other than the interpreter is {@code null}
         * @throws IllegalArgumentException if the path is empty, the SHA-256 is not lowercase hex, or the interpreter
         *                                  is not the one of the wrapper
         */
        public Wrapper {
            Objects.requireNonNull(name, "name");
            nonEmpty(relativePath, "relative_path");
            requireSha256(sha256, FIELD_SHA256);
            pinnedFiles = List.copyOf(Objects.requireNonNull(pinnedFiles, "pinned_files"));
            var required = name == WrapperName.PW ? Interpreter.PYTHON3 : null;
            if (interpreter != required) {
                throw new IllegalArgumentException("The wrapper %s is run by the interpreter %s, not %s".formatted(
                        name.wireName(), wireName(required), wireName(interpreter)));
            }
        }

        private static String wireName(Interpreter interpreter) {
            return interpreter == null ? "none" : interpreter.wireName();
        }
    }

    /**
     * A file that decides which tool a wrapper runs and with which flags.
     *
     * @param relativePath the path of the file in the repository
     * @param sha256       the SHA-256 of the file, lowercase hex; {@code null} if the file must stay absent
     * @since 0.1
     */
    public record PinnedFile(String relativePath, String sha256) {

        /**
         * @throws NullPointerException     if the path is {@code null}
         * @throws IllegalArgumentException if the path is empty or the SHA-256 is not lowercase hex
         */
        public PinnedFile {
            nonEmpty(relativePath, "relative_path");
            if (sha256 != null) {
                requireSha256(sha256, FIELD_SHA256);
            }
        }

        /** @return whether the file must stay absent */
        public boolean mustBeAbsent() {
            return sha256 == null;
        }
    }

    /**
     * A pinned host tool.
     *
     * @param name           the tool
     * @param executablePath the resolved path of the executable
     * @param sha256         the SHA-256 of the executable, lowercase hex
     * @param interpreter    the interpreter that runs the tool if it is a script ({@link Interpreter#RUBY});
     *                       {@code null} for a binary
     * @param version        the version printed at enrolment; {@code null} if none was printed
     * @since 0.1
     */
    public record HostTool(HostToolName name, String executablePath, String sha256, Interpreter interpreter,
    String version) {

        /**
         * @throws NullPointerException     if the name, the path or the SHA-256 is {@code null}
         * @throws IllegalArgumentException if the path is empty, the SHA-256 is not lowercase hex, or the interpreter
         *                                  is not Ruby
         */
        public HostTool {
            Objects.requireNonNull(name, "name");
            nonEmpty(executablePath, "executable_path");
            requireSha256(sha256, FIELD_SHA256);
            if (interpreter != null && interpreter != Interpreter.RUBY) {
                throw new IllegalArgumentException(
                        "A host tool is run by the interpreter ruby or none, not " + interpreter.wireName());
            }
        }
    }

    /**
     * The command templates of the build jobs: per build system the argument vector of each canonical verb and of
     * each discovery template. An absent entry means the compiled default.
     *
     * @param canonicalTemplates the argument vector per build system and canonical verb
     * @param discoveryTemplates the argument vector per build system and discovery template name
     * @since 0.1
     */
    public record Build(Map<BuildSystem, SortedMap<String, Argv>> canonicalTemplates,
    Map<BuildSystem, SortedMap<String, Argv>> discoveryTemplates) {

        /**
         * @throws NullPointerException     if a map, a key or a vector is {@code null}
         * @throws IllegalArgumentException if a verb or a template name is outside its pattern
         */
        public Build {
            canonicalTemplates = templates(canonicalTemplates, "canonical_templates");
            discoveryTemplates = templates(discoveryTemplates, "discovery_templates");
        }

        /** @return the build without an enrolled template: every job runs the compiled default */
        public static Build defaults() {
            return new Build(Map.of(), Map.of());
        }

        private static Map<BuildSystem, SortedMap<String, Argv>> templates(
                Map<BuildSystem, SortedMap<String, Argv>> templates, String name) {
            var copy = new EnumMap<BuildSystem, SortedMap<String, Argv>>(BuildSystem.class);
            for (var entry : Objects.requireNonNull(templates, name).entrySet()) {
                copy.put(Objects.requireNonNull(entry.getKey(), name),
                        sortedMap(entry.getValue(), name + "." + entry.getKey().wireName(), VERB));
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    /**
     * The argument vector that replaces the compiled verification of a CLI provider.
     *
     * @param verifyArgv the argument vector of the verification
     * @since 0.1
     */
    public record ProviderVerification(Argv verifyArgv) {

        /** @throws NullPointerException if the vector is {@code null} */
        public ProviderVerification {
            Objects.requireNonNull(verifyArgv, "verify_argv");
        }
    }

    /**
     * An argument vector: literal arguments and at most one module-argument group, the only place of the typed
     * placeholder {@code {module}}, which a whole-tree job omits. The placeholder is validated at job time.
     *
     * @param elements the elements of the vector, in order; not empty
     * @since 0.1
     */
    public record Argv(List<Element> elements) {

        /**
         * @throws NullPointerException     if the list or an element is {@code null}
         * @throws IllegalArgumentException if the vector is empty or has more than one module-argument group
         */
        public Argv {
            elements = List.copyOf(Objects.requireNonNull(elements, "argv"));
            if (elements.isEmpty()) {
                throw new IllegalArgumentException("An argument vector has at least one element");
            }
            if (elements.stream().filter(ModuleGroup.class::isInstance).count() > 1) {
                throw new IllegalArgumentException("An argument vector has at most one module-argument group");
            }
        }

        /**
         * @param arguments literal arguments
         * @return the vector of these literal arguments
         */
        public static Argv of(String... arguments) {
            return new Argv(Arrays.stream(arguments).<Element>map(Literal::new).toList());
        }

        /**
         * One element of an argument vector.
         *
         * @since 0.1
         */
        public sealed interface Element permits Literal, ModuleGroup {
        }

        /**
         * A literal argument.
         *
         * @param value the argument; not empty
         * @since 0.1
         */
        public record Literal(String value) implements Element {

            /**
             * @throws NullPointerException     if the value is {@code null}
             * @throws IllegalArgumentException if the value is empty
             */
            public Literal {
                nonEmpty(value, "argument");
            }
        }

        /**
         * The module-argument group: the arguments that name the module of a job, omitted by a whole-tree job.
         *
         * @param arguments the arguments of the group; not empty, none empty
         * @since 0.1
         */
        public record ModuleGroup(List<String> arguments) implements Element {

            /**
             * @throws NullPointerException     if the list or an argument is {@code null}
             * @throws IllegalArgumentException if the group or an argument is empty
             */
            public ModuleGroup {
                arguments = nonEmptyStrings(arguments, "module-argument group");
                if (arguments.isEmpty()) {
                    throw new IllegalArgumentException("A module-argument group has at least one argument");
                }
            }
        }
    }

    /**
     * An approved out-of-process project script.
     *
     * @param scriptPath        the repository-relative path of the script
     * @param sha256            the SHA-256 of the script, lowercase hex
     * @param interpreter       the interpreter that runs the script, {@link Interpreter#PYTHON3}
     * @param binding           what the script serves
     * @param argumentSchema    the schema of the script's arguments
     * @param maxTimeoutSeconds the budget of every run in seconds, at least 10
     * @since 0.1
     */
    public record ProjectScript(String scriptPath, String sha256, Interpreter interpreter, Binding binding,
    ArgumentSchema argumentSchema, long maxTimeoutSeconds) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the path is empty, the SHA-256 is not lowercase hex, the interpreter is
         *                                  not Python 3, or the budget is below 10 seconds
         */
        public ProjectScript {
            nonEmpty(scriptPath, "script_path");
            requireSha256(sha256, FIELD_SHA256);
            if (Objects.requireNonNull(interpreter, "interpreter") != Interpreter.PYTHON3) {
                throw new IllegalArgumentException(
                        "A project script is run by python3, not " + interpreter.wireName());
            }
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(argumentSchema, "argument_schema");
            if (maxTimeoutSeconds < MIN_SCRIPT_TIMEOUT_SECONDS) {
                throw new IllegalArgumentException("The budget of a project script is at least %d seconds: %d"
                        .formatted(MIN_SCRIPT_TIMEOUT_SECONDS, maxTimeoutSeconds));
            }
        }
    }

    /**
     * What a project script serves.
     *
     * @param workerKind the kind of worker
     * @param target     the step id, recipe key or build system the script serves
     * @since 0.1
     */
    public record Binding(WorkerKind workerKind, String target) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the target is empty
         */
        public Binding {
            Objects.requireNonNull(workerKind, "worker_kind");
            nonEmpty(target, "target");
        }
    }

    /**
     * The schema of a project script's arguments, in the subset of JSON Schema (draft 2020-12) an enrolment admits:
     * {@code type}, {@code properties}, {@code required}, {@code additionalProperties: false}, {@code enum},
     * {@code pattern}, {@code minimum}, {@code maximum} and {@code maxLength}. A keyword the schema does not use is
     * {@code null} (or {@code false} for {@code closed}), so what is read is written again as it was.
     *
     * @param type       the JSON type of the value; {@code null} if not constrained
     * @param properties the schemas of the properties of an object by their names; {@code null} if absent
     * @param required   the names of the required properties; {@code null} if absent
     * @param closed     whether the schema declares {@code additionalProperties: false}
     * @param enumValues the admitted values; {@code null} if absent
     * @param pattern    the pattern a string matches; {@code null} if absent
     * @param minimum    the least admitted number; {@code null} if absent
     * @param maximum    the greatest admitted number; {@code null} if absent
     * @param maxLength  the greatest admitted length of a string, not negative; {@code null} if absent
     * @since 0.1
     */
    public record ArgumentSchema(SchemaType type, SortedMap<String, ArgumentSchema> properties,
    List<String> required, boolean closed, List<EnumValue> enumValues, String pattern, BigDecimal minimum,
    BigDecimal maximum, Long maxLength) {

        /**
         * @throws NullPointerException     if a key or an element is {@code null}
         * @throws IllegalArgumentException if the maximal length is negative
         */
        public ArgumentSchema {
            if (properties != null) {
                properties = sortedMap(properties, "properties", null);
            }
            if (required != null) {
                required = List.copyOf(required);
            }
            if (enumValues != null) {
                enumValues = List.copyOf(enumValues);
            }
            if (maxLength != null && maxLength < 0) {
                throw new IllegalArgumentException("maxLength is not negative: " + maxLength);
            }
        }

        /** @return the schema that constrains nothing, {@code {}} */
        public static ArgumentSchema any() {
            return new ArgumentSchema(null, null, null, false, null, null, null, null, null);
        }
    }

    /**
     * A JSON type an argument schema names.
     *
     * @since 0.1
     */
    public enum SchemaType {
        /** A JSON object. */
        OBJECT("object"),
        /** A JSON array. */
        ARRAY("array"),
        /** A JSON string. */
        STRING("string"),
        /** A JSON number without a fraction. */
        INTEGER("integer"),
        /** A JSON number. */
        NUMBER("number"),
        /** A JSON boolean. */
        BOOLEAN("boolean"),
        /** The JSON null. */
        NULL("null");

        private final String wireName;

        SchemaType(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in an enrolment file */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in an enrolment file
         * @return the value of that name; empty if no value has it
         */
        public static Optional<SchemaType> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * A value of the {@code enum} keyword of an argument schema: a JSON string, number or boolean.
     *
     * @since 0.1
     */
    public sealed interface EnumValue permits TextValue, NumberValue, BooleanValue {
    }

    /**
     * A string value of an {@code enum}.
     *
     * @param value the string
     * @since 0.1
     */
    public record TextValue(String value) implements EnumValue {

        /** @throws NullPointerException if the value is {@code null} */
        public TextValue {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A number value of an {@code enum}.
     *
     * @param value the number
     * @since 0.1
     */
    public record NumberValue(BigDecimal value) implements EnumValue {

        /** @throws NullPointerException if the value is {@code null} */
        public NumberValue {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A boolean value of an {@code enum}.
     *
     * @param value the boolean
     * @since 0.1
     */
    public record BooleanValue(boolean value) implements EnumValue {
    }

    /**
     * A language server of the project.
     *
     * @param executablePath the path of the server's executable
     * @param sha256         the SHA-256 of the executable, lowercase hex
     * @param args           the arguments the server is started with
     * @since 0.1
     */
    public record LanguageServer(String executablePath, String sha256, List<String> args) {

        /**
         * @throws NullPointerException     if a component or an argument is {@code null}
         * @throws IllegalArgumentException if the path is empty or the SHA-256 is not lowercase hex
         */
        public LanguageServer {
            nonEmpty(executablePath, "executable_path");
            requireSha256(sha256, FIELD_SHA256);
            args = List.copyOf(Objects.requireNonNull(args, "args"));
        }
    }

    private static void requireAbsolutePath(String value, String name) {
        nonEmpty(value, name);
        try {
            var path = Path.of(value);
            if (path.isAbsolute() && path.normalize().toString().equals(value)) {
                return;
            }
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("%s is not a path: %s".formatted(name, value), e);
        }
        throw new IllegalArgumentException("%s is a normalized absolute path: %s".formatted(name, value));
    }

    private static void nonEmpty(String value, String name) {
        if (Objects.requireNonNull(value, name).isEmpty()) {
            throw new IllegalArgumentException(name + " is not empty");
        }
    }

    private static void requireSha256(String value, String name) {
        if (!SHA_256.matcher(Objects.requireNonNull(value, name)).matches()) {
            throw new IllegalArgumentException("%s is a SHA-256 in lowercase hex: %s".formatted(name, value));
        }
    }

    private static List<String> nonEmptyStrings(List<String> values, String name) {
        var copy = List.copyOf(Objects.requireNonNull(values, name));
        copy.forEach(value -> nonEmpty(value, name + " entry"));
        return copy;
    }

    private static <K extends Enum<K>, V> Map<K, V> enumMap(Class<K> type, Map<K, V> map, String name) {
        var copy = new EnumMap<K, V>(type);
        for (var entry : Objects.requireNonNull(map, name).entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), name), Objects.requireNonNull(entry.getValue(), name));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static <V> SortedMap<String, V> sortedMap(Map<String, V> map, String name, Pattern keys) {
        var copy = new TreeMap<String, V>();
        for (var entry : Objects.requireNonNull(map, name).entrySet()) {
            var key = Objects.requireNonNull(entry.getKey(), name);
            if (keys != null && !keys.matcher(key).matches()) {
                throw new IllegalArgumentException("A key of %s does not match %s: %s".formatted(name, keys, key));
            }
            copy.put(key, Objects.requireNonNull(entry.getValue(), name));
        }
        return Collections.unmodifiableSortedMap(copy);
    }
}
