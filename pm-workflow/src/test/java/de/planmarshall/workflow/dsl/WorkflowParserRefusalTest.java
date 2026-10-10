/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import static de.planmarshall.workflow.dsl.WorkflowParserTest.lines;
import static de.planmarshall.workflow.dsl.WorkflowParserTest.unitWithBody;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.InstanceInputs;
import de.planmarshall.workflow.ast.UnitOrigin;

/**
 * The refusals of the parser (PM-IMPL-5): every malformed fixture states its expected rule key and line in a leading
 * comment and is refused with that rule, its resource name and that line; inline units cover every further refusal
 * path. A refusal is a value: no exception leaves the parser.
 */
@DisplayName("WorkflowParser refusals")
class WorkflowParserRefusalTest {

    private static final Pattern EXPECTATION = Pattern.compile("^#\\s*expect:\\s*([A-Z_]+)\\s+line\\s+(\\d+)");

    private static final String HEADER = "workflow demo version 1 format 1";
    private static final String TEMPLATES = "\"templates\": []";
    private static final String COMPOSITION_RULES = "\"composition_rules\": {\"revision\": \"r\", \"digest\": \"d\"}";
    private static final String INPUTS = "{\"config_hash\": \"c\", " + TEMPLATES + ", \"sub_units\": [], "
            + COMPOSITION_RULES + ", \"plan_facts_digest\": \"f\"}";
    private static final String INSTANCE_MEMBERS =
            "\"plan_id\": \"p\", \"version\": 1, \"event\": \"plan\", \"inputs\": " + INPUTS;

    private static Diagnostic refuse(String resource, String source) {
        var result = WorkflowParser.parse(resource, source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Refused.class, result, () -> resource + " was parsed").diagnostic();
    }

    private static Arguments refusal(String name, RefusalRule rule, int line, String source) {
        return Arguments.of(name, rule, line, source);
    }

    /** A unit whose header line after the unit header is the given instance line. */
    private static String withInstance(String instanceLine) {
        return lines(HEADER, instanceLine, "scope plan", "start demo.end", "state demo.end terminal done success",
                "phase init");
    }

    /** A unit whose instance header carries the given members. */
    private static String withHeaderMembers(String members) {
        return withInstance("instance {" + members + "}");
    }

    /** A unit whose instance header is valid but for the given member of its inputs, replaced as stated. */
    private static String withInputs(String member, String replacement) {
        return withHeaderMembers(INSTANCE_MEMBERS.replace(member, replacement));
    }

    /** A unit of the scope whose header lines from line 3 on are the given lines. */
    private static String withHeaderLines(String scope, String... headerLines) {
        return lines(HEADER, "scope " + scope) + lines(headerLines)
                + lines("start demo.end", "state demo.end terminal done success", "phase inherit");
    }

    /** A unit whose states from line 4 on are the given lines. */
    private static String withStates(String... stateLines) {
        return lines(HEADER, "scope plan", "start demo.begin") + lines(stateLines);
    }

    /** A unit whose start state, from line 6 on, has one link of the given parameter list. */
    private static String withLinkParams(String params) {
        return unitWithBody("workspace", "link next go (" + params + ")", "do demo.go", "done -> demo.begin");
    }

    /** A unit whose start state has one link with the given lines from line 7 on, before its action. */
    private static String withLinkGuard(String... guardLines) {
        return unitWithBody("workspace", "link next go ()") + lines(guardLines)
                + lines("do demo.go", "done -> demo.begin");
    }

    @Nested
    @DisplayName("Malformed fixtures")
    class MalformedFixtures {

        static List<String> malformedFixtures() {
            return DslFixtures.list(DslFixtures.MALFORMED);
        }

        @Test
        @DisplayName("finds one fixture per refusal class")
        void fixturesPresent() {
            assertEquals(12, malformedFixtures().size());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("malformedFixtures")
        @DisplayName("refuses every malformed fixture with the rule and line its leading comment states")
        void refusesAsStated(String resource) {
            var source = DslFixtures.read(resource);
            var expectation = EXPECTATION.matcher(source);
            assertTrue(expectation.find(), () -> resource + " states no '# expect: <RULE> line <n>' comment");

            var diagnostic = refuse(resource, source);

            assertAll(
                    () -> assertEquals(RefusalRule.valueOf(expectation.group(1)), diagnostic.rule(),
                            diagnostic::message),
                    () -> assertEquals(resource, diagnostic.resourceName()),
                    () -> assertEquals(Integer.parseInt(expectation.group(2)), diagnostic.line(), diagnostic::message),
                    () -> assertTrue(diagnostic.column() >= 1),
                    () -> assertFalse(diagnostic.message().isBlank()));
        }
    }

