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

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
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
 * not; a quoted value is matched through its closing quote, spaces and at most one line end included, that line end
 * also when a backslash escapes it, and an unclosed quote masks the rest of the line);</li>
 * <li>a PEM private-key block or a PGP private-key block ({@code -----BEGIN PGP PRIVATE KEY BLOCK-----}) whose
 * {@code BEGIN} line is carried inside the text.</li>
 * </ul>
 * Each non-assignment shape is anchored to its own form — a URL needs its {@code scheme://} and {@code @}, a flag
 * needs its leading {@code --} and a following space, a key block needs its {@code BEGIN} marker — so prose cannot
 * match. A hash, a commit id, a path and a prose sentence that merely contains the word {@code token} or
 * {@code PRIVATE KEY} are not secrets and stay readable.
 * <p>
 * The label, separator, URL and credential-flag patterns are Unicode-aware: their {@code \s}, {@code \S}, {@code \w} and
 * {@code \b} match their Unicode forms, so a no-break space cannot separate a label from its value, and a quoted
 * assignment value runs to its closing quote. The provider-token and npm-token shapes stay ASCII, as the wire formats
 * they encode are. The soft hyphen, the zero-width space, the word joiner and the byte-order mark are removed before the
 * shapes are matched, so one of them cannot split a label; none of them is visible, so removing one costs no evidence.
 * The zero-width joiners and the bidi controls stay in the text, because they carry meaning in Persian, Indic and
 * right-to-left text and in emoji sequences.
 * <p>
 * An escaped form reads the JSON object embedded as a JSON string ({@code {\"password\":\"secret\"}}): the escaped
 * opening quote is taken with the separator and the rest of the line is masked, as the unclosed form does.
 * <p>
 * The replacement keeps the label so the reader knows what was removed: {@code "Authorization: Bearer …"} becomes
 * {@code "Authorization: Bearer [redacted]"}, {@code "https://user:secret@host"} keeps its scheme and user, and a key
 * block is replaced whole (an unclosed block masks from its {@code BEGIN} line to the end of the text). The key-block
 * scan runs before the label passes, so a block that is itself the value of a labelled assignment
 * ({@code PRIVATE_KEY="-----BEGIN …"} or {@code secret: -----BEGIN …}) is masked whole instead of losing its
 * {@code BEGIN} marker to the label pass, which would leave the body and the {@code END} line readable.
 * <p>
 * The residue: a PEM or PGP block whose {@code BEGIN} line lies before the excerpt window, so that the text starts
 * inside the block, is not masked by {@link #redactText(String)}, because nothing in the text marks the key body as one.
 * The window is not widened to reach the marker. A windowed read that carries the block's {@code END} line can mask its
 * interior prefix through that {@code END} ({@link #redactKeyBlockTail(String)}); a window that carries neither marker
 * cannot be recognised and stays readable. The redactor is idempotent: redacting redacted text changes nothing.
 * <p>
 * On the journal path ({@link #redact(ActivityEntry)}) the terminal escape sequences are stripped before the shapes are
 * matched, so a colour code between a label and its value does not hide the value; {@link #redactText(String)} leaves
 * escape sequences to its caller.
 *
 * @since 0.1
 */
@UtilityClass
public class SecretRedactor {

    /** The text that replaces a redacted value. */
    public static final String REDACTED = "[redacted]";

    /**
     * The invisible characters removed before any shape is matched: the soft hyphen, the zero-width space, the word
     * joiner and the byte-order mark. They carry no evidence, so removing one costs nothing. The zero-width joiners
     * ({@code U+200C} and {@code U+200D}) and the bidi controls ({@code U+200E} to {@code U+200F} and {@code U+202A} to
     * {@code U+202E}) stay: they carry meaning in Persian, Indic and right-to-left text and in emoji sequences (an
     * operator decision, ADR {@code core-build/009}). The cost of keeping them: a label split by a joiner is no longer
     * matched, and a split label with a joiner is not a realistic secret shape.
     */
    private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u200B\\u2060\\uFEFF]");

    /** The separator head every assignment form shares: an optional quoted or backslash-quoted opening and the operator. */
    private static final String SEPARATOR_HEAD = "(?:\\\\?[\"'])?\\s*[:=]\\s*";

    /** The shared separator head with its own capturing group and an optional closing quote. */
    private static final String SEPARATOR = "(" + SEPARATOR_HEAD + "(?:\\\\?[\"'])?)";

    /** The head of every quoted assignment form: its label group closes, then the separator group opens. */
    private static final String QUOTED_ASSIGNMENT_HEAD = ")\\b(" + SEPARATOR_HEAD + ")";

    /** The opening quote of a quoted value, its own capturing group. */
    private static final String OPENING_QUOTE = "([\"'])";

    /** The rest of a quoted assignment's line, its own capturing group. */
    private static final String REST_OF_LINE = "([^\\r\\n]*)";

    private static final String VALUE = "([^\\s\"',;]{4,})";

    private static final Pattern AUTH_HEADER = Pattern.compile(
            "(?iU)\\b(authorization)\\b(\\s*[:=]\\s*)((?i:bearer|basic|token)\\s+)?([^\\s\"',;]{4,})");

    /**
     * The anchors every assignment form shares: the case-insensitive Unicode mode and the boundary before the label.
     */
    private static final String ASSIGNMENT_PREFIX = "(?iU)(?<![a-z0-9])(";

    /** The label of a password-shaped assignment, in the three forms the assignment lists apply. */
    private static final String LABEL_PASSWORD = "pass(?:[_-]?(?:word|wd|phrase|code|key))?";

    /** The label of a secret-shaped assignment. */
    private static final String LABEL_SECRET = "secret";

    /** The label of a token-shaped assignment. */
    private static final String LABEL_TOKEN = "token";

    /** The label of a key-shaped assignment. */
    private static final String LABEL_KEY = "(?:api|access|private)[_-]?key";

    /** The label of an auth-shaped assignment. */
    private static final String LABEL_AUTH = "auth";

    /** The camelCase label suffix: a label that follows a letter only when it starts a camelCase word. */
    private static final String LABEL_CAMEL_CASE = "(?-i:[a-z]+(?=[A-Z]))"
            + "(?:Secret|Token|Password|Passwd|Passphrase|Passcode|Passkey|ApiKey|AccessKey|PrivateKey)";

    /** The credential labels, one entry each, shared by every assignment form so the three lists cannot drift apart. */
    private static final List<String> ASSIGNMENT_LABELS = List.of(LABEL_PASSWORD, LABEL_SECRET, LABEL_TOKEN, LABEL_KEY,
            LABEL_AUTH, LABEL_CAMEL_CASE);

    /**
     * The credential labels as one pattern each: a label's own alternation stays small, so neither the regex nor the
     * reader has to hold a combined 30-branch expression. A label may follow a letter only when it starts a camelCase
     * word, and may be followed by a closing quote before the separator, as a JSON key is.
     */
    private static final List<Pattern> ASSIGNMENTS =
            ASSIGNMENT_LABELS.stream().map(SecretRedactor::assignment).toList();

    private static final Pattern BEARER = Pattern.compile("(?iU)\\b(bearer\\s+)([\\w.~+/=-]{8,})");

    /** The prefixes of the provider-token shapes, shared by the token pattern and the cut-tail rule. */
    private static final String TOKEN_PREFIXES = "sk|ghp|gho|ghu|ghs|ghr|github_pat|xox[abprs]";

    /** The npm access-token prefix, shared by the token pattern and the cut-tail rule. */
    private static final String NPM_TOKEN_PREFIX = "npm_";

    private static final Pattern TOKEN_SHAPE = Pattern.compile(
            "\\b(?:" + TOKEN_PREFIXES + ")[-_][\\w-]{12,}");

    /**
     * The npm access token: {@code npm_} followed by 36 alphanumeric characters (npm Registry API documentation, "Delete
     * npm access token"; the shape introduced in 2021, whose last six characters are a Base62 CRC32 checksum). The exact
     * length is an operator decision and keeps a package name that merely starts with {@code npm_} readable. A run of 37
     * or more alphanumeric characters after {@code npm_}, a run followed by {@code _} and a run preceded by a word
     * character stay readable as well, because none of them is an npm token.
     */
    private static final Pattern NPM_ACCESS_TOKEN = Pattern.compile("\\b" + NPM_TOKEN_PREFIX + "[A-Za-z0-9]{36}\\b");

    /**
     * The user-information form of a URL: {@code scheme://user:secret@host}. The scan keeps the scheme and the user
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
     * ({@code https://host/p?at=a:b@c}) stays readable. The user is optional, so the empty-user form
     * {@code https://:secret@host} (Azure DevOps, git credential helpers) is masked as well.
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
     * The scan is linear in the text: it finds the scheme starts once, takes the user up to the next colon and looks the
     * token's last {@code @} up once per token, keeping that answer for the token a line holds, so a line such as
     * {@code x://y:x://y:…} without any {@code @} costs one pass instead of a scan back from every scheme start. The
     * plain and the percent-encoded separator are two passes in sequence, so neither carries the alternation of both.
     */
    private static final List<String> URL_SCHEME_SEPARATORS = List.of("://", "%3A%2F%2F");

    /**
     * A URL user-information head that a cut left without its closing {@code @}: the cut can fall between a URL's
     * password and its {@code @}, and the ordinary URL shape then does not match, so the secret is masked here. The
     * scheme separators are the ones {@link #URL_SCHEME_SEPARATORS} runs the scan for, so a new separator cannot miss
     * this cut-tail rule.
     */
    private static final Pattern CUT_URL_HEAD = cutUrlHead();

    /**
     * A provider-token or npm-token head that a cut left incomplete: built from the same prefixes as
     * {@link #TOKEN_SHAPE} and {@link #NPM_ACCESS_TOKEN}, so a new prefix cannot miss the cut-tail rule.
     */
    private static final Pattern CUT_TOKEN_TAIL = Pattern.compile(
            "(?iU)\\b(?:" + NPM_TOKEN_PREFIX + "[a-z0-9]*|(?:" + TOKEN_PREFIXES + ")[-_][\\w-]*)$");

    /**
     * The closed list of credential flags whose value is separated by whitespace instead of {@code :} or {@code =}.
     * The flag name is matched whatever its case ({@code --PASSWORD x}). The leading {@code --} anchors the shape, so
     * {@code --password-file notes.txt} is a different flag and stays readable, and a flag outside the list
     * ({@code --key value}, an operator decision) stays readable as well. The quoted forms are matched first: a quoted
     * value runs to the first unescaped closing quote, also across one line end to its closing quote on the next line
     * (never further; the line end may be escaped by a backslash, a shell line continuation, and still counts as the
     * one line end), and a quote that does not close on its own line or the next masks the rest of its line (an escaped
     * quote is no close, so {@code --password "abc\"def} masks {@code abc\"def}); an unquoted value runs to the next
     * whitespace and may carry a comma, a {@code ;} or a quote.
     * <p>
     * The false-positive cost: the word after a listed flag is read as its value, so usage text such as
     * {@code --token <token>} masks the placeholder {@code <token>}.
     */
    private static final String CREDENTIAL_FLAG_NAMES =
            "password|passwd|passphrase|token|secret|auth-token|api-key|apikey|access-token|client-secret|otp";

    /** The shared head of a credential-flag pattern: the flag name and the horizontal whitespace after it. */
    private static final String CREDENTIAL_FLAG_HEAD =
            "(?<![\\w-])(--(?:" + CREDENTIAL_FLAG_NAMES + "))(\\h+)";

    /** A run of characters of a quoted flag value that are neither a backslash, the opening quote nor a line end. */
    private static final String QUOTED_PLAIN_RUN = "(?:(?!\\3)[^\\\\\\r\\n])*+";

    /**
     * One line of a quoted flag value as an unrolled loop: a plain run, then any number of escape pairs (a backslash and
     * the character after it, the quote included) each followed by a plain run. No repetition holds an alternation and
     * every repetition is possessive, so the match neither backtracks nor recurses per character.
     */
    private static final String QUOTED_LINE =
            QUOTED_PLAIN_RUN + "(?:\\\\[^\\r\\n]" + QUOTED_PLAIN_RUN + ")*+";

    /**
     * The one line end a quoted flag value may run across: a CR LF, a CR or an LF, optionally preceded by a backslash
     * (a shell line continuation inside the quotes). {@link #QUOTED_LINE} cannot consume a backslash before a line end,
     * because its escape pair takes a character that is not a line end, so the backslash is taken here and the escaped
     * line end counts as the one line end; the limit stays one line end.
     */
    private static final String QUOTED_LINE_END = "\\\\?(?:\\r\\n|\\r|\\n)";

    /** A quoted flag value up to its closing quote: one line, then at most one line end and one more line. */
    private static final String QUOTED_VALUE = QUOTED_LINE + "(?:" + QUOTED_LINE_END + QUOTED_LINE + ")?";

    private static final Pattern QUOTED_CREDENTIAL_FLAG = Pattern.compile(
            CREDENTIAL_FLAG_HEAD + "([\"'])(" + QUOTED_VALUE + ")\\3",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * A quoted flag value whose quote does not close on its own line or the next: the lookahead refuses the value
     * exactly when {@link #QUOTED_VALUE}, the form {@link #QUOTED_CREDENTIAL_FLAG} matches, reaches an unescaped closing
     * quote, so an escaped quote ({@code --password "abc\"def}) is no close and the rest of the line is masked, while a
     * value that closes after one line end, escaped or not, is left to the quoted form.
     */
    private static final Pattern UNCLOSED_CREDENTIAL_FLAG = Pattern.compile(
            CREDENTIAL_FLAG_HEAD + "([\"'])(?!" + QUOTED_VALUE + "\\3)([^\\r\\n]*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern CREDENTIAL_FLAG = Pattern.compile(
            CREDENTIAL_FLAG_HEAD + "([^\\s\"']\\S*)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * The value of a quoted assignment: the plain runs and escape pairs of {@link #QUOTED_LINE} up to the same closing
     * quote (group 3). The lookahead accepts a closing quote only when whitespace, a delimiter ({@code , ; ) ] }}) or the
     * end of the text follows it, so a value cannot swallow the next label and its value ({@code pass="abc token="x"}
     * and {@code pass="abc token="!x"} both mask the rest of the line instead).
     */
    private static final String QUOTED_ASSIGNMENT_VALUE = QUOTED_LINE + "\\3" + "(?=[\\s,;)\\]}]|$)";

    /**
     * The quoted form of a credential-shaped assignment with no acceptable closing quote on its line: it runs after the
     * closed form, so a value the closed form masked is left alone, and a value whose close would swallow a later label
     * — the quote followed by something other than whitespace, a delimiter or the end of the text — is masked to the
     * end of its line instead. The lookahead refuses the text exactly when {@link #QUOTED_ASSIGNMENT_VALUE} reaches an
     * acceptable close on the line.
     */
    private static final List<Pattern> UNCLOSED_QUOTED_ASSIGNMENTS =
            ASSIGNMENT_LABELS.stream().map(SecretRedactor::unclosedQuotedAssignment).toList();

    /**
     * The escaped form of a credential-shaped assignment, the shape a JSON object takes when it is embedded as a JSON
     * string: the label's quotes and the value's opening quote are each preceded by a backslash
     * ({@code {\"password\":\"hunter2\"}}). The sharp forms cannot read it, because their opening quote is a bare one;
     * this form takes the backslash with the opening quote and masks the rest of the line, as the unclosed form does.
     */
    private static final List<Pattern> ESCAPED_JSON_ASSIGNMENTS =
            ASSIGNMENT_LABELS.stream().map(SecretRedactor::escapedJsonAssignment).toList();

    /**
     * The quoted form of a credential-shaped assignment. A quoted value runs to its own closing quote, so a value whose
     * first word is shorter than the four characters the unquoted form requires ({@code token="ab cd ef"}) is masked
     * whole. The escape handling of {@link #QUOTED_LINE} keeps an escaped quote from closing the value.
     */
    private static final List<Pattern> QUOTED_ASSIGNMENTS =
            ASSIGNMENT_LABELS.stream().map(SecretRedactor::quotedAssignment).toList();

    /**
     * The head of a PEM private-key block or a PGP private-key block ({@code -----BEGIN PGP PRIVATE KEY BLOCK-----}).
     * A window that starts inside a block (its {@code BEGIN} line before the window) is not masked: that is the
     * residue the class documentation names.
     */
    private static final Pattern PEM_BEGIN = Pattern.compile("-----BEGIN [^-]*PRIVATE KEY(?: BLOCK)?-----");

    /** The tail of a PEM private-key block or a PGP private-key block, the match made from a {@link #PEM_BEGIN}. */
    private static final Pattern PEM_END = Pattern.compile("-----END [^-]*PRIVATE KEY(?: BLOCK)?-----");

    private static final List<Pattern> REPLACEMENTS = List.of(TOKEN_SHAPE, NPM_ACCESS_TOKEN);

    private static Pattern assignment(String label) {
        return Pattern.compile(ASSIGNMENT_PREFIX + label + ")\\b" + SEPARATOR + VALUE);
    }

    /**
     * The quoted form of one credential label: the label, the separator, the opening quote and the value up to the same
     * closing quote, read through the escape pairs of {@link #QUOTED_LINE}.
     */
    private static Pattern quotedAssignment(String label) {
        return Pattern.compile(ASSIGNMENT_PREFIX + label + QUOTED_ASSIGNMENT_HEAD + OPENING_QUOTE
                + QUOTED_ASSIGNMENT_VALUE);
    }

    /** One unclosed quoted assignment: the label, the separator, the opening quote and the rest of its line. */
    private static Pattern unclosedQuotedAssignment(String label) {
        return Pattern.compile(ASSIGNMENT_PREFIX + label + QUOTED_ASSIGNMENT_HEAD + OPENING_QUOTE + "(?!"
                + QUOTED_ASSIGNMENT_VALUE + ")" + REST_OF_LINE);
    }

    /** One escaped-JSON assignment: the label, the separator, the escaped opening quote and the rest of its line. */
    private static Pattern escapedJsonAssignment(String label) {
        return Pattern.compile(ASSIGNMENT_PREFIX + label + QUOTED_ASSIGNMENT_HEAD + "(\\\\[\"'])" + REST_OF_LINE);
    }

    /**
     * Masks the user-information shapes of one scheme separator in one linear left-to-right pass.
     * <p>
     * Each scheme start is visited once: the scheme is the run of scheme characters before the separator whose first
     * character is a letter, the user is the possessive run up to the next colon, and the secret is masked up to the last
     * {@code @} of the token that starts after that colon. The token's end and its last {@code @} are found in one pass
     * over that token, and the answer is kept for every scheme start of the same token, so a token without an {@code @}
     * costs one pass rather than a lookup per start.
     *
     * @param value           the text to redact
     * @param schemeSeparator {@code ://} or the percent-encoded {@code %3A%2F%2F}
     * @return the text with every user-information shape of that separator masked
     */
    private static String redactUrlUserInformation(String value, String schemeSeparator) {
        var out = new StringBuilder(value.length());
        var token = new UrlToken();
        var copied = 0;
        var separator = indexOfSeparator(value, schemeSeparator, 0);
        while (separator >= 0) {
            var match = urlUserInformationMatch(value, separator, schemeSeparator.length(), token);
            var next = match == null ? separator + 1 : match.lastAt() + 1;
            if (match != null) {
                out.append(value, copied, match.secretStart()).append(REDACTED).append('@');
                copied = next;
            }
            separator = indexOfSeparator(value, schemeSeparator, next);
        }
        return out.append(value, copied, value.length()).toString();
    }

    /**
     * One user-information shape of a scheme separator, or {@code null} when the separator starts no such shape.
     *
     * @param value           the text to inspect
     * @param separator       where the scheme separator starts
     * @param separatorLength the length of the separator
     * @param token           the token cache in use for this pass
     * @return the shape's secret start and its token's last {@code @}, or {@code null}
     */
    private static @Nullable UrlMatch urlUserInformationMatch(String value, int separator, int separatorLength,
            UrlToken token) {
        if (schemeStart(value, separator) < 0) {
            return null;
        }
        var userEnd = tokenUserStop(value, separator + separatorLength, token);
        if (userEnd >= value.length() || value.charAt(userEnd) != ':') {
            return null;
        }
        var secretStart = userEnd + 1;
        var lastAt = tokenLastAt(value, secretStart, token);
        return lastAt <= secretStart ? null : new UrlMatch(secretStart, lastAt);
    }

    /** The start of the scheme before a separator, or {@code -1} when no letter starts one there. */
    private static int schemeStart(String value, int separator) {
        var start = separator;
        while (start > 0 && isSchemeCharacter(value.charAt(start - 1))) {
            start--;
        }
        return start < separator && isSchemeStart(value.charAt(start)) ? start : -1;
    }

    /**
     * One masked user-information shape.
     *
     * @param secretStart where the secret starts, just after the user's colon
     * @param lastAt      the index of the token's last {@code @}
     */
    private record UrlMatch(int secretStart, int lastAt) {
    }

    /**
     * The token a pass last measured — its end, its last {@code @} and the first user delimiter — so a later scheme
     * start of the same token reads the answers instead of scanning the token again.
     */
    private static final class UrlToken {

        private int end = -1;
        private int lastAt = -1;
        private int userStop = -1;
    }

    /** The index of the token's last {@code @}, measured once per token. */
    private static int tokenLastAt(String value, int secretStart, UrlToken token) {
        if (token.end < 0 || secretStart >= token.end) {
            token.end = secretStart;
            token.lastAt = -1;
            while (token.end < value.length() && !isWhiteSpace(value.charAt(token.end))) {
                if (value.charAt(token.end) == '@') {
                    token.lastAt = token.end;
                }
                token.end++;
            }
        }
        return token.lastAt;
    }

    /** The first user delimiter at or after {@code from}, measured once per token. */
    private static int tokenUserStop(String value, int from, UrlToken token) {
        if (token.userStop < from) {
            var position = from;
            while (position < value.length() && !isUrlUserStop(value.charAt(position))) {
                position++;
            }
            token.userStop = position;
        }
        return token.userStop;
    }

    /** The next occurrence of a scheme separator, compared case-insensitively (the encoded form carries letters). */
    private static int indexOfSeparator(String value, String separator, int from) {
        for (var index = from; index <= value.length() - separator.length(); index++) {
            if (value.regionMatches(true, index, separator, 0, separator.length())) {
                return index;
            }
        }
        return -1;
    }

    /** Whether the character can be part of a URL scheme ({@code [a-z0-9+.-]}, its case-insensitive form). */
    private static boolean isSchemeCharacter(char value) {
        return isSchemeStart(value) || value >= '0' && value <= '9' || value == '+' || value == '.' || value == '-';
    }

    /** Whether the character can start a URL scheme ({@code [a-z]}, its case-insensitive form). */
    private static boolean isSchemeStart(char value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z';
    }

    /** Whether the character ends the user part of a URL's user information ({@code [\s/?#:]}). */
    private static boolean isUrlUserStop(char value) {
        return isWhiteSpace(value) || value == '/' || value == '?' || value == '#' || value == ':';
    }

    /**
     * Whether the character is whitespace in the Unicode form the shapes' {@code \s} matches. The set is read from the
     * matching engine once, so a Unicode update reaches this scan with the patterns instead of leaving a hand-kept list
     * behind. It is the {@code White_Space} property, which is wider than {@link Character#isWhitespace(char)}: the
     * no-break space ({@code U+00A0}) is whitespace for the shapes and for this scan alike.
     */
    private static final BitSet WHITE_SPACE = whiteSpace();

    private static BitSet whiteSpace() {
        var matcher = Pattern.compile("\\s", Pattern.UNICODE_CHARACTER_CLASS).matcher("");
        var set = new BitSet();
        for (var code = 0; code <= Character.MAX_VALUE; code++) {
            set.set(code, matcher.reset(String.valueOf((char) code)).matches());
        }
        return set;
    }

    private static boolean isWhiteSpace(char value) {
        return WHITE_SPACE.get(value);
    }

    /** The replacement prefix that keeps groups 1 to 3 and masks the group that follows them. */
    private static final String KEEP_THREE_GROUPS = "$1$2$3";

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
        var normalized = INVISIBLE.matcher(value).replaceAll("");
        // the key-block scan runs before every label pass: a block whose BEGIN marker is the value of a labelled
        // assignment would otherwise lose that marker to the label pass, and the body and END line would stay readable
        var masked = redactPrivateKeyBlocks(normalized);
        masked = AUTH_HEADER.matcher(masked)
                .replaceAll(KEEP_THREE_GROUPS + Matcher.quoteReplacement(REDACTED));
        masked = BEARER.matcher(masked).replaceAll("$1" + Matcher.quoteReplacement(REDACTED));
        // a credentialed URL is masked before the assignment passes: an inner match must never remove the `@` that
        // makes the URL shape recognisable
        for (var separator : URL_SCHEME_SEPARATORS) {
            masked = redactUrlUserInformation(masked, separator);
        }
        // the closed form first: an outer quoted value is masked whole before an inner label can be looked at
        for (var pattern : QUOTED_ASSIGNMENTS) {
            masked = pattern.matcher(masked)
                    .replaceAll(KEEP_THREE_GROUPS + Matcher.quoteReplacement(REDACTED) + "$3");
        }
        for (var pattern : UNCLOSED_QUOTED_ASSIGNMENTS) {
            masked = pattern.matcher(masked)
                    .replaceAll(KEEP_THREE_GROUPS + Matcher.quoteReplacement(REDACTED));
        }
        for (var pattern : ESCAPED_JSON_ASSIGNMENTS) {
            masked = pattern.matcher(masked)
                    .replaceAll(KEEP_THREE_GROUPS + Matcher.quoteReplacement(REDACTED));
        }
        for (var pattern : ASSIGNMENTS) {
            masked = pattern.matcher(masked).replaceAll("$1$2" + Matcher.quoteReplacement(REDACTED));
        }
        for (var pattern : REPLACEMENTS) {
            masked = pattern.matcher(masked).replaceAll(Matcher.quoteReplacement(REDACTED));
        }
        masked = QUOTED_CREDENTIAL_FLAG.matcher(masked)
                .replaceAll(KEEP_THREE_GROUPS + Matcher.quoteReplacement(REDACTED) + "$3");
        masked = UNCLOSED_CREDENTIAL_FLAG.matcher(masked)
                .replaceAll(KEEP_THREE_GROUPS + Matcher.quoteReplacement(REDACTED));
        masked = CREDENTIAL_FLAG.matcher(masked)
                .replaceAll("$1$2" + Matcher.quoteReplacement(REDACTED));
        return Optional.of(masked);
    }

    /**
     * Masks a URL user-information head that a cut left without its closing {@code @}.
     *
     * @param text the text a cut produced, its complete shapes already redacted
     * @return the text with a trailing incomplete user-information head masked
     */
    public static String redactCutUrlHead(String text) {
        return CUT_URL_HEAD.matcher(text).replaceAll("$1$2:" + Matcher.quoteReplacement(REDACTED));
    }

    /** The cut-head pattern of the scheme separators the scan runs, so a new separator cannot miss this rule. */
    private static Pattern cutUrlHead() {
        var separators = URL_SCHEME_SEPARATORS.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        return Pattern.compile(
                "(?iU)(?<![a-z0-9+.-])([a-z][a-z0-9+.-]*+(?:" + separators + "))([^\\s/?#:]*+):(\\S+)$");
    }

    /**
     * Masks the interior of a key block whose {@code BEGIN} line lies before a windowed read and whose {@code END} line
     * the window carries: the window starts inside the body, so {@link #redactPrivateKeyBlocks} has no marker to find.
     * <p>
     * A window that carries neither a {@code BEGIN} nor the block's {@code END} cannot be recognised and stays readable;
     * that is the residue the class documentation names for a block that starts before the text. A text whose own
     * {@code BEGIN} precedes its {@code END} is left to the ordinary scan. The window's invisible characters are removed
     * first, so a marker split by one is still found.
     *
     * @param text a windowed read of a text, as a job-log read hands it on
     * @return the text with an interior block prefix masked, or the text unchanged
     */
    public static String redactKeyBlockTail(String text) {
        var normalized = INVISIBLE.matcher(text).replaceAll("");
        var end = PEM_END.matcher(normalized);
        if (!end.find()) {
            return normalized;
        }
        var begin = PEM_BEGIN.matcher(normalized);
        if (begin.find() && begin.start() <= end.start()) {
            return normalized;
        }
        return REDACTED + normalized.substring(end.end());
    }

    /**
     * Masks a provider-token or npm-token head that a cut left incomplete.
     *
     * @param text the text a cut produced, its complete shapes already redacted
     * @return the text with a trailing incomplete token masked
     */
    public static String redactCutTokenTail(String text) {
        return CUT_TOKEN_TAIL.matcher(text).replaceAll(Matcher.quoteReplacement(REDACTED));
    }

    /**
     * Masks every private-key block in one left-to-right pass.
     * <p>
     * A block runs from its {@code BEGIN} marker to the next {@code END} marker after it; the scan resumes after that
     * {@code END}, so a marker already passed is never revisited and the cost stays linear in the length of the text. A
     * {@code BEGIN} without a later {@code END} masks the rest of the text: the over-masking is kept deliberately,
     * because masking too much costs evidence while leaving a key body readable costs the credential.
     */
    private static String redactPrivateKeyBlocks(String value) {
        var out = new StringBuilder(value.length());
        var begin = PEM_BEGIN.matcher(value);
        var end = PEM_END.matcher(value);
        var position = 0;
        while (begin.find(position)) {
            out.append(value, position, begin.start()).append(REDACTED);
            if (!end.find(begin.end())) {
                return out.toString();
            }
            position = end.end();
        }
        return out.append(value, position, value.length()).toString();
    }

    /**
     * Redacts the parts of an activity entry that are written to the journal.
     * <p>
     * The text, the tool target, the link target and the egress destination are stripped of their terminal escape
     * sequences
     * ({@link BuildLogText#stripAnsi(String)}, the expression the unparsed-evidence excerpt uses) before they are
     * redacted, so an escape sequence that splits a label from its value ({@code ESC[1mpassword ESC[0m=value}) cannot
     * hide the value from the label patterns. The stored journal text therefore carries no escape sequence. The bound of
     * each part is applied after the redaction.
     *
     * @param entry an activity entry as the runtime read it
     * @return the entry with its text, tool target, link target and egress destination stripped of escape sequences and
     *         redacted
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
        if (entry.egress() != null) {
            masked = masked.withEgress(new ActivityEntry.Egress(destination(entry.egress())));
        }
        if (entry.launchModel() != null) {
            masked = masked.withLaunchModel(modelIdentifier(entry.launchModel()));
        }
        if (entry.servedModel() != null) {
            masked = masked.withServedModel(modelIdentifier(entry.servedModel()));
        }
        return masked;
    }

    /** Strips the terminal escape sequences of a journal value, then redacts it. */
    private static String stripAndRedact(String value) {
        return redactText(BuildLogText.stripAnsi(value)).orElseThrow();
    }

    /**
     * The masked destination of an egress entry. Masking can shorten a value (the invisible characters are removed), so
     * a destination that drops below the record's floor is masked whole instead of being rebuilt as an invalid one: the
     * journal never refuses an entry it was handed, and a three-character value of which the visible part is shorter was
     * no {@code host:port} to begin with.
     */
    private static String destination(ActivityEntry.Egress egress) {
        var masked = cutUtf16(stripAndRedact(egress.destination()), ActivityEntry.Egress.MAX_DESTINATION).value();
        return masked.length() < ActivityEntry.Egress.MIN_DESTINATION ? REDACTED : masked;
    }

    /**
     * The masked identifier of a {@code model_differs} entry. Masking can empty a value (invisible characters removed),
     * so an identifier that drops to nothing is masked whole instead of being rebuilt as an invalid one: the journal
     * never refuses an entry it was handed.
     */
    private static String modelIdentifier(String identifier) {
        var masked = cutUtf16(stripAndRedact(identifier), ActivityEntry.MODEL_BOUND).value();
        return masked.isEmpty() ? REDACTED : masked;
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
