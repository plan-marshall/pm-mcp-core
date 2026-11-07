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
import static de.planmarshall.runtime.test.HostToolCalls.git;
import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.words;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

/**
 * Stage 3 of the pipeline, the argument check (PM-EXEC-1, PM-EXEC-2, PM-TEST-5): a build job runs its enrolled
 * template before the caller's default and is refused without either; a module must be discovered and needs a
 * module-argument group; a forbidden free flag is refused in a canonical build job and passes in a host tool call or a
 * project script, a shell metacharacter in any caller-supplied argument of every job kind is refused, as are project
 * script arguments that violate the enrolled schema and a host tool call that names an enrolled interpreter
 * ({@code python3}, {@code ruby}). Each refusal has its matched positive control, and a job refused
 * by stage 2 never reaches the argument check.
 * <p>
 * Contract literals that stay: the forbidden free flags and their look-alikes, the shell metacharacters, the build
 * verbs of the enrolled and default templates with their module-argument group ({@code -pl}, {@code {module}},
 * {@code -am}), the reason details of the refusals, and the {@code --version} flag of a host tool. The module, the
 * project script id and every other argument or value are generated.
 */
@EnableGeneratorController
@DisplayName("ArgumentCheck")
class ArgumentCheckTest {

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
    private String module;

    @BeforeEach
    void enrol() throws IOException {
        fixture = new JobEnrolmentFixture();
        module = fixture.module;
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
        return fail("the arguments should pass the check");
    }

    private JobRefusal refused(JobRequest.Target target) {
        return refused(enrolment, target);
    }

    private JobRefusal refused(ProjectEnrolment checked, JobRequest.Target target) {
        if (check(checked, target) instanceof VerifyNotResolvePipeline.Stage.Failed<ArgumentCheck.Arguments> failed) {
            return failed.refusal();
        }
        return fail("the arguments should be refused by the check");
    }

    private static WrapperBuild maven(String verb, String module) {
        return new WrapperBuild(WrapperName.MVNW, verb, Optional.ofNullable(module));
    }

    /** Checks a job whose discovered modules are these, so that any module name can reach the flag rule. */
    private VerifyNotResolvePipeline.Stage<ArgumentCheck.Arguments> checkWithModules(JobRequest.Target target,
            String... modules) {
        return ArgumentCheck.check(enrolment, DEFAULTS, new JobRequest(fixture.project,
                fixture.root.toString(), Set.of(fixture.worktree), Set.of(modules), target));
    }

    @Nested
    @DisplayName("forbidden free flags in a canonical build job")
    class ForbiddenFlags {

