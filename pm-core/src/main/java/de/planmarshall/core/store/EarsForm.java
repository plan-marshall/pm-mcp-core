/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The EARS grammar an intent record's criterion text is written in (Phase Workflows § The Intent Record, server check 1;
 * the former implementation-watch item `PM-WATCH-PHASE-106`, retired by this change): the optional preconditions
 * {@code WHERE <feature>}, {@code WHILE <state>} and
 * {@code WHEN <trigger>} in that order, each at most once, optionally {@code IF <condition> THEN}, then
 * {@code THE <system> SHALL <response>}. A criterion of kind {@code unchanged} carries a response that begins with
 * {@code CONTINUE TO}; every other criterion's response does not.
 * <p>
 * The structural words are the uppercase English tokens whatever the project's model language: the text between them
 * is written in the model language and is never inspected. A structural word written in lower case is refused as a
 * misspelt keyword, so a prose sentence that only contains {@code when} and {@code shall} is not read as a criterion,
 * and a German criterion whose clause text is not English is accepted.
 *
 * @since 0.1
 */
public final class EarsForm {

    /** The precondition keywords, in the only order they may appear. */
    private static final List<String> PRECONDITIONS = List.of("WHERE", "WHILE", "WHEN");

    private static final String IF = "IF";

    private static final String THEN = "THEN";

    private static final String THE = "THE";

    private static final String SHALL = "SHALL";

    private static final String CONTINUE = "CONTINUE";

    private static final String TO = "TO";

    /** Every structural word whose lower-case spelling is refused. */
    private static final List<String> KEYWORDS =
            List.of("WHERE", "WHILE", "WHEN", "IF", "THEN", "THE", "SHALL", "CONTINUE");

    private static final String NO_CLAUSE = "the keyword '%s' has no clause";

    private EarsForm() {
    }

    /**
     * Checks a criterion's text against the EARS grammar.
     *
     * @param text      the criterion text
     * @param unchanged whether the criterion is of kind {@code unchanged}
     * @return the reason the text is not in EARS form; empty when it is
     * @throws NullPointerException if the text is {@code null}
     */
    public static Optional<String> violation(String text, boolean unchanged) {
        Objects.requireNonNull(text, "text");
        var stripped = text.strip();
        if (stripped.isEmpty()) {
            return Optional.of("the criterion is empty");
        }
        var words = stripped.split("\\s+");
        var first = firstIndices(words);
        var duplicate = duplicateKeyword(words);
        if (duplicate.isPresent()) {
            return Optional.of("the keyword '%s' is repeated".formatted(duplicate.get()));
        }
        var shape = shapeViolation(first);
        if (shape.isPresent()) {
            return shape;
        }
        var clauses = clauseViolations(words, first);
        if (clauses.isPresent()) {
            return clauses;
        }
        return responseViolation(words, first.get(SHALL), unchanged);
    }

    private static Map<String, Integer> firstIndices(String[] words) {
        var first = new LinkedHashMap<String, Integer>();
        for (var index = 0; index < words.length; index++) {
            if (KEYWORDS.contains(words[index])) {
                first.putIfAbsent(words[index], index);
            }
        }
        return first;
    }

    private static Optional<String> duplicateKeyword(String[] words) {
        var seen = new HashSet<String>();
        for (var word : words) {
            if (KEYWORDS.contains(word) && !seen.add(word)) {
                return Optional.of(word);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> shapeViolation(Map<String, Integer> first) {
        if (first.containsKey(THEN) && !first.containsKey(IF)) {
            return Optional.of("'%s' appears without a preceding '%s'".formatted(THEN, IF));
        }
        if (first.containsKey(IF) && !first.containsKey(THEN)) {
            return Optional.of("'%s' is missing after '%s'".formatted(THEN, IF));
        }
        if (!first.containsKey(THE)) {
            return Optional.of("the text holds no '%s <system> %s <response>'".formatted(THE, SHALL));
        }
        if (!first.containsKey(SHALL)) {
            return Optional.of("'%s' appears without '%s'".formatted(THE, SHALL));
        }
        var the = first.get(THE);
        var shall = first.get(SHALL);
        if (shall < the) {
            return Optional.of("'%s' appears before '%s'".formatted(SHALL, THE));
        }
        var previous = -1;
        var expected = 0;
        for (var precondition : PRECONDITIONS) {
            var index = first.get(precondition);
            if (index == null) {
                continue;
            }
            if (PRECONDITIONS.indexOf(precondition) < expected || index < previous) {
                return Optional.of("the precondition '%s' is out of order".formatted(precondition));
            }
            previous = index;
            expected = PRECONDITIONS.indexOf(precondition) + 1;
        }
        var ifIndex = first.get(IF);
        if (ifIndex != null) {
            var thenIndex = first.get(THEN);
            if (thenIndex < ifIndex) {
                return Optional.of("'%s' appears before its '%s'".formatted(THEN, IF));
            }
            if (ifIndex < previous) {
                return Optional.of("the conditional '%s' is out of order".formatted(IF));
            }
            if (thenIndex > the) {
                return Optional.of("'%s' appears before its '%s'".formatted(THE, THEN));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> clauseViolations(String[] words, Map<String, Integer> first) {
        var indexes = first.values().stream().sorted().toList();
        if (indexes.getFirst() != 0) {
            return Optional.of("the text does not begin with a structural keyword");
        }
        for (var position = 0; position < indexes.size() - 1; position++) {
            var keyword = keywordAt(words, indexes.get(position));
            var next = indexes.get(position + 1);
            var needsClause = PRECONDITIONS.contains(keyword) || IF.equals(keyword);
            if (needsClause && next == indexes.get(position) + 1) {
                return Optional.of(NO_CLAUSE.formatted(keyword));
            }
        }
        var the = first.get(THE);
        var shall = first.get(SHALL);
        if (the + 1 == shall) {
            return Optional.of("'%s' names no system".formatted(THE));
        }
        return Optional.empty();
    }

    private static String keywordAt(String[] words, int index) {
        return words[index];
    }

    private static Optional<String> responseViolation(String[] words, int shall, boolean unchanged) {
        if (shall + 1 >= words.length) {
            return Optional.of("'%s' names no response".formatted(SHALL));
        }
        var continues = CONTINUE.equals(words[shall + 1]);
        if (continues && (shall + 2 >= words.length || !TO.equals(words[shall + 2]))) {
            return Optional.of("'%s' is not followed by '%s'".formatted(CONTINUE, TO));
        }
        if (unchanged && !continues) {
            return Optional.of("the response of an unchanged criterion begins with 'CONTINUE TO'");
        }
        if (!unchanged && continues) {
            return Optional.of("only an unchanged criterion's response begins with 'CONTINUE TO'");
        }
        return Optional.empty();
    }
}
