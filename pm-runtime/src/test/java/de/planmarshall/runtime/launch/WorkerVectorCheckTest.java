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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.planIds;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.store.JobId;
import de.planmarshall.runtime.launch.WorkerVectorCheck.Rule;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The composed-vector check: the four template shapes of the fixture profile, one violating vector per rule of the
 * check, and the end-to-end path through a real {@link LaunchComposer} and a recording starter.
 *
 * <p>
 * The paths, the model, the effort values and the tool set are the contract the check compares against and stay
 * literals; the profile template of every shape is read from {@link LaunchFixtures}, so a change of the fixture profile
 * breaks this table instead of letting it drift. The generation id and the plan id of the end-to-end composition are
 * generated.
 */
@EnableGeneratorController
@DisplayName("Worker vector check")
class WorkerVectorCheckTest {

    private static final Path JOB_PRIVATE = Path.of("/opt/pm/run/jobs/gen-1");

    private static final Path CWD = JOB_PRIVATE.resolve("cwd");

    private static final Path EXECUTABLE = Path.of("/opt/pm/bin/claude");

    private static final String PROMPT = JOB_PRIVATE.resolve("prompt.md").toString();

    private static final String SCHEMA = JOB_PRIVATE.resolve("result-schema.json").toString();

    private static final String MODEL = "claude-opus-4-1";

    private static final String ZERO_TOOL_MODEL = "claude-haiku-3";

    private static final List<String> EFFORTS = List.of("low", "medium", "high");

    private static final List<String> TOOLS = List.of("read_file", "pm_state", "pm_do", "resources_list",
            "resources_read");

    private static final String TOOL_LIST = String.join(",", TOOLS);

    private static final String MCP = "{\"mcpServers\":{\"pm-job\":{}}}";

    private static final String AGENT = "{\"pm-job\":{}}";

    private static final String SESSION = "session-1";

    private final SkillSource skills = LaunchFixtures.skills();

    @ParameterizedTest(name = "{0}")
    @MethodSource("conformingVectors")
    @DisplayName("every template shape of the fixture profile conforms")
    void conformingVectors(String name, WorkerVectorCheck.Input input) {
        assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the conforming vector is accepted");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusingVectors")
    @DisplayName("one violating vector per rule is answered with its rule and the position")
    void refusingVectors(String name, WorkerVectorCheck.Input input, Rule rule, int position) {
        var violation = WorkerVectorCheck.check(input).orElseThrow();

        assertAll(
                () -> assertEquals(rule, violation.rule(), "the answer names the violated rule"),
                () -> assertEquals(position, violation.position(), "the answer names the element"));
    }

    @Test
    @DisplayName("the detail names the rule and the position and never the value")
    void detailCarriesNoValue() {
        var violation = new WorkerVectorCheck.Violation(Rule.METACHARACTER, 4).detail();

        assertAll(
                () -> assertTrue(violation.contains("(d)"), "the detail names the rule"),
                () -> assertTrue(violation.contains("element 4"), "the detail names the position"),
                () -> assertTrue(violation.contains("shell metacharacter"), "the detail names what broke"));
    }

    @Test
    @DisplayName("a vector the composition renders with a metacharacter never reaches the starter")
    void refusedVectorNeverReachesTheStarter(@TempDir Path root) {
        var block = HeadlessBlock.of(LaunchFixtures.profile(LaunchFixtures.documentWithMetacharacterInArgv()));
        var generation = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        var starter = new RecordingStarter();

        starter.start(request(root, block, generation));

        assertAll(
                () -> assertEquals(List.of(), starter.started, "no launch is started for a refused vector"),
                () -> assertEquals(LaunchRefusal.ARGUMENT_REJECTED, starter.refusal,
                        "the refusal names the rejected argument"));
    }

    @Test
    @DisplayName("a conforming composition still reaches the starter")
    void conformingVectorReachesTheStarter(@TempDir Path root) {
        var generation = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        var starter = new RecordingStarter();

        starter.start(request(root, LaunchFixtures.block(), generation));

        assertAll(
                () -> assertEquals(1, starter.started.size(), "a conforming vector is started once"),
                () -> assertEquals(List.of(LaunchFixtures.enrolmentPath(root).toString()), starter.started.getFirst()
                        .argv().subList(0, 1), "the launch carries the executable"),
                () -> assertNull(starter.refusal, "a conforming vector is not refused"));
    }

