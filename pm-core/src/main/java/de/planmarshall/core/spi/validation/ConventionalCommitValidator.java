/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates a commit message a model submits (PM-WF-6).
 * <p>
 * The first line is the header {@code type(scope)!: description}; the scope and the {@code !} are optional. A body
 * starts on the third line, after exactly one blank line.
 * <p>
 * A {@code Co-Authored-By:} line is refused wherever it stands and whatever it names. The server appends the trailer
 * it resolved itself; a trailer in the submitted text, even one equal to the resolved value, would let the text of a
 * model decide whom a commit credits.
 *
 * @since 0.1
 */
public final class ConventionalCommitValidator implements ContentValidator {

    /** The message is empty or consists of white space only. */
    public static final String RULE_BLANK = "commit.blank";

    /** The header is not of the form {@code type(scope)!: description}. */
    public static final String RULE_HEADER_FORM = "commit.header-form";

    /** The header has no type before the colon. */
    public static final String RULE_TYPE_EMPTY = "commit.type-empty";

    /** The header has no description after the colon. */
    public static final String RULE_DESCRIPTION_EMPTY = "commit.description-empty";

    /** The body is not separated from the header by exactly one blank line. */
    public static final String RULE_BODY_SEPARATION = "commit.body-separation";

    /** The message contains a {@code Co-Authored-By:} line. */
    public static final String RULE_CO_AUTHORED_BY = "commit.co-authored-by";

    private static final Pattern HEADER = Pattern.compile(
            "(?<type>[A-Za-z]*)(?:\\([^()]+\\))?!?:(?<description>.*)");

    private static final Pattern CO_AUTHORED_BY = Pattern.compile("\\s*co-authored-by\\s*:.*",
            Pattern.CASE_INSENSITIVE);

    @Override
    public List<ContentViolation> validate(String text) {
        if (text.isBlank()) {
            return List.of(new ContentViolation(RULE_BLANK, 0, "the commit message is blank"));
        }
        var lines = text.lines().toList();
        var violations = new ArrayList<ContentViolation>();
        checkHeader(lines.getFirst(), violations);
        checkBodySeparation(lines, violations);
        for (var index = 0; index < lines.size(); index++) {
            if (CO_AUTHORED_BY.matcher(lines.get(index)).matches()) {
                violations.add(new ContentViolation(RULE_CO_AUTHORED_BY, index + 1,
                        "the message supplies the trailer '%s'; the server appends the trailer itself"
                                .formatted(lines.get(index).strip())));
            }
        }
        violations.sort(Comparator.comparingInt(ContentViolation::line));
        return List.copyOf(violations);
    }

    private static void checkHeader(String header, List<ContentViolation> violations) {
        var matcher = HEADER.matcher(header);
        if (!matcher.matches()) {
            violations.add(new ContentViolation(RULE_HEADER_FORM, 1,
                    "the header '%s' is not of the form type(scope)!: description".formatted(header)));
            return;
        }
        if (matcher.group("type").isEmpty()) {
            violations.add(new ContentViolation(RULE_TYPE_EMPTY, 1,
                    "the header '%s' has no type before the colon".formatted(header)));
        }
        var description = matcher.group("description");
        if (description.isBlank()) {
            violations.add(new ContentViolation(RULE_DESCRIPTION_EMPTY, 1,
                    "the header '%s' has no description after the colon".formatted(header)));
        } else if (!description.startsWith(" ") || description.startsWith("  ")) {
            violations.add(new ContentViolation(RULE_HEADER_FORM, 1,
                    "the header '%s' does not separate the colon from the description by one space"
                            .formatted(header)));
        }
    }

    private static void checkBodySeparation(List<String> lines, List<ContentViolation> violations) {
        if (lines.size() > 1 && !lines.get(1).isBlank()) {
            violations.add(new ContentViolation(RULE_BODY_SEPARATION, 2,
                    "the line '%s' follows the header without a blank line".formatted(lines.get(1))));
        }
        if (lines.size() > 2 && lines.get(1).isBlank() && lines.get(2).isBlank()) {
            violations.add(new ContentViolation(RULE_BODY_SEPARATION, 3,
                    "more than one blank line separates the header from the body"));
        }
    }
}