        // The forbidden free flags of PM-EXEC-1 in their bare and attached forms: contract literals.
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"-D", "-Dmaven.repo.local=/tmp/evil", "-f", "-fother-pom.xml", "--file",
                "--file=other-pom.xml", "-s", "-sother-settings.xml", "--settings", "--settings=other.xml",
                "--init-script", "--init-script=evil.gradle"})
        @DisplayName("a forbidden free flag as the module of a wrapper build is refused as argument_rejected")
        void forbiddenFlagInWrapperBuild(String flag) {
            var refusal = refused(maven("verify", flag));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason(),
                            "a forbidden flag is refused as argument_rejected"),
                    () -> assertTrue(refusal.detail().startsWith("the module is the forbidden flag "),
                            "the detail names the module as a forbidden flag"));
        }

        // Forbidden free flags of PM-EXEC-1: contract literals.
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"-Dexec=sh", "-fother-pom.xml", "--settings=other.xml"})
        @DisplayName("a forbidden free flag as the module of an npm build is refused as argument_rejected")
        void forbiddenFlagInHostToolBuild(String flag) {
            var refusal = refused(new HostToolBuild("test", Optional.of(flag)));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason(),
                            "a forbidden npm module is argument_rejected"),
                    () -> assertTrue(refusal.detail().startsWith("the module is the forbidden flag "),
                            "the detail names the module as a forbidden flag"));
        }

        @Test
        @DisplayName("a forbidden flag (contract literal) is refused even when it is a discovered module")
        void forbiddenFlagAsDiscoveredModule() {
            var stage = checkWithModules(maven("verify", "-Dexec=sh"), "-Dexec=sh");

            assertEquals(Reason.ARGUMENT_REJECTED,
                    assertInstanceOf(VerifyNotResolvePipeline.Stage.Failed.class, stage,
                            "a discovered forbidden flag fails the stage").refusal().reason(),
                    "a discovered forbidden flag is argument_rejected");
        }

        @Test
        @DisplayName("positive control: a module only beginning like a forbidden long flag passes (boundary literal)")
        void lookalikeLongFlag() {
            var stage = checkWithModules(maven("verify", "--filesystem"), "--filesystem");

            if (stage instanceof VerifyNotResolvePipeline.Stage.Passed<ArgumentCheck.Arguments> passed) {
                assertEquals(List.of("verify", "-pl", "--filesystem"), passed.value().arguments(),
                        "the look-alike module enters the module group");
            } else {
                fail("the look-alike module should pass the check");
            }
        }
    }

    @Nested
    @DisplayName("the arguments of a host tool call")
    class HostToolCallArguments {

        // The shell metacharacters the argument check refuses, each between generated-free letters: contract.
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"a|b", "a;b", "a&b", "$HOME", "`id`", "a>out", "a<in"})
        @DisplayName("an argument with a shell metacharacter is refused as argument_rejected, without the argument")
        void metacharacter(String argument) {
            var refusal = refused(git(words().next(), argument));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason(),
                            "a metacharacter is refused as argument_rejected"),
                    () -> assertEquals("the argument 2 contains a shell metacharacter", refusal.detail(),
                            "the detail names the position, not the argument"));
        }

        @Test
        @DisplayName("a shell metacharacter attached to a short flag is still refused")
        void metacharacterAfterShortFlag() {
            var refusal = refused(git(words().next(), "-" + words().next() + ";" + words().next()));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason(),
                            "a metacharacter after a flag is argument_rejected"),
                    () -> assertEquals("the argument 2 contains a shell metacharacter", refusal.detail(),
                            "the detail names the second argument"));
        }

        @Test
        @DisplayName("positive control: ordinary flags and values pass in order, with no template source")
        void ordinaryArguments() {
            var expected = List.of(words().next(), "--" + words().next(), "-" + words().next(),
                    String.valueOf(Generators.integers(1, 999).next()), "--" + words().next() + "=" + words().next());

            var arguments = passed(git(expected.toArray(String[]::new)));

            assertAll(
                    () -> assertEquals(expected, arguments.arguments(), "the ordinary arguments pass in their order"),
                    () -> assertEquals(Optional.empty(), arguments.source(),
                            "a host tool call has no template source"));
        }

        // Flags forbidden only in a canonical build job (PM-EXEC-1): contract literals.
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"-sb", "-f", "-D", "--file=notes.txt", "--settings"})
        @DisplayName("positive control: a flag forbidden only in build jobs passes in a host tool call")
        void buildOnlyFlagPasses(String flag) {
            var subcommand = words().next();

            assertEquals(List.of(subcommand, flag), passed(git(subcommand, flag)).arguments(),
                    "a build-only flag passes in a host tool call");
        }
    }

    @Nested
    @DisplayName("a host tool call that names an enrolled interpreter")
    class InterpreterHostToolCall {

        private void assertInterpreterRefused(JobRefusal refusal, HostToolName interpreter) {
            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason(),
                            "an interpreter call is refused as argument_rejected"),
                    () -> assertEquals(("the host tool %s is an enrolled interpreter and runs only through a project"
                            + " script, a script host tool or a wrapper").formatted(interpreter.wireName()),
                            refusal.detail(), "the detail names the interpreter host tool"));
        }

        @Test
        @DisplayName("python3 with -c and caller code is refused as argument_rejected, naming the tool, not the code")
        void pythonWithCode() {
            var code = Generators.letterStrings(8, 32).next();

            var refusal = refused(new HostToolCall(HostToolName.PYTHON3, List.of("-c", code)));

            assertAll(
                    () -> assertInterpreterRefused(refusal, HostToolName.PYTHON3),
                    () -> assertFalse(refusal.detail().contains(code), "the detail does not repeat the caller code"));
        }

        @Test
        @DisplayName("python3 without arguments is refused as argument_rejected")
        void pythonWithoutArguments() {
            assertInterpreterRefused(refused(new HostToolCall(HostToolName.PYTHON3, List.of())),
                    HostToolName.PYTHON3);
        }

        @Test
        @DisplayName("ruby, the interpreter of the script host tool asciidoctor, is refused as argument_rejected")
        void ruby() {
            assertInterpreterRefused(refused(new HostToolCall(HostToolName.RUBY, List.of("-e",
                    Generators.letterStrings(8, 32).next()))), HostToolName.RUBY);
        }

        @Test
        @DisplayName("the interpreters are derived from the enrolment: wrappers, script host tools, project scripts")
        void derivedFromEnrolment() {
            assertEquals(Set.of(HostToolName.PYTHON3, HostToolName.RUBY),
                    ArgumentCheck.enrolledInterpreters(enrolment), "python3 and ruby are the enrolled interpreters");
        }

        @Test
        @DisplayName("positive control: python3 is a plain host tool where nothing enrolled is run by it")
        void pythonNotAnInterpreter() {
            var withoutPython = withoutPythonUses(enrolment);

            assertAll(
                    () -> assertEquals(Set.of(HostToolName.RUBY), ArgumentCheck.enrolledInterpreters(withoutPython),
                            "ruby is the only interpreter left"),
                    () -> assertEquals(List.of("--version"), passed(withoutPython,
                                    new HostToolCall(HostToolName.PYTHON3, List.of("--version"))).arguments(),
                            "python3 passes as a plain host tool"));
        }

        @Test
        @DisplayName("positive control: a project script still runs through python3")
        void projectScriptRunsThroughPython() {
            var outcome = fixture.verify(fixture.request(fixture.root.toString(),
                    new ProjectScriptCall(fixture.scriptId, SCRIPT_BINDING, Fields.of(Map.of()))));

            assertEquals(List.of(fixture.tools.get(HostToolName.PYTHON3).toString(),
                            fixture.root.resolve(JobEnrolmentFixture.SCRIPT_PATH).toString()),
                    assertInstanceOf(JobVerification.Verified.class, outcome,
                            "the project script call is verified").job().argv(),
                    "the project script runs through python3");
        }

        @Test
        @DisplayName("positive control: asciidoctor still runs with its ruby prefix")
        void scriptHostToolRunsThroughRuby() {
            var outcome = fixture.verify(fixture.request(fixture.root.toString(),
                    new HostToolCall(HostToolName.ASCIIDOCTOR, List.of("--version"))));

            assertEquals(List.of(fixture.tools.get(HostToolName.RUBY).toString(),
                            fixture.tools.get(HostToolName.ASCIIDOCTOR).toString(), "--version"),
                    assertInstanceOf(JobVerification.Verified.class, outcome,
                            "the asciidoctor call is verified").job().argv(),
                    "asciidoctor runs with its ruby prefix");
        }

        @Test
        @DisplayName("positive control: a host tool call of a tool that interprets nothing, git, still passes")
        void gitPasses() {
            var subcommand = words().next();

            assertEquals(List.of(subcommand), passed(git(subcommand)).arguments(),
                    "a git call passes the interpreter rule");
        }

        @Test
        @DisplayName("a python3 whose binary drifted is refused in stage 2 first, as executable_digest_mismatch")
        void stageTwoFirst() throws Exception {
            Files.write(fixture.tools.get(HostToolName.PYTHON3), JobEnrolmentFixture.randomContent());

            var outcome = fixture.verify(fixture.request(fixture.root.toString(),
                    new HostToolCall(HostToolName.PYTHON3, List.of("-c", Generators.letterStrings(8, 32).next()))));

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH,
                    assertInstanceOf(JobVerification.Refused.class, outcome,
                            "the drifted python3 call is refused").refusal().reason(),
                    "stage 2 refuses the drifted binary first");
        }

        /** @return the enrolment without the project scripts and the wrapper {@code pw}, the uses of python3 */
        private static ProjectEnrolment withoutPythonUses(ProjectEnrolment enrolment) {
            var wrappers = enrolment.wrappers().stream().filter(wrapper -> wrapper.name() != WrapperName.PW).toList();
            var withoutPw = JobEnrolmentFixture.withWrappers(enrolment, wrappers);
            return new ProjectEnrolment(withoutPw.projectId(), withoutPw.projectRoot(), withoutPw.enrolledAt(),
                    withoutPw.pilot(), withoutPw.containerEngine(), withoutPw.releasedAt(), withoutPw.wrappers(),
                    withoutPw.hostTools(), withoutPw.build(), withoutPw.providers(), new TreeMap<>(),
                    withoutPw.languageServers(), withoutPw.commandBudgets(), withoutPw.writePaths(),
                    withoutPw.buildEnvironment());
        }
    }

    @Nested
    @DisplayName("the template of a build job")
    class Templates {

        @Test
        @DisplayName("an enrolled template wins over the default and is recorded as enrolled")
        void enrolledWins() {
            var arguments = passed(maven("verify", module));

            assertAll(
                    () -> assertEquals(List.of("verify", "-pl", module), arguments.arguments(),
                            "the enrolled template fills the module group"),
                    () -> assertEquals(Optional.of(TemplateSource.ENROLLED), arguments.source(),
                            "the template source is recorded as enrolled"));
        }

        @Test
        @DisplayName("the default is used when none is enrolled and is recorded as compiled_default")
        void defaultWhenNoneEnrolled() {
            var arguments = passed(maven("test", module));

            assertAll(
                    () -> assertEquals(List.of("test", "-pl", module, "-am"), arguments.arguments(),
                            "the default template fills the module group"),
                    () -> assertEquals(Optional.of(TemplateSource.COMPILED_DEFAULT), arguments.source(),
                            "the template source is compiled_default"));
        }

        @Test
        @DisplayName("the default of npm is used for a build job of npm")
        void npmDefault() {
            var arguments = passed(new HostToolBuild("test", Optional.empty()));

            assertAll(
                    () -> assertEquals(List.of("test"), arguments.arguments(), "the npm default template is used"),
                    () -> assertEquals(Optional.of(TemplateSource.COMPILED_DEFAULT), arguments.source(),
                            "the npm template source is compiled_default"));
        }

        @Test
        @DisplayName("a verb with neither an enrolled nor a default template is refused as verb_not_supported")
        void noTemplate() {
            assertEquals(Reason.VERB_NOT_SUPPORTED, refused(maven("deploy", null)).reason(),
                    "a verb without a template is verb_not_supported");
        }

        @Test
        @DisplayName("positive control: the same enrolment without defaults still runs its enrolled verb")
        void enrolledWithoutDefaults() {
            var stage = ArgumentCheck.check(enrolment, JobEnrolmentFixture.NO_DEFAULTS,
                    fixture.request(fixture.root.toString(), maven("verify", null)));

            assertInstanceOf(VerifyNotResolvePipeline.Stage.Passed.class, stage,
                    "the enrolled verb passes without defaults");
        }

        @Test
        @DisplayName("a whole-tree job omits the module-argument group")
        void wholeTreeOmitsGroup() {
            assertEquals(List.of("verify"), passed(maven("verify", null)).arguments(),
                    "a whole-tree job omits the module group");
        }

        @Test
        @DisplayName("a module that is not a discovered module of the project is refused as argument_rejected")
        void unknownModule() {
            var unknown = "unknown-" + words().next();

            var refusal = refused(maven("verify", unknown));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason(),
                            "an undiscovered module is argument_rejected"),
                    () -> assertFalse(refusal.detail().contains(unknown),
                            "the detail does not repeat the module name"));
        }

        @Test
        @DisplayName("a module-scoped job of a template without a module-argument group is refused as verb_not_supported")
        void moduleWithoutGroup() {
            assertEquals(Reason.VERB_NOT_SUPPORTED, refused(maven("site", module)).reason(),
                    "a module job of a group-less verb is refused");
        }

        @Test
        @DisplayName("positive control: the template without a group runs whole-tree")
        void groupLessWholeTree() {
            assertEquals(List.of("site"), passed(maven("site", null)).arguments(),
                    "the group-less template runs whole-tree");
        }

        @Test
        @DisplayName("a module-scoped job of a default without a group is refused as verb_not_supported")
        void defaultWithoutGroup() {
            var withoutEnrolled = JobEnrolmentFixture.withBuild(enrolment, Build.defaults());

            assertAll(
                    () -> assertEquals(Reason.VERB_NOT_SUPPORTED, refused(withoutEnrolled, maven("verify", module))
                            .reason(), "a module job of a group-less default fails"),
                    () -> assertEquals(List.of("clean", "verify"),
                            passed(withoutEnrolled, maven("verify", null)).arguments(),
                            "the group-less default runs whole-tree"));
        }
    }

    @Nested
    @DisplayName("the arguments of a project script")
    class ProjectScriptArguments {

        private ProjectEnrolment schemaEnrolment;
        private String member;

        @BeforeEach
        void schema() {
            member = words().next();
            var properties = new TreeMap<String, ArgumentSchema>();
            properties.put(member, new ArgumentSchema(SchemaType.STRING, null, null, false, null, "^[a-z-]+$", null,
                    null, 32L));
            schemaEnrolment = JobEnrolmentFixture.withScriptSchema(enrolment, new ArgumentSchema(SchemaType.OBJECT,
                    properties, List.of(member), true, null, null, null, null, null));
        }

        private ProjectScriptCall call(JobRequest.Value arguments) {
            return new ProjectScriptCall(fixture.scriptId, SCRIPT_BINDING, arguments);
        }

        @Test
        @DisplayName("positive control: conforming arguments pass and add nothing to the argument vector")
        void conforming() {
            var arguments = passed(schemaEnrolment, call(Fields.of(Map.of(member, new Text(words().next())))));

            assertAll(
                    () -> assertEquals(List.of(), arguments.arguments(), "script arguments add nothing to the argv"),
                    () -> assertEquals(Optional.empty(), arguments.source(),
                            "a project script has no template source"));
        }

        @Test
        @DisplayName("arguments that violate the schema are refused as argument_rejected, naming keyword and path")
        void schemaViolation() {
            var refusal = refused(schemaEnrolment, call(Fields.of(Map.of())));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason(),
                            "a schema violation is argument_rejected"),
                    () -> assertEquals("the arguments violate the argument schema: required at $." + member,
                            refusal.detail(), "the detail names the keyword and the path"));
        }

        @Test
        @DisplayName("a string with a shell metacharacter is refused before the schema is looked at")
        void metacharacterInValue() {
            var refusal = refused(enrolment, call(Fields.of(Map.of(member,
                    new Items(List.of(new Text(words().next() + ";" + words().next())))))));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason(),
                            "a metacharacter value is argument_rejected"),
                    () -> assertEquals("the value at $." + member + "[] contains a shell metacharacter",
                            refusal.detail(), "the detail names the path of the value"));
        }

        @Test
        @DisplayName("a member name with a shell metacharacter is refused")
        void metacharacterInMemberName() {
            var refusal = refused(enrolment, call(Fields.of(Map.of(words().next() + "|" + words().next(),
                    new Text(Generators.letterStrings(1, 16).next())))));

            assertAll(
                    () -> assertEquals(Reason.ARGUMENT_REJECTED, refusal.reason(),
                            "a metacharacter member is argument_rejected"),
                    () -> assertEquals("a member name at $ contains a shell metacharacter", refusal.detail(),
                            "the detail names the path of the member"));
        }

        @Test
        @DisplayName("positive control: a flag forbidden only in build jobs (contract) passes as a member and a value")
        void buildOnlyFlagPasses() {
            var arguments = passed(enrolment, call(Fields.of(Map.of("--settings",
                    new Text("-D" + words().next() + "=" + words().next())))));

            assertEquals(List.of(), arguments.arguments(), "build-only flags pass in a project script");
        }
    }

    @Nested
    @DisplayName("the argument check is the last stage of the pipeline")
    class StageOrder {

        private String metacharacterArgument() {
            return words().next() + ";" + words().next();
        }

        @Test
        @DisplayName("a job refused in stage 2 never reaches the argument check")
        void refusedInStageTwo() throws Exception {
            Files.write(fixture.tools.get(HostToolName.GIT), JobEnrolmentFixture.randomContent());

            var outcome = fixture.verify(fixture.request(fixture.root.toString(),
                    git(words().next(), metacharacterArgument())));

            assertEquals(Reason.EXECUTABLE_DIGEST_MISMATCH,
                    assertInstanceOf(JobVerification.Refused.class, outcome,
                            "the drifted git call is refused").refusal().reason(),
                    "stage 2 refuses before the argument check");
        }

        @Test
        @DisplayName("positive control: with the executable verified, the same arguments reach and fail the check")
        void reachedAfterStageTwo() {
            var outcome = fixture.verify(fixture.request(fixture.root.toString(),
                    git(words().next(), metacharacterArgument())));

            assertEquals(Reason.ARGUMENT_REJECTED,
                    assertInstanceOf(JobVerification.Refused.class, outcome,
                            "the metacharacter git call is refused").refusal().reason(),
                    "the argument check refuses the metacharacter");
        }

        @Test
        @DisplayName("a verified build job starts with the wrapper followed by the enrolled template")
        void verifiedBuildJob() {
            var outcome = fixture.verify(fixture.pipeline(DEFAULTS),
                    fixture.request(fixture.root.toString(), maven("verify", module)));
            var job = assertInstanceOf(JobVerification.Verified.class, outcome, "the build job is verified").job();

            assertAll(
                    () -> assertEquals(List.of(fixture.root.resolve(JobEnrolmentFixture.MVNW).toString(), "verify",
                            "-pl", module), job.argv(), "the argv is the wrapper and the template"),
                    () -> assertEquals(Optional.of(TemplateSource.ENROLLED), job.templateSource(),
                            "the job records the enrolled template"));
        }
    }
}
