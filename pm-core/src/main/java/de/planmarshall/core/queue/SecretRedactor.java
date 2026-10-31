/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks a secret-shaped value before an activity entry is written (Store Schemas § Activity Journal: every text and
 * target is masked like every job log; Job Runtime § Pipeline and Subprocesses names the component `SecretRedactor`).
 * <p>
 * The redactor is deliberately narrow, because a false positive costs a piece of the operator's evidence while a false
 * negative writes a credential to a store: it replaces the value of an explicit credential-shaped assignment (a
 * password, token, secret, API key or authorization header) and a bearer value, and it leaves every other text alone.
 * A hash, a commit id, a path and a prose sentence that merely contains the word {@code token} are not secrets and stay
 * readable.
 * <p>
 * The replacement keeps the label so the reader knows what was removed: {@code "Authorization: Bearer …"} becomes
 * {@code "Authorization: Bearer [redacted]"}. The redactor is idempotent: redacting redacted text changes nothing.
 *
 * @since 0.1
 */
public final class SecretRedactor {

    /** The text that replaces a redacted value. */
    public static final String REDACTED = "[redacted]";

    private static final String SEPARATOR = "([\"']?\\s*[:=]\\s*[\"']?)";

    private static final String VALUE = "([^\\s\"',;]{4,})";

    private static final Pattern AUTH_HEADER = Pattern.compile(
            "(?i)\\b(authorization)\\b(\\s*[:=]\\s*)((?i:bearer|basic|token)\\s+)?([^\\s\"',;]{4,})");

    /**
     * The credential labels, one pattern each: a label's own alternation stays small, so neither the regex nor the
     * reader has to hold a combined 30-branch expression. A label may follow a letter only when it starts a camelCase
     * word, and may be followed by a closing quote before the separator, as a JSON key is.
     */
    private static final List<Pattern> ASSIGNMENTS = List.of(
            assignment("pass(?:[_-]?(?:word|wd|phrase|code|key))?"),
            assignment("secret"),
            assignment("token"),
            assignment("(?:api|access|private)[_-]?key"),
            assignment("auth"),
            assignment("(?-i:[a-z]+(?=[A-Z]))"
                    + "(?:Secret|Token|Password|Passwd|Passphrase|Passcode|Passkey|ApiKey|AccessKey|PrivateKey)"));

    private static final Pattern BEARER = Pattern.compile("(?i)\\b(bearer\\s+)([\\w.~+/=-]{8,})");

    private static final Pattern TOKEN_SHAPE = Pattern.compile(
            "\\b(?:sk|ghp|gho|ghu|ghs|ghr|github_pat|xox[abprs])[-_][\\w-]{12,}");

    private static final List<Pattern> REPLACEMENTS = List.of(TOKEN_SHAPE);

    private static Pattern assignment(String label) {
        return Pattern.compile("(?i)(?<![a-z0-9])(" + label + ")\\b" + SEPARATOR + VALUE);
    }

    private SecretRedactor() {
    }

    /**
     * @param value a text or a target about to be written
     * @return the value with every secret-shaped part replaced by {@link #REDACTED}; empty when {@code value} is
     *         {@code null}
     */
    public static Optional<String> redactText(@Nullable String value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value.isEmpty()) {
            return Optional.of(value);
        }
        var masked = AUTH_HEADER.matcher(value).replaceAll("$1$2$3" + Matcher.quoteReplacement(REDACTED));
        masked = BEARER.matcher(masked).replaceAll("$1" + Matcher.quoteReplacement(REDACTED));
        for (var pattern : ASSIGNMENTS) {
            masked = pattern.matcher(masked).replaceAll("$1$2" + Matcher.quoteReplacement(REDACTED));
        }
        for (var pattern : REPLACEMENTS) {
            masked = pattern.matcher(masked).replaceAll(Matcher.quoteReplacement(REDACTED));
        }
        return Optional.of(masked);
    }

    /**
     * @param entry an activity entry as the runtime read it
     * @return the entry with its text, tool target and link target redacted
     */
    public static ActivityEntry redact(ActivityEntry entry) {
        var masked = entry;
        if (entry.text() != null) {
            var redacted = cut(redactText(entry.text()).orElseThrow(), ActivityEntry.TEXT_BOUND);
            masked = masked.withText(redacted.value()).withTruncated(entry.truncated() || redacted.cut());
        }
        if (entry.tool() != null && entry.tool().target() != null) {
            masked = masked.withTool(new ActivityEntry.Tool(entry.tool().name(),
                    cutUtf16(redactText(entry.tool().target()).orElseThrow(), 1000).value()));
        }
        if (entry.link() != null && entry.link().target() != null) {
            masked = masked.withLink(new ActivityEntry.Link(entry.link().rel(),
                    cutUtf16(redactText(entry.link().target()).orElseThrow(), 128).value()));
        }
        return masked;
    }

    /**
     * Redaction can lengthen a value, so the bound of the entry is applied once more after it: a redacted value that
     * grew past its bound is cut, and the text says it was cut.
     */
    private static Cut cut(String value, int bound) {
        var length = value.codePointCount(0, value.length());
        return length <= bound ? new Cut(value, false)
                : new Cut(value.substring(0, value.offsetByCodePoints(0, bound)), true);
    }

    /** Cuts by UTF-16 units, never splitting a surrogate pair: a target's bound is its {@code length()}. */
    private static Cut cutUtf16(String value, int bound) {
        if (value.length() <= bound) {
            return new Cut(value, false);
        }
        var end = Character.isHighSurrogate(value.charAt(bound - 1)) ? bound - 1 : bound;
        return new Cut(value.substring(0, end), true);
    }

    private record Cut(String value, boolean cut) {
    }
}
