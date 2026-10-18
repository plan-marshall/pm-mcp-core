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

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.DecisionDeclaration;
import de.planmarshall.workflow.ast.GuardExpr;
import de.planmarshall.workflow.ast.GuardSpec;
import de.planmarshall.workflow.ast.InstanceHeader;
import de.planmarshall.workflow.ast.LinkAction;
import de.planmarshall.workflow.ast.LinkForm;
import de.planmarshall.workflow.ast.LinkRelation;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamDecl;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.ParamType;
import de.planmarshall.workflow.ast.ResultGuard;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateMigration;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.StepCompletion;
import de.planmarshall.workflow.ast.StepCompletionStatus;
import de.planmarshall.workflow.ast.TerminalOutcome;
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.ValueDeclaration;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * The recursive-descent parser of the workflow language (PM-IMPL-5, PM-WF-16): it reads one unit into the immutable
 * tree of {@link de.planmarshall.workflow.ast}. The definition, a {@code declare} line, a {@code decide} line, a state,
 * a state migration, a sub-workflow parameter, a link, a link parameter, an outcome transition and a step completion
 * carry the {@link SourceLocation} they were read at; every other node carries none.
 * <p>
 * It reads the frontmatter first, through {@link UnitFrontmatterReader} (a missing or refused frontmatter is a
 * {@link RefusalRule#FRONTMATTER} refusal), then the body from the line after the closing {@code ---}: the unit
 * header, the optional {@code instance} line of a plan instance, the {@code scope} clause (with the {@code param} and
 * {@code result} declarations of a sub-workflow), the {@code declare} and {@code decide} lines, either one
 * {@code start} line or one or more {@code entry <name> -> <state>} bindings, the {@code migrate} lines, and every
 * state form with its links, guards, transitions, result guards and step completions; a {@code call} may name the
 * callee's entry with {@code entry <name>}. A {@code link} clause without a form takes the relation's default form.
 * The keyword {@code entry} is positional, not reserved, so {@code setup.entry} stays a state identifier.
 * <p>
 * It refuses, and the first refusal wins: a frontmatter outside its closed format or field rules, a structural
 * violation of the grammar (among them a body with both a {@code start} line and entry bindings, or with neither), a
 * reserved keyword where the grammar expects an outcome name, a parameter name, an entity kind, a role name, or a
 * segment of a state, primitive or configuration key identifier, a unit without a {@code scope} clause, a duplicate
 * identifier in the file (a state, a {@code declare} name, a decision kind, a parameter, an entry bound twice, a value
 * repeated in a list value or an {@code enum[...]} type), and an {@code instance} line that is no instance header. A
 * refusal is returned as a {@link ParseResult.Refused}; no exception escapes {@link #parse}.
 * <p>
 * The parser never compares the entry bindings with the entries the frontmatter declares: that the two match, and
 * which of {@code start} and the bindings a unit must declare, are interface rules the verifier checks.
 * <p>
 * The keywords of a scope, a link relation, a link form, a step completion and an outcome class are the lower-case
 * names of the constants of {@link WorkflowScope}, {@link LinkRelation}, {@link LinkForm},
 * {@link StepCompletionStatus} and {@link OutcomeClass}, read off the enumerations as the printer writes them. The
 * decision kinds a {@code decide} line admits are {@link DecisionDeclaration#DECISION_KINDS}.
 * <p>
 * The {@code derived_from <n>} clause of the header is recorded in {@link WorkflowDefinition#derivedFrom()} as
 * {@code {<unit id> -> n}}. It is admitted only in a {@link UnitOrigin#PROJECT} unit: a default unit derives from
 * nothing, so the clause is refused there, and a revision of zero is refused for every origin.
 * <p>
 * The parser checks the grammar alone. The static verification rules of a unit (identifier length bounds, the
 * reachability, call-site and link-form rules), the merge of a multi-file unit (part versions, the derived-from
 * revisions of its files) and the project-unit refusal of an {@code instance} line are the work of later stages.
 *
 * @since 0.1
 */
public final class WorkflowParser {

    private static final String ARROW = "->";
    private static final String COMMA = ",";
    private static final String DOT = ".";
    private static final String COLON = ":";
    private static final String OPEN_BRACKET = "[";
    private static final String CLOSE_BRACKET = "]";
    private static final String OPEN_PAREN = "(";
    private static final String CLOSE_PAREN = ")";
    private static final String KEYWORD_SCOPE = "scope";
    private static final String KEYWORD_START = "start";
    private static final String KEYWORD_ENTRY = "entry";
    private static final String KEYWORD_RESULT = "result";
    private static final String KEYWORD_DECLARE = "declare";
    private static final String KEYWORD_INHERIT = "inherit";
    private static final String KEYWORD_COMPOSITE_JOB = "composite_job";
    private static final String KEYWORD_QUESTION = "question";
    private static final String KEYWORD_CONSULT = "consult";
    private static final String KEYWORD_SCHEMA = "schema";
    private static final String KEYWORD_GUARD = "guard";
    private static final String KEYWORD_LINK = "link";
    private static final String KEYWORD_STATE = "state";
    private static final String KEYWORD_LIST = "list";
    private static final String KEYWORD_INT = "int";
    private static final String KEYWORD_ENUM = "enum";
    private static final String KEYWORD_EACH = "each";
    private static final String KEYWORD_NOT = "not";

    private static final String EXPECTED_BUT_FOUND = "expected %s but found '%s'";
    private static final String EXPECTED_BUT_UNIT_ENDS = "expected %s but the unit ends";
    private static final String PARAMETER_DECLARED_TWICE = "the parameter '%s' is declared twice";
    private static final String EXPECTATION_PARAMETER_NAME = "a parameter name";

    private static final Set<String> RESERVED_KEYWORDS = Set.of("workflow", "version", "format", "derived_from",
            KEYWORD_SCOPE, "param", KEYWORD_RESULT, KEYWORD_DECLARE, KEYWORD_START, "migrate", KEYWORD_STATE, "terminal",
            "phase", KEYWORD_INHERIT, "skill", "facts", "auto", "job", KEYWORD_COMPOSITE_JOB, "call", KEYWORD_QUESTION,
            KEYWORD_LINK, KEYWORD_GUARD, "depends_on_config", "do", "llm", "role", KEYWORD_EACH, KEYWORD_CONSULT,
            "completes", "as", "and", "or", KEYWORD_NOT, "in_worktree", "has_open_findings", "all_tasks_completed",
            "predicate", "text", KEYWORD_INT, KEYWORD_ENUM, KEYWORD_SCHEMA, KEYWORD_LIST);

    private static final Set<String> DECISION_KINDS = Set.copyOf(DecisionDeclaration.DECISION_KINDS);

    private static final Set<String> QUESTION_CLASSES = Set.of("init", "clarification", "review", "task", "setup",
            "ask");

    private static final Set<String> BODY_KEYWORDS = Set.of("auto", "job", KEYWORD_COMPOSITE_JOB, "call",
            KEYWORD_QUESTION, KEYWORD_LINK);

    private static final Pattern PARAM_NAME = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern SEGMENT = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern POSITIVE_INT = Pattern.compile("[1-9]\\d*");

    /** The deepest nesting of parentheses a guard may have; it bounds the recursion of the parser. */
    private static final int MAX_GUARD_DEPTH = 32;

    private final String resourceName;
    private final List<WorkflowLexer.Line> lines;
    /** The 1-based line the body starts at, the line after the closing {@code ---} of the frontmatter. */
    private final int bodyLine;
    private int index;

    private WorkflowParser(String resourceName, List<WorkflowLexer.Line> lines, int bodyLine) {
        this.resourceName = resourceName;
        this.lines = lines;
        this.bodyLine = bodyLine;
    }

    /**
     * Parses one unit.
     *
     * @param resourceName the resource the unit is read from, named in every source location and diagnostic
     * @param source       the text of the unit
     * @param origin       whether the unit is compiled into the binary or owned by the project
     * @return the tree of the unit, or the first refusal
     */
    public static ParseResult parse(String resourceName, String source, UnitOrigin origin) {
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(origin, "origin");
        var read = UnitFrontmatterReader.read(resourceName, source);
        if (read instanceof UnitFrontmatterReader.Result.Refused(var diagnostic)) {
            return new ParseResult.Refused(diagnostic);
        }
        var frontmatter = (UnitFrontmatterReader.Result.Read) read;
        var bodyLines = WorkflowLexer.tokenize(source).stream()
                .filter(line -> line.number() >= frontmatter.bodyLine()).toList();
        var parser = new WorkflowParser(resourceName, bodyLines, frontmatter.bodyLine());
        try {
            return new ParseResult.Parsed(parser.parseUnit(frontmatter.frontmatter(), origin));
        } catch (Refusal refusal) {
            return new ParseResult.Refused(new Diagnostic(refusal.rule, resourceName, refusal.line, refusal.column,
                    refusal.getMessage()));
        }
    }

    private WorkflowDefinition parseUnit(UnitFrontmatter frontmatter, UnitOrigin origin) {
        var header = statement("workflow", "the unit header 'workflow <id> version <n> format <n>'");
        var location = location(header.first());
        var id = stateId(header);
        header.expect("version");
        var version = positiveInt(header);
        header.expect("format");
        var formatVersion = positiveInt(header);
        var derivedFrom = parseDerivedFrom(header, id, origin);
        header.expectEnd();
        var instance = parseInstance();
        var scope = parseScope();
        var params = new ArrayList<ParamDecl>();
        Optional<String> resultSchema = Optional.empty();
        if (scope == WorkflowScope.SUBWORKFLOW) {
            params.addAll(parseParams());
            resultSchema = parseResult();
        }
        var declarations = new LinkedHashMap<String, ValueDeclaration>();
        var decisions = new LinkedHashMap<String, DecisionDeclaration>();
        parseDeclarations(declarations, decisions);
        Optional<String> startState = Optional.empty();
        var entryBindings = new LinkedHashMap<String, WorkflowDefinition.EntryBinding>();
        if (nextStatementIs(KEYWORD_START)) {
            var start = nextLine();
            start.next("the keyword 'start'");
            startState = Optional.of(stateId(start));
            start.expectEnd();
            if (nextStatementIs(KEYWORD_ENTRY)) {
                throw refuse(RefusalRule.STRUCTURE, firstOfNextLine(),
                        "a unit declares a 'start' line or binds its entries, never both");
            }
        } else if (nextStatementIs(KEYWORD_ENTRY)) {
            do {
                parseEntryBinding(entryBindings);
            } while (nextStatementIs(KEYWORD_ENTRY));
            if (nextStatementIs(KEYWORD_START)) {
                throw refuse(RefusalRule.STRUCTURE, firstOfNextLine(),
                        "a unit binds its entries or declares a 'start' line, never both");
            }
        } else {
            throw refuseExpected("the 'start <state>' line or an 'entry <name> -> <state>' line");
        }
        var migrations = new ArrayList<StateMigration>();
        while (nextStatementIs("migrate")) {
            migrations.add(parseMigration());
        }
        var states = new LinkedHashMap<String, StateNode>();
        do {
            parseState(states);
        } while (hasLine());
        return new WorkflowDefinition(frontmatter, id, version, scope, params, resultSchema, instance, declarations,
                decisions, Map.of(), formatVersion, origin, derivedFrom, startState, entryBindings, migrations, states,
                location);
    }

    /** Reads one {@code entry <name> -> <state>} line, refusing an entry name bound twice. */
    private void parseEntryBinding(Map<String, WorkflowDefinition.EntryBinding> entryBindings) {
        var line = nextLine();
        var keyword = line.next("the keyword 'entry'");
        var name = identifier(line, "an entry name");
        if (entryBindings.containsKey(name.text())) {
            throw refuse(RefusalRule.DUPLICATE_ID, name, "the entry '%s' is bound twice", name.text());
        }
        line.expect(ARROW);
        var stateId = stateId(line);
        line.expectEnd();
        entryBindings.put(name.text(), new WorkflowDefinition.EntryBinding(stateId, location(keyword)));
    }

    /**
     * Reads the optional {@code derived_from <n>} clause of the header into the map {@code {<unit id> -> n}}. The
     * revision must be positive for every origin; the clause is refused on a {@link UnitOrigin#DEFAULT} unit, which
     * derives from nothing.
     */
    private Map<String, Integer> parseDerivedFrom(LineCursor header, String id, UnitOrigin origin) {
        if (!header.hasNext() || !header.peek().is("derived_from")) {
            return Map.of();
        }
        var keyword = header.next("the keyword 'derived_from'");
        var revision = positiveInt(header);
        if (origin == UnitOrigin.DEFAULT) {
            throw refuse(RefusalRule.STRUCTURE, keyword,
                    "'derived_from' is admitted only in a project unit, but '%s' is parsed as a default unit", id);
        }
        return Map.of(id, revision);
    }

    private Optional<InstanceHeader> parseInstance() {
        if (!nextStatementIs("instance")) {
            return Optional.empty();
        }
        var line = nextLine();
        var keyword = line.next("the keyword 'instance'");
        if (!line.hasNext() || line.peek().kind() != Token.Kind.JSON) {
            throw refuse(RefusalRule.INSTANCE_HEADER, keyword, "the instance line carries no JSON object");
        }
        var json = line.next("the instance header");
        try {
            return Optional.of(InstanceHeaderReader.read(json.text()));
        } catch (InstanceHeaderReader.HeaderRefusal refusal) {
            var column = json.column() + refusal.column().orElse(1) - 1;
            throw refuse(RefusalRule.INSTANCE_HEADER, json.line(), column, refusal.getMessage());
        }
    }

    private WorkflowScope parseScope() {
        if (!nextStatementIs(KEYWORD_SCOPE)) {
            if (hasLine()) {
                throw refuse(RefusalRule.MISSING_SCOPE, lines.get(index).tokens().getFirst(),
                        "the unit declares no scope clause; found '%s'", lines.get(index).tokens().getFirst().text());
            }
            throw refuseAtEnd(RefusalRule.MISSING_SCOPE, "the unit declares no scope clause");
        }
        var line = nextLine();
        line.next("the keyword 'scope'");
        var scope = keyword(line, WorkflowScope.class, "a scope");
        line.expectEnd();
        return scope;
    }

    private List<ParamDecl> parseParams() {
        var params = new ArrayList<ParamDecl>();
        var names = new HashSet<String>();
        while (nextStatementIs("param")) {
            var line = nextLine();
            var keyword = line.next("the keyword 'param'");
            var name = paramName(line);
            if (!names.add(name.text())) {
                throw refuse(RefusalRule.DUPLICATE_ID, name, PARAMETER_DECLARED_TWICE, name.text());
            }
            line.expect(COLON);
            var type = subWorkflowType(line);
            line.expectEnd();
            params.add(new ParamDecl(name.text(), type, location(keyword)));
        }
        return params;
    }

    private Optional<String> parseResult() {
        if (!nextStatementIs(KEYWORD_RESULT)) {
            return Optional.empty();
        }
        var line = nextLine();
        line.next("the keyword 'result'");
        line.expect(KEYWORD_SCHEMA);
        var schemaName = schemaName(line);
        line.expectEnd();
        return Optional.of(schemaName);
    }

    private void parseDeclarations(Map<String, ValueDeclaration> declarations,
            Map<String, DecisionDeclaration> decisions) {
        while (nextStatementIs(KEYWORD_DECLARE) || nextStatementIs("decide")) {
            var line = nextLine();
            var keyword = line.next("'declare' or 'decide'");
            if (keyword.is(KEYWORD_DECLARE)) {
                var name = paramName(line);
                if (declarations.containsKey(name.text())) {
                    throw refuse(RefusalRule.DUPLICATE_ID, name, PARAMETER_DECLARED_TWICE, name.text());
                }
                line.expect("=");
                declarations.put(name.text(), new ValueDeclaration(callValue(line), location(keyword)));
            } else {
                var kind = line.next("a decision kind");
                if (kind.kind() != Token.Kind.IDENT || !DECISION_KINDS.contains(kind.text())) {
                    throw refuse(RefusalRule.STRUCTURE, kind, EXPECTED_BUT_FOUND, "a decision kind", kind.text());
                }
                if (decisions.containsKey(kind.text())) {
                    throw refuse(RefusalRule.DUPLICATE_ID, kind, "the decision kind '%s' is declared twice",
                            kind.text());
                }
                line.expect(ARROW);
                decisions.put(kind.text(), decision(line, location(keyword)));
            }
            line.expectEnd();
        }
    }

    private DecisionDeclaration decision(LineCursor line, SourceLocation location) {
        var mode = line.next("'ask' or 'quorum'");
        if (mode.is("ask")) {
            return new DecisionDeclaration.Ask(location);
        }
        if (!mode.is("quorum")) {
            throw refuse(RefusalRule.STRUCTURE, mode, EXPECTED_BUT_FOUND, "'ask' or 'quorum'", mode.text());
        }
        line.expect(OPEN_PAREN);
        var roles = new ArrayList<String>();
        roles.add(roleName(line));
        line.expect(COMMA);
        while (!line.hasNext() || line.peek().kind() != Token.Kind.INT) {
            roles.add(roleName(line));
            line.expect(COMMA);
        }
        var score = positiveInt(line);
        line.expect(CLOSE_PAREN);
        return new DecisionDeclaration.Quorum(roles, score, location);
    }

    private StateMigration parseMigration() {
        var line = nextLine();
        var keyword = line.next("the keyword 'migrate'");
        var oldStateId = stateId(line);
        line.expect(ARROW);
        var newStateId = stateId(line);
        line.expectEnd();
        return new StateMigration(oldStateId, newStateId, location(keyword));
    }

    private void parseState(Map<String, StateNode> states) {
        var line = statement(KEYWORD_STATE, "a state");
        var keyword = line.first();
        var idToken = line.hasNext() ? line.peek() : keyword;
        var id = stateId(line);
        if (states.containsKey(id)) {
            throw refuse(RefusalRule.DUPLICATE_ID, idToken, "the state '%s' is declared twice", id);
        }
        Optional<TerminalOutcome> terminal = Optional.empty();
        if (line.consumeIf("terminal")) {
            var outcome = outcomeName(line);
            var outcomeClass = keyword(line, OutcomeClass.class, "an outcome class");
            terminal = Optional.of(new TerminalOutcome(outcome, outcomeClass));
        }
        line.expectEnd();
        var phase = parsePhase();
        var skillPath = parseSkill();
        var factsPrimitiveId = parseFacts();
        StateBehavior behavior = terminal.isPresent() ? new StateBehavior.TerminalState() : parseBody();
        states.put(id, new StateNode(id, phase, terminal, skillPath, factsPrimitiveId, behavior, location(keyword)));
    }

    private String parsePhase() {
        var line = statement("phase", "the 'phase' clause of the state");
        String phase;
        if (line.consumeIf(KEYWORD_INHERIT)) {
            phase = KEYWORD_INHERIT;
        } else {
            phase = identifier(line, "a phase").text();
        }
        line.expectEnd();
        return phase;
    }

    private Optional<String> parseSkill() {
        if (!nextStatementIs("skill")) {
            return Optional.empty();
        }
        var line = nextLine();
        line.next("the keyword 'skill'");
        line.expect("pm");
        var path = new StringBuilder("pm");
        do {
            line.expect("/");
            path.append('/').append(identifier(line, "a segment of the skill path").text());
        } while (line.hasNext() && line.peek().is("/"));
        line.expectEnd();
        return Optional.of(path.toString());
    }

    private Optional<String> parseFacts() {
        if (!nextStatementIs("facts")) {
            return Optional.empty();
        }
        var line = nextLine();
        line.next("the keyword 'facts'");
        var primitive = primitiveId(line);
        line.expectEnd();
        return Optional.of(primitive);
    }

    private StateBehavior parseBody() {
        if (!hasLine() || !BODY_KEYWORDS.contains(firstOfNextLine().text())
                || firstOfNextLine().kind() != Token.Kind.IDENT) {
            throw refuseExpected("a state body (auto, job, composite_job, call, question or link)");
        }
        return switch (firstOfNextLine().text()) {
            case "auto" -> parseAuto();
            case "job", KEYWORD_COMPOSITE_JOB -> parseJob();
            case "call" -> parseCall();
            case KEYWORD_QUESTION -> parseQuestion();
            default -> new StateBehavior.InteractiveLinks(parseLinks(true));
        };
    }

    private StateBehavior parseAuto() {
        var line = nextLine();
        line.next("the keyword 'auto'");
        var primitive = primitiveId(line);
        line.expectEnd();
        return new StateBehavior.AutoStep(primitive, parseTransitions());
    }

    private StateBehavior parseJob() {
        var line = nextLine();
        var keyword = line.next("'job' or 'composite_job'");
        if (keyword.is("job") && line.hasNext() && line.peek().is("llm") && !line.peekIs(1, DOT)) {
            return parseModelJob(line);
        }
        var primitive = primitiveId(line);
        line.expectEnd();
        return new StateBehavior.JobStep(primitive, keyword.is(KEYWORD_COMPOSITE_JOB), parseTransitions());
    }

    private StateBehavior parseModelJob(LineCursor line) {
        line.expect("llm");
        var entityKind = checkedIdentifier(line, "an entity kind");
        line.expect("role");
        Optional<String> roleName = Optional.empty();
        Optional<String> fanOutPrimitiveId = Optional.empty();
        Optional<String> rolesPrimitiveId = Optional.empty();
        if (line.consumeIf(KEYWORD_EACH)) {
            rolesPrimitiveId = Optional.of(primitiveId(line));
        } else {
            roleName = Optional.of(roleName(line));
            if (line.consumeIf(KEYWORD_EACH)) {
                fanOutPrimitiveId = Optional.of(primitiveId(line));
            }
        }
        var ask = line.consumeIf("ask");
        line.expectEnd();
        var consultRoles = new ArrayList<String>();
        while (nextStatementIs(KEYWORD_CONSULT)) {
            var consult = nextLine();
            consult.next("the keyword 'consult'");
            consult.expect("role");
            consultRoles.add(roleName(consult));
            consult.expectEnd();
        }
        return new StateBehavior.ModelJob(entityKind, roleName, fanOutPrimitiveId, rolesPrimitiveId, ask,
                consultRoles, parseTransitions());
    }

    private StateBehavior parseCall() {
        var line = nextLine();
        line.next("the keyword 'call'");
        var subWorkflowId = stateId(line);
        Optional<String> entry = Optional.empty();
        if (line.consumeIf(KEYWORD_ENTRY)) {
            entry = Optional.of(identifier(line, "the name of the callee's entry").text());
        }
        var bindings = new LinkedHashMap<String, CallValue>();
        if (line.consumeIf(OPEN_PAREN) && !line.consumeIf(CLOSE_PAREN)) {
            do {
                var name = paramName(line);
                if (bindings.containsKey(name.text())) {
                    throw refuse(RefusalRule.DUPLICATE_ID, name, "the parameter '%s' is bound twice", name.text());
                }
                line.expect("=");
                bindings.put(name.text(), callValue(line));
            } while (line.consumeIf(COMMA));
            line.expect(CLOSE_PAREN);
        }
        line.expectEnd();
        return new StateBehavior.SubWorkflowCall(subWorkflowId, entry, bindings, parseTransitions());
    }

    private StateBehavior parseQuestion() {
        var line = nextLine();
        line.next("the keyword 'question'");
        var questionClass = line.next("a question class");
        if (questionClass.kind() != Token.Kind.IDENT || !QUESTION_CLASSES.contains(questionClass.text())) {
            throw refuse(RefusalRule.STRUCTURE, questionClass, EXPECTED_BUT_FOUND, "a question class",
                    questionClass.text());
        }
        line.expectEnd();
        var transitions = parseTransitions();
        return new StateBehavior.QuestionWait(questionClass.text(), transitions, parseLinks(false));
    }

    private List<LinkSpec> parseLinks(boolean required) {
        var links = new ArrayList<LinkSpec>();
        if (required || nextStatementIs(KEYWORD_LINK)) {
            do {
                links.add(parseLink());
            } while (nextStatementIs(KEYWORD_LINK));
        }
        return links;
    }

    private LinkSpec parseLink() {
        var line = statement(KEYWORD_LINK, "a link clause");
        var keyword = line.first();
        var relation = keyword(line, LinkRelation.class, "a link relation");
        var idPattern = new StringBuilder(identifier(line, "an action").text());
        if (line.consumeIf(COLON)) {
            idPattern.append(COLON);
            if (line.consumeIf("<")) {
                idPattern.append('<').append(identifier(line, "an entity placeholder").text()).append('>');
                line.expect(">");
            } else {
                idPattern.append(identifier(line, "the suffix of the link id").text());
            }
        }
        var form = relation.defaultForm();
        if (line.consumeIf(OPEN_BRACKET)) {
            form = keyword(line, LinkForm.class, "a link form");
            line.expect(CLOSE_BRACKET);
        }
        line.expect(OPEN_PAREN);
        var params = new ArrayList<ParamSpec>();
        var names = new HashSet<String>();
        if (!line.consumeIf(CLOSE_PAREN)) {
            do {
                var param = paramSpec(line);
                if (!names.add(param.name())) {
                    throw refuse(RefusalRule.DUPLICATE_ID, param.location().line(), param.location().column(),
                            PARAMETER_DECLARED_TWICE.formatted(param.name()));
                }
                params.add(param);
            } while (line.consumeIf(COMMA));
            line.expect(CLOSE_PAREN);
        }
        line.expectEnd();
        var guard = parseGuard();
        var action = statement("do", "the 'do <primitive>' action of the link");
        var primitive = primitiveId(action);
        action.expectEnd();
        return new LinkSpec(relation, idPattern.toString(), form, params, guard,
                new LinkAction.ExecutePrimitive(primitive), parseTransitions(), location(keyword));
    }

    private ParamSpec paramSpec(LineCursor line) {
        var name = paramName(line);
        line.expect(COLON);
        var type = typeSpec(line);
        var optional = line.consumeIf("?");
        return new ParamSpec(name.text(), type, optional, location(name));
    }

    private Optional<GuardSpec> parseGuard() {
        if (!nextStatementIs(KEYWORD_GUARD)) {
            return Optional.empty();
        }
        var line = nextLine();
        line.next("the keyword 'guard'");
        var expr = guardExpr(line, 0);
        line.expectEnd();
        var keys = new ArrayList<String>();
        if (nextStatementIs("depends_on_config")) {
            var config = nextLine();
            config.next("the keyword 'depends_on_config'");
            config.expect(OPEN_BRACKET);
            do {
                keys.add(configKey(config));
            } while (config.consumeIf(COMMA));
            config.expect(CLOSE_BRACKET);
            config.expectEnd();
        }
        return Optional.of(new GuardSpec(expr, keys));
    }

    private GuardExpr guardExpr(LineCursor line, int depth) {
        var left = guardTerm(line, depth);
        while (line.consumeIf("or")) {
            left = new GuardExpr.Or(left, guardTerm(line, depth));
        }
        return left;
    }

    private GuardExpr guardTerm(LineCursor line, int depth) {
        var left = guardFactor(line, depth);
        while (line.consumeIf("and")) {
            left = new GuardExpr.And(left, guardFactor(line, depth));
        }
        return left;
    }

    private GuardExpr guardFactor(LineCursor line, int depth) {
        var negated = line.consumeIf(KEYWORD_NOT);
        GuardExpr expr;
        if (line.hasNext() && line.peek().is(OPEN_PAREN)) {
            var open = line.next("'('");
            if (depth >= MAX_GUARD_DEPTH) {
                throw refuse(RefusalRule.STRUCTURE, open, "the guard nests deeper than %d parentheses",
                        MAX_GUARD_DEPTH);
            }
            expr = guardExpr(line, depth + 1);
            line.expect(CLOSE_PAREN);
        } else {
            expr = guardAtom(line);
        }
        return negated ? new GuardExpr.Not(expr) : expr;
    }

    private GuardExpr guardAtom(LineCursor line) {
        var what = "a guard (in_worktree, has_open_findings, all_tasks_completed or predicate:<primitive>)";
        var atom = line.next(what);
        return switch (atom.kind() == Token.Kind.IDENT ? atom.text() : "") {
            case "in_worktree" -> new GuardExpr.InWorktree();
            case "has_open_findings" -> new GuardExpr.HasOpenFindings();
            case "all_tasks_completed" -> new GuardExpr.AllTasksCompleted();
            case "predicate" -> {
                line.expect(COLON);
                yield new GuardExpr.CustomPredicate(primitiveId(line));
            }
            default -> throw refuse(RefusalRule.STRUCTURE, atom, EXPECTED_BUT_FOUND, what, atom.text());
        };
    }

    private List<OutcomeTransition> parseTransitions() {
        if (!nextIsTransition()) {
            throw refuseExpected("an outcome transition '<outcome> -> <state>'");
        }
        var transitions = new ArrayList<OutcomeTransition>();
        while (nextIsTransition()) {
            transitions.add(parseTransition());
        }
        return transitions;
    }

    private OutcomeTransition parseTransition() {
        var line = nextLine();
        var outcomeToken = line.peek();
        var outcome = outcomeName(line);
        line.expect(ARROW);
        var target = stateId(line);
        line.expectEnd();
        Optional<ResultGuard> resultGuard = Optional.empty();
        if (nextStatementIs(KEYWORD_GUARD)) {
            var guard = nextLine();
            guard.next("the keyword 'guard'");
            var negated = guard.consumeIf(KEYWORD_NOT);
            guard.expect(KEYWORD_RESULT);
            guard.expect(DOT);
            var field = paramName(guard);
            guard.expectEnd();
            resultGuard = Optional.of(new ResultGuard(field.text(), negated));
        }
        var completions = new ArrayList<StepCompletion>();
        while (nextStatementIs("completes")) {
            completions.add(parseCompletion());
        }
        return new OutcomeTransition(outcome, target, resultGuard, completions, location(outcomeToken));
    }

    private StepCompletion parseCompletion() {
        var line = nextLine();
        var keyword = line.next("the keyword 'completes'");
        var stepId = segment(line, "a step segment") + COLON;
        line.expect(COLON);
        stepId += segment(line, "a step segment");
        line.expect("as");
        var completion = keyword(line, StepCompletionStatus.class, "a step completion");
        line.expectEnd();
        return new StepCompletion(stepId, completion, location(keyword));
    }

    private ParamType typeSpec(LineCursor line) {
        if (line.consumeIf(KEYWORD_LIST)) {
            line.expect("<");
            var element = scalarType(line);
            line.expect(">");
            return new ParamType.ListOf(element);
        }
        return scalarType(line);
    }

    private ParamType scalarType(LineCursor line) {
        var type = line.next("a type (text, int, enum or schema)");
        return switch (type.kind() == Token.Kind.IDENT ? type.text() : "") {
            case "text" -> {
                var range = intRange(line);
                yield new ParamType.Text(range.map(Range::low), range.map(Range::high));
            }
            case KEYWORD_INT -> intType(line);
            case KEYWORD_ENUM -> enumValues(line);
            case KEYWORD_SCHEMA -> new ParamType.SchemaRef(schemaName(line));
            default -> throw refuse(RefusalRule.STRUCTURE, type, EXPECTED_BUT_FOUND,
                    "a type (text, int, enum, schema or list)", type.text());
        };
    }

    private ParamType subWorkflowType(LineCursor line) {
        var what = "a sub-workflow parameter type (enum, int or list<enum>)";
        var type = line.next(what);
        return switch (type.kind() == Token.Kind.IDENT ? type.text() : "") {
            case KEYWORD_ENUM -> enumValues(line);
            case KEYWORD_INT -> intType(line);
            case KEYWORD_LIST -> {
                line.expect("<");
                line.expect(KEYWORD_ENUM);
                var element = enumValues(line);
                line.expect(">");
                yield new ParamType.ListOf(element);
            }
            default -> throw refuse(RefusalRule.STRUCTURE, type, EXPECTED_BUT_FOUND, what, type.text());
        };
    }

    private Optional<Range> intRange(LineCursor line) {
        if (!line.consumeIf(OPEN_BRACKET)) {
            return Optional.empty();
        }
        var low = intValue(line, true);
        line.expect("..");
        var high = intValue(line, false);
        line.expect(CLOSE_BRACKET);
        return Optional.of(new Range(low, high));
    }

    private ParamType.Int intType(LineCursor line) {
        var range = intRange(line);
        return new ParamType.Int(range.map(Range::low), range.map(Range::high));
    }

    private ParamType.EnumOf enumValues(LineCursor line) {
        line.expect(OPEN_BRACKET);
        var values = new ArrayList<String>();
        do {
            addDistinct(values, enumValue(line), "set");
        } while (line.consumeIf("|"));
        line.expect(CLOSE_BRACKET);
        return new ParamType.EnumOf(values);
    }

    /** Adds an enum value to a list or set of values, refusing a value the list already holds. */
    private void addDistinct(List<String> values, Token value, String container) {
        if (values.contains(value.text())) {
            throw refuse(RefusalRule.DUPLICATE_ID, value, "the value '%s' occurs twice in the %s", value.text(),
                    container);
        }
        values.add(value.text());
    }

    private String schemaName(LineCursor line) {
        line.expect(OPEN_BRACKET);
        var name = segment(line, "a schema name");
        line.expect(CLOSE_BRACKET);
        return name;
    }

    private CallValue callValue(LineCursor line) {
        if (line.consumeIf(OPEN_BRACKET)) {
            var values = new ArrayList<String>();
            do {
                addDistinct(values, enumValue(line), "list");
            } while (line.consumeIf(COMMA));
            line.expect(CLOSE_BRACKET);
            return new CallValue.EnumList(values);
        }
        if (line.hasNext() && line.peek().kind() == Token.Kind.INT) {
            return new CallValue.IntValue(intValue(line, true));
        }
        return new CallValue.EnumValue(enumValue(line).text());
    }

    private Token enumValue(LineCursor line) {
        var value = line.next("an enum value");
        if (value.kind() != Token.Kind.IDENT && value.kind() != Token.Kind.ENUM_VALUE) {
            throw refuse(RefusalRule.STRUCTURE, value, EXPECTED_BUT_FOUND, "an enum value", value.text());
        }
        return value;
    }

    private int positiveInt(LineCursor line) {
        return intValue(line, false);
    }

    private int intValue(LineCursor line, boolean zeroAllowed) {
        var what = zeroAllowed ? "an integer" : "a positive integer";
        var value = line.next(what);
        var valid = value.kind() == Token.Kind.INT
                && (POSITIVE_INT.matcher(value.text()).matches() || zeroAllowed && "0".equals(value.text()));
        if (!valid) {
            throw refuse(RefusalRule.STRUCTURE, value, EXPECTED_BUT_FOUND, what, value.text());
        }
        try {
            return Integer.parseInt(value.text());
        } catch (NumberFormatException _) {
            throw refuse(RefusalRule.STRUCTURE, value, "the integer '%s' is out of range", value.text());
        }
    }

    private String stateId(LineCursor line) {
        var id = new StringBuilder(checkedIdentifier(line, "a state identifier"));
        while (line.consumeIf(DOT)) {
            id.append('.').append(checkedIdentifier(line, "a segment of a state identifier"));
        }
        return id.toString();
    }

    private String primitiveId(LineCursor line) {
        var domain = checkedIdentifier(line, "a primitive identifier '<domain>.<name>'");
        line.expect(DOT);
        return domain + DOT + checkedIdentifier(line, "the name of a primitive");
    }

    private String configKey(LineCursor line) {
        var key = new StringBuilder(checkedIdentifier(line, "a configuration key"));
        while (line.consumeIf(DOT)) {
            key.append('.').append(checkedIdentifier(line, "a segment of a configuration key"));
        }
        return key.toString();
    }

    private String outcomeName(LineCursor line) {
        return checkedIdentifier(line, "an outcome name");
    }

    private String roleName(LineCursor line) {
        return checkedIdentifier(line, "a role name");
    }

    /**
     * Reads a keyword that names a constant of an enumeration: the lower-case name of the constant, the form the
     * printer writes. The admitted keywords are read off the constants, so a new constant is admitted with no change
     * here.
     */
    private <E extends Enum<E>> E keyword(LineCursor line, Class<E> type, String what) {
        var constants = type.getEnumConstants();
        var keywords = Arrays.stream(constants).map(WorkflowParser::keywordOf).toList();
        var expected = "%s (%s)".formatted(what, listing(keywords));
        var value = line.next(expected);
        var position = value.kind() == Token.Kind.IDENT ? keywords.indexOf(value.text()) : -1;
        if (position < 0) {
            throw refuse(RefusalRule.STRUCTURE, value, EXPECTED_BUT_FOUND, expected, value.text());
        }
        return constants[position];
    }

    private static String keywordOf(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT);
    }

    /** Lists the words as prose: {@code a, b or c}. */
    private static String listing(List<String> words) {
        if (words.size() == 1) {
            return words.getFirst();
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " or " + words.getLast();
    }

    private Token paramName(LineCursor line) {
        var name = line.next(EXPECTATION_PARAMETER_NAME);
        rejectReserved(name, EXPECTATION_PARAMETER_NAME);
        if (name.kind() != Token.Kind.IDENT || !PARAM_NAME.matcher(name.text()).matches()) {
            throw refuse(RefusalRule.STRUCTURE, name, EXPECTED_BUT_FOUND, EXPECTATION_PARAMETER_NAME, name.text());
        }
        return name;
    }

    private String segment(LineCursor line, String what) {
        var value = line.next(what);
        if (value.kind() != Token.Kind.IDENT || !SEGMENT.matcher(value.text()).matches()) {
            throw refuse(RefusalRule.STRUCTURE, value, EXPECTED_BUT_FOUND, what, value.text());
        }
        return value.text();
    }

    /** An identifier that may not be a reserved keyword. */
    private String checkedIdentifier(LineCursor line, String what) {
        var value = line.next(what);
        rejectReserved(value, what);
        return requireIdentifier(value, what).text();
    }

    private Token identifier(LineCursor line, String what) {
        return requireIdentifier(line.next(what), what);
    }

    private Token requireIdentifier(Token value, String what) {
        if (value.kind() != Token.Kind.IDENT) {
            throw refuse(RefusalRule.STRUCTURE, value, EXPECTED_BUT_FOUND, what, value.text());
        }
        return value;
    }

    private void rejectReserved(Token value, String what) {
        if (value.kind() == Token.Kind.IDENT && RESERVED_KEYWORDS.contains(value.text())) {
            throw refuse(RefusalRule.RESERVED_KEYWORD, value, "'%s' is a reserved keyword and cannot be %s",
                    value.text(), what);
        }
    }

    private boolean hasLine() {
        return index < lines.size();
    }

    private Token firstOfNextLine() {
        return lines.get(index).tokens().getFirst();
    }

    private boolean nextStatementIs(String keyword) {
        return hasLine() && firstOfNextLine().kind() == Token.Kind.IDENT && firstOfNextLine().is(keyword);
    }

    private boolean nextIsTransition() {
        if (!hasLine()) {
            return false;
        }
        var tokens = lines.get(index).tokens();
        return tokens.size() >= 2 && tokens.get(1).is(ARROW);
    }

    private LineCursor nextLine() {
        return new LineCursor(lines.get(index++));
    }

    /** Takes the next line, which must open with the keyword, and returns it with the keyword consumed. */
    private LineCursor statement(String keyword, String what) {
        if (!nextStatementIs(keyword)) {
            throw refuseExpected(what);
        }
        var line = nextLine();
        line.next(what);
        return line;
    }

    /** The refusal of the next line, or of the end of the unit, where the grammar expects the given construct. */
    private Refusal refuseExpected(String what) {
        if (!hasLine()) {
            return refuseAtEnd(RefusalRule.STRUCTURE, EXPECTED_BUT_UNIT_ENDS.formatted(what));
        }
        return refuse(RefusalRule.STRUCTURE, firstOfNextLine(), EXPECTED_BUT_FOUND, what, firstOfNextLine().text());
    }

    private SourceLocation location(Token token) {
        return new SourceLocation(resourceName, token.line(), token.column());
    }

    private Refusal refuse(RefusalRule rule, Token at, String format, Object... arguments) {
        return refuse(rule, at.line(), at.column(), format.formatted(arguments));
    }

    private Refusal refuse(RefusalRule rule, int line, int column, String message) {
        return new Refusal(rule, line, column, message);
    }

    /**
     * The refusal of the end of the unit: just after the last token of the body, or at the first line of the body when
     * the body holds no token.
     */
    private Refusal refuseAtEnd(RefusalRule rule, String message) {
        if (lines.isEmpty()) {
            return refuse(rule, bodyLine, 1, message);
        }
        var last = lines.getLast();
        return refuse(rule, last.number(), last.tokens().getLast().endColumn(), message);
    }

    /** The bounds of an {@code int_range}, {@code [<low>..<high>]}. */
    private record Range(int low, int high) {
    }

    /** The tokens of one line, read from left to right. */
    private final class LineCursor {

        private final WorkflowLexer.Line line;
        private int position;

        private LineCursor(WorkflowLexer.Line line) {
            this.line = line;
        }

        Token first() {
            return line.tokens().getFirst();
        }

        boolean hasNext() {
            return position < line.tokens().size();
        }

        Token peek() {
            return line.tokens().get(position);
        }

        boolean peekIs(int offset, String text) {
            var at = position + offset;
            return at < line.tokens().size() && line.tokens().get(at).is(text);
        }

        boolean consumeIf(String text) {
            if (hasNext() && peek().is(text)) {
                position++;
                return true;
            }
            return false;
        }

        Token next(String what) {
            if (!hasNext()) {
                throw refuse(RefusalRule.STRUCTURE, line.number(), line.tokens().getLast().endColumn(),
                        "expected %s but the line ends".formatted(what));
            }
            return line.tokens().get(position++);
        }

        void expect(String text) {
            var token = next("'" + text + "'");
            if (!token.is(text)) {
                throw refuse(RefusalRule.STRUCTURE, token, EXPECTED_BUT_FOUND, "'" + text + "'", token.text());
            }
        }

        void expectEnd() {
            if (hasNext()) {
                throw refuse(RefusalRule.STRUCTURE, peek(), "unexpected '%s' at the end of the line", peek().text());
            }
        }
    }

    /**
     * The first refusal, carried out of the recursive descent and turned into a {@link Diagnostic} by
     * {@link #parse}; it never leaves the parser.
     */
    private static final class Refusal extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private final RefusalRule rule;
        private final int line;
        private final int column;

        private Refusal(RefusalRule rule, int line, int column, String message) {
            super(message, null, false, false);
            this.rule = rule;
            this.line = line;
            this.column = column;
        }
    }
}
