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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.DecisionDeclaration;
import de.planmarshall.workflow.ast.GuardExpr;
import de.planmarshall.workflow.ast.GuardSpec;
import de.planmarshall.workflow.ast.LinkAction;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.ParamType;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.StepCompletion;
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * Prints the tree of a unit back to the source of the workflow language (PM-IMPL-5), so that the parser reads the
 * printed source into the same tree, source locations aside.
 * <p>
 * The output is canonical: the frontmatter between its two {@code ---} lines in the order of its field table (name,
 * description, type, icon, each entry with its description, each exit with its description, modification date, version,
 * author, signature), each value plain when a plain scalar can carry it and double-quoted otherwise; the unit header,
 * the {@code instance} line with the header JSON exactly as it was read, the {@code scope} clause (with the
 * {@code param} and {@code result} declarations of a sub-workflow), the {@code declare} lines, the {@code decide} lines,
 * the {@code start} line or the {@code entry <name> -> <state>} bindings, the {@code migrate} lines, and the states in
 * declaration order, a {@code call} with the entry it names, each preceded by a blank line and followed by its clauses, indented by nesting. Every link names
 * its form explicitly, and a guard carries exactly the parentheses its precedence ({@code not} before {@code and}
 * before {@code or}, both operators left-associative) needs; a negated operand that is no single guard is always
 * parenthesised, because the grammar admits no {@code not not}.
 * <p>
 * The header carries {@code derived_from <n>} when {@link WorkflowDefinition#derivedFrom()} holds a revision for the
 * unit's own id, the entry the parser records for a single-file project unit. What the parser of one file does not
 * record is not printed: the part versions of a merged unit and the derived-from revisions of its part files. A range
 * of a {@code text} or {@code int} type is printed when both of its bounds are present, as the grammar writes one only
 * with both. The printer checks nothing: it prints the tree it is given.
 *
 * @since 0.1
 */
public final class WorkflowPrinter {

    private static final String INDENT = "    ";
    private static final String LIST_SEPARATOR = ", ";

    /** The binding strength of {@code or}, the weakest operator. */
    private static final int PRECEDENCE_OR = 1;
    /** The binding strength of {@code and}. */
    private static final int PRECEDENCE_AND = 2;
    /** The binding strength of {@code not}. */
    private static final int PRECEDENCE_NOT = 3;
    /** The context of the operand of {@code not}: only a single guard binds that tightly. */
    private static final int PRECEDENCE_NOT_OPERAND = 4;

    private final StringBuilder out = new StringBuilder();

    private WorkflowPrinter() {
    }

    /**
     * Prints a unit.
     *
     * @param definition the tree of the unit
     * @return the source of the unit, every line ended by a line feed
     */
    public static String print(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        var printer = new WorkflowPrinter();
        printer.printUnit(definition);
        return printer.out.toString();
    }

    /**
     * Prints the frontmatter between its two {@code ---} lines, in the order of the field table: name, description,
     * type, icon, each entry with its description, each exit with its description, modification date, version, author
     * and signature.
     */
    private void printFrontmatter(UnitFrontmatter frontmatter) {
        line(0, UnitFrontmatterReader.DELIMITER);
        field("name", frontmatter.name());
        field("description", frontmatter.description());
        field("type", frontmatter.type().wireName());
        field("icon", frontmatter.icon());
        frontmatter.entries().forEach(entry -> {
            field("entry", entry.name());
            entry.description().ifPresent(description -> field("entry_description", description));
        });
        frontmatter.exits().forEach(exit -> {
            field("exit", exit.name());
            exit.description().ifPresent(description -> field("exit_description", description));
        });
        field("modification_date", frontmatter.modificationDate().toString());
        field("version", frontmatter.version());
        field("author", frontmatter.author());
        frontmatter.signature().ifPresent(signature -> field("signature", signature));
        line(0, UnitFrontmatterReader.DELIMITER);
    }

    private void field(String key, String value) {
        line(0, key + ": " + scalar(value));
    }

    /**
     * Writes a frontmatter value plain when a plain scalar can carry it ({@link UnitFrontmatterReader#isPlain}), and
     * double-quoted with {@code \"} and {@code \\} escaped otherwise.
     */
    private static String scalar(String value) {
        if (UnitFrontmatterReader.isPlain(value)) {
            return value;
        }
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private void printUnit(WorkflowDefinition definition) {
        printFrontmatter(definition.frontmatter());
        var unitHeader = new StringBuilder("workflow %s version %d format %d".formatted(definition.id(),
                definition.version(), definition.formatVersion()));
        var derivedFrom = definition.derivedFrom().get(definition.id());
        if (derivedFrom != null) {
            unitHeader.append(" derived_from ").append(derivedFrom);
        }
        line(0, unitHeader.toString());
        definition.instance().ifPresent(header -> line(0, "instance " + header.json()));
        line(0, "scope " + lower(definition.scope()));
        if (definition.scope() == WorkflowScope.SUBWORKFLOW) {
            definition.params().forEach(param -> line(0, "param %s : %s".formatted(param.name(), type(param.type()))));
            definition.resultSchema().ifPresent(schema -> line(0, "result schema[" + schema + "]"));
        }
        definition.declarations().forEach((name, declaration) -> line(0, "declare %s = %s".formatted(name,
                value(declaration.value()))));
        definition.decisions().forEach((kind, decision) -> line(0, "decide %s -> %s".formatted(kind,
                decision(decision))));
        definition.startState().ifPresent(startState -> line(0, "start " + startState));
        definition.entryBindings().forEach((entry, binding) -> line(0, "entry %s -> %s".formatted(entry,
                binding.stateId())));
        definition.migrations().forEach(migration -> line(0, "migrate %s -> %s".formatted(migration.oldStateId(),
                migration.newStateId())));
        definition.states().values().forEach(this::printState);
    }

    private void printState(StateNode state) {
        out.append('\n');
        var header = new StringBuilder("state ").append(state.id());
        state.terminalOutcome().ifPresent(outcome -> header.append(" terminal ").append(outcome.id()).append(' ')
                .append(lower(outcome.outcomeClass())));
        line(0, header.toString());
        line(1, "phase " + state.phase());
        state.skillPath().ifPresent(path -> line(1, "skill " + path));
        state.factsPrimitiveId().ifPresent(primitive -> line(1, "facts " + primitive));
        printBehavior(state.behavior());
    }

    private void printBehavior(StateBehavior behavior) {
        switch (behavior) {
            case StateBehavior.InteractiveLinks(var links) -> printLinks(links);
            case StateBehavior.AutoStep(var primitiveId, var transitions) -> {
                line(1, "auto " + primitiveId);
                printTransitions(2, transitions);
            }
            case StateBehavior.JobStep(var primitiveId, var composite, var transitions) -> {
                line(1, (composite ? "composite_job " : "job ") + primitiveId);
                printTransitions(2, transitions);
            }
            case StateBehavior.ModelJob job -> printModelJob(job);
            case StateBehavior.SubWorkflowCall(var subWorkflowId, var entry, var paramBindings, var transitions) -> {
                line(1, "call " + subWorkflowId + entry.map(name -> " entry " + name).orElse("")
                        + bindings(paramBindings));
                printTransitions(2, transitions);
            }
            case StateBehavior.QuestionWait(var questionClass, var transitions, var links) -> {
                line(1, "question " + questionClass);
                printTransitions(2, transitions);
                printLinks(links);
            }
            case StateBehavior.TerminalState _ -> {
                // a terminal state has no body
            }
        }
    }

    private void printModelJob(StateBehavior.ModelJob job) {
        var clause = new StringBuilder("job llm ").append(job.entityKind()).append(" role");
        job.roleName().ifPresent(role -> clause.append(' ').append(role));
        job.fanOutPrimitiveId().ifPresent(primitive -> clause.append(" each ").append(primitive));
        job.rolesPrimitiveId().ifPresent(primitive -> clause.append(" each ").append(primitive));
        if (job.askForm() != StateBehavior.AskForm.NONE) {
            clause.append(' ').append(job.askForm().sourceText());
        }
        line(1, clause.toString());
        job.consultRoles().forEach(role -> line(1, "consult role " + role));
        printTransitions(2, job.transitions());
    }

    private void printLinks(List<LinkSpec> links) {
        for (var link : links) {
            line(1, "link %s %s [%s] (%s)".formatted(lower(link.rel()), link.idPattern(), lower(link.form()),
                    link.freeParams().stream().map(WorkflowPrinter::param)
                            .collect(Collectors.joining(LIST_SEPARATOR))));
            link.guard().ifPresent(this::printGuard);
            line(2, "do " + action(link.action()));
            printTransitions(3, link.transitions());
        }
    }

    private void printGuard(GuardSpec guard) {
        line(2, "guard " + guard(guard.expr(), PRECEDENCE_OR));
        if (!guard.dependsOnConfig().isEmpty()) {
            line(2, "depends_on_config [" + String.join(LIST_SEPARATOR, guard.dependsOnConfig()) + "]");
        }
    }

    private void printTransitions(int depth, List<OutcomeTransition> transitions) {
        for (var transition : transitions) {
            line(depth, transition.outcomeName() + " -> " + transition.targetStateId());
            transition.resultGuard().ifPresent(guard -> line(depth + 1,
                    "guard " + (guard.negated() ? "not " : "") + "result." + guard.field()));
            transition.completions().forEach(completion -> line(depth + 1, completion(completion)));
        }
    }

    private void line(int depth, String text) {
        out.append(INDENT.repeat(depth)).append(text).append('\n');
    }

    private static String completion(StepCompletion completion) {
        return "completes " + completion.stepId() + " as " + lower(completion.status());
    }

    private static String action(LinkAction action) {
        return switch (action) {
            case LinkAction.ExecutePrimitive(var primitiveId) -> primitiveId;
        };
    }

    private static String param(ParamSpec param) {
        return param.name() + ": " + type(param.type()) + (param.optional() ? "?" : "");
    }

    private static String type(ParamType type) {
        return switch (type) {
            case ParamType.Text(var minLength, var maxLength) -> "text" + range(minLength, maxLength);
            case ParamType.Int(var min, var max) -> "int" + range(min, max);
            case ParamType.EnumOf(var values) -> "enum[" + String.join("|", values) + "]";
            case ParamType.SchemaRef(var schemaName) -> "schema[" + schemaName + "]";
            case ParamType.ListOf(var elementType) -> "list<" + type(elementType) + ">";
        };
    }

    private static String range(Optional<Integer> low, Optional<Integer> high) {
        if (low.isEmpty() || high.isEmpty()) {
            return "";
        }
        return "[" + low.orElseThrow() + ".." + high.orElseThrow() + "]";
    }

    private static String value(CallValue value) {
        return switch (value) {
            case CallValue.EnumValue(var enumValue) -> enumValue;
            case CallValue.IntValue(var intValue) -> String.valueOf(intValue);
            case CallValue.EnumList(var values) -> "[" + String.join(LIST_SEPARATOR, values) + "]";
        };
    }

    private static String bindings(Map<String, CallValue> bindings) {
        if (bindings.isEmpty()) {
            return "";
        }
        return bindings.entrySet().stream()
                .map(binding -> binding.getKey() + " = " + value(binding.getValue()))
                .collect(Collectors.joining(LIST_SEPARATOR, "(", ")"));
    }

    private static String decision(DecisionDeclaration decision) {
        return switch (decision) {
            case DecisionDeclaration.Ask _ -> "ask";
            case DecisionDeclaration.Quorum(var roles, var score, _) -> "quorum(" + String.join(LIST_SEPARATOR, roles)
                    + LIST_SEPARATOR + score + ")";
        };
    }

    /**
     * Prints a guard expression in a context of the given binding strength, parenthesising it when it binds weaker.
     */
    private static String guard(GuardExpr expr, int context) {
        return switch (expr) {
            case GuardExpr.InWorktree _ -> "in_worktree";
            case GuardExpr.HasOpenFindings _ -> "has_open_findings";
            case GuardExpr.AllTasksCompleted _ -> "all_tasks_completed";
            case GuardExpr.CustomPredicate(var primitiveId) -> "predicate:" + primitiveId;
            case GuardExpr.Or(var left, var right) -> parenthesise(guard(left, PRECEDENCE_OR) + " or "
                    + guard(right, PRECEDENCE_AND), PRECEDENCE_OR, context);
            case GuardExpr.And(var left, var right) -> parenthesise(guard(left, PRECEDENCE_AND) + " and "
                    + guard(right, PRECEDENCE_NOT), PRECEDENCE_AND, context);
            case GuardExpr.Not(var operand) -> parenthesise("not " + guard(operand, PRECEDENCE_NOT_OPERAND),
                    PRECEDENCE_NOT, context);
        };
    }

    private static String parenthesise(String text, int precedence, int context) {
        return precedence < context ? "(" + text + ")" : text;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
