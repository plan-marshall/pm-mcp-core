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

            assertAll(
                    () -> assertEquals(FULL, text(EnrolmentCodec.write(enrolment))),
                    () -> assertEquals("pm-mcp-core", enrolment.projectId()),
                    () -> assertTrue(enrolment.pilot()),
                    () -> assertEquals(ContainerEngine.DOCKER, enrolment.containerEngine()),
                    () -> assertFalse(enrolment.isReleased()),
                    () -> assertEquals(WrapperName.PW, enrolment.wrappers().get(1).name()),
                    () -> assertEquals(Interpreter.PYTHON3, enrolment.wrappers().get(1).interpreter()),
                    () -> assertTrue(enrolment.wrappers().getFirst().pinnedFiles().get(1).mustBeAbsent()),
                    () -> assertEquals(HostToolName.ASCIIDOCTOR, enrolment.hostTools().get(1).name()),
                    () -> assertEquals(List.of(new Argv.Literal("verify"), new Argv.Literal("-Pintegration-tests"),
                                    new Argv.ModuleGroup(List.of("-pl", "{module}"))),
                            enrolment.build().canonicalTemplates().get(BuildSystem.MAVEN).get("integration-tests")
                                    .elements()),
                    () -> assertEquals(Argv.of("gh", "auth", "status", "--hostname", "github.com"),
                            enrolment.providers().get(Provider.GITHUB).verifyArgv()),
                    () -> assertEquals(WorkerKind.LOG_PARSER,
                            enrolment.projectScripts().get("lint-report").binding().workerKind()),
                    () -> assertEquals(900L, enrolment.commandBudgets().get("maven:verify@pm-core")),
                    () -> assertEquals("-Xmx2g", enrolment.buildEnvironment().get("MAVEN_OPTS")));
        }

        @Test
        @DisplayName("the argument schema keeps its subset of keywords, enum values of every scalar kind included")
        void argumentSchema() {
            var schema = read(FULL).projectScripts().get("lint-report").argumentSchema();

            assertAll(
                    () -> assertEquals(SchemaType.OBJECT, schema.type()),
                    () -> assertTrue(schema.closed()),
                    () -> assertEquals(List.of("level"), schema.required()),
                    () -> assertEquals(List.of(new TextValue("fast"), new NumberValue(new BigDecimal("2.5")),
                            new BooleanValue(true)), schema.properties().get("mode").enumValues()),
                    () -> assertEquals(new BigDecimal("100"), schema.properties().get("limit").maximum()),
                    () -> assertEquals(32L, schema.properties().get("name").maxLength()),
                    () -> assertNull(schema.properties().get("mode").type()));
        }

        @Test
        @DisplayName("a minimal enrolment reads its optional fields as their defaults and is written with them")
        void minimal() {
            var enrolment = read(MINIMAL);

            assertAll(
                    () -> assertFalse(enrolment.pilot()),
                    () -> assertNull(enrolment.containerEngine()),
                    () -> assertNull(enrolment.releasedAt()),
                    () -> assertEquals(MINIMAL_CANONICAL, text(EnrolmentCodec.write(enrolment))),
                    () -> assertEquals(enrolment, read(MINIMAL_CANONICAL)));
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

            assertEquals(FULL, text(EnrolmentCodec.write(rebuilt)));
        }

        @Test
        @DisplayName("a released project carries its release instant")
        void released() {
            var enrolment = read(mutate(FULL, "\"released_at\":null", "\"released_at\":\"2026-10-12T09:30:00Z\""));

            assertAll(
                    () -> assertTrue(enrolment.isReleased()),
                    () -> assertEquals(Instant.parse("2026-10-12T09:30:00Z"), enrolment.releasedAt()));
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

            var refusal = assertThrows(StoreRefusal.class, () -> read(document));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(FILE, refusal.file()),
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

            var refusal = assertThrows(StoreRefusal.class, () -> read(document));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(FILE, refusal.file()));
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

            var refusal = assertThrows(StoreRefusal.class, () -> read(document));

            assertAll(
                    () -> assertEquals(outcome, refusal.outcome()),
                    () -> assertEquals(OptionalInt.of(version), refusal.found()),
                    () -> assertEquals(ProjectEnrolment.FORMAT_VERSION, refusal.current()),
                    () -> assertTrue(refusal.getMessage().startsWith(FILE + ": " + outcome.code()),
                            refusal.getMessage()));
        }

        @Test
        @DisplayName("a document without a format version is unreadable")
        void missingVersion() {
            var document = mutate(FULL, "{\"format_version\":1,", "{");

            assertEquals(Outcome.UNREADABLE, assertThrows(StoreRefusal.class, () -> read(document)).outcome());
        }
    }

    @Nested
    @DisplayName("enrolment record")
    class EnrolmentRecord {

        @Test
        @DisplayName("the defaults and factories of the record hold what they name")
        void factories() {
            assertAll(
                    () -> assertEquals(Map.of(), Build.defaults().canonicalTemplates()),
                    () -> assertNull(ArgumentSchema.any().type()),
                    () -> assertFalse(ArgumentSchema.any().closed()),
                    () -> assertInstanceOf(Argv.Literal.class, Argv.of("verify").elements().getFirst()));
        }

        @Test
        @DisplayName("every closed enum is found by its wire name and nothing else")
        void wireNames() {
            assertAll(
                    () -> assertEquals(ContainerEngine.DOCKER, ContainerEngine.fromWireName("docker").orElseThrow()),
                    () -> assertEquals(WrapperName.GRADLEW, WrapperName.fromWireName("gradlew").orElseThrow()),
                    () -> assertEquals(HostToolName.NPX, HostToolName.fromWireName("npx").orElseThrow()),
                    () -> assertEquals(Interpreter.RUBY, Interpreter.fromWireName("ruby").orElseThrow()),
                    () -> assertEquals(BuildSystem.PYPROJECT, BuildSystem.fromWireName("pyproject").orElseThrow()),
                    () -> assertEquals(Provider.GITLAB, Provider.fromWireName("gitlab").orElseThrow()),
                    () -> assertEquals(WorkerKind.EDGE_RESOLVER,
                            WorkerKind.fromWireName("edge_resolver").orElseThrow()),
                    () -> assertEquals(SchemaType.NULL, SchemaType.fromWireName("null").orElseThrow()),
                    () -> assertTrue(SchemaType.fromWireName("OBJECT").isEmpty()));
        }

        @Test
        @DisplayName("a budget key of the longest grammar, 155 characters, is admitted and one character more is not")
        void budgetKeyLength() {
            var longest = "m".repeat(32) + ":" + "v".repeat(64) + "@" + "M".repeat(57);
            var tooLong = "m".repeat(32) + ":" + "v".repeat(65) + "@" + "M".repeat(57);

            assertAll(
                    () -> assertEquals(155, longest.length()),
                    () -> assertEquals(1L, budgets(longest).commandBudgets().get(longest)),
                    () -> assertThrows(IllegalArgumentException.class, () -> budgets(tooLong)));
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

            assertAll(
                    () -> assertEquals(new StoreDigest(12, SHA_A), sidecar.digest()),
                    () -> assertEquals(SHA_B, sidecar.hmac()),
                    () -> assertEquals(DOCUMENT, text(sidecar.write())));
        }

        @Test
        @DisplayName("the recorded size and SHA-256 match the content they were taken of, and no other")
        void matches() {
            var content = utf8(FULL);
            var sidecar = new EnrolmentSidecar(StoreDigest.of(content), SHA_B);

            assertAll(
                    () -> assertTrue(sidecar.matches(content)),
                    () -> assertFalse(sidecar.matches(utf8(FULL.replace("\"pilot\":true", "\"pilot\":fals")))));
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

            var refusal = assertThrows(StoreRefusal.class, () -> EnrolmentSidecar.read(SIDECAR, document));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(SIDECAR, refusal.file()));
        }

        @Test
        @DisplayName("another format version of the sidecar is refused as outdated or newer, naming the sidecar file")
        void otherVersion() {
            var newerDocument = utf8(mutate(DOCUMENT, "\"format_version\":1", "\"format_version\":2"));
            var outdatedDocument = utf8(mutate(DOCUMENT, "\"format_version\":1", "\"format_version\":0"));

            var newer = assertThrows(StoreRefusal.class, () -> EnrolmentSidecar.read(SIDECAR, newerDocument));
            var outdated = assertThrows(StoreRefusal.class, () -> EnrolmentSidecar.read(SIDECAR, outdatedDocument));

            assertAll(
                    () -> assertEquals(Outcome.FORMAT_NEWER, newer.outcome()),
                    () -> assertEquals(Outcome.FORMAT_OUTDATED, outdated.outcome()),
                    () -> assertEquals(SIDECAR, newer.file()));
        }
    }
}
