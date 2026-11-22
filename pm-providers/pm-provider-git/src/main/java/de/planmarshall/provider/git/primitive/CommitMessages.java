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
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.spi.validation.ContentViolation;
import de.planmarshall.core.spi.validation.ConventionalCommitValidator;

/**
 * The rules of a commit message of a server commit (PM-WF-16, Stage 3): its validation and the one place that writes
 * the trailer line.
 * <p>
 * {@link #validate(String)} applies {@link ConventionalCommitValidator}, which also refuses a {@code Co-Authored-By:}
 * line in any case and position, together with the bound of the message type {@code commit_message[1..2000]}, the
 * refusal of a lone surrogate, which no UTF-8 commit can carry, and the refusal of the control characters that type
 * refuses (every one but a line break). {@link #withTrailer(String, CommitTrailer)} is the only
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

    /** The message holds a control character other than a line break, ESC and NUL among them. */
    public static final String RULE_CONTROL = "commit.control";

    private static final String TRAILER_KEY = "Co-Authored-By";

    private static final String MESSAGE_PARAMETER = "message";

    /**
     * The Unicode line terminators that {@link String#lines()} does not split at and the dot of a regular expression
     * does not match: a {@code Co-Authored-By:} line ended by one of them escapes the validator's line match.
     * {@link CommitTrailer#isValidName(String)} refuses a name that holds one of them.
     */
    static final Pattern UNSPLIT_TERMINATORS = Pattern.compile("[\\u0085\\u2028\\u2029]");

    private static final ConventionalCommitValidator VALIDATOR = new ConventionalCommitValidator();

    /**
     * Validates a commit message. A blank message is refused by the rule {@link ConventionalCommitValidator#RULE_BLANK},
     * which covers the lower bound of one character. A {@code Co-Authored-By:} line is found also where it is ended by
     * U+0085, U+2028 or U+2029.
     *
     * @param message the message, without the trailer
     * @return the violations ordered by line, empty when the message is valid
     */
    public static List<ContentViolation> validate(String message) {
        Objects.requireNonNull(message, MESSAGE_PARAMETER);
        var violations = new ArrayList<ContentViolation>();
        VALIDATOR.validate(message).stream().filter(violation -> !isCoAuthor(violation)).forEach(violations::add);
        violations.addAll(coAuthorViolations(message));
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
            if (holdsControl(lines.get(index))) {
                violations.add(new ContentViolation(RULE_CONTROL, index + 1,
                        "the line holds a control character"));
            }
        }
        violations.sort(Comparator.comparingInt(ContentViolation::line));
        return List.copyOf(violations);
    }

    /**
     * Appends the trailer to a message: one blank line, the trailer line, and one final line feed. A message that
     * already holds a {@code Co-Authored-By:} line, in any case and position and whatever it names, ended by a line
     * break or by U+0085, U+2028 or U+2029, is refused naming that line. A commit message is delimited by line feeds,
     * never by the platform's line separator.
     *
     * @param message the message, validated with {@link #validate(String)}
     * @param trailer the resolved trailer
     * @return {@link Appended} with the message to commit, or {@link Refused} with the violation
     */
    public static TrailerOutcome withTrailer(String message, CommitTrailer trailer) {
        Objects.requireNonNull(message, MESSAGE_PARAMETER);
        Objects.requireNonNull(trailer, "trailer");
        var existing = coAuthorViolations(message);
        if (!existing.isEmpty()) {
            return new Refused(existing.getFirst());
        }
        return new Appended(message.stripTrailing() + "\n\n" + TRAILER_KEY + ": " + trailer.name() + " <"
                + trailer.email() + ">\n");
    }

    /**
     * The {@code Co-Authored-By:} lines of a message, one violation per line of {@link String#lines()}: each line is
     * split at {@link #UNSPLIT_TERMINATORS} first, so a trailer the validator's line match would miss is found too.
     */
    private static List<ContentViolation> coAuthorViolations(String message) {
        var violations = new ArrayList<ContentViolation>();
        var lines = message.lines().toList();
        for (var index = 0; index < lines.size(); index++) {
            var line = index + 1;
            UNSPLIT_TERMINATORS.splitAsStream(lines.get(index))
                    .flatMap(segment -> VALIDATOR.validate(segment).stream().filter(CommitMessages::isCoAuthor))
                    .findFirst()
                    .ifPresent(found -> violations.add(new ContentViolation(found.ruleKey(), line, found.message())));
        }
        return violations;
    }

    private static boolean isCoAuthor(ContentViolation violation) {
        return ConventionalCommitValidator.RULE_CO_AUTHORED_BY.equals(violation.ruleKey());
    }

    /**
     * The control characters the input type {@code commit_message} refuses: C0 except the line breaks, which
     * {@link String#lines()} has already removed, DEL and C1.
     */
    private static boolean holdsControl(String line) {
        return line.codePoints().anyMatch(codePoint -> codePoint < 0x20 || (codePoint >= 0x7f && codePoint <= 0x9f));
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
            Objects.requireNonNull(message, MESSAGE_PARAMETER);
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
