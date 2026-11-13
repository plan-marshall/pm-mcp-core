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

import de.planmarshall.core.build.BuildLogText;

/**
 * Masks a secret-shaped value before an activity entry is written (Store Schemas § Activity Journal: every text and
 * target is masked like every job log; Job Runtime § Pipeline and Subprocesses names the component `SecretRedactor`).
 * <p>
 * The redactor is deliberately narrow, because a false positive costs a piece of the operator's evidence while a false
 * negative writes a credential to a store. It masks these shapes and leaves every other text alone:
 * <ul>
 * <li>the value of an explicit credential-shaped assignment: a password, token, secret, API key or authorization
 * header;</li>
 * <li>a bearer value;</li>
 * <li>a provider token shape, and the npm access token {@code npm_} followed by exactly 36 alphanumeric
 * characters;</li>
 * <li>the user-information form of a URL ({@code scheme://user:secret@host});</li>
 * <li>the value of a spaced credential flag of a closed list, whatever its case ({@code --password <value>}, quoted or
 * not; a quoted value is matched through its closing quote, spaces and at most one line end included, and an unclosed
 * quote masks the rest of the line);</li>
 * <li>a PEM private-key block or a PGP private-key block ({@code -----BEGIN PGP PRIVATE KEY BLOCK-----}) whose
 * {@code BEGIN} line is carried inside the text.</li>
 * </ul>
 * Each non-assignment shape is anchored to its own form — a URL needs its {@code scheme://} and {@code @}, a flag needs
 * its leading {@code --} and a following space, a key block needs its {@code BEGIN} marker — so prose cannot match. A
 * hash, a commit id, a path and a prose sentence that merely contains the word {@code token} or {@code PRIVATE KEY} are
 * not secrets and stay readable.
 * <p>
 * The replacement keeps the label so the reader knows what was removed: {@code "Authorization: Bearer …"} becomes
 * {@code "Authorization: Bearer [redacted]"}, {@code "https://user:secret@host"} keeps its scheme and user, and a key
 * block is replaced whole (an unclosed block masks from its {@code BEGIN} line to the end of the text).
 * <p>
 * The residue: a PEM or PGP block whose {@code BEGIN} line lies before the excerpt window, so that the text starts
 * inside the block, is not masked, because nothing in the text marks the key body as one. The window is not widened to
 * reach the marker. The redactor is idempotent: redacting redacted text changes nothing.
 * <p>
 * On the journal path ({@link #redact(ActivityEntry)}) the terminal escape sequences are stripped before the shapes are
 * matched, so a colour code between a label and its value does not hide the value; {@link #redactText(String)} leaves
 * escape sequences to its caller.
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
     * length is an operator decision and keeps a package name that merely starts with {@code npm_} readable. A run of 37
     * or more alphanumeric characters after {@code npm_}, a run followed by {@code _} and a run preceded by a word
     * character stay readable as well, because none of them is an npm token.
     */
    private static final Pattern NPM_ACCESS_TOKEN = Pattern.compile("\\bnpm_[A-Za-z0-9]{36}\\b");

    /**
     * The user-information form of a URL: {@code scheme://user:secret@host}. The pattern keeps the scheme and the user
     * readable and masks the secret, which runs from the first colon after the user to the last {@code @} of the token;
     * the host after that {@code @} stays readable when the token carries no later {@code @}. The secret is masked also
     * when
     * <ul>
     * <li>it holds a raw {@code /}, {@code ?} or {@code #} ({@code https://deploy:pa/ss@registry.example/pkg}), or a raw
     * {@code @}, which is masked with it; a percent-encoded secret stays masked as a whole;</li>
     * <li>the user holds a raw {@code @} ({@code https://me@corp.example:s3cret@host/p}): the user runs to the first
     * colon, so the secret is the part between that colon and the last {@code @} of the token;</li>
     * <li>the scheme separator is percent-encoded ({@code https%3A%2F%2Fuser:secret@host}).</li>
     * </ul>
     * The scheme starts where no scheme character precedes it. The user excludes whitespace, {@code /}, {@code ?},
     * {@code #} and {@code :}, so a query or fragment that carries a colon and an {@code @} before any user information
     * ({@code https://host/p?at=a:b@c}) stays readable.
     * <p>
     * What stays readable, and why: the single-user form {@code https://<token>@host} has no colon and so no secret
     * part (an operator decision; the token there is a known residue), {@code scheme://host:8080/path} has no
     * {@code @}, and a path with a colon or a prose sentence has no {@code scheme://}. The false-positive costs:
     * <ul>
     * <li>a {@code host:port/path} that is followed later on the same token by a raw {@code @} is read as user
     * information, and the port and path up to that {@code @} are masked;</li>
     * <li>a credentialed URL followed later on the same token by a raw {@code @} (an {@code @scope} path segment
     * {@code https://user:secret@registry.example/@scope/pkg}, an e-mail address) masks its host and path up to that
     * last {@code @}, because the secret runs to the last {@code @} of the token.</li>
     * </ul>
     * <p>
     * The plain separator {@code ://} and the percent-encoded one are two patterns applied in sequence, so neither
     * carries the alternation of both. The cost: the scheme and the user are possessive and the scheme starts only
     * where no scheme character precedes it, while the secret is a greedy {@code \S+} that gives characters back until
     * it reaches the last {@code @} of the token. A token without that {@code @} ({@code x://y:x://y:…}) is therefore
     * scanned back once from each scheme start, which is quadratic in the length of one line at worst. The unparsed
     * excerpt, the npm diagnostic and the journal cut the line at {@link BuildLogText#MAX_LINE_LENGTH} (or a smaller
     * bound) before the redaction, and that cut bounds the cost there; {@code ProviderVerification} of
     * {@code pm-runtime} passes a provider answer uncut, a residue ADR {@code core-build/009} and the guide of
     * {@code core.queue} name.
     */
    private static final Pattern URL_USER_INFORMATION = urlUserInformation("://");

    /** The user-information form behind the percent-encoded scheme separator ({@code https%3A%2F%2Fuser:secret@host}). */
    private static final Pattern URL_USER_INFORMATION_ENCODED = urlUserInformation("%3A%2F%2F");

    /**
     * The closed list of credential flags whose value is separated by whitespace instead of {@code :} or {@code =}.
     * The flag name is matched whatever its case ({@code --PASSWORD x}). The leading {@code --} anchors the shape, so
     * {@code --password-file notes.txt} is a different flag and stays readable, and a flag outside the list
     * ({@code --key value}, an operator decision) stays readable as well. The quoted forms are matched first: a quoted
     * value runs to the first unescaped closing quote, also across one line end to its closing quote on the next line
     * (never further), and a quote that does not close on its own line or the next masks the rest of its line (an
     * escaped quote is no close, so {@code --password "abc\"def} masks {@code abc\"def}); an
     * unquoted value runs to the next whitespace and may carry a comma, a {@code ;} or a quote.
     * <p>
     * The false-positive cost: the word after a listed flag is read as its value, so usage text such as
     * {@code --token <token>} masks the placeholder {@code <token>}.
     */
    private static final String CREDENTIAL_FLAG_NAMES =
            "password|passwd|passphrase|token|secret|auth-token|api-key|apikey|access-token|client-secret|otp";

    /** The shared head of a credential-flag pattern: the flag name and the whitespace after it, kept as groups 1 and 2. */
    private static final String CREDENTIAL_FLAG_HEAD =
            "(?<![\\w-])(--(?:" + CREDENTIAL_FLAG_NAMES + "))([ \\t]+)";

    /** A run of characters of a quoted flag value that are neither a backslash, the opening quote nor a line end. */
    private static final String QUOTED_PLAIN_RUN = "(?:(?!\\3)[^\\\\\\r\\n])*+";

    /**
     * One line of a quoted flag value as an unrolled loop: a plain run, then any number of escape pairs (a backslash and
     * the character after it, the quote included) each followed by a plain run. No repetition holds an alternation and
     * every repetition is possessive, so the match neither backtracks nor recurses per character.
     */
    private static final String QUOTED_LINE =
            QUOTED_PLAIN_RUN + "(?:\\\\[^\\r\\n]" + QUOTED_PLAIN_RUN + ")*+";

    private static final Pattern QUOTED_CREDENTIAL_FLAG = Pattern.compile(
            CREDENTIAL_FLAG_HEAD + "([\"'])(" + QUOTED_LINE + "(?:(?:\\r\\n|\\r|\\n)" + QUOTED_LINE + ")?)\\3",
            Pattern.CASE_INSENSITIVE);

    /**
     * A quoted flag value whose quote does not close on its own line: the lookahead refuses the line only when
     * {@link #QUOTED_LINE} reaches an unescaped closing quote, so an escaped quote ({@code --password "abc\"def}) is no
     * close and the rest of the line is masked.
     */
    private static final Pattern UNCLOSED_CREDENTIAL_FLAG = Pattern.compile(
            CREDENTIAL_FLAG_HEAD + "([\"'])(?!" + QUOTED_LINE + "\\3)([^\\r\\n]*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CREDENTIAL_FLAG = Pattern.compile(
            CREDENTIAL_FLAG_HEAD + "([^\\s\"']\\S*)", Pattern.CASE_INSENSITIVE);

    /**
     * A PEM private-key block or a PGP private-key block ({@code -----BEGIN PGP PRIVATE KEY BLOCK-----} to its
     * {@code END} line) carried inside one text. A window that starts inside a block (its {@code BEGIN} line before the
     * window) is not masked: that is the residue the class documentation names.
     */
    private static final Pattern PEM_PRIVATE_KEY = Pattern.compile(
            "(?s)-----BEGIN [^-]*PRIVATE KEY(?: BLOCK)?-----.*?-----END [^-]*PRIVATE KEY(?: BLOCK)?-----");

    /**
     * A PEM or PGP private-key block whose {@code END} line is missing from the text: the text ends inside the block, so
     * the rest of it is masked. The over-masking is kept deliberately: whatever follows the block in the text is masked
     * with it, because masking too much costs evidence while leaving a key body readable costs the credential.
     */
    private static final Pattern PEM_PRIVATE_KEY_UNCLOSED =
            Pattern.compile("(?s)-----BEGIN [^-]*PRIVATE KEY(?: BLOCK)?-----.*");

    private static final List<Pattern> REPLACEMENTS = List.of(TOKEN_SHAPE, NPM_ACCESS_TOKEN);

    private static Pattern assignment(String label) {
        return Pattern.compile("(?i)(?<![a-z0-9])(" + label + ")\\b" + SEPARATOR + VALUE);
    }

    /** The user-information pattern behind one scheme separator: scheme and separator, user, secret. */
    private static Pattern urlUserInformation(String schemeSeparator) {
        return Pattern.compile("(?i)(?<![a-z0-9+.-])([a-z][a-z0-9+.-]*+" + schemeSeparator + ")([^\\s/?#:]++):(\\S+)@");
    }

    /** The replacement prefix that keeps groups 1 to 3 and masks the group that follows them. */
    private static final String KEEP_THREE_GROUPS = "$1$2$3";

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
        var masked = AUTH_HEADER.matcher(value).replaceAll(KEEP_THREE_GROUPS + Matcher.quoteReplacement(REDACTED));
        masked = BEARER.matcher(masked).replaceAll("$1" + Matcher.quoteReplacement(REDACTED));
        for (var pattern : ASSIGNMENTS) {
            masked = pattern.matcher(masked).replaceAll("$1$2" + Matcher.quoteReplacement(REDACTED));
        }
        for (var pattern : REPLACEMENTS) {
            masked = pattern.matcher(masked).replaceAll(Matcher.quoteReplacement(REDACTED));
        }
        for (var pattern : List.of(URL_USER_INFORMATION, URL_USER_INFORMATION_ENCODED)) {
            masked = pattern.matcher(masked).replaceAll("$1$2:" + Matcher.quoteReplacement(REDACTED) + "@");
        }
        masked = QUOTED_CREDENTIAL_FLAG.matcher(masked)
                .replaceAll(KEEP_THREE_GROUPS + Matcher.quoteReplacement(REDACTED) + "$3");
        masked = UNCLOSED_CREDENTIAL_FLAG.matcher(masked)
                .replaceAll(KEEP_THREE_GROUPS + Matcher.quoteReplacement(REDACTED));
        masked = CREDENTIAL_FLAG.matcher(masked)
                .replaceAll("$1$2" + Matcher.quoteReplacement(REDACTED));
        masked = PEM_PRIVATE_KEY.matcher(masked).replaceAll(Matcher.quoteReplacement(REDACTED));
        masked = PEM_PRIVATE_KEY_UNCLOSED.matcher(masked).replaceAll(Matcher.quoteReplacement(REDACTED));
        return Optional.of(masked);
    }

    /**
     * Redacts the parts of an activity entry that are written to the journal.
     * <p>
     * The text, the tool target and the link target are stripped of their terminal escape sequences
     * ({@link BuildLogText#stripAnsi(String)}, the expression the unparsed-evidence excerpt uses) before they are
     * redacted, so an escape sequence that splits a label from its value ({@code ESC[1mpassword ESC[0m=value}) cannot
     * hide the value from the label patterns. The stored journal text therefore carries no escape sequence. The bound of
     * each part is applied after the redaction.
     *
     * @param entry an activity entry as the runtime read it
     * @return the entry with its text, tool target and link target stripped of escape sequences and redacted
     */
    public static ActivityEntry redact(ActivityEntry entry) {
        var masked = entry;
        if (entry.text() != null) {
            var redacted = cut(stripAndRedact(entry.text()), ActivityEntry.TEXT_BOUND);
            masked = masked.withText(redacted.value()).withTruncated(entry.truncated() || redacted.cut());
        }
        if (entry.tool() != null && entry.tool().target() != null) {
            masked = masked.withTool(new ActivityEntry.Tool(entry.tool().name(),
                    cutUtf16(stripAndRedact(entry.tool().target()), 1000).value()));
        }
        if (entry.link() != null && entry.link().target() != null) {
            masked = masked.withLink(new ActivityEntry.Link(entry.link().rel(),
                    cutUtf16(stripAndRedact(entry.link().target()), 128).value()));
        }
        return masked;
    }

    /** Strips the terminal escape sequences of a journal value, then redacts it. */
    private static String stripAndRedact(String value) {
        return redactText(BuildLogText.stripAnsi(value)).orElseThrow();
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
