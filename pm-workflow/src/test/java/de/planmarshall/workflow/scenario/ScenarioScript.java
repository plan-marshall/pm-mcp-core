/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.scenario;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;

import de.planmarshall.core.store.JournalScopeType;

/**
 * A scenario script of the replay harness {@code pm-scenario} (PM-TEST-4), read from its text: the scenario's name,
 * the declarations of its scripted workers, and its steps in order.
 * <p>
 * The text uses the line syntax of the harness: a first line {@code scenario <name>}, then one directive per line,
 * words separated by blanks, a {@code #} outside a quoted string starting a comment, and the JSON-shaped argument of
 * {@code complete_job} and {@code answer_question} reduced to the member the directive uses ({@code outcome} and
 * {@code branch_key}):
 *
 * <pre>
 * scenario review-and-ask
 * worker reviewer submitted                      # the scripted worker of the role reviewer
 * start plan plan-a
 * complete_job b-0000000000000001 {"outcome":"succeeded"}
 * assert_task_outcome reviewer submitted
 * answer_question Q-001 web {"branch_key":"approve"}
 * expire_question Q-002
 * assert_state completed
 * assert_outcome completed
 * </pre>
 * <p>
 * A directive the harness specification names but this harness does not run yet is refused by name, with its line
 * and the work package that brings it, so a script never passes by skipping a step; a word that is no directive at all
 * is refused as unknown. Every refusal is an {@link IllegalArgumentException} whose message starts with
 * {@code line <n>:}.
 *
 * @param name       the scenario's name
 * @param workers    the worker declarations, at most one per role, in the order of the script; copied
 * @param directives the steps, in the order of the script; copied
 */
record ScenarioScript(String name, List<ScenarioDirective.Worker> workers, List<ScenarioDirective> directives) {

    /**
     * The directives of the harness specification this harness refuses, each with the work package that brings it.
     */
    static final Map<String, String> REFUSED;

    static {
        var refused = new LinkedHashMap<String, String>();
        var links = "the offered links and pm_do (no work package yet)";
        refused.put("assert_links", links);
        refused.put("assert_skill", links);
        refused.put("assert_rejected", links);
        refused.put("do", links);
        refused.put("assert_instance_version", "plan-instance");
        refused.put("worker_wait", "task-queue-and-protocol");
        refused.put("worker_do", "task-queue-and-protocol");
        for (var fault : List.of("kill_worker", "stall_worker", "end_turn", "kill_relay", "restart_runtime",
                "assert_worker_state", "assert_refused")) {
            refused.put(fault, "supervision-scenarios");
        }
        refused.put("set_role_unqualified", "role-qualification");
        refused.put("operator_cli", "the operator verbs (no work package yet)");
        REFUSED = Collections.unmodifiableMap(refused);
    }

