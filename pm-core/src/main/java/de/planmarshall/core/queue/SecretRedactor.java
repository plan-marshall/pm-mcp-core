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

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Masks a secret-shaped value before an activity entry is written (Store Schemas § Activity Journal: every text and
 * target is masked like every job log; Job Runtime § Pipeline and Subprocesses names the component `SecretRedactor`).
 * <p>
 * The redactor is deliberately narrow, because a false positive costs a piece of the operator's evidence while a false
 * negative writes a credential to a store: it replaces the value of an explicit credential-shaped assignment (a
 * password, token, secret, API key or authorization header), a bearer value, a provider token shape (including the npm
 * {@code npm_} access token), the user-information form of a URL ({@code scheme://user:secret@host}), the value of a
 * spaced credential flag ({@code --password <value>}, {@code --token <value>}) and a PEM private-key block carried inside
 * one text, and it leaves every other text alone. Each non-assignment shape is anchored to the form itself — a URL needs
 * its {@code scheme://} and {@code @}, a flag needs its leading {@code --} and a following space — so prose cannot match.
 * A hash, a commit id, a path and a prose sentence that merely contains the word {@code token} are not secrets and stay
 * readable.
 * <p>
 * The replacement keeps the label so the reader knows what was removed: {@code "Authorization: Bearer …"} becomes
 * {@code "Authorization: Bearer [redacted]"}, {@code "https://user:secret@host"} keeps its scheme and user, and a PEM
 * block is replaced whole. A PEM block whose {@code BEGIN} line lies outside the text is not masked; the carried open
 * item names it. The redactor is idempotent: redacting redacted text changes nothing.
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

    /**
     * The npm access token: {@code npm_} followed by 36 alphanumeric characters (npm Registry API documentation, "Delete
     * npm access token"; the shape introduced in 2021, whose last six characters are a Base62 CRC32 checksum). The exact
     * length keeps a package name that merely starts with {@code npm_} readable.
     */
    private static final Pattern NPM_ACCESS_TOKEN = Pattern.compile("\\bnpm_[A-Za-z0-9]{36}\\b");

    /**
     * The user-information form of a URL: {@code scheme://user:secret@host}. The pattern keeps the scheme and the user
     * and masks the secret; without the {@code scheme://} and {@code @} a colon in a path or a prose sentence does not
     * match.
     */
    private static final Pattern URL_USER_INFORMATION =
            Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)([^\\s/:@]+):([^\\s/?#]+)@");

    /**
     * A credential flag whose value is separated by whitespace instead of {@code :} or {@code =}. The leading
     * {@code --} anchors the shape, so {@code --password-file notes.txt} is a different flag and stays readable.
     */
    private static final Pattern CREDENTIAL_FLAG =
            Pattern.compile("(?<![\\w-])(--(?:password|passwd|passphrase|token|secret))([ \\t]+)([^\\s\"',;]{4,})");

    /**
     * A PEM private-key block carried inside one text. A window that starts inside a block (its {@code BEGIN} line
     * outside) is not masked; the carried open item names it.
     */
    private static final Pattern PEM_PRIVATE_KEY =
            Pattern.compile("(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----");

    private static final List<Pattern> REPLACEMENTS = List.of(TOKEN_SHAPE, NPM_ACCESS_TOKEN);

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
        masked = URL_USER_INFORMATION.matcher(masked)
                .replaceAll("$1$2:" + Matcher.quoteReplacement(REDACTED) + "@");
        masked = CREDENTIAL_FLAG.matcher(masked)
                .replaceAll("$1$2" + Matcher.quoteReplacement(REDACTED));
        masked = PEM_PRIVATE_KEY.matcher(masked).replaceAll(Matcher.quoteReplacement(REDACTED));
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