    @Nested
    @DisplayName("Inline units")
    class InlineUnits {

        static Stream<Arguments> headerRefusals() {
            return Stream.of(
                    refusal("empty source", RefusalRule.STRUCTURE, 1, ""),
                    refusal("comment-only source", RefusalRule.STRUCTURE, 1, "# nothing but a comment\n"),
                    refusal("control character", RefusalRule.STRUCTURE, 1, "\u0001\n"),
                    refusal("no unit header", RefusalRule.STRUCTURE, 1, lines("scope plan")),
                    refusal("bare unit header", RefusalRule.STRUCTURE, 1, lines("workflow")),
                    refusal("unit id is an enum value", RefusalRule.STRUCTURE, 1,
                            lines("workflow Demo version 1 format 1")),
                    refusal("unit id is a reserved keyword", RefusalRule.RESERVED_KEYWORD, 1,
                            lines("workflow state version 1 format 1")),
                    refusal("unknown character in the unit id", RefusalRule.STRUCTURE, 1,
                            lines("workflow demo@x version 1 format 1")),
                    refusal("misspelled version", RefusalRule.STRUCTURE, 1, lines("workflow demo revision 1 format 1")),
                    refusal("version is a word", RefusalRule.STRUCTURE, 1, lines("workflow demo version one format 1")),
                    refusal("version out of range", RefusalRule.STRUCTURE, 1,
                            lines("workflow demo version 99999999999 format 1")),
                    refusal("derived_from zero", RefusalRule.STRUCTURE, 1, lines(HEADER + " derived_from 0")),
                    refusal("token after the header", RefusalRule.STRUCTURE, 1, lines(HEADER + " extra")),
                    refusal("bare instance keyword", RefusalRule.INSTANCE_HEADER, 2, withInstance("instance")),
                    refusal("instance without JSON", RefusalRule.INSTANCE_HEADER, 2, withInstance("instance plan")),
                    refusal("instance JSON broken", RefusalRule.INSTANCE_HEADER, 2,
                            withInstance("instance {\"plan_id\": }")),
                    refusal("instance JSON followed by a word", RefusalRule.INSTANCE_HEADER, 2,
                            withInstance("instance {" + INSTANCE_MEMBERS + "} extra")),
                    refusal("instance JSON followed by an object", RefusalRule.INSTANCE_HEADER, 2,
                            withInstance("instance {" + INSTANCE_MEMBERS + "} {}")),
                    refusal("instance member twice", RefusalRule.INSTANCE_HEADER, 2,
                            withInstance("instance {" + INSTANCE_MEMBERS + ", \"event\": \"again\"}")),
                    refusal("instance plan_id not a string", RefusalRule.INSTANCE_HEADER, 2,
                            withHeaderMembers(INSTANCE_MEMBERS.replace("\"plan_id\": \"p\"", "\"plan_id\": 1"))),
                    refusal("instance version not an integer", RefusalRule.INSTANCE_HEADER, 2,
                            withHeaderMembers(INSTANCE_MEMBERS.replace("\"version\": 1", "\"version\": \"1\""))),
                    refusal("instance event not a string", RefusalRule.INSTANCE_HEADER, 2,
                            withHeaderMembers(INSTANCE_MEMBERS.replace("\"event\": \"plan\"", "\"event\": 2"))),
                    refusal("instance inputs missing", RefusalRule.INSTANCE_HEADER, 2,
                            withHeaderMembers("\"plan_id\": \"p\", \"version\": 1, \"event\": \"plan\"")),
                    refusal("instance input digests in the old shape", RefusalRule.INSTANCE_HEADER, 2,
                            withHeaderMembers("\"plan_id\": \"p\", \"version\": 1, \"event\": \"plan\", "
                                    + "\"input_digests\": {\"plan\": \"a\"}")),
                    refusal("instance inputs not an object", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(INPUTS, "[]")),
                    refusal("instance input missing", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(", \"plan_facts_digest\": \"f\"", "")),
                    refusal("instance input twice", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(TEMPLATES, TEMPLATES + ", " + TEMPLATES)),
                    refusal("instance config hash not a string", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs("\"config_hash\": \"c\"", "\"config_hash\": 1")),
                    refusal("instance optional input neither string nor null", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(TEMPLATES, TEMPLATES + ", \"change_type\": 1")),
                    refusal("instance templates not an array", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(TEMPLATES, "\"templates\": {}")),
                    refusal("instance unit reference not an object", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(TEMPLATES, "\"templates\": [\"plan\"]")),
                    refusal("instance unit reference without origin", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(TEMPLATES, "\"templates\": [{\"unit\": \"plan\", \"digest\": \"t\"}]")),
                    refusal("instance unit file neither string nor null", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(TEMPLATES, "\"templates\": [{\"unit\": \"plan\", \"digest\": \"t\", "
                                    + "\"origin\": \"project\", \"file\": 1}]")),
                    refusal("instance unit member twice", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(TEMPLATES, "\"templates\": [{\"unit\": \"plan\", \"unit\": \"other\"}]")),
                    refusal("instance composition rules not an object", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(COMPOSITION_RULES, "\"composition_rules\": \"r\"")),
                    refusal("instance composition rules without digest", RefusalRule.INSTANCE_HEADER, 2,
                            withInputs(COMPOSITION_RULES, "\"composition_rules\": {\"revision\": \"r\"}")),
                    refusal("scope missing at the end of the unit", RefusalRule.MISSING_SCOPE, 1, lines(HEADER)),
                    refusal("unknown scope", RefusalRule.STRUCTURE, 2, lines(HEADER, "scope team")),
                    refusal("bare scope", RefusalRule.STRUCTURE, 2, lines(HEADER, "scope")),
                    refusal("token after the scope", RefusalRule.STRUCTURE, 2, lines(HEADER, "scope plan extra")));
        }

