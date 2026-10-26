/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An immutable registry of known {@link QuestionClass} instances (PM-IMPL-5, PM-WF-12).
 *
 * @since 0.1
 */
public final class QuestionClassRegistry {

    private static final String ANSWERED = "answered";

    private final Map<String, QuestionClass> classes;

    /**
     * @param classes the question classes to register
     * @throws NullPointerException if the collection or any element is {@code null}
     */
    public QuestionClassRegistry(Collection<QuestionClass> classes) {
        Objects.requireNonNull(classes, "classes");
        var map = new LinkedHashMap<String, QuestionClass>();
        for (var qc : classes) {
            Objects.requireNonNull(qc, "qc");
            map.put(qc.name(), qc);
        }
        this.classes = Collections.unmodifiableMap(map);
    }

    /**
     * Finds a question class by name.
     *
     * @param name the question class name
     * @return the question class, or empty if not found
     */
    public Optional<QuestionClass> find(String name) {
        return Optional.ofNullable(classes.get(name));
    }

    /**
     * Returns a new registry with the additional or replaced question class.
     *
     * @param questionClass the question class to add or replace
     * @return a new registry containing the class
     * @throws NullPointerException if {@code questionClass} is {@code null}
     */
    public QuestionClassRegistry with(QuestionClass questionClass) {
        Objects.requireNonNull(questionClass, "questionClass");
        var updated = new LinkedHashMap<>(classes);
        updated.put(questionClass.name(), questionClass);
        return new QuestionClassRegistry(updated.values());
    }

    /**
     * @return the default registry containing the standard specification question classes. Every class has exactly the
     *         closed resolution outcomes of the specification: an answer, and for the review the operator's
     *         {@code approve} or {@code revise}. No class has a fallback by time.
     */
    public static QuestionClassRegistry defaults() {
        return new QuestionClassRegistry(List.of(
                new QuestionClass("init", Set.of(ANSWERED)),
                new QuestionClass("clarification", Set.of(ANSWERED)),
                new QuestionClass("review", Set.of("approve", "revise")),
                new QuestionClass("task", Set.of(ANSWERED)),
                new QuestionClass("setup", Set.of(ANSWERED)),
                new QuestionClass("ask", Set.of(ANSWERED))));
    }
}