    private static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9-]*$");
    private static final Pattern BLANKS = Pattern.compile("\\s+");
    private static final JsonFactory JSON = new JsonFactory();

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if a role is declared twice or no step is named
     */
    ScenarioScript {
        Objects.requireNonNull(name, "name");
        workers = List.copyOf(workers);
        directives = List.copyOf(directives);
        var roles = new HashSet<String>();
        for (var worker : workers) {
            if (!roles.add(worker.role())) {
                throw new IllegalArgumentException("line %d: the worker of role %s is declared twice"
                        .formatted(worker.line(), worker.role()));
            }
        }
        if (directives.isEmpty()) {
            throw new IllegalArgumentException("the scenario %s names no step".formatted(name));
        }
    }

    /**
     * @param role the role
     * @return the declaration of the role's scripted worker, if the script declares one
     */
    Optional<ScenarioDirective.Worker> worker(String role) {
        return workers.stream().filter(worker -> worker.role().equals(role)).findFirst();
    }

    /**
     * Reads a scenario script from its text.
     *
     * @param text the text of the script
     * @return the script
     * @throws NullPointerException     if the text is {@code null}
     * @throws IllegalArgumentException if the text is not a script this harness runs; the message names the line
     */
    static ScenarioScript read(String text) {
        Objects.requireNonNull(text, "text");
        String name = null;
        var workers = new ArrayList<ScenarioDirective.Worker>();
        var directives = new ArrayList<ScenarioDirective>();
        var lines = text.lines().toList();
        for (var index = 0; index < lines.size(); index++) {
            var number = index + 1;
            var content = withoutComment(lines.get(index)).strip();
            if (content.isEmpty()) {
                continue;
            }
            var line = Line.of(number, content);
            if (name == null) {
                name = scenarioName(line);
            } else if ("worker".equals(line.word())) {
                workers.add(worker(line));
            } else {
                directives.add(directive(line));
            }
        }
        if (name == null) {
            throw new IllegalArgumentException("the script names no scenario: its first line is scenario <name>");
        }
        return new ScenarioScript(name, workers, directives);
    }

    private static String scenarioName(Line line) {
        if (!"scenario".equals(line.word())) {
            throw line.refused("a script starts with scenario <name>, not with '%s'".formatted(line.word()));
        }
        line.requireNoJson();
        line.requireArity(1, "scenario <name>");
        var name = line.args().getFirst();
        if (!NAME.matcher(name).matches()) {
            throw line.refused("the scenario name does not match ^[a-z0-9][a-z0-9-]*$: " + name);
        }
        return name;
    }

    private static ScenarioDirective.Worker worker(Line line) {
        line.requireNoJson();
        if (line.args().size() < 2) {
            throw line.refused("missing argument: worker <role> <task outcome>...");
        }
        var args = line.args();
        return line.build(() -> new ScenarioDirective.Worker(line.number(), args.getFirst(),
                args.subList(1, args.size())));
    }

    private static ScenarioDirective directive(Line line) {
        var word = line.word();
        if ("scenario".equals(word)) {
            throw line.refused("a script names one scenario, on its first line");
        }
        if (REFUSED.containsKey(word)) {
            throw line.refused("'%s' is not supported by this harness yet; it arrives with %s"
                    .formatted(word, REFUSED.get(word)));
        }
        return switch (word) {
            case "start" -> start(line);
            case "assert_state" -> {
                line.requireNoJson();
                line.requireArity(1, "assert_state <state id>");
                yield line.build(() -> new ScenarioDirective.AssertState(line.number(), line.args().getFirst()));
            }
            case "assert_outcome" -> {
                line.requireNoJson();
                line.requireArity(1, "assert_outcome <outcome>");
                yield line.build(() -> new ScenarioDirective.AssertOutcome(line.number(), line.args().getFirst()));
            }
            case "complete_job" -> {
                line.requireArity(1, "complete_job <job id> {\"outcome\":\"<fate>\"}");
                var fate = line.jsonMember("outcome");
                yield line.build(() -> new ScenarioDirective.CompleteJob(line.number(), line.args().getFirst(), fate));
            }
            case "answer_question" -> {
                line.requireArity(2, "answer_question <question id> <channel> {\"branch_key\":\"<key>\"}");
                var branchKey = line.jsonMember("branch_key");
                yield line.build(() -> new ScenarioDirective.AnswerQuestion(line.number(), line.args().getFirst(),
                        line.args().get(1), branchKey));
            }
            case "expire_question" -> {
                line.requireNoJson();
                line.requireArity(1, "expire_question <question id>");
                yield line.build(() -> new ScenarioDirective.ExpireQuestion(line.number(), line.args().getFirst()));
            }
            case "assert_task_outcome" -> {
                line.requireNoJson();
                line.requireArity(2, "assert_task_outcome <role> <task outcome>");
                yield line.build(() -> new ScenarioDirective.AssertTaskOutcome(line.number(), line.args().getFirst(),
                        line.args().get(1)));
            }
            default -> throw line.refused("'%s' is no directive".formatted(word));
        };
    }

    private static ScenarioDirective start(Line line) {
        line.requireNoJson();
        if (line.args().isEmpty()) {
            throw line.refused("missing argument: start <plan|epic> <scope id> or start workspace");
        }
        var scope = Arrays.stream(JournalScopeType.values())
                .filter(type -> type != JournalScopeType.LESSONS)
                .filter(type -> type.wireName().equals(line.args().getFirst()))
                .findFirst()
                .orElseThrow(() -> line.refused("'%s' is no scope a scenario starts: plan, epic or workspace"
                        .formatted(line.args().getFirst())));
        line.requireArity(scope.scopeIdRequired() ? 2 : 1,
                scope.scopeIdRequired() ? "start %s <scope id>".formatted(scope.wireName()) : "start workspace");
        var scopeId = scope.scopeIdRequired() ? line.args().get(1) : null;
        return line.build(() -> new ScenarioDirective.Start(line.number(), scope, scopeId));
    }

    /** @return the line without a comment: everything from a {@code #} outside a quoted string on */
    private static String withoutComment(String line) {
        var quoted = false;
        for (var i = 0; i < line.length(); i++) {
            var c = line.charAt(i);
            if (quoted && c == '\\') {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /**
     * One line of the script, split into its directive word, its blank-separated arguments and its JSON-shaped
     * argument, which starts at the first opening brace.
     */
    private record Line(int number, String word, List<String> args, String json) {

        static Line of(int number, String content) {
            var brace = content.indexOf('{');
            var head = brace < 0 ? content : content.substring(0, brace).strip();
            var json = brace < 0 ? null : content.substring(brace);
            if (head.isEmpty()) {
                throw new IllegalArgumentException("line %d: a line starts with a directive".formatted(number));
            }
            var words = List.of(BLANKS.split(head));
            return new Line(number, words.getFirst(), words.subList(1, words.size()), json);
        }

        IllegalArgumentException refused(String reason) {
            return new IllegalArgumentException("line %d: %s".formatted(number, reason));
        }

        void requireArity(int arity, String form) {
            if (args.size() < arity) {
                throw refused("missing argument: " + form);
            }
            if (args.size() > arity) {
                throw refused("surplus argument '%s': %s".formatted(args.get(arity), form));
            }
        }

        void requireNoJson() {
            if (json != null) {
                throw refused("%s takes no JSON argument".formatted(word));
            }
        }

        /** Builds a directive, giving a refusal of its values the line. */
        <T> T build(Supplier<T> directive) {
            try {
                return directive.get();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("line %d: %s".formatted(number, e.getMessage()), e);
            }
        }

        /**
         * @param member the member of the JSON-shaped argument the directive uses
         * @return its string value; other scalar members are not used
         */
        String jsonMember(String member) {
            if (json == null) {
                throw refused("missing argument: %s takes {\"%s\":\"…\"}".formatted(word, member));
            }
            var members = new LinkedHashMap<String, String>();
            try (var parser = JSON.createParser(json)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    throw refused("the JSON argument is no object");
                }
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    var key = parser.currentName();
                    var value = parser.nextToken();
                    if (value == null || value.isStructStart()) {
                        throw refused("the JSON member '%s' is no scalar".formatted(key));
                    }
                    members.put(key, parser.getText());
                }
                if (parser.currentToken() != JsonToken.END_OBJECT || parser.nextToken() != null) {
                    throw refused("the JSON argument is not one object");
                }
            } catch (IOException e) {
                var reason = e instanceof JsonProcessingException malformed ? malformed.getOriginalMessage()
                        : e.getMessage();
                throw refused("the JSON argument is malformed: " + reason);
            }
            if (!members.containsKey(member)) {
                throw refused("missing argument: the JSON member '%s'".formatted(member));
            }
            return members.get(member);
        }
    }
}
