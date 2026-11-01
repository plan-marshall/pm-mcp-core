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

import java.util.Objects;

/**
 * One lesson of the project lesson store: its front matter and its Markdown body (Store Schemas § Lesson Record;
 * PM-IMPL-2).
 * <p>
 * The body is the narrative that stays Markdown; the front matter is the structured part of the file. A body may be
 * empty — a lesson with valid front matter and an empty body is the state {@code empty_shell}, never a content
 * verdict.
 *
 * @param frontMatter the structured front matter of the lesson
 * @param body        the Markdown body, verbatim
 * @since 0.1
 */
public record Lesson(LessonFrontMatter frontMatter, String body) {

    /**
     * @param frontMatter the structured front matter of the lesson
     * @param body        the Markdown body, verbatim
     * @throws IllegalArgumentException if the front matter is missing
     */
    public Lesson {
        if (frontMatter == null) {
            throw new IllegalArgumentException("A lesson without front matter is not written");
        }
        Objects.requireNonNull(body, "body");
    }
}