        static Stream<Arguments> declarationRefusals() {
            return Stream.of(
                    refusal("param name is reserved", RefusalRule.RESERVED_KEYWORD, 3,
                            withHeaderLines("subworkflow", "param text : int")),
                    refusal("param name with a hyphen", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "param max-rounds : int")),
                    refusal("param name is an enum value", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "param Mode : int")),
                    refusal("param without colon", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "param mode int")),
                    refusal("text param of a sub-workflow", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "param mode : text")),
                    refusal("list of int param of a sub-workflow", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "param modes : list<int>")),
                    refusal("list param not closed", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "param modes : list<enum[a] extra")),
                    refusal("enum param not closed", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "param mode : enum[a|b")),
                    refusal("empty enum param", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "param mode : enum[]")),
                    refusal("result without schema", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "result text")),
                    refusal("result schema name is an enum value", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "result schema[Result]")),
                    refusal("token after the result", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("subworkflow", "result schema[result-record] extra")),
                    refusal("param outside a sub-workflow", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("plan", "param mode : int")),
                    refusal("declare name is reserved", RefusalRule.RESERVED_KEYWORD, 3,
                            withHeaderLines("plan", "declare text = 1")),
                    refusal("declare without equals sign", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("plan", "declare rounds 1")),
                    refusal("declare value is punctuation", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("plan", "declare rounds = (")),
                    refusal("declare value is negative", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("plan", "declare rounds = -1")),
                    refusal("declare list without comma", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("plan", "declare modes = [a b]")),
                    refusal("token after declare", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("plan", "declare rounds = 1 2")),
                    refusal("unknown decision kind", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("entity", "decide budget -> ask")),
                    refusal("decision kind is an enum value", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("entity", "decide Approach -> ask")),
                    refusal("decide without arrow", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("entity", "decide approach ask")),
                    refusal("unknown decision mode", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("entity", "decide approach -> vote")),
                    refusal("quorum without comma", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("entity", "decide approach -> quorum(architect 60)")),
                    refusal("quorum role is reserved", RefusalRule.RESERVED_KEYWORD, 3,
                            withHeaderLines("entity", "decide approach -> quorum(role, 60)")),
                    refusal("quorum score zero", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("entity", "decide approach -> quorum(architect, 0)")),
                    refusal("quorum without score", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("entity", "decide approach -> quorum(architect, reviewer)")),
                    refusal("quorum not closed", RefusalRule.STRUCTURE, 3,
                            withHeaderLines("entity", "decide approach -> quorum(architect, 60")),
                    refusal("start missing", RefusalRule.STRUCTURE, 3,
                            lines(HEADER, "scope plan", "state demo.end terminal done success", "phase init")),
                    refusal("bare start", RefusalRule.STRUCTURE, 3, lines(HEADER, "scope plan", "start")),
                    refusal("unit ends after start", RefusalRule.STRUCTURE, 3,
                            lines(HEADER, "scope plan", "start demo.end")),
                    refusal("migrate without arrow", RefusalRule.STRUCTURE, 4, lines(HEADER, "scope plan",
                            "start demo.end", "migrate demo.old demo.end", "state demo.end terminal done success",
                            "phase init")));
        }

        static Stream<Arguments> stateRefusals() {
            return Stream.of(
                    refusal("bare state", RefusalRule.STRUCTURE, 4, withStates("state")),
                    refusal("state segment is reserved", RefusalRule.RESERVED_KEYWORD, 4,
                            withStates("state demo.state terminal done success", "phase init")),
                    refusal("terminal without class", RefusalRule.STRUCTURE, 4,
                            withStates("state demo.begin terminal done", "phase init")),
                    refusal("unknown outcome class", RefusalRule.STRUCTURE, 4,
                            withStates("state demo.begin terminal done great", "phase init")),
                    refusal("outcome class is an enum value", RefusalRule.STRUCTURE, 4,
                            withStates("state demo.begin terminal done Success", "phase init")),
                    refusal("terminal outcome is reserved", RefusalRule.RESERVED_KEYWORD, 4,
                            withStates("state demo.begin terminal phase success", "phase init")),
                    refusal("state without phase", RefusalRule.STRUCTURE, 5,
                            withStates("state demo.begin terminal done success", "auto demo.run")),
                    refusal("phase is an enum value", RefusalRule.STRUCTURE, 5,
                            withStates("state demo.begin terminal done success", "phase Init")),
                    refusal("bare skill path", RefusalRule.STRUCTURE, 6,
                            withStates("state demo.begin terminal done success", "phase init", "skill pm")),
                    refusal("skill path outside pm", RefusalRule.STRUCTURE, 6,
                            withStates("state demo.begin terminal done success", "phase init", "skill tools/x")),
                    refusal("skill segment is an enum value", RefusalRule.STRUCTURE, 6,
                            withStates("state demo.begin terminal done success", "phase init", "skill pm/Plan")),
                    refusal("facts without primitive", RefusalRule.STRUCTURE, 6,
                            withStates("state demo.begin terminal done success", "phase init", "facts demo")),
                    refusal("body missing at the end of the unit", RefusalRule.STRUCTURE, 5,
                            withStates("state demo.begin", "phase init")),
                    refusal("unknown body", RefusalRule.STRUCTURE, 6,
                            withStates("state demo.begin", "phase init", "wait demo.run")),
                    refusal("terminal state followed by a body", RefusalRule.STRUCTURE, 6,
                            withStates("state demo.end terminal done success", "phase init", "auto demo.run")),
                    refusal("token after auto", RefusalRule.STRUCTURE, 6, unitWithBody("plan", "auto demo.run extra")),
                    refusal("transitions missing at the end of the unit", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "auto demo.run")),
                    refusal("transition without target", RefusalRule.STRUCTURE, 7,
                            unitWithBody("plan", "auto demo.run", "done ->")),
                    refusal("token after transition", RefusalRule.STRUCTURE, 7,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin extra")),
                    refusal("outcome is an enum value", RefusalRule.STRUCTURE, 7,
                            unitWithBody("plan", "auto demo.run", "Done -> demo.begin")),
                    refusal("result guard without dot", RefusalRule.STRUCTURE, 8,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin", "guard result")),
                    refusal("result guard on another record", RefusalRule.STRUCTURE, 8,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin", "guard not outcome.ok")),
                    refusal("result field is an enum value", RefusalRule.STRUCTURE, 8,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin", "guard result.Ok")),
                    refusal("token after result guard", RefusalRule.STRUCTURE, 8,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin", "guard result.ok extra")),
                    refusal("completes without colon", RefusalRule.STRUCTURE, 8,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin", "completes init as done")),
                    refusal("completes step segment is an enum value", RefusalRule.STRUCTURE, 8,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin", "completes Init:x as done")),
                    refusal("completes without as", RefusalRule.STRUCTURE, 8,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin", "completes init:x done")),
                    refusal("unknown step completion", RefusalRule.STRUCTURE, 8,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin", "completes init:x as finished")),
                    refusal("step completion is an enum value", RefusalRule.STRUCTURE, 8,
                            unitWithBody("plan", "auto demo.run", "done -> demo.begin", "completes init:x as Done")),
                    refusal("bare job", RefusalRule.STRUCTURE, 6, unitWithBody("plan", "job")),
                    refusal("bare model job", RefusalRule.STRUCTURE, 6, unitWithBody("plan", "job llm")),
                    refusal("llm as the domain of a primitive", RefusalRule.RESERVED_KEYWORD, 6,
                            unitWithBody("plan", "job llm.run", "done -> demo.begin")),
                    refusal("entity kind is reserved", RefusalRule.RESERVED_KEYWORD, 6,
                            unitWithBody("plan", "job llm role role writer")),
                    refusal("model job without role", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "job llm task writer")),
                    refusal("model job role is reserved", RefusalRule.RESERVED_KEYWORD, 6,
                            unitWithBody("plan", "job llm task role link")),
                    refusal("role each without primitive", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "job llm task role each")),
                    refusal("token after model job", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "job llm task role writer extra")),
                    refusal("consult without role", RefusalRule.STRUCTURE, 7,
                            unitWithBody("plan", "job llm task role writer", "consult reviewer")),
                    refusal("consult role is reserved", RefusalRule.RESERVED_KEYWORD, 7,
                            unitWithBody("plan", "job llm task role writer", "consult role job")),
                    refusal("token after composite job", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "composite_job demo.all extra")),
                    refusal("call binding twice", RefusalRule.DUPLICATE_ID, 6,
                            unitWithBody("plan", "call demo.sub(mode = a, mode = b)", "done -> demo.begin")),
                    refusal("call binding without equals sign", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "call demo.sub(mode a)")),
                    refusal("call bindings not closed", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "call demo.sub(mode = a")),
                    refusal("call binding name is reserved", RefusalRule.RESERVED_KEYWORD, 6,
                            unitWithBody("plan", "call demo.sub(text = a)")),
                    refusal("call with a bare argument", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "call demo.sub mode")),
                    refusal("unknown question class", RefusalRule.STRUCTURE, 6, unitWithBody("plan", "question later")),
                    refusal("question class is an enum value", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "question Review")),
                    refusal("token after question", RefusalRule.STRUCTURE, 6,
                            unitWithBody("plan", "question review extra")),
                    refusal("question without transitions", RefusalRule.STRUCTURE, 7,
                            unitWithBody("plan", "question review", "link next go ()")));
        }

        static Stream<Arguments> linkRefusals() {
            return Stream.of(
                    refusal("unknown relation", RefusalRule.STRUCTURE, 6, unitWithBody("workspace", "link jump go ()")),
                    refusal("relation is an enum value", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link Next go ()")),
                    refusal("action is no identifier", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link next 7 ()")),
                    refusal("entity placeholder is an enum value", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link next go:<Entity> ()")),
                    refusal("entity placeholder not closed", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link next go:<entity ()")),
                    refusal("link id suffix is no identifier", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link next go:7 ()")),
                    refusal("unknown link form", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link next go [wait] ()")),
                    refusal("link form not closed", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link next go [submit ()")),
                    refusal("link without parameter list", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link next go")),
                    refusal("link parameter twice", RefusalRule.DUPLICATE_ID, 6, withLinkParams("a: int, a: text")),
                    refusal("link parameters not closed", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link next go (a: int")),
                    refusal("token after link", RefusalRule.STRUCTURE, 6,
                            unitWithBody("workspace", "link next go () extra")),
                    refusal("link without action", RefusalRule.STRUCTURE, 7,
                            unitWithBody("workspace", "link next go ()", "done -> demo.begin")),
                    refusal("action without primitive", RefusalRule.STRUCTURE, 7,
                            unitWithBody("workspace", "link next go ()", "do demo")),
                    refusal("token after action", RefusalRule.STRUCTURE, 7,
                            unitWithBody("workspace", "link next go ()", "do demo.go extra")),
                    refusal("link without transitions", RefusalRule.STRUCTURE, 7,
                            unitWithBody("workspace", "link next go ()", "do demo.go")),
                    refusal("list of a list", RefusalRule.STRUCTURE, 6, withLinkParams("a: list<list<int>>")),
                    refusal("list not closed", RefusalRule.STRUCTURE, 6, withLinkParams("a: list<int")),
                    refusal("range without upper bound", RefusalRule.STRUCTURE, 6, withLinkParams("a: text[1..]")),
                    refusal("range without dots", RefusalRule.STRUCTURE, 6, withLinkParams("a: int[1 5]")),
                    refusal("range with zero upper bound", RefusalRule.STRUCTURE, 6, withLinkParams("a: int[0..0]")),
                    refusal("range not closed", RefusalRule.STRUCTURE, 6, withLinkParams("a: int[1..5 b: int")),
                    refusal("empty enum", RefusalRule.STRUCTURE, 6, withLinkParams("a: enum[]")),
                    refusal("enum not closed", RefusalRule.STRUCTURE, 6, withLinkParams("a: enum[x|y")),
                    refusal("schema not closed", RefusalRule.STRUCTURE, 6, withLinkParams("a: schema[x")),
                    refusal("schema without bracket", RefusalRule.STRUCTURE, 6, withLinkParams("a: schema x")),
                    refusal("link parameter without colon", RefusalRule.STRUCTURE, 6, withLinkParams("a int")),
                    refusal("type is an enum value", RefusalRule.STRUCTURE, 6, withLinkParams("a: Text")),
                    refusal("link parameter name is reserved", RefusalRule.RESERVED_KEYWORD, 6,
                            withLinkParams("list: int")),
                    refusal("bare guard", RefusalRule.STRUCTURE, 7, withLinkGuard("guard")),
                    refusal("unknown guard", RefusalRule.STRUCTURE, 7, withLinkGuard("guard ready")),
                    refusal("predicate without colon", RefusalRule.STRUCTURE, 7,
                            withLinkGuard("guard predicate demo.ready")),
                    refusal("predicate primitive is reserved", RefusalRule.RESERVED_KEYWORD, 7,
                            withLinkGuard("guard predicate:demo.link")),
                    refusal("two guards without operator", RefusalRule.STRUCTURE, 7,
                            withLinkGuard("guard in_worktree has_open_findings")),
                    refusal("dangling and", RefusalRule.STRUCTURE, 7, withLinkGuard("guard in_worktree and")),
                    refusal("empty parentheses", RefusalRule.STRUCTURE, 7, withLinkGuard("guard ( )")),
                    refusal("guard nested beyond the bound", RefusalRule.STRUCTURE, 7,
                            withLinkGuard("guard " + "(".repeat(33) + "in_worktree" + ")".repeat(33))),
                    refusal("configuration keys not bracketed", RefusalRule.STRUCTURE, 8,
                            withLinkGuard("guard in_worktree", "depends_on_config project.mode")),
                    refusal("configuration keys not closed", RefusalRule.STRUCTURE, 8,
                            withLinkGuard("guard in_worktree", "depends_on_config [project.mode")),
                    refusal("configuration key segment is reserved", RefusalRule.RESERVED_KEYWORD, 8,
                            withLinkGuard("guard in_worktree", "depends_on_config [project.state]")),
                    refusal("token after configuration keys", RefusalRule.STRUCTURE, 8,
                            withLinkGuard("guard in_worktree", "depends_on_config [project.mode] extra")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource({"headerRefusals", "declarationRefusals", "stateRefusals", "linkRefusals"})
        @DisplayName("refuses the unit with the rule and line of its first violation")
        void refuses(String name, RefusalRule rule, int line, String source) {
            var diagnostic = refuse("inline.pmwf", source);

            assertAll(
                    () -> assertEquals(rule, diagnostic.rule(), diagnostic::message),
                    () -> assertEquals("inline.pmwf", diagnostic.resourceName()),
                    () -> assertEquals(line, diagnostic.line(), diagnostic::message),
                    () -> assertTrue(diagnostic.column() >= 1),
                    () -> assertFalse(diagnostic.message().isBlank()));
        }
    }

    @Nested
    @DisplayName("Columns and messages")
    class ColumnsAndMessages {

        @Test
        @DisplayName("points an empty source at line 1, column 1")
        void emptySource() {
            var diagnostic = refuse("empty.pmwf", "");

            assertAll(
                    () -> assertEquals(1, diagnostic.line()),
                    () -> assertEquals(1, diagnostic.column()));
        }

        @Test
        @DisplayName("points a refusal at the end of the unit just after its last token")
        void endOfUnit() {
            var diagnostic = refuse("short.pmwf", lines(HEADER));

            assertAll(
                    () -> assertEquals(RefusalRule.MISSING_SCOPE, diagnostic.rule()),
                    () -> assertEquals(33, diagnostic.column()));
        }

        @Test
        @DisplayName("points a refusal at the end of a line just after its last token")
        void endOfLine() {
            var diagnostic = refuse("short.pmwf", unitWithBody("plan", "auto demo.run", "done ->"));

            assertEquals(8, diagnostic.column());
        }

        @Test
        @DisplayName("points a duplicate link parameter at its second name")
        void duplicateLinkParameter() {
            var diagnostic = refuse("dup.pmwf", withLinkParams("a: int, a: text"));

            assertAll(
                    () -> assertEquals(23, diagnostic.column()),
                    () -> assertTrue(diagnostic.message().contains("'a'"), diagnostic::message));
        }

        @Test
        @DisplayName("names the reserved keyword and the position it stands in")
        void reservedKeywordMessage() {
            var diagnostic = refuse("reserved.pmwf", unitWithBody("plan", "auto demo.run", "as -> demo.begin"));

            assertAll(
                    () -> assertEquals(1, diagnostic.column()),
                    () -> assertEquals("'as' is a reserved keyword and cannot be an outcome name",
                            diagnostic.message()));
        }
    }

    @Nested
    @DisplayName("Instance header reader")
    class InstanceHeaderReading {

        @Test
        @DisplayName("refuses JSON that is no object")
        void notAnObject() {
            var refusal = assertThrows(IllegalArgumentException.class, () -> InstanceHeaderReader.read("[1]"));

            assertEquals("the instance header is not a JSON object", refusal.getMessage());
        }

        @Test
        @DisplayName("names the column where the line stops being JSON")
        void malformedColumn() {
            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> InstanceHeaderReader.read("{\"plan_id\": }"));

            assertAll(
                    () -> assertTrue(refusal.getMessage().startsWith("the instance header is not valid JSON: "),
                            refusal::getMessage),
                    () -> assertTrue(refusal.getMessage().matches(".*\\(column \\d+\\)$"), refusal::getMessage));
        }

        @Test
        @DisplayName("keeps the JSON as read and ignores members it does not interpret")
        void readsHeader() {
            var json = "{\"extra\": {\"deep\": [true]}, \"plan_id\": \"p\", \"version\": 1, \"event\": \"plan\", "
                    + "\"inputs\": {\"extra\": [1], \"config_hash\": \"c\", \"templates\": [{\"unit\": \"plan\", "
                    + "\"digest\": \"t\", \"origin\": \"project\", \"extra\": null}], \"sub_units\": [], "
                    + "\"composition_rules\": {\"revision\": \"r\", \"digest\": \"d\", \"extra\": true}, "
                    + "\"plan_facts_digest\": \"f\", \"realized_footprint_digest\": \"g\"}}";

            var header = InstanceHeaderReader.read(json);
            var inputs = header.inputs();

            assertAll(
                    () -> assertEquals("p", header.planId()),
                    () -> assertEquals(1, header.version()),
                    () -> assertEquals("plan", header.event()),
                    () -> assertEquals("c", inputs.configHash()),
                    () -> assertEquals(
                            List.of(new InstanceInputs.UnitRef("plan", Optional.empty(), "t", "project")),
                            inputs.templates()),
                    () -> assertEquals(List.of(), inputs.subUnits()),
                    () -> assertEquals(new InstanceInputs.CompositionRules("r", "d"), inputs.compositionRules()),
                    () -> assertEquals("f", inputs.planFactsDigest()),
                    () -> assertEquals(Optional.of("g"), inputs.realizedFootprintDigest()),
                    () -> assertEquals(Optional.empty(), inputs.changeType()),
                    () -> assertEquals(Optional.empty(), inputs.providersDigest()),
                    () -> assertEquals(json, header.json()));
        }

        @Test
        @DisplayName("names the nested member a refusal lies in")
        void nestedMemberPath() {
            var json = "{" + INSTANCE_MEMBERS.replace(TEMPLATES,
                    "\"templates\": [{\"unit\": \"plan\", \"digest\": 7, \"origin\": \"project\"}]") + "}";

            var refusal = assertThrows(IllegalArgumentException.class, () -> InstanceHeaderReader.read(json));

            assertEquals("member 'inputs.templates[0].digest' of the instance header must be a string",
                    refusal.getMessage());
        }
    }
}
