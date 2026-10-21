/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.ArgumentSchema;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.Binding;
import de.planmarshall.core.store.ProjectEnrolment.Interpreter;
import de.planmarshall.core.store.ProjectEnrolment.ProjectScript;
import de.planmarshall.core.store.ProjectEnrolment.SchemaType;
import de.planmarshall.core.store.ProjectEnrolment.WorkerKind;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;
import de.planmarshall.core.store.ProjectEnrolment.Provider;
import de.planmarshall.core.store.ProjectEnrolment.ProviderVerification;

/**
 * The enrolment policy (PM-EXEC-1, PM-EXEC-2): the four rules the specification states in concrete terms, checked on
 * an enrolment before it is signed: placeholders of an argument vector, an argument schema whose pattern's
 * {@code maxLength} is above the global ceiling, write paths against the base directory, and refused build
 * environment names. A compliant enrolment has no violation.
 */
@DisplayName("Enrolment policy")
class EnrolmentPolicyTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String MODULE = "{module}";
    private static final String OTHER_PLACEHOLDER = "{other}";
    private static final String PL = "-pl";

    @TempDir
    Path root;

    private Path base;

    @BeforeEach
    void createBase() throws IOException {
        base = Files.createDirectories(root.resolve("base"));
    }

    static ProjectEnrolment enrolment(Build build, Map<Provider, ProviderVerification> providers,
            List<String> writePaths, Map<String, String> environment) {
        return new ProjectEnrolment(PROJECT, "/home/dev/git/" + PROJECT, Instant.parse("2026-10-11T08:00:00Z"), false,
                null, null, List.of(), List.of(), build, providers, new TreeMap<>(), new TreeMap<>(), new TreeMap<>(),
                writePaths, new TreeMap<>(environment));
    }

    private List<String> violations(ProjectEnrolment enrolment) {
        return EnrolmentPolicy.violations(enrolment, base);
    }

    @Test
    @DisplayName("a compliant enrolment has no violation")
    void compliant() {
        assertTrue(violations(enrolment(Build.defaults(), Map.of(), List.of(), Map.of())).isEmpty());
    }

    /** The three places of an argument vector, each with the name a reason gives it. */
    enum Place {
        CANONICAL("build.canonical_templates.maven.verify"),
        DISCOVERY("build.discovery_templates.gradle.modules"),
        PROVIDER("providers.github.verify_argv");

        private final String vector;

        Place(String vector) {
            this.vector = vector;
        }

        ProjectEnrolment with(Argv argv) {
            return switch (this) {
                case CANONICAL -> enrolment(new Build(Map.of(BuildSystem.MAVEN, new TreeMap<>(Map.of("verify", argv))),
                        Map.of()), Map.of(), List.of(), Map.of());
                case DISCOVERY -> enrolment(new Build(Map.of(),
                                Map.of(BuildSystem.GRADLE, new TreeMap<>(Map.of("modules", argv)))), Map.of(), List.of(),
                        Map.of());
                case PROVIDER -> enrolment(Build.defaults(), Map.of(Provider.GH, new ProviderVerification(argv)),
                        List.of(), Map.of());
            };
        }
    }

    /** What the policy says about a write path, with the part of the reason that tells the refusals apart. */
    enum Verdict {
        BASE("is, lies below or contains the base directory"),
        PARENT("goes through '..' below its last existing ancestor"),
        UNRESOLVABLE("cannot be resolved below its last existing ancestor"),
        ADMITTED("");

        private final String fragment;

        Verdict(String fragment) {
            this.fragment = fragment;
        }

        int count() {
            return this == ADMITTED ? 0 : 1;
        }
    }

    private static Argv vector(Argv.Element... elements) {
        return new Argv(List.of(elements));
    }

    private static Argv.Element literal(String value) {
        return new Argv.Literal(value);
    }

    private static Argv.Element group(String... arguments) {
        return new Argv.ModuleGroup(List.of(arguments));
    }

    @Nested
    @DisplayName("placeholders of an argument vector")
    class Placeholders {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Place.class)
        @DisplayName("{module} outside the group and any other placeholder are refused, naming the vector and the token")
        void refused(Place place) {
            var moduleOutside = violations(place.with(vector(literal("mvn"), literal(MODULE))));
            var otherLiteral = violations(place.with(vector(literal("-D" + OTHER_PLACEHOLDER))));
            var otherInGroup = violations(place.with(vector(literal("mvn"), group(PL, OTHER_PLACEHOLDER))));

            assertAll(
                    () -> assertEquals(1, moduleOutside.size(), moduleOutside::toString),
                    () -> assertTrue(moduleOutside.getFirst().contains(place.vector), moduleOutside::toString),
                    () -> assertTrue(moduleOutside.getFirst().contains(MODULE), moduleOutside::toString),
                    () -> assertEquals(1, otherLiteral.size(), otherLiteral::toString),
                    () -> assertTrue(otherLiteral.getFirst().contains(OTHER_PLACEHOLDER), otherLiteral::toString),
                    () -> assertEquals(1, otherInGroup.size(), otherInGroup::toString),
                    () -> assertTrue(otherInGroup.getFirst().contains(place.vector), otherInGroup::toString),
                    () -> assertTrue(otherInGroup.getFirst().contains(OTHER_PLACEHOLDER), otherInGroup::toString));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Place.class)
        @DisplayName("{module} in the group, a lone brace and {} are admitted")
        void admitted(Place place) {
            assertAll(
                    () -> assertEquals(List.of(),
                            violations(place.with(vector(literal("mvn"), group(PL, MODULE), literal("verify"))))),
                    () -> assertEquals(List.of(), violations(place.with(vector(literal("a{b"), literal("{}"))))),
                    () -> assertEquals(List.of(), violations(place.with(vector(literal("x}"), group("{", MODULE))))));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Place.class)
        @DisplayName("two violations in one vector are both reported")
        void twoViolations(Place place) {
            var found = violations(place.with(vector(literal("{a}"), literal("x{b}y"))));

            assertAll(
                    () -> assertEquals(2, found.size(), found::toString),
                    () -> assertTrue(found.getFirst().contains("{a}"), found::toString),
                    () -> assertTrue(found.getLast().contains("{b}"), found::toString));
        }
    }

    @Nested
    @DisplayName("write paths against the base directory")
    class WritePaths {

        private ProjectEnrolment writing(String writePath) {
            return enrolment(Build.defaults(), Map.of(), List.of(writePath), Map.of());
        }

        private String absolute(String relative) {
            return "/".equals(relative) ? relative : root.resolve(relative).toString();
        }

        @ParameterizedTest(name = "''{0}'' violates: {1}")
        @CsvSource({"base, true", "base/sub, true", "base/missing/deeper, true", "'', true", "/, true",
                "sibling, false", "sibling/cache, false", "base-like, false"})
        @DisplayName("a path that is, lies below or contains the base is refused, naming the path")
        void judged(String relative, boolean violates) throws Exception {
            Files.createDirectories(base.resolve("sub"));
            Files.createDirectories(root.resolve("sibling"));
            var path = absolute(relative);

            var found = violations(writing(path));

            assertEquals(violates ? 1 : 0, found.size(), found::toString);
            if (violates) {
                assertTrue(found.getFirst().contains(path), found::toString);
            }
        }

        @ParameterizedTest(name = "''{0}''")
        @ValueSource(strings = {"cache/m2", "../base", "/tmp/a\u0000b"})
        @DisplayName("a relative or unparseable path is not judged")
        void notJudged(String writePath) {
            assertEquals(List.of(), violations(writing(writePath)));
        }

        @Test
        @DisplayName("a symbolic link into the base is refused by its real path, a link to a sibling is not")
        void symbolicLinks() throws Exception {
            var inside = Files.createDirectories(base.resolve("cache"));
            var sibling = Files.createDirectories(root.resolve("sibling"));
            var intoBase = Files.createSymbolicLink(root.resolve("into-base"), inside);
            var toSibling = Files.createSymbolicLink(root.resolve("to-sibling"), sibling);

            var refused = violations(writing(intoBase.toString()));

            assertAll(
                    () -> assertEquals(1, refused.size(), refused::toString),
                    () -> assertTrue(refused.getFirst().contains(intoBase.toString()), refused::toString),
                    () -> assertEquals(List.of(), violations(writing(toSibling.toString()))));
        }

        /**
         * Builds, beside the base, an ordinary directory outside it, and symbolic links to the base, to a directory
         * below it, to the directory outside, to the parent of the base and two dangling ones, one aimed into the base
         * and one outside it.
         */
        private void createLinks() throws IOException {
            var sub = Files.createDirectories(base.resolve("sub"));
            var outside = Files.createDirectories(root.resolve("outside"));
            Files.createSymbolicLink(root.resolve("link-to-base"), base);
            Files.createSymbolicLink(root.resolve("link-to-sub"), sub);
            Files.createSymbolicLink(root.resolve("link-outside"), outside);
            Files.createSymbolicLink(root.resolve("link-to-root"), root);
            Files.createSymbolicLink(root.resolve("dangling-in"), base.resolve("not-yet"));
            Files.createSymbolicLink(root.resolve("dangling-out"), outside.resolve("not-yet"));
        }

        @ParameterizedTest(name = "''{0}'': {1}")
        @CsvSource({"link-to-base/missing, BASE", "link-to-sub/a/b, BASE", "link-to-root/base/missing, BASE",
                "link-to-sub/../x, BASE", "outside/missing/../x, PARENT", "dangling-in, UNRESOLVABLE",
                "dangling-in/leaf, UNRESOLVABLE", "dangling-out, UNRESOLVABLE", "link-outside/missing, ADMITTED",
                "outside/missing, ADMITTED", "link-to-root/missing, ADMITTED", "outside/../x, ADMITTED"})
        @DisplayName("a path is judged by the real path of its longest existing ancestor with the missing names appended")
        void throughLongestExistingAncestor(String relative, Verdict verdict) throws Exception {
            createLinks();
            var path = root.resolve(relative).toString();

            var found = violations(writing(path));

            assertAll(
                    () -> assertEquals(verdict.count(), found.size(), found::toString),
                    () -> assertTrue(found.stream().allMatch(
                            reason -> reason.contains(path) && reason.contains(verdict.fragment)), found::toString));
        }

        @Test
        @DisplayName("a base given by a symbolic link is compared by its real path too")
        void baseByLink() throws Exception {
            var linkedBase = Files.createSymbolicLink(root.resolve("linked-base"), base);
            var below = Files.createDirectories(base.resolve("x"));

            var found = EnrolmentPolicy.violations(writing(below.toString()), linkedBase);

            assertEquals(1, found.size(), found::toString);
        }
    }

    @Nested
    @DisplayName("build environment names")
    class EnvironmentNames {

        private List<String> withName(String name) {
            return violations(enrolment(Build.defaults(), Map.of(), List.of(), Map.of(name, "value")));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"FOO_TOKEN", "DB_PASSWORD", "X_SECRET", "API_KEY", "GH_TOKEN", "GITHUB_TOKEN",
                "GITLAB_TOKEN", "ANTHROPIC_API_KEY", "DBUS_SESSION_BUS_ADDRESS", "XDG_RUNTIME_DIR",
                "MVND_DAEMON_STORAGE"})
        @DisplayName("a credential variable and the three named variables are refused, naming the variable")
        void refused(String name) {
            var found = withName(name);

            assertAll(
                    () -> assertEquals(1, found.size(), found::toString),
                    () -> assertTrue(found.getFirst().contains(name), found::toString));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"JAVA_HOME", "PATH", "KEY", "TOKEN", "TOKENIZER", "MY_KEYS", "TOKEN_COUNT",
                "DOCKER_HOST"})
        @DisplayName("any other name is admitted")
        void admitted(String name) {
            assertEquals(List.of(), withName(name));
        }
    }

    @Test
    @DisplayName("the reasons cover every family at once, in a deterministic order")
    void orderOfAllFamilies() {
        var build = new Build(Map.of(BuildSystem.MAVEN, new TreeMap<>(Map.of("verify", vector(literal(MODULE))))),
                Map.of(BuildSystem.NPM, new TreeMap<>(Map.of("modules", vector(literal(OTHER_PLACEHOLDER))))));
        var enrolment = enrolment(build, Map.of(Provider.GLAB, new ProviderVerification(vector(literal("{x}")))),
                List.of(base.toString()), Map.of("Z_TOKEN", "z", "A_SECRET", "a"));

        var found = violations(enrolment);

        assertAll(
                () -> assertEquals(6, found.size(), found::toString),
                () -> assertTrue(found.get(0).startsWith("build.canonical_templates.maven.verify"), found::toString),
                () -> assertTrue(found.get(1).startsWith("build.discovery_templates.npm.modules"), found::toString),
                () -> assertTrue(found.get(2).startsWith("providers.gitlab.verify_argv"), found::toString),
                () -> assertTrue(found.get(3).startsWith("write_paths"), found::toString),
                () -> assertTrue(found.get(4).contains("A_SECRET"), found::toString),
                () -> assertTrue(found.get(5).contains("Z_TOKEN"), found::toString),
                () -> assertEquals(found, violations(enrolment)));
    }

    @Test
    @DisplayName("the argument-schema family sits between providers and write paths")
    void orderPlacesTheArgumentSchemaFamily() {
        var above = ArgumentSchema.PATTERN_MAX_LENGTH_CEILING + 1;
        var properties = new TreeMap<String, ArgumentSchema>();
        properties.put("body", new ArgumentSchema(SchemaType.STRING, null, null, false, null, "^[a-z]+$", null, null,
                above));
        var schema = new ArgumentSchema(SchemaType.OBJECT, properties, null, false, null, null, null, null, null);
        var scripts = new TreeMap<String, ProjectScript>();
        scripts.put("parse-log", new ProjectScript("scripts/parse-log.py", "0".repeat(64), Interpreter.PYTHON3,
                new Binding(WorkerKind.LOG_PARSER, "maven"), schema, 60));
        var enrolment = new ProjectEnrolment(PROJECT, "/home/dev/git/" + PROJECT,
                Instant.parse("2026-10-11T08:00:00Z"), false, null, null, List.of(), List.of(),
                Build.defaults(), Map.of(Provider.GLAB, new ProviderVerification(vector(literal("{x}")))),
                scripts, new TreeMap<>(), new TreeMap<>(), List.of(base.toString()), new TreeMap<String, String>());

        var found = violations(enrolment);

        assertAll(
                () -> assertEquals(3, found.size(), found::toString),
                () -> assertTrue(found.get(0).startsWith("providers.gitlab.verify_argv"), found::toString),
                () -> assertTrue(found.get(1).startsWith("project_scripts.parse-log.argument_schema.body"),
                        found::toString),
                () -> assertTrue(found.get(2).startsWith("write_paths"), found::toString));
    }
}