    @Test
    @DisplayName("an executable path carrying a shell metacharacter is not refused")
    void anExecutableWithAMetacharacterConforms() {
        var executable = Path.of("/opt/pm&tools/claude");
        var vector = new ArrayList<>(freshVector());
        vector.set(0, executable.toString());
        var input = input(writerTemplate(), List.copyOf(vector), MODEL, Set.of(), executable);

        assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the enrolled path is no caller argument");
    }

    static Stream<Arguments> conformingVectors() {
        return Stream.of(
                Arguments.of("fresh writer", input(writerTemplate(), freshVector(), MODEL, Set.of())),
                Arguments.of("warm writer", input(writerTemplate(), warmVector(), MODEL, Set.of("result_schema"))),
                Arguments.of("resumed writer", input(resumeTemplate(), resumedVector(), MODEL, Set.of())),
                Arguments.of("zero_tool with no tools", input(zeroToolTemplate(), zeroToolVector(), ZERO_TOOL_MODEL,
                        Set.of("effort", "mcp_config", "result_schema", "tools"))));
    }

    static Stream<Arguments> refusingVectors() {
        var metacharacters = List.of("|", ";", "&", "$", "`", ">", "<");
        var rows = new ArrayList<Arguments>();
        rows.add(Arguments.of("rule a: another executable", replaced(0, "/usr/bin/claude"), Rule.EXECUTABLE, 0));
        rows.add(Arguments.of("rule b: an altered literal", replaced(1, "-q"), Rule.CORRESPONDENCE, 1));
        rows.add(Arguments.of("rule b: a literal with a suffix", replaced(3, "--modelx"), Rule.CORRESPONDENCE, 3));
        rows.add(Arguments.of("rule b: an element that lost its flag pair", without(11, 12), Rule.CORRESPONDENCE,
                11));
        rows.add(Arguments.of("rule c: a model the composer did not resolve", replaced(4, "claude-sonnet-4"),
                Rule.SLOT_GRAMMAR, 4));
        rows.add(Arguments.of("rule c: an effort the profile does not accept", replaced(6, "max"),
                Rule.SLOT_GRAMMAR, 6));
        rows.add(Arguments.of("rule c: a prompt file outside the job-private directory", replaced(8, "/etc/passwd"),
                Rule.SLOT_GRAMMAR, 8));
        rows.add(Arguments.of("rule c: a result schema outside the job-private directory", replaced(12,
                "/opt/pm/run/jobs/other/result-schema.json"), Rule.SLOT_GRAMMAR, 12));
        rows.add(Arguments.of("rule c: a working directory the composer did not fix", input(List.of("--cwd",
                "{{cwd}}"), List.of(EXECUTABLE.toString(), "--cwd", "/tmp/work"), MODEL, Set.of()),
                Rule.SLOT_GRAMMAR, 2));
        rows.add(Arguments.of("rule c: a session id outside the grammar", input(resumeTemplate(), List.of(EXECUTABLE
                .toString(), "--resume", "a session", "-p", "--prompt-file", PROMPT), MODEL, Set.of()),
                Rule.SLOT_GRAMMAR, 2));
        rows.add(Arguments.of("rule c: a tool outside the role's tool set", replaced(14, "read_file,rm_rf"),
                Rule.SLOT_GRAMMAR, 14));
        rows.add(Arguments.of("rule c: an MCP configuration that is not JSON", replaced(10, "not-json"),
                Rule.SLOT_GRAMMAR, 10));
        rows.add(Arguments.of("rule c: an agent of another shape", replaced(16, "{\"other\":{}}"),
                Rule.SLOT_GRAMMAR, 16));
        for (var metacharacter : metacharacters) {
            rows.add(Arguments.of("rule d: the metacharacter " + metacharacter, replaced(8, PROMPT.substring(0,
                    PROMPT.length() - 3) + metacharacter + ".md"), Rule.METACHARACTER, 8));
        }
        rows.add(Arguments.of("rule e: a surplus element", appended("injected"), Rule.SURPLUS,
                freshVector().size()));
        return rows.stream();
    }

    // The vectors below are the fixture profile's four template shapes filled with the literals above.

    private static List<String> writerTemplate() {
        return LaunchFixtures.block().argumentsFor(RoleClass.WRITER);
    }

