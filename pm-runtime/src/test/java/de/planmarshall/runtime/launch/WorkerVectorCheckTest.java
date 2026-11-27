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

import static de.planmarshall.runtime.launch.WorkerVectorCheck.SLOT_AGENT;
import static de.planmarshall.runtime.launch.WorkerVectorCheck.SLOT_MCP_CONFIG;
import static de.planmarshall.runtime.launch.WorkerVectorCheck.SLOT_RESULT_SCHEMA;
import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.planIds;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * check, the end-to-end path through a real {@link LaunchComposer} and a recording starter, and the limits of the parse
 * back: a known value followed by a literal, the literals around a JSON slot, the only slot of an element bounded from
 * both ends, the tool rule, the presence of a slot and the residual shape of two slots in one element.
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

    // the template shape of an absent slot after its flag, a contract of the presence rule
    private static final List<String> FLAGGED_SLOT_TEMPLATE = List.of("-p", "--json-schema", "{{result_schema}}",
            "--model", "{{model}}");

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

    @Nested
    @DisplayName("a known value followed by a literal")
    class KnownValues {

        private final List<String> template = writerTemplateOf(LaunchFixtures
                .documentWithTrailingLiteralsAfterKnownSlots());

        @Test
        @DisplayName("a model containing the literal tail of its element is accepted")
        void modelContainingItsTail() {
            var model = letters() + LaunchFixtures.MODEL_TAIL + letters();
            var input = input(template, knownSlotVector(model, CWD), model, Set.of());

            assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the model is matched at the cursor");
        }

        @Test
        @DisplayName("a working directory containing the literal tail of its element is accepted")
        void workingDirectoryContainingItsTail() {
            // the job-private directory lies below /opt/pm, so every working directory contains the tail /pm
            var cwd = JOB_PRIVATE.resolve(letters());
            var input = WorkerVectorCheck.Input.builder()
                    .template(template)
                    .vector(knownSlotVector(MODEL, cwd))
                    .executablePath(EXECUTABLE)
                    .model(MODEL)
                    .acceptedEfforts(EFFORTS)
                    .jobPrivateDirectory(JOB_PRIVATE)
                    .workingDirectory(cwd)
                    .tools(TOOLS)
                    .absentSlots(Set.of())
                    .build();

            assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the directory is matched at the cursor");
        }

        @Test
        @DisplayName("another model followed by the literal tail is refused by rule (c)")
        void anotherModelIsRefused() {
            var input = input(template, knownSlotVector(letters(), CWD), MODEL, Set.of());

            assertRefused(input, 'c', 4);
        }
    }

    @Nested
    @DisplayName("the literals around a JSON slot")
    class JsonLiterals {

        @ParameterizedTest(name = "the metacharacter {0}")
        @MethodSource("de.planmarshall.runtime.launch.WorkerVectorCheckTest#metacharacters")
        @DisplayName("a literal tail of the MCP element carrying a metacharacter is refused by rule (d)")
        void metacharacterInTheTail(String metacharacter) {
            var template = writerTemplateOf(LaunchFixtures.documentWithLiteralsAroundJsonSlot(SLOT_MCP_CONFIG, "",
                    metacharacter));
            var input = input(template, withElement(10, MCP + metacharacter), MODEL, Set.of());

            assertRefused(input, 'd', 10);
        }

        @Test
        @DisplayName("a literal prefix of the agent element carrying a metacharacter is refused by rule (d)")
        void metacharacterInThePrefix() {
            var metacharacter = metacharacter();
            var template = writerTemplateOf(LaunchFixtures.documentWithLiteralsAroundJsonSlot(SLOT_AGENT,
                    metacharacter, ""));
            var input = input(template, withElement(16, metacharacter + AGENT), MODEL, Set.of());

            assertRefused(input, 'd', 16);
        }

        @Test
        @DisplayName("a JSON value carrying a metacharacter between clean literals is accepted")
        void metacharacterInTheValue() {
            // the prefix cfg= and the suffix .json are the template shape, free of metacharacters
            var template = writerTemplateOf(LaunchFixtures.documentWithLiteralsAroundJsonSlot(SLOT_MCP_CONFIG, "cfg=",
                    ".json"));
            var value = "{\"mcpServers\":{\"pm-job\":{\"env\":{\"K\":\"" + letters() + metacharacter() + letters()
                    + "\"}}}}";
            var input = input(template, withElement(10, "cfg=" + value + ".json"), MODEL, Set.of());

            assertTrue(WorkerVectorCheck.check(input).isEmpty(), "only the literals of the element count");
        }
    }

    @Nested
    @DisplayName("the only slot of an element between two literals")
    class BoundedSingleSlot {

        private final String document = LaunchFixtures.documentWithBoundedSingleSlots();

        @Test
        @DisplayName("a prompt file containing the suffix literal of its element is accepted")
        void promptFileContainingItsSuffix() {
            var prompt = JOB_PRIVATE.resolve(letters() + LaunchFixtures.PROMPT_SUFFIX).toString();
            var input = input(writerTemplateOf(document), withElement(8, boundedPrompt(prompt)), MODEL, Set.of());

            assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the value is bounded from both ends");
        }

        @Test
        @DisplayName("a session id containing the suffix literal of its element is accepted")
        void sessionContainingItsSuffix() {
            var session = letters() + LaunchFixtures.SESSION_SUFFIX + letters();
            var input = input(resumeTemplateOf(document), boundedResumedVector(session), MODEL, Set.of());

            assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the session id is bounded from both ends");
        }

        @Test
        @DisplayName("a prompt file outside the job-private directory is refused by rule (c)")
        void promptFileOutsideIsRefused() {
            var input = input(writerTemplateOf(document), withElement(8, boundedPrompt("/etc/" + letters())), MODEL,
                    Set.of());

            assertRefused(input, 'c', 8);
        }

        @Test
        @DisplayName("an element without its suffix literal is refused by rule (b)")
        void missingSuffixIsRefused() {
            var prompt = JOB_PRIVATE.resolve(letters()).toString();
            var input = input(writerTemplateOf(document), withElement(8, LaunchFixtures.PROMPT_PREFIX + prompt), MODEL,
                    Set.of());

            assertRefused(input, 'b', 8);
        }
    }

    @Nested
    @DisplayName("the tool list")
    class Tools {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.runtime.launch.WorkerVectorCheckTest#refusedToolLists")
        @DisplayName("a tool list breaking the tool rule is refused by rule (c)")
        void refused(String name, String tools) {
            assertRefused(input(writerTemplate(), withElement(14, tools), MODEL, Set.of()), 'c', 14);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.runtime.launch.WorkerVectorCheckTest#acceptedToolLists")
        @DisplayName("a tool list naming each tool of the role once is accepted in any order")
        void accepted(String name, String tools) {
            var input = input(writerTemplate(), withElement(14, tools), MODEL, Set.of());

            assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the tool list conforms to rule (c)");
        }
    }

    @Nested
    @DisplayName("the presence of a slot")
    class Presence {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.runtime.launch.WorkerVectorCheckTest#absentSlotShapes")
        @DisplayName("an absent slot removes its element and the flag before it, read off the template alone")
        void absentSlot(String name, List<String> template, List<String> vector) {
            var input = input(template, vector, MODEL, Set.of(SLOT_RESULT_SCHEMA));

            assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the vector without the slot conforms");
        }

        @Test
        @DisplayName("the flag of an absent slot left in the vector is refused by rule (b)")
        void flagOfAnAbsentSlotIsRefused() {
            var vector = List.of(EXECUTABLE.toString(), "-p", "--json-schema", "--model", MODEL);

            assertRefused(input(FLAGGED_SLOT_TEMPLATE, vector, MODEL, Set.of(SLOT_RESULT_SCHEMA)), 'b', 2);
        }

        @Test
        @DisplayName("a present slot keeps its flag and its element")
        void presentSlot() {
            var vector = List.of(EXECUTABLE.toString(), "-p", "--json-schema", SCHEMA, "--model", MODEL);
            var input = input(FLAGGED_SLOT_TEMPLATE, vector, MODEL, Set.of());

            assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the vector with the slot conforms");
        }
    }

    @Nested
    @DisplayName("two slots in one element, the unknown one first")
    class Residual {

        private final List<String> template = resumeTemplateOf(LaunchFixtures.documentWithTwoSlotsInOneElement());

        @Test
        @DisplayName("a session id cut short at the separator literal is refused by rule (b) when the rest no longer matches")
        void sessionContainingTheSeparatorIsRefused() {
            var session = letters() + LaunchFixtures.SLOT_SEPARATOR + letters();

            assertRefused(input(template, residualVector(session), MODEL, Set.of()), 'b', 2);
        }

        @Test
        @DisplayName("a session id without the separator literal is accepted")
        void sessionWithoutTheSeparator() {
            var input = input(template, residualVector(letters()), MODEL, Set.of());

            assertTrue(WorkerVectorCheck.check(input).isEmpty(), "the earliest literal bounds the session");
        }
    }

    static Stream<String> metacharacters() {
        return WorkerVectorCheck.SHELL_METACHARACTERS.chars().mapToObj(code -> String.valueOf((char) code));
    }

    static Stream<Arguments> refusedToolLists() {
        var tool = tool();
        return Stream.of(
                Arguments.of("a duplicate member", String.join(",", TOOLS) + "," + tool),
                Arguments.of("an empty member", tool + ",," + tool()),
                Arguments.of("a tool outside the role's tool set", tool + "," + letters()));
    }

    static Stream<Arguments> acceptedToolLists() {
        return Stream.of(
                Arguments.of("every tool once in reverse order", String.join(",", TOOLS.reversed())),
                Arguments.of("a single tool of the role", tool()),
                Arguments.of("every tool once in the composer's order", TOOL_LIST));
    }

    // The template shapes are the contract of the presence rule: the absent slot first, in the middle, last, after a
    // flag and after an element that names a value of its own.
    static Stream<Arguments> absentSlotShapes() {
        var exe = EXECUTABLE.toString();
        return Stream.of(
                Arguments.of("first", List.of("{{result_schema}}", "-p", "--model", "{{model}}"), List.of(exe, "-p",
                        "--model", MODEL)),
                Arguments.of("middle", List.of("-p", "{{result_schema}}", "--model", "{{model}}"), List.of(exe, "-p",
                        "--model", MODEL)),
                Arguments.of("last", List.of("-p", "--model", "{{model}}", "{{result_schema}}"), List.of(exe, "-p",
                        "--model", MODEL)),
                Arguments.of("after a flag", FLAGGED_SLOT_TEMPLATE, List.of(exe, "-p", "--model", MODEL)),
                Arguments.of("after an element with its own value", List.of("-p", "--mode=strict",
                        "{{result_schema}}"), List.of(exe, "-p", "--mode=strict")));
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

    private static String letters() {
        return Generators.letterStrings(1, 12).next();
    }

    private static String metacharacter() {
        var metacharacters = WorkerVectorCheck.SHELL_METACHARACTERS;
        return String.valueOf(metacharacters.charAt(Generators.integers(0, metacharacters.length() - 1).next()));
    }

    private static String tool() {
        return TOOLS.get(Generators.integers(0, TOOLS.size() - 1).next());
    }

    private static List<String> writerTemplateOf(String document) {
        return HeadlessBlock.of(LaunchFixtures.profile(document)).argumentsFor(RoleClass.WRITER);
    }

    private static List<String> resumeTemplateOf(String document) {
        return HeadlessBlock.of(LaunchFixtures.profile(document)).resumeArgv();
    }

    /** @return the fresh vector with one element replaced */
    private static List<String> withElement(int index, String element) {
        var vector = new ArrayList<>(freshVector());
        vector.set(index, element);
        return List.copyOf(vector);
    }

    /** @return the fresh vector of the template with the literal tails after {@code {{model}}} and {@code {{cwd}}} */
    private static List<String> knownSlotVector(String model, Path cwd) {
        var vector = new ArrayList<>(freshVector());
        vector.set(4, model + LaunchFixtures.MODEL_TAIL);
        vector.add(5, "--cwd");
        vector.add(6, cwd + LaunchFixtures.CWD_TAIL);
        return List.copyOf(vector);
    }

    private static String boundedPrompt(String prompt) {
        return LaunchFixtures.PROMPT_PREFIX + prompt + LaunchFixtures.PROMPT_SUFFIX;
    }

    private static List<String> boundedResumedVector(String session) {
        return List.of(EXECUTABLE.toString(), "--resume", LaunchFixtures.SESSION_PREFIX + session
                + LaunchFixtures.SESSION_SUFFIX, "-p", "--prompt-file", boundedPrompt(PROMPT));
    }

    private static List<String> residualVector(String session) {
        return List.of(EXECUTABLE.toString(), "--resume", session + LaunchFixtures.SLOT_SEPARATOR + MODEL, "-p",
                "--prompt-file", PROMPT);
    }

    /** Asserts the refusal a composition answers for the vector: argument_rejected, its rule letter and position. */
    private static void assertRefused(WorkerVectorCheck.Input input, char letter, int position) {
        var violation = WorkerVectorCheck.check(input);
        assertTrue(violation.isPresent(), "the vector is refused by the check");
        var refusal = LaunchComposer.CompositionException.argumentRejected(violation.get());

        assertAll("the refusal",
                () -> assertEquals(LaunchRefusal.ARGUMENT_REJECTED, refusal.refusal(),
                        "the refusal is argument_rejected"),
                () -> assertEquals(letter, violation.get().rule().letter(), "the refusal names the rule letter"),
                () -> assertEquals(position, violation.get().position(), "the refusal names the element position"),
                () -> assertTrue(refusal.getMessage().contains("(" + letter + ") at element " + position),
                        "the detail names the rule and the element"));
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
