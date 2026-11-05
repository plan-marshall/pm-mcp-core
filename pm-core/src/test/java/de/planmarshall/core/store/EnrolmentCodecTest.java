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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.ProjectEnrolment.ArgumentSchema;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.BooleanValue;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;
import de.planmarshall.core.store.ProjectEnrolment.ContainerEngine;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.Interpreter;
import de.planmarshall.core.store.ProjectEnrolment.NumberValue;
import de.planmarshall.core.store.ProjectEnrolment.Provider;
import de.planmarshall.core.store.ProjectEnrolment.SchemaType;
import de.planmarshall.core.store.ProjectEnrolment.TextValue;
import de.planmarshall.core.store.ProjectEnrolment.WorkerKind;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The enrolment file of a project and its integrity sidecar (PM-EXEC-2, PM-MIG-2, PM-IMPL-2): one format version, a
 * schema closed at every level of nesting, and one canonical byte sequence per enrolment, the bytes the integrity HMAC
 * is computed over.
 */
// Every datum in this class is a contract: the wire documents, their values, fragments, wire names and the boundary key.
@DisplayName("Enrolment codec")
class EnrolmentCodecTest {

    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final String SHA_C = "c".repeat(64);
    private static final String SHA_D = "d".repeat(64);
    private static final String SHA_E = "e".repeat(64);
    private static final String SHA_F = "f".repeat(64);
    private static final String SHA_0 = "0".repeat(64);

    private static final String FULL = "{\"format_version\":1,\"project_id\":\"pm-mcp-core\","
            + "\"project_root\":\"/home/dev/git/pm-mcp-core\",\"enrolled_at\":\"2026-10-11T08:00:00Z\","
            + "\"pilot\":true,\"container_engine\":\"docker\",\"released_at\":null,"
            + "\"wrappers\":[{\"name\":\"mvnw\",\"relative_path\":\"mvnw\",\"sha256\":\"" + SHA_A + "\","
            + "\"pinned_files\":[{\"relative_path\":\".mvn/wrapper/maven-wrapper.properties\",\"sha256\":\"" + SHA_B
            + "\"},{\"relative_path\":\".mvn/jvm.config\",\"sha256\":null}],\"interpreter\":null},"
            + "{\"name\":\"pw\",\"relative_path\":\"pw\",\"sha256\":\"" + SHA_C
            + "\",\"pinned_files\":[],\"interpreter\":\"python3\"}],"
            + "\"host_tools\":[{\"name\":\"git\",\"executable_path\":\"/usr/bin/git\",\"sha256\":\"" + SHA_D
            + "\",\"interpreter\":null,\"version\":\"2.47.0\"},{\"name\":\"asciidoctor\","
            + "\"executable_path\":\"/usr/local/bin/asciidoctor\",\"sha256\":\"" + SHA_E
            + "\",\"interpreter\":\"ruby\",\"version\":null}],"
            + "\"build\":{\"canonical_templates\":{\"maven\":{\"integration-tests\":[\"verify\",\"-Pintegration-tests\","
            + "[\"-pl\",\"{module}\"]],\"verify\":[\"verify\"]}},"
            + "\"discovery_templates\":{\"maven\":{\"modules\":[\"help:evaluate\"]}}},"
            + "\"providers\":{\"github\":{\"verify_argv\":[\"gh\",\"auth\",\"status\",\"--hostname\",\"github.com\"]}},"
            + "\"project_scripts\":{\"lint-report\":{\"script_path\":\"tools/lint_report.py\",\"sha256\":\"" + SHA_F
            + "\",\"interpreter\":\"python3\",\"binding\":{\"worker_kind\":\"log_parser\",\"target\":\"maven\"},"
            + "\"argument_schema\":{\"type\":\"object\",\"properties\":{"
            + "\"level\":{\"type\":\"string\",\"enum\":[\"warn\",\"error\"]},"
            + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":100},"
            + "\"mode\":{\"enum\":[\"fast\",2.5,true]},"
            + "\"name\":{\"type\":\"string\",\"pattern\":\"^[a-z]+$\",\"maxLength\":32}},"
            + "\"required\":[\"level\"],\"additionalProperties\":false},\"max_timeout_seconds\":120}},"
            + "\"language_servers\":{\"java\":{\"executable_path\":\"/opt/jdtls/bin/jdtls\",\"sha256\":\"" + SHA_0
            + "\",\"args\":[\"--stdio\"]}},"
            + "\"command_budgets\":{\"git:fetch\":60,\"maven:verify@pm-core\":900},"
            + "\"write_paths\":[\"/home/dev/.m2/repository\"],"
            + "\"build_environment\":{\"JAVA_HOME\":\"/opt/jdk-25\",\"MAVEN_OPTS\":\"-Xmx2g\"}}";

