/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_BINDING;
import static de.planmarshall.runtime.job.JobEnrolmentFixture.SCRIPT_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;


import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.ArgumentSchema;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.SchemaType;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.runtime.job.JobRefusal.Reason;
import de.planmarshall.runtime.job.JobRequest.Fields;
import de.planmarshall.runtime.job.JobRequest.HostToolBuild;
import de.planmarshall.runtime.job.JobRequest.HostToolCall;
import de.planmarshall.runtime.job.JobRequest.Items;
import de.planmarshall.runtime.job.JobRequest.ProjectScriptCall;
import de.planmarshall.runtime.job.JobRequest.Text;
import de.planmarshall.runtime.job.JobRequest.WrapperBuild;
import de.planmarshall.runtime.job.VerifiedJob.TemplateSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Stage 3 of the pipeline, the argument check (PM-EXEC-1, PM-EXEC-2, PM-TEST-5): a build job runs its enrolled
 * template before the caller's default and is refused without either; a module must be discovered and needs a
 * module-argument group; a forbidden free flag is refused in a canonical build job and passes in a host tool call or a
 * project script, a shell metacharacter in any caller-supplied argument of every job kind is refused, as are project
 * script arguments that violate the enrolled schema. Each refusal has its matched positive control, and a job refused
 * by stage 2 never reaches the argument check.
 */
@EnableGeneratorController
@DisplayName("ArgumentCheck")
class ArgumentCheckTest {

    private static final String MODULE = "core";

    /** The enrolled Maven templates: {@code verify} with a module-argument group, {@code site} without one. */
    private static final Build ENROLLED = new Build(Map.of(BuildSystem.MAVEN, new TreeMap<>(Map.of(
            "verify", new Argv(List.of(new Argv.Literal("verify"), new Argv.ModuleGroup(List.of("-pl", "{module}")))),
            "site", Argv.of("site")))), Map.of());

    /** The caller's defaults: Maven {@code verify} and {@code test}, npm {@code test}; nothing else. */
    private static final DefaultTemplates DEFAULTS = (buildSystem, verb) -> switch (buildSystem) {
        case MAVEN -> switch (verb) {
            case "verify" -> Optional.of(Argv.of("clean", "verify"));
            case "test" -> Optional.of(new Argv(List.of(new Argv.Literal("test"),
                    new Argv.ModuleGroup(List.of("-pl", "{module}", "-am")))));
            default -> Optional.empty();
        };
        case NPM -> "test".equals(verb) ? Optional.of(Argv.of("test")) : Optional.empty();
        default -> Optional.empty();
    };

    private JobEnrolmentFixture fixture;
    private ProjectEnrolment enrolment;

    @BeforeEach
    void enrol() throws IOException {
        fixture = new JobEnrolmentFixture();
        enrolment = fixture.enrol(JobEnrolmentFixture.withBuild(fixture.enrolment(), ENROLLED));
    }

    @AfterEach
    void delete() {
        fixture.close();
    }

    private VerifyNotResolvePipeline.Stage<ArgumentCheck.Arguments> check(ProjectEnrolment checked,
            JobRequest.Target target) {
        return ArgumentCheck.check(checked, DEFAULTS, fixture.request(fixture.root.toString(), target));
    }

    private ArgumentCheck.Arguments passed(JobRequest.Target target) {
        return passed(enrolment, target);
    }

    private ArgumentCheck.Arguments passed(ProjectEnrolment checked, JobRequest.Target target) {
        if (check(checked, target) instanceof VerifyNotResolvePipeline.Stage.Passed<ArgumentCheck.Arguments> passed) {
            return passed.value();
        }
        return fail("the arguments should pass");
    }

    private JobRefusal refused(JobRequest.Target target) {
        return refused(enrolment, target);
    }

    private JobRefusal refused(ProjectEnrolment checked, JobRequest.Target target) {
        if (check(checked, target) instanceof VerifyNotResolvePipeline.Stage.Failed<ArgumentCheck.Arguments> failed) {
            return failed.refusal();
        }
        return fail("the arguments should be refused");
    }

