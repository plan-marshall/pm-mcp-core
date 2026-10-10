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
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * Prints the tree of a unit back to the source of the workflow language (PM-IMPL-5), so that the parser reads the
 * printed source into the same tree, source locations aside.
 * <p>
 * The output is canonical: the unit header, the {@code instance} line with the header JSON exactly as it was read,
 * the {@code scope} clause (with the {@code param} and {@code result} declarations of a sub-workflow), the
 * {@code declare} lines, the {@code decide} lines, the {@code start} line, the {@code migrate} lines, and the states in
 * declaration order, each preceded by a blank line and followed by its clauses, indented by nesting. Every link names
 * its form explicitly, and a guard carries exactly the parentheses its precedence ({@code not} before {@code and}
 * before {@code or}, both operators left-associative) needs; a negated operand that is no single guard is always
 * parenthesised, because the grammar admits no {@code not not}.
 * <p>
 * What the parser of one file does not record is not printed: the part versions and derived-from revisions of a
 * merged unit, and with them any {@code derived_from} of the header. A range of a {@code text} or {@code int} type is
 * printed when both of its bounds are present, as the grammar writes one only with both. The printer checks nothing:
 * it prints the tree it is given.
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

    private void printUnit(WorkflowDefinition definition) {
        line(0, "workflow %s version %d format %d".formatted(definition.id(), definition.version(),
                definition.formatVersion()));
        definition.instance().ifPresent(header -> line(0, "instance " + header.json()));
        line(0, "scope " + lower(definition.scope()));
        if (definition.scope() == WorkflowScope.SUBWORKFLOW) {
            definition.params().forEach(param -> line(0, "param %s : %s".formatted(param.name(), type(param.type()))));
            definition.resultSchema().ifPresent(schema -> line(0, "result schema[" + schema + "]"));
        }
        definition.declarations().forEach((name, value) -> line(0, "declare %s = %s".formatted(name, value(value))));
        definition.decisions().forEach((kind, decision) -> line(0, "decide %s -> %s".formatted(kind,
                decision(decision))));
        line(0, "start " + definition.startState());
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
            case StateBehavior.InteractiveLinks links -> printLinks(links.links());
            case StateBehavior.AutoStep auto -> {
                line(1, "auto " + auto.primitiveId());
                printTransitions(2, auto.transitions());
            }
            case StateBehavior.JobStep job -> {
                line(1, (job.composite() ? "composite_job " : "job ") + job.primitiveId());
                printTransitions(2, job.transitions());
            }
            case StateBehavior.ModelJob job -> printModelJob(job);
            case StateBehavior.SubWorkflowCall call -> {
                line(1, "call " + call.subWorkflowId() + bindings(call.paramBindings()));
                printTransitions(2, call.transitions());
            }
            case StateBehavior.QuestionWait question -> {
                line(1, "question " + question.questionClass());
                printTransitions(2, question.transitions());
                printLinks(question.links());
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
        if (job.ask()) {
            clause.append(" ask");
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
            case LinkAction.ExecutePrimitive execute -> execute.primitiveId();
        };
    }

    private static String param(ParamSpec param) {
        return param.name() + ": " + type(param.type()) + (param.optional() ? "?" : "");
    }

    private static String type(ParamType type) {
        return switch (type) {
            case ParamType.Text text -> "text" + range(text.minLength(), text.maxLength());
            case ParamType.Int integer -> "int" + range(integer.min(), integer.max());
            case ParamType.EnumOf enumOf -> "enum[" + String.join("|", enumOf.values()) + "]";
            case ParamType.SchemaRef schema -> "schema[" + schema.schemaName() + "]";
            case ParamType.ListOf list -> "list<" + type(list.elementType()) + ">";
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
            case CallValue.EnumValue enumValue -> enumValue.value();
            case CallValue.IntValue intValue -> String.valueOf(intValue.value());
            case CallValue.EnumList list -> "[" + String.join(LIST_SEPARATOR, list.values()) + "]";
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
            case DecisionDeclaration.Quorum quorum -> "quorum(" + String.join(LIST_SEPARATOR, quorum.roles())
                    + LIST_SEPARATOR + quorum.score() + ")";
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
            case GuardExpr.CustomPredicate predicate -> "predicate:" + predicate.primitiveId();
            case GuardExpr.Or or -> parenthesise(guard(or.left(), PRECEDENCE_OR) + " or "
                    + guard(or.right(), PRECEDENCE_AND), PRECEDENCE_OR, context);
            case GuardExpr.And and -> parenthesise(guard(and.left(), PRECEDENCE_AND) + " and "
                    + guard(and.right(), PRECEDENCE_NOT), PRECEDENCE_AND, context);
            case GuardExpr.Not not -> parenthesise("not " + guard(not.operand(), PRECEDENCE_NOT_OPERAND),
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