    private static final String MINIMAL = "{\"format_version\":1,\"project_id\":\"pm-mcp-core\","
            + "\"project_root\":\"/home/dev/git/pm-mcp-core\",\"enrolled_at\":\"2026-10-11T08:00:00Z\","
            + "\"wrappers\":[],\"host_tools\":[],\"build\":{\"canonical_templates\":{},\"discovery_templates\":{}},"
            + "\"providers\":{},\"project_scripts\":{},\"language_servers\":{},\"command_budgets\":{},"
            + "\"write_paths\":[],\"build_environment\":{}}";

    private static final String MINIMAL_CANONICAL = MINIMAL.replace("\"enrolled_at\":\"2026-10-11T08:00:00Z\",",
            "\"enrolled_at\":\"2026-10-11T08:00:00Z\",\"pilot\":false,\"container_engine\":null,\"released_at\":null,");

    private static final Path FILE = Path.of("/base/enrolments/pm-mcp-core.json");

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static ProjectEnrolment read(String document) {
        return EnrolmentCodec.read(FILE, utf8(document));
    }

    /** Replaces the one occurrence of a fragment of the full document, so a mutation never silently misses. */
    private static String mutate(String document, String from, String to) {
        var at = document.indexOf(from);
        assertTrue(at >= 0 && document.indexOf(from, at + 1) < 0, "fragment occurs exactly once: " + from);
        return document.substring(0, at) + to + document.substring(at + from.length());
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a full enrolment is read into typed values and written again byte for byte")
        void full() {
            var enrolment = read(FULL);

            assertAll("the full document should round trip",
                    () -> assertEquals(FULL, text(EnrolmentCodec.write(enrolment)),
                            "The full document should be written back byte for byte"),
                    () -> assertEquals("pm-mcp-core", enrolment.projectId(),
                            "The project id should be read from the document"),
                    () -> assertTrue(enrolment.pilot(), "The pilot flag should be read as true"),
                    () -> assertEquals(ContainerEngine.DOCKER, enrolment.containerEngine(),
                            "The container engine should be read as docker"),
                    () -> assertFalse(enrolment.isReleased(),
                            "An enrolment with no release should not be released"),
                    () -> assertEquals(WrapperName.PW, enrolment.wrappers().get(1).name(),
                            "The second wrapper should keep its wire name"),
                    () -> assertEquals(Interpreter.PYTHON3, enrolment.wrappers().get(1).interpreter(),
                            "The second wrapper should keep its interpreter"),
                    () -> assertTrue(enrolment.wrappers().getFirst().pinnedFiles().get(1).mustBeAbsent(),
                            "The unpinned file should be read as absent"),
                    () -> assertEquals(HostToolName.ASCIIDOCTOR, enrolment.hostTools().get(1).name(),
                            "The second host tool should keep its wire name"),
                    () -> assertEquals(List.of(new Argv.Literal("verify"), new Argv.Literal("-Pintegration-tests"),
                                    new Argv.ModuleGroup(List.of("-pl", "{module}"))),
                            enrolment.build().canonicalTemplates().get(BuildSystem.MAVEN).get("integration-tests")
                                    .elements(),
                            "The integration-tests template should keep its argv"),
                    () -> assertEquals(Argv.of("gh", "auth", "status", "--hostname", "github.com"),
                            enrolment.providers().get(Provider.GH).verifyArgv(),
                            "The provider verification argv should be read in order"),
                    () -> assertEquals(WorkerKind.LOG_PARSER,
                            enrolment.projectScripts().get("lint-report").binding().workerKind(),
                            "The script binding should name its worker kind"),
                    () -> assertEquals(900L, enrolment.commandBudgets().get("maven:verify@pm-core"),
                            "The command budget should be read as its number"),
                    () -> assertEquals("-Xmx2g", enrolment.buildEnvironment().get("MAVEN_OPTS"),
                            "The build environment should keep its value"));
        }

        @Test
        @DisplayName("the argument schema keeps its subset of keywords, enum values of every scalar kind included")
        void argumentSchema() {
            var schema = read(FULL).projectScripts().get("lint-report").argumentSchema();

            assertAll("the argument schema should keep its keywords",
                    () -> assertEquals(SchemaType.OBJECT, schema.type(),
                            "The schema type should be read as object"),
                    () -> assertTrue(schema.closed(), "The schema should be read as closed"),
                    () -> assertEquals(List.of("level"), schema.required(),
                            "The required list should hold the one name"),
                    () -> assertEquals(List.of(new TextValue("fast"), new NumberValue(new BigDecimal("2.5")),
                                    new BooleanValue(true)), schema.properties().get("mode").enumValues(),
                            "The enum should keep values of every scalar kind"),
                    () -> assertEquals(new BigDecimal("100"), schema.properties().get("limit").maximum(),
                            "The maximum should be read as its number"),
                    () -> assertEquals(32L, schema.properties().get("name").maxLength(),
                            "The maxLength should be read as its number"),
                    () -> assertNull(schema.properties().get("mode").type(),
                            "A schema without a type should read as null"));
        }

        @Test
        @DisplayName("a minimal enrolment reads its optional fields as their defaults and is written with them")
        void minimal() {
            var enrolment = read(MINIMAL);

            assertAll("the minimal document should take its defaults",
                    () -> assertFalse(enrolment.pilot(), "An absent pilot flag should default to false"),
                    () -> assertNull(enrolment.containerEngine(),
                            "An absent container engine should default to null"),
                    () -> assertNull(enrolment.releasedAt(), "An absent release instant should default to null"),
                    () -> assertEquals(MINIMAL_CANONICAL, text(EnrolmentCodec.write(enrolment)),
                            "The defaults should be written into the document"),
                    () -> assertEquals(enrolment, read(MINIMAL_CANONICAL),
                            "The canonical document should read back as the same record"));
        }

        @Test
        @DisplayName("the same enrolment built in another order is written as the same bytes")
        void canonicalOrder() {
            var budgets = new TreeMap<String, Long>(Comparator.reverseOrder());
            budgets.put("git:fetch", 60L);
            budgets.put("maven:verify@pm-core", 900L);
            var full = read(FULL);

            var rebuilt = new ProjectEnrolment(full.projectId(), full.projectRoot(), full.enrolledAt(), full.pilot(),
                    full.containerEngine(), full.releasedAt(), full.wrappers(), full.hostTools(), full.build(),
                    full.providers(), full.projectScripts(), full.languageServers(), budgets, full.writePaths(),
                    full.buildEnvironment());

            assertEquals(FULL, text(EnrolmentCodec.write(rebuilt)),
                    "The same enrolment should be written as the same bytes");
        }

        @Test
        @DisplayName("a released project carries its release instant")
        void released() {
            var enrolment = read(mutate(FULL, "\"released_at\":null", "\"released_at\":\"2026-10-12T09:30:00Z\""));

            assertAll("a released enrolment should carry its instant",
                    () -> assertTrue(enrolment.isReleased(),
                            "An enrolment with a release should be released"),
                    () -> assertEquals(Instant.parse("2026-10-12T09:30:00Z"), enrolment.releasedAt(),
                            "The release instant should be read as given"));
        }
    }