    private static WrapperBuild maven(String verb, String module) {
        return new WrapperBuild(WrapperName.MVNW, verb, Optional.ofNullable(module));
    }

    private static HostToolCall git(String... arguments) {
        return new HostToolCall(HostToolName.GIT, List.of(arguments));
    }

    /** Checks a job whose discovered modules are these, so that any module name can reach the flag rule. */
    private VerifyNotResolvePipeline.Stage<ArgumentCheck.Arguments> checkWithModules(JobRequest.Target target,
            String... modules) {
        return ArgumentCheck.check(enrolment, DEFAULTS, new JobRequest(JobEnrolmentFixture.PROJECT,
                fixture.root.toString(), Set.of(fixture.worktree), Set.of(modules), target));
    }

    @Nested
    @DisplayName("forbidden free flags in a canonical build job")
    class ForbiddenFlags {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"-D", "-Dmaven.repo.local=/tmp/evil", "-f", "-fother-pom.xml", "--file",
                "--file=other-pom.xml", "-s", "-sother-settings.xml", "--settings", "--settings=other.xml",
                "--init-script", "--init-script=evil.gradle"})
        @DisplayName("a forbidden free flag as the module of a wrapper build is refused as argument_rejected")
        void forbiddenFlagInWrapperBuild(String flag) {
            var refusal = refused(maven("verify", flag));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason()),
                    () -> assertTrue(refusal.detail().startsWith("the module is the forbidden flag "),
                            refusal.detail()));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"-Dexec=sh", "-fother-pom.xml", "--settings=other.xml"})
        @DisplayName("a forbidden free flag as the module of an npm build is refused as argument_rejected")
        void forbiddenFlagInHostToolBuild(String flag) {
            var refusal = refused(new HostToolBuild("test", Optional.of(flag)));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason()),
                    () -> assertTrue(refusal.detail().startsWith("the module is the forbidden flag "),
                            refusal.detail()));
        }

        @Test
        @DisplayName("a forbidden flag is refused even when it is a discovered module")
        void forbiddenFlagAsDiscoveredModule() {
            var stage = checkWithModules(maven("verify", "-Dexec=sh"), "-Dexec=sh");

            assertEquals(Reason.ARGUMENT_REJECTED,
                    assertInstanceOf(VerifyNotResolvePipeline.Stage.Failed.class, stage).refusal().reason());
        }

        @Test
        @DisplayName("positive control: a discovered module that only begins like a forbidden long flag passes")
        void lookalikeLongFlag() {
            var stage = checkWithModules(maven("verify", "--filesystem"), "--filesystem");

            if (stage instanceof VerifyNotResolvePipeline.Stage.Passed<ArgumentCheck.Arguments> passed) {
                assertEquals(List.of("verify", "-pl", "--filesystem"), passed.value().arguments());
            } else {
                fail("the module should pass");
            }
        }
    }

    @Nested
    @DisplayName("the arguments of a host tool call")
    class HostToolCallArguments {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"a|b", "a;b", "a&b", "$HOME", "`id`", "a>out", "a<in"})
        @DisplayName("an argument with a shell metacharacter is refused as argument_rejected, without the argument")
        void metacharacter(String argument) {
            var refusal = refused(git("log", argument));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason()),
                    () -> assertEquals("the argument 2 contains a shell metacharacter", refusal.detail()));
        }

        @Test
        @DisplayName("a shell metacharacter attached to a short flag is still refused")
        void metacharacterAfterShortFlag() {
            var refusal = refused(git("status", "-sb;id"));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason()),
                    () -> assertEquals("the argument 2 contains a shell metacharacter", refusal.detail()));
        }

        @Test
        @DisplayName("positive control: ordinary flags and values pass in order, with no template source")
        void ordinaryArguments() {
            var arguments = passed(git("log", "--oneline", "-n", "5", "--format=%h"));

            assertAll(
                    () -> assertEquals(List.of("log", "--oneline", "-n", "5", "--format=%h"), arguments.arguments()),
                    () -> assertEquals(Optional.empty(), arguments.source()));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"-sb", "-f", "-D", "--file=notes.txt", "--settings"})
        @DisplayName("positive control: a flag forbidden only in build jobs passes in a host tool call")
        void buildOnlyFlagPasses(String flag) {
            assertEquals(List.of("status", flag), passed(git("status", flag)).arguments());
        }
    }

    @Nested
    @DisplayName("the template of a build job")
    class Templates {

        @Test
        @DisplayName("an enrolled template wins over the default and is recorded as enrolled")
        void enrolledWins() {
            var arguments = passed(maven("verify", MODULE));

            assertAll(
                    () -> assertEquals(List.of("verify", "-pl", MODULE), arguments.arguments()),
                    () -> assertEquals(Optional.of(TemplateSource.ENROLLED), arguments.source()));
        }

        @Test
        @DisplayName("the default is used when none is enrolled and is recorded as compiled_default")
        void defaultWhenNoneEnrolled() {
            var arguments = passed(maven("test", MODULE));

            assertAll(
                    () -> assertEquals(List.of("test", "-pl", MODULE, "-am"), arguments.arguments()),
                    () -> assertEquals(Optional.of(TemplateSource.COMPILED_DEFAULT), arguments.source()));
        }

        @Test
        @DisplayName("the default of npm is used for a build job of npm")
        void npmDefault() {
            var arguments = passed(new HostToolBuild("test", Optional.empty()));

            assertAll(
                    () -> assertEquals(List.of("test"), arguments.arguments()),
                    () -> assertEquals(Optional.of(TemplateSource.COMPILED_DEFAULT), arguments.source()));
        }

        @Test
        @DisplayName("a verb with neither an enrolled nor a default template is refused as verb_not_supported")
        void noTemplate() {
            assertEquals(Reason.VERB_NOT_SUPPORTED, refused(maven("deploy", null)).reason());
        }

        @Test
        @DisplayName("positive control: the same enrolment without defaults still runs its enrolled verb")
        void enrolledWithoutDefaults() {
            var stage = ArgumentCheck.check(enrolment, JobEnrolmentFixture.NO_DEFAULTS,
                    fixture.request(fixture.root.toString(), maven("verify", null)));

            assertInstanceOf(VerifyNotResolvePipeline.Stage.Passed.class, stage);
        }

        @Test
        @DisplayName("a whole-tree job omits the module-argument group")
        void wholeTreeOmitsGroup() {
            assertEquals(List.of("verify"), passed(maven("verify", null)).arguments());
        }

        @Test
        @DisplayName("a module that is not a discovered module of the project is refused as argument_rejected")
        void unknownModule() {
            var refusal = refused(maven("verify", "other-module"));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason()),
                    () -> assertFalse(refusal.detail().contains("other-module"), refusal.detail()));
        }

        @Test
        @DisplayName("a module-scoped job of a template without a module-argument group is refused as verb_not_supported")
        void moduleWithoutGroup() {
            assertEquals(Reason.VERB_NOT_SUPPORTED, refused(maven("site", MODULE)).reason());
        }

        @Test
        @DisplayName("positive control: the template without a group runs whole-tree")
        void groupLessWholeTree() {
            assertEquals(List.of("site"), passed(maven("site", null)).arguments());
        }

        @Test
        @DisplayName("a module-scoped job of a default without a group is refused as verb_not_supported")
        void defaultWithoutGroup() {
            var withoutEnrolled = JobEnrolmentFixture.withBuild(enrolment, Build.defaults());

            assertAll(
                    () -> assertEquals(Reason.VERB_NOT_SUPPORTED, refused(withoutEnrolled, maven("verify", MODULE))
                            .reason()),
                    () -> assertEquals(List.of("clean", "verify"),
                            passed(withoutEnrolled, maven("verify", null)).arguments()));
        }
    }

    @Nested
    @DisplayName("the arguments of a project script")
    class ProjectScriptArguments {

        private ProjectEnrolment schemaEnrolment;

        @BeforeEach
        void schema() {
            var properties = new TreeMap<String, ArgumentSchema>();
            properties.put("goal", new ArgumentSchema(SchemaType.STRING, null, null, false, null, "^[a-z-]+$", null,
                    null, 32L));
            schemaEnrolment = JobEnrolmentFixture.withScriptSchema(enrolment, new ArgumentSchema(SchemaType.OBJECT,
                    properties, List.of("goal"), true, null, null, null, null, null));
        }

        private ProjectScriptCall call(JobRequest.Value arguments) {
            return new ProjectScriptCall(SCRIPT_ID, SCRIPT_BINDING, arguments);
        }

        @Test
        @DisplayName("positive control: conforming arguments pass and add nothing to the argument vector")
        void conforming() {
            var arguments = passed(schemaEnrolment, call(Fields.of(Map.of("goal", new Text("module-tests")))));

            assertAll(
                    () -> assertEquals(List.of(), arguments.arguments()),
                    () -> assertEquals(Optional.empty(), arguments.source()));
        }

        @Test
        @DisplayName("arguments that violate the schema are refused as argument_rejected, naming keyword and path")
        void schemaViolation() {
            var refusal = refused(schemaEnrolment, call(Fields.of(Map.of())));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason()),
                    () -> assertEquals("the arguments violate the argument schema: required at $.goal",
                            refusal.detail()));
        }

        @Test
        @DisplayName("a string with a shell metacharacter is refused before the schema is looked at")
        void metacharacterInValue() {
            var refusal = refused(enrolment, call(Fields.of(Map.of("goal", new Items(List.of(new Text("x;y")))))));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason()),
                    () -> assertEquals("the value at $.goal[] contains a shell metacharacter", refusal.detail()));
        }

        @Test
        @DisplayName("a member name with a shell metacharacter is refused")
        void metacharacterInMemberName() {
            var refusal = refused(enrolment, call(Fields.of(Map.of("a|b",
                    new Text(Generators.letterStrings(1, 16).next())))));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason()),
                    () -> assertEquals("a member name at $ contains a shell metacharacter", refusal.detail()));
        }

        @Test
        @DisplayName("positive control: a flag forbidden only in build jobs passes as a member name and a value")
        void buildOnlyFlagPasses() {
            var arguments = passed(enrolment, call(Fields.of(Map.of("--settings", new Text("-Dx=y")))));

            assertEquals(List.of(), arguments.arguments());
        }
    }

    @Nested
    @DisplayName("the argument check is the last stage of the pipeline")
    class StageOrder {

        @Test
        @DisplayName("a job refused in stage 2 never reaches the argument check")
        void refusedInStageTwo() throws Exception {
            Files.write(fixture.tools.get(HostToolName.GIT), JobEnrolmentFixture.randomContent());

            var outcome = fixture.verify(fixture.request(fixture.root.toString(), git("log", "a;b")));

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH,
                    assertInstanceOf(JobVerification.Refused.class, outcome).refusal().reason());
        }

        @Test
        @DisplayName("positive control: with the executable verified, the same arguments reach and fail the check")
        void reachedAfterStageTwo() {
            var outcome = fixture.verify(fixture.request(fixture.root.toString(), git("log", "a;b")));

            assertEquals(Reason.ARGUMENT_REJECTED,
                    assertInstanceOf(JobVerification.Refused.class, outcome).refusal().reason());
        }

        @Test
        @DisplayName("a verified build job starts with the wrapper followed by the enrolled template")
        void verifiedBuildJob() {
            var outcome = fixture.verify(fixture.pipeline(DEFAULTS),
                    fixture.request(fixture.root.toString(), maven("verify", MODULE)));
            var job = assertInstanceOf(JobVerification.Verified.class, outcome).job();

            assertAll(
                    () -> assertEquals(List.of(fixture.root.resolve(JobEnrolmentFixture.MVNW).toString(), "verify",
                            "-pl", MODULE), job.argv()),
                    () -> assertEquals(Optional.of(TemplateSource.ENROLLED), job.templateSource()));
        }
    }
}
