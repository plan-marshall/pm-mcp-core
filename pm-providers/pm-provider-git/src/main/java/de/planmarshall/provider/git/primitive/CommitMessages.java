/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.spi.validation.ContentViolation;
import de.planmarshall.core.spi.validation.ConventionalCommitValidator;

/**
 * The rules of a commit message of a server commit (PM-WF-16, Stage 3): its validation and the one place that writes
 * the trailer line.
 * <p>
 * {@link #validate(String)} applies {@link ConventionalCommitValidator}, which also refuses a {@code Co-Authored-By:}
 * line in any case and position, together with the bound of the message type {@code commit_message[1..2000]} and the
 * refusal of a lone surrogate, which no UTF-8 commit can carry. {@link #withTrailer(String, CommitTrailer)} is the only
 * writer of a {@code Co-Authored-By:} line in the production code of this module; it refuses a message that already
 * holds one, so a commit carries exactly the trailer the server resolved. Every refusal is a returned value.
 *
 * @since 0.1
 */
@UtilityClass
public final class CommitMessages {

    /** The largest message, in characters. */
    public static final int MAX_LENGTH = 2000;

    /** The message is longer than {@value #MAX_LENGTH} characters. */
    public static final String RULE_LENGTH = "commit.length";

    /** The message holds a high or low surrogate without its partner. */
    public static final String RULE_LONE_SURROGATE = "commit.lone-surrogate";

    private static final String TRAILER_KEY = "Co-Authored-By";

    private static final ConventionalCommitValidator VALIDATOR = new ConventionalCommitValidator();

    /**
     * Validates a commit message. A blank message is refused by the rule {@link ConventionalCommitValidator#RULE_BLANK},
     * which covers the lower bound of one character.
     *
     * @param message the message, without the trailer
     * @return the violations ordered by line, empty when the message is valid
     */
    public static List<ContentViolation> validate(String message) {
        Objects.requireNonNull(message, "message");
        var violations = new ArrayList<>(VALIDATOR.validate(message));
        var length = message.codePointCount(0, message.length());
        if (length > MAX_LENGTH) {
            violations.add(new ContentViolation(RULE_LENGTH, 0,
                    "the message has %d characters, more than %d".formatted(length, MAX_LENGTH)));
        }
        var lines = message.lines().toList();
        for (var index = 0; index < lines.size(); index++) {
            if (holdsLoneSurrogate(lines.get(index))) {
                violations.add(new ContentViolation(RULE_LONE_SURROGATE, index + 1,
                        "the line holds a surrogate without its partner"));
            }
        }
        violations.sort(Comparator.comparingInt(ContentViolation::line));
        return List.copyOf(violations);
    }

    /**
     * Appends the trailer to a message: one blank line, the trailer line, and one final line feed. A message that
     * already holds a {@code Co-Authored-By:} line, in any case and position and whatever it names, is refused naming
     * that line.
     *
     * @param message the message, validated with {@link #validate(String)}
     * @param trailer the resolved trailer
     * @return {@link Appended} with the message to commit, or {@link Refused} with the violation
     */
    public static TrailerOutcome withTrailer(String message, CommitTrailer trailer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(trailer, "trailer");
        var existing = VALIDATOR.validate(message).stream()
                .filter(violation -> ConventionalCommitValidator.RULE_CO_AUTHORED_BY.equals(violation.ruleKey()))
                .findFirst();
        if (existing.isPresent()) {
            return new Refused(existing.get());
        }
        return new Appended("%s\n\n%s: %s <%s>\n".formatted(message.stripTrailing(), TRAILER_KEY, trailer.name(),
                trailer.email()));
    }

    /**
     * A paired surrogate is read as one supplementary code point; only a lone surrogate stays in the surrogate range.
     */
    private static boolean holdsLoneSurrogate(String line) {
        return line.codePoints()
                .anyMatch(codePoint -> codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
    }

    /**
     * The result of {@link #withTrailer(String, CommitTrailer)}.
     *
     * @since 0.1
     */
    public sealed interface TrailerOutcome permits Appended, Refused {
    }

    /**
     * The message with the trailer appended.
     *
     * @param message the message to commit
     * @since 0.1
     */
    public record Appended(String message) implements TrailerOutcome {

        /**
         * @param message the message to commit
         */
        public Appended {
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * The message already holds a trailer line.
     *
     * @param violation the violation of the rule {@link ConventionalCommitValidator#RULE_CO_AUTHORED_BY}, naming the
     *                  line
     * @since 0.1
     */
    public record Refused(ContentViolation violation) implements TrailerOutcome {

        /**
         * @param violation the violation
         */
        public Refused {
            Objects.requireNonNull(violation, "violation");
        }
    }
}