    @Nested
    @DisplayName("closed schema")
    class ClosedSchema {

        static Stream<Arguments> nestingLevels() {
            return Stream.of(
                    Arguments.of("root", "{\"format_version\":1,", "{\"format_version\":1,\"extra\":1,"),
                    Arguments.of("wrapper", "{\"name\":\"mvnw\",", "{\"extra\":1,\"name\":\"mvnw\","),
                    Arguments.of("pinned file", "{\"relative_path\":\".mvn/jvm.config\",",
                            "{\"extra\":1,\"relative_path\":\".mvn/jvm.config\","),
                    Arguments.of("host tool", "{\"name\":\"git\",", "{\"extra\":1,\"name\":\"git\","),
                    Arguments.of("build", "\"build\":{", "\"build\":{\"extra\":{},"),
                    Arguments.of("templates", "\"canonical_templates\":{", "\"canonical_templates\":{\"ant\":{},"),
                    Arguments.of("provider", "{\"verify_argv\":", "{\"extra\":1,\"verify_argv\":"),
                    Arguments.of("project script", "{\"script_path\":", "{\"extra\":1,\"script_path\":"),
                    Arguments.of("binding", "{\"worker_kind\":", "{\"extra\":1,\"worker_kind\":"),
                    Arguments.of("argument schema", "\"argument_schema\":{", "\"argument_schema\":{\"items\":{},"),
                    Arguments.of("nested argument schema", "\"level\":{", "\"level\":{\"default\":\"warn\","),
                    Arguments.of("language server", "{\"executable_path\":\"/opt/jdtls",
                            "{\"extra\":1,\"executable_path\":\"/opt/jdtls"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("nestingLevels")
        @DisplayName("an undeclared field is refused at every level of nesting")
        void undeclaredField(String level, String from, String to) {
            var document = mutate(FULL, from, to);

            var refusal = assertThrows(StoreRefusal.class, () -> read(document),
                    "An undeclared field should be refused");

            assertAll("an undeclared field at " + level + " is refused",
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "The refusal should report an unreadable document"),
                    () -> assertEquals(FILE, refusal.file(), "The refusal should name the enrolment file"),
                    () -> assertTrue(refusal.getMessage().startsWith(FILE.toString()), refusal.getMessage()));
        }

        static Stream<Arguments> invalidValues() {
            return Stream.of(
                    Arguments.of("pilot a string", "\"pilot\":true", "\"pilot\":\"true\""),
                    Arguments.of("pilot twice", "\"pilot\":true", "\"pilot\":true,\"pilot\":true"),
                    Arguments.of("container_engine undeclared", "\"container_engine\":\"docker\"",
                            "\"container_engine\":\"podman\""),
                    Arguments.of("project_id outside the grammar", "\"project_id\":\"pm-mcp-core\"",
                            "\"project_id\":\"PM\""),
                    Arguments.of("project_root relative", "\"project_root\":\"/home/dev/git/pm-mcp-core\"",
                            "\"project_root\":\"git/pm-mcp-core\""),
                    Arguments.of("project_root not normalized", "\"project_root\":\"/home/dev/git/pm-mcp-core\"",
                            "\"project_root\":\"/home/dev/git/../pm-mcp-core\""),
                    Arguments.of("enrolled_at with an offset", "\"enrolled_at\":\"2026-10-11T08:00:00Z\"",
                            "\"enrolled_at\":\"2026-10-11T08:00:00+00:00\""),
                    Arguments.of("wrapper name undeclared", "{\"name\":\"mvnw\"", "{\"name\":\"ant\""),
                    Arguments.of("wrapper sha256 in upper case", "\"sha256\":\"" + SHA_A + "\"",
                            "\"sha256\":\"" + "A".repeat(64) + "\""),
                    Arguments.of("pw without its interpreter", "\"pinned_files\":[],\"interpreter\":\"python3\"",
                            "\"pinned_files\":[],\"interpreter\":null"),
                    Arguments.of("mvnw with an interpreter", "\"sha256\":null}],\"interpreter\":null",
                            "\"sha256\":null}],\"interpreter\":\"python3\""),
                    Arguments.of("host tool run by python3", "\"interpreter\":\"ruby\"",
                            "\"interpreter\":\"python3\""),
                    Arguments.of("write_paths not an array", "\"write_paths\":[\"/home/dev/.m2/repository\"]",
                            "\"write_paths\":\"/home/dev/.m2/repository\""),
                    Arguments.of("write_paths entry empty", "\"write_paths\":[\"/home/dev/.m2/repository\"]",
                            "\"write_paths\":[\"\"]"),
                    Arguments.of("verb outside its pattern", "\"verify\":[\"verify\"]", "\"Verify\":[\"verify\"]"),
                    Arguments.of("build system undeclared", "\"discovery_templates\":{\"maven\"",
                            "\"discovery_templates\":{\"make\""),
                    Arguments.of("argument vector empty", "\"verify\":[\"verify\"]", "\"verify\":[]"),
                    Arguments.of("argument a number", "\"verify\":[\"verify\"]", "\"verify\":[1]"),
                    Arguments.of("argument empty", "\"verify\":[\"verify\"]", "\"verify\":[\"\"]"),
                    Arguments.of("second module-argument group", "[\"-pl\",\"{module}\"]",
                            "[\"-pl\",\"{module}\"],[\"-am\"]"),
                    Arguments.of("module-argument group empty", "[\"-pl\",\"{module}\"]", "[]"),
                    Arguments.of("provider undeclared", "\"github\":{", "\"bitbucket\":{"),
                    Arguments.of("verify_argv missing", "{\"verify_argv\":[\"gh\",\"auth\",\"status\",\"--hostname\","
                            + "\"github.com\"]}", "{}"),
                    Arguments.of("project script id outside its pattern", "\"lint-report\":{", "\"Lint\":{"),
                    Arguments.of("project script run by ruby", "\"interpreter\":\"python3\",\"binding\"",
                            "\"interpreter\":\"ruby\",\"binding\""),
                    Arguments.of("worker kind undeclared", "\"worker_kind\":\"log_parser\"",
                            "\"worker_kind\":\"linter\""),
                    Arguments.of("binding target empty", "\"target\":\"maven\"", "\"target\":\"\""),
                    Arguments.of("max_timeout_seconds below 10", "\"max_timeout_seconds\":120",
                            "\"max_timeout_seconds\":9"),
                    Arguments.of("additionalProperties true", "\"additionalProperties\":false",
                            "\"additionalProperties\":true"),
                    Arguments.of("schema type undeclared", "\"type\":\"object\"", "\"type\":\"map\""),
                    Arguments.of("maxLength negative", "\"maxLength\":32", "\"maxLength\":-1"),
                    Arguments.of("minimum a string", "\"minimum\":1", "\"minimum\":\"1\""),
                    Arguments.of("enum value null", "\"enum\":[\"warn\",\"error\"]", "\"enum\":[null]"),
                    Arguments.of("language id outside its pattern", "\"java\":{", "\"Java\":{"),
                    Arguments.of("budget below 1", "\"git:fetch\":60", "\"git:fetch\":0"),
                    Arguments.of("budget key outside its pattern", "\"git:fetch\":60", "\"git\":60"),
                    Arguments.of("budget a string", "\"git:fetch\":60", "\"git:fetch\":\"60\""),
                    Arguments.of("environment name in lower case", "\"JAVA_HOME\"", "\"java_home\""),
                    Arguments.of("environment value a number", "\"MAVEN_OPTS\":\"-Xmx2g\"", "\"MAVEN_OPTS\":2"),
                    Arguments.of("required field missing", ",\"write_paths\":[\"/home/dev/.m2/repository\"]", ""),
                    Arguments.of("content after the document", "\"-Xmx2g\"}}", "\"-Xmx2g\"}}{}"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidValues")
        @DisplayName("a value outside the schema is refused as unreadable, naming the file")
        void invalidValue(String name, String from, String to) {
            var document = mutate(FULL, from, to);

            var refusal = assertThrows(StoreRefusal.class, () -> read(document),
                    "A value outside the schema should be refused");

            assertAll("an invalid value is refused as unreadable",
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "The refusal should report an unreadable document"),
                    () -> assertEquals(FILE, refusal.file(), "The refusal should name the enrolment file"));
        }
    }

    @Nested
    @DisplayName("format version")
    class FormatVersion {

        static Stream<Arguments> versions() {
            return Stream.of(
                    Arguments.of(0, Outcome.FORMAT_OUTDATED),
                    Arguments.of(2, Outcome.FORMAT_NEWER));
        }

        @ParameterizedTest(name = "format_version {0}")
        @MethodSource("versions")
        @DisplayName("another format version is refused as outdated or newer, naming the enrolment file")
        void otherVersion(int version, Outcome outcome) {
            var document = mutate(FULL, "{\"format_version\":1,", "{\"format_version\":" + version + ",");

            var refusal = assertThrows(StoreRefusal.class, () -> read(document),
                    "Another format version should be refused");

            assertAll("another format version is refused",
                    () -> assertEquals(outcome, refusal.outcome(),
                            "The outcome should name the version relation"),
                    () -> assertEquals(OptionalInt.of(version), refusal.found(),
                            "The refusal should carry the version it found"),
                    () -> assertEquals(ProjectEnrolment.FORMAT_VERSION, refusal.current(),
                            "The refusal should carry the current version"),
                    () -> assertTrue(refusal.getMessage().startsWith(FILE + ": " + outcome.code()),
                            refusal.getMessage()));
        }

        @Test
        @DisplayName("a document without a format version is unreadable")
        void missingVersion() {
            var document = mutate(FULL, "{\"format_version\":1,", "{");

            assertEquals(Outcome.UNREADABLE, assertThrows(StoreRefusal.class, () -> read(document),
                            "A document without a version should be refused").outcome(),
                    "The refusal should report an unreadable document");
        }
    }

    @Nested
    @DisplayName("enrolment record")
    class EnrolmentRecord {

        @Test
        @DisplayName("the defaults and factories of the record hold what they name")
        void factories() {
            assertAll("the defaults and factories hold what they name",
                    () -> assertEquals(Map.of(), Build.defaults().canonicalTemplates(),
                            "The build defaults should hold no templates"),
                    () -> assertNull(ArgumentSchema.any().type(),
                            "An any schema should have no type"),
                    () -> assertFalse(ArgumentSchema.any().closed(), "An any schema should be open"),
                    () -> assertInstanceOf(Argv.Literal.class, Argv.of("verify").elements().getFirst(),
                            "The argv factory should make literal elements"));
        }

        @Test
        @DisplayName("every closed enum is found by its wire name and nothing else")
        void wireNames() {
            assertAll("every closed enum is found by its wire name",
                    () -> assertEquals(ContainerEngine.DOCKER, ContainerEngine.fromWireName("docker").orElseThrow(),
                            "The docker wire name should find its enum"),
                    () -> assertEquals(WrapperName.GRADLEW, WrapperName.fromWireName("gradlew").orElseThrow(),
                            "The gradlew wire name should find its enum"),
                    () -> assertEquals(HostToolName.NPX, HostToolName.fromWireName("npx").orElseThrow(),
                            "The npx wire name should find its enum"),
                    () -> assertEquals(Interpreter.RUBY, Interpreter.fromWireName("ruby").orElseThrow(),
                            "The ruby wire name should find its enum"),
                    () -> assertEquals(BuildSystem.PYPROJECT, BuildSystem.fromWireName("pyproject").orElseThrow(),
                            "The pyproject wire name should find its enum"),
                    () -> assertEquals(Provider.GLAB, Provider.fromWireName("gitlab").orElseThrow(),
                            "The gitlab wire name should find its enum"),
                    () -> assertEquals("github", Provider.GH.wireName(),
                            "The GitHub constant should write its wire name"),
                    () -> assertEquals(WorkerKind.EDGE_RESOLVER,
                            WorkerKind.fromWireName("edge_resolver").orElseThrow(),
                            "The edge_resolver wire name should find its enum"),
                    () -> assertEquals(SchemaType.NULL, SchemaType.fromWireName("null").orElseThrow(),
                            "The null wire name should find its enum"),
                    () -> assertTrue(SchemaType.fromWireName("OBJECT").isEmpty(),
                            "A wire name in another case should not be found"));
        }

        @Test
        @DisplayName("a budget key of the longest grammar, 155 characters, is admitted and one character more is not")
        void budgetKeyLength() {
            var longest = "m".repeat(32) + ":" + "v".repeat(64) + "@" + "M".repeat(57);
            var tooLong = "m".repeat(32) + ":" + "v".repeat(65) + "@" + "M".repeat(57);

            assertAll("the longest budget key is admitted alone",
                    () -> assertEquals(155, longest.length(),
                            "The longest key should be 155 characters"),
                    () -> assertEquals(1L, budgets(longest).commandBudgets().get(longest),
                            "A key of that length should be admitted"),
                    () -> assertThrows(IllegalArgumentException.class, () -> budgets(tooLong),
                            "One character more should be refused"));
        }

        private ProjectEnrolment budgets(String key) {
            return new ProjectEnrolment("pm-mcp-core", "/home/dev/git/pm-mcp-core",
                    Instant.parse("2026-10-11T08:00:00Z"), false, null, null, List.of(), List.of(), Build.defaults(),
                    Map.of(), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(Map.of(key, 1L)), List.of(),
                    new TreeMap<>());
        }
    }

    @Nested
    @DisplayName("integrity sidecar")
    class Sidecar {

        private static final Path SIDECAR = Path.of("/base/enrolments/pm-mcp-core.json.hmac");
        private static final String DOCUMENT = "{\"format_version\":1,\"size\":12,\"sha256\":\"" + SHA_A
                + "\",\"hmac\":\"" + SHA_B + "\"}";

        @Test
        @DisplayName("the record of size, SHA-256 and HMAC is read and written again byte for byte")
        void roundTrip() {
            var sidecar = EnrolmentSidecar.read(SIDECAR, utf8(DOCUMENT));

            assertAll("the sidecar should round trip byte for byte",
                    () -> assertEquals(new StoreDigest(12, SHA_A), sidecar.digest(),
                            "The digest should carry the recorded size and hash"),
                    () -> assertEquals(SHA_B, sidecar.hmac(), "The HMAC should be read as recorded"),
                    () -> assertEquals(DOCUMENT, text(sidecar.write()),
                            "The sidecar should be written back byte for byte"));
        }

        @Test
        @DisplayName("the recorded size and SHA-256 match the content they were taken of, and no other")
        void matches() {
            var content = utf8(FULL);
            var sidecar = new EnrolmentSidecar(StoreDigest.of(content), SHA_B);

            assertAll("the recorded digest should match its content alone",
                    () -> assertTrue(sidecar.matches(content),
                            "The digest should match the content it was taken of"),
                    () -> assertFalse(sidecar.matches(utf8(FULL.replace("\"pilot\":true", "\"pilot\":fals"))),
                            "Another content should not match the digest"));
        }

        static Stream<Arguments> invalidSidecars() {
            return Stream.of(
                    Arguments.of("an undeclared field", "\"hmac\":", "\"key\":\"k\",\"hmac\":"),
                    Arguments.of("the hmac in upper case", "\"hmac\":\"" + SHA_B, "\"hmac\":\"" + "B".repeat(64)),
                    Arguments.of("the hmac too short", "\"hmac\":\"" + SHA_B, "\"hmac\":\"" + "b".repeat(63)),
                    Arguments.of("the size negative", "\"size\":12", "\"size\":-1"),
                    Arguments.of("the hmac missing", ",\"hmac\":\"" + SHA_B + "\"", ""),
                    Arguments.of("a bare hex HMAC", DOCUMENT, SHA_B));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidSidecars")
        @DisplayName("a sidecar outside its one shape is refused as unreadable, naming the sidecar file")
        void invalid(String name, String from, String to) {
            var document = utf8(mutate(DOCUMENT, from, to));

            var refusal = assertThrows(StoreRefusal.class, () -> EnrolmentSidecar.read(SIDECAR, document),
                    "A sidecar outside its shape should be refused");

            assertAll("an invalid sidecar is refused as unreadable",
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome(),
                            "The refusal should report an unreadable document"),
                    () -> assertEquals(SIDECAR, refusal.file(), "The refusal should name the sidecar file"));
        }

        @Test
        @DisplayName("another format version of the sidecar is refused as outdated or newer, naming the sidecar file")
        void otherVersion() {
            var newerDocument = utf8(mutate(DOCUMENT, "\"format_version\":1", "\"format_version\":2"));
            var outdatedDocument = utf8(mutate(DOCUMENT, "\"format_version\":1", "\"format_version\":0"));

            var newer = assertThrows(StoreRefusal.class, () -> EnrolmentSidecar.read(SIDECAR, newerDocument),
                    "A newer sidecar version should be refused");
            var outdated = assertThrows(StoreRefusal.class, () -> EnrolmentSidecar.read(SIDECAR, outdatedDocument),
                    "An outdated sidecar version should be refused");

            assertAll("another sidecar format version is refused",
                    () -> assertEquals(Outcome.FORMAT_NEWER, newer.outcome(),
                            "The newer version should be reported as newer"),
                    () -> assertEquals(Outcome.FORMAT_OUTDATED, outdated.outcome(),
                            "The outdated version should be reported as outdated"),
                    () -> assertEquals(SIDECAR, newer.file(), "The refusal should name the sidecar file"));
        }
    }
}