    private static List<String> resumeTemplate() {
        return LaunchFixtures.block().resumeArgv();
    }

    private static List<String> zeroToolTemplate() {
        return LaunchFixtures.block().argumentsFor(RoleClass.ZERO_TOOL);
    }

    private static List<String> freshVector() {
        return List.of(EXECUTABLE.toString(), "-p", "--no-session-persistence", "--model", MODEL, "--effort", "high",
                "--prompt-file", PROMPT, "--mcp-config", MCP, "--json-schema", SCHEMA, "--allowedTools", TOOL_LIST,
                "--agents", AGENT);
    }

    private static List<String> warmVector() {
        var warm = new ArrayList<>(freshVector());
        warm.remove(12);
        warm.remove(11);
        return List.copyOf(warm);
    }

    private static List<String> resumedVector() {
        return List.of(EXECUTABLE.toString(), "--resume", SESSION, "-p", "--prompt-file", PROMPT);
    }

    private static List<String> zeroToolVector() {
        return List.of(EXECUTABLE.toString(), "-p", "--no-session-persistence", "--model", ZERO_TOOL_MODEL,
                "--prompt-file", PROMPT, "--agents", AGENT, "--setting-sources", "", "--max-turns", "1");
    }

    private static WorkerVectorCheck.Input input(List<String> template, List<String> vector, String model,
            Set<String> absentSlots) {
        return input(template, vector, model, absentSlots, EXECUTABLE);
    }

    private static WorkerVectorCheck.Input input(List<String> template, List<String> vector, String model,
            Set<String> absentSlots, Path executable) {
        return WorkerVectorCheck.Input.builder()
                .template(template)
                .vector(vector)
                .executablePath(executable)
                .model(model)
                .acceptedEfforts(EFFORTS)
                .jobPrivateDirectory(JOB_PRIVATE)
                .workingDirectory(CWD)
                .tools(TOOLS)
                .absentSlots(absentSlots)
                .build();
    }

    private static WorkerVectorCheck.Input replaced(int index, String element) {
        var vector = new ArrayList<>(freshVector());
        vector.set(index, element);
        return input(writerTemplate(), vector, MODEL, Set.of());
    }

    private static WorkerVectorCheck.Input without(int first, int second) {
        var vector = new ArrayList<>(freshVector());
        vector.remove(second);
        vector.remove(first);
        return input(writerTemplate(), vector, MODEL, Set.of());
    }

    private static WorkerVectorCheck.Input appended(String element) {
        var vector = new ArrayList<>(freshVector());
        vector.add(element);
        return input(writerTemplate(), vector, MODEL, Set.of());
    }

    private LaunchRequest request(Path root, HeadlessBlock block, String generation) {
        return LaunchRequest.builder()
                .role(LaunchFixtures.writer(skills))
                .block(block)
                .enrolment(LaunchFixtures.enrolment(LaunchFixtures.enrolmentPath(root), LaunchFixtures
                        .executableDigest(), "1.5.0"))
                .generationId(generation)
                .runtimeHome(root.resolve("runtime"))
                .jobPrivateDirectory(root.resolve("run/jobs").resolve(generation))
                .workdirKind(LaunchRequest.WorkdirKind.PLAN_WORKTREE)
                .planId(planIds().next())
                .workdir(root.resolve("worktree"))
                .relaySocket(root.resolve("daemon.sock"))
                .shimParagraph(LaunchFixtures.SHIM)
                .skills(skills)
                .fresh(true)
                .writesFreeText(false)
                .stateSkills(List.of())
                .domainSkills(List.of())
                .envelope(null)
                .inBandBoundChars(0)
                .zeroToolPrompt("")
                .resumeSessionId(null)
                .clock(LaunchFixtures.clock())
                .build();
    }

    /**
     * The start path of the supervisor, reduced to what this test needs: it composes the launch and hands it to the
     * starter only when the composition did not refuse it.
     */
    private static final class RecordingStarter {

        private final List<WorkerLaunch> started = new ArrayList<>();

        private LaunchRefusal refusal;

        void start(LaunchRequest request) {
            try {
                started.add(new LaunchComposer().compose(request));
            } catch (LaunchComposer.CompositionException refused) {
                refusal = refused.refusal();
            }
        }
    }
}
