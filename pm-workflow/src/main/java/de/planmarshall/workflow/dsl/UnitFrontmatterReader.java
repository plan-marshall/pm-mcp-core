/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import java.io.Serial;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.ast.UnitFrontmatter.InterfaceElement;
import de.planmarshall.workflow.ast.UnitType;

/**
 * Reads the frontmatter a unit opens with into a {@link UnitFrontmatter} (PM-IMPL-5): the closed, line-oriented
 * subset of YAML between the two {@code ---} lines, read by this parser of its own and never by a general YAML loader,
 * so no tag, anchor, alias or merge key is ever resolved. It reads the frontmatter alone and stops at
 * the closing {@code ---}, so a unit's name, description, type and interface can be shown without its body, and the
 * body need not be parseable.
 * <p>
 * A line ends with a line feed or with a carriage return followed by a line feed; the line ending is not part of the
 * line, so line and column numbers are the same for both. A carriage return anywhere else is a control character and
 * refused. Every line is {@code key: value} at column 1. The value is plain (no leading or trailing blank, no YAML indicator
 * character at its start, neither {@code ": "} nor {@code " #"} inside) or double-quoted with the escapes {@code \"}
 * and {@code \\} only. The reader refuses, and the first refusal wins: a missing frontmatter, a tag, an anchor, an
 * alias, a merge key, a flow collection, a block scalar, a comment, a blank or indented line, a control character, a
 * format character (Cf, such as a bidi override or a zero-width space), a line separator (Zl), a paragraph separator
 * (Zp), an unknown key, a key of its own missing ({@code name}, {@code description}, {@code type},
 * {@code modification_date}, {@code version}, {@code author}) or repeated (those and {@code signature}), a value that
 * is neither plain nor double-quoted, and an {@code entry_description} or {@code exit_description} that does not stand
 * directly below its {@code entry} or {@code exit} line, or is the second description of one element.
 * <p>
 * The reader reads the frontmatter from the head of the source alone and never the body. A frontmatter holds at most
 * {@value #MAX_FRONTMATTER_LINES} lines, its two delimiters included; the text a refusal echoes from the source is
 * cut at {@value #MAX_ECHO_LENGTH} characters and marked with {@code ...}.
 * <p>
 * It checks every field: {@code name} 3 to 100 characters, {@code description} 1 to 1000, each entry and exit
 * description 1 to 500, {@code type} one of the {@link UnitType} wire names, an entry an identifier and an exit an
 * outcome id (the identifier grammar of the Identifier Registry, at most 32 characters), each unique, an exit never
 * {@code stepped_out}, {@code modification_date} a valid calendar date {@code YYYY-MM-DD} not after the day of the
 * validation in UTC, {@code version} in the release version grammar, and {@code author} 1 to 100 characters. The
 * {@code signature} is carried and never verified.
 * <p>
 * A refusal is a {@link Result.Refused} with the rule {@link RefusalRule#FRONTMATTER}, the resource, the line and the
 * column; no exception escapes {@link #read}.
 *
 * @since 0.1
 */
public final class UnitFrontmatterReader {

    /** The line that opens and closes the frontmatter. */
    static final String DELIMITER = "---";

    /** The most lines a frontmatter holds, its two {@code ---} delimiters included. */
    public static final int MAX_FRONTMATTER_LINES = 200;

    /**
     * The most characters of the source a refusal echoes; a longer text is cut at this bound and marked with
     * {@code ...}.
     */
    public static final int MAX_ECHO_LENGTH = 80;

    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_TYPE = "type";
    private static final String KEY_ENTRY = "entry";
    private static final String KEY_ENTRY_DESCRIPTION = "entry_description";
    private static final String KEY_EXIT = "exit";
    private static final String KEY_EXIT_DESCRIPTION = "exit_description";
    private static final String KEY_MODIFICATION_DATE = "modification_date";
    private static final String KEY_VERSION = "version";
    private static final String KEY_AUTHOR = "author";
    private static final String KEY_SIGNATURE = "signature";

    /** The keys that occur exactly once, in the order of the field table. */
    private static final List<String> REQUIRED_KEYS = List.of(KEY_NAME, KEY_DESCRIPTION, KEY_TYPE,
            KEY_MODIFICATION_DATE, KEY_VERSION, KEY_AUTHOR);

    /** The keys that occur at most once. */
    private static final Set<String> SINGLE_KEYS = Set.of(KEY_NAME, KEY_DESCRIPTION, KEY_TYPE, KEY_MODIFICATION_DATE,
            KEY_VERSION, KEY_AUTHOR, KEY_SIGNATURE);

    private static final Set<String> KEYS = Set.of(KEY_NAME, KEY_DESCRIPTION, KEY_TYPE, KEY_ENTRY,
            KEY_ENTRY_DESCRIPTION, KEY_EXIT, KEY_EXIT_DESCRIPTION, KEY_MODIFICATION_DATE, KEY_VERSION, KEY_AUTHOR,
            KEY_SIGNATURE);

    /** The outcome id of the implicit control exit, which no frontmatter declares. */
    private static final String STEPPED_OUT = "stepped_out";

    /** The DSL identifier of the Identifier Registry, with its bound of 32 characters. */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_-]{0,31}");

    /** The release version of the Identifier Registry; its bound of 64 characters is checked apart. */
    private static final Pattern RELEASE_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.-]+)?");

    private static final int MAX_RELEASE_VERSION = 64;

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /** The characters a plain YAML scalar may not start with. */
    private static final String INDICATORS = "-?:,[]{}#&*!|>'\"%@`";

    private static final String VALUE_TYPES = Arrays.stream(UnitType.values()).map(UnitType::wireName)
            .collect(Collectors.joining(", "));

    private UnitFrontmatterReader() {
    }

    /**
     * Reads the frontmatter of a unit.
     *
     * @param resourceName the resource the unit is read from, named in the frontmatter's location and in every
     *                     diagnostic
     * @param source       the text of the unit; only the lines up to the closing {@code ---} are read
     * @return the frontmatter and the line its body starts at, or the first refusal
     */
    public static Result read(String resourceName, String source) {
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(source, "source");
        try {
            return new Fields(resourceName).read(source);
        } catch (Refusal refusal) {
            return new Result.Refused(new Diagnostic(RefusalRule.FRONTMATTER, resourceName, refusal.line,
                    refusal.column, refusal.getMessage()));
        }
    }

    /**
     * The result of reading a frontmatter: the frontmatter, or the one refusal that stopped the reader. A refusal is a
     * value, never an exception.
     */
    public sealed interface Result permits Result.Read, Result.Refused {

        /**
         * The frontmatter follows its closed format and its field rules.
         *
         * @param frontmatter the frontmatter
         * @param bodyLine    the 1-based line the body starts at, the line after the closing {@code ---}
         */
        record Read(UnitFrontmatter frontmatter, int bodyLine) implements Result {
        }

        /**
         * The frontmatter is missing or was refused; the first refusal wins.
         *
         * @param diagnostic why and where it was refused, with the rule {@link RefusalRule#FRONTMATTER}
         */
        record Refused(Diagnostic diagnostic) implements Result {
        }
    }

    /** The fields read so far, line by line. */
    private static final class Fields {

        private final String resourceName;
        private final Map<String, String> singles = new HashMap<>();
        private final List<Element> entries = new ArrayList<>();
        private final List<Element> exits = new ArrayList<>();
        private final Set<String> entryNames = new HashSet<>();
        private final Set<String> exitNames = new HashSet<>();
        private UnitType type;
        private LocalDate modificationDate;
        private String previousKey;

        private Fields(String resourceName) {
            this.resourceName = resourceName;
        }

        private Result.Read read(String source) {
            var start = 0;
            var number = 1;
            while (true) {
                if (number > MAX_FRONTMATTER_LINES) {
                    throw new Refusal(number, 1,
                            "the frontmatter holds more than %d lines".formatted(MAX_FRONTMATTER_LINES));
                }
                var end = source.indexOf('\n', start);
                var last = end < 0;
                var text = last ? source.substring(start) : source.substring(start, end);
                if (!last && !text.isEmpty() && text.charAt(text.length() - 1) == '\r') {
                    text = text.substring(0, text.length() - 1);
                }
                rejectForbiddenCharacter(text, number);
                if (number == 1 && !DELIMITER.equals(text)) {
                    throw new Refusal(1, 1, "the unit does not open with its frontmatter, a line '---'");
                }
                if (number > 1 && DELIMITER.equals(text)) {
                    return new Result.Read(complete(number), number + 1);
                }
                if (last && text.isEmpty() && number > 1) {
                    throw new Refusal(number, 1, "the frontmatter is not closed by a line '---'");
                }
                if (number > 1) {
                    line(text, number);
                }
                if (last) {
                    throw new Refusal(number, 1, "the frontmatter is not closed by a line '---'");
                }
                start = end + 1;
                number++;
            }
        }

        private void line(String text, int number) {
            if (text.isEmpty()) {
                throw new Refusal(number, 1, "a blank line in the frontmatter");
            }
            if (text.charAt(0) == ' ') {
                throw new Refusal(number, 1, "an indented line in the frontmatter");
            }
            if (text.startsWith("<<")) {
                throw new Refusal(number, 1, "a merge key in the frontmatter");
            }
            rejectIndicator(text, number, 1);
            var colon = text.indexOf(':');
            if (colon < 0) {
                throw new Refusal(number, 1, "a line of the frontmatter that is not 'key: value'");
            }
            var key = text.substring(0, colon);
            if (!KEYS.contains(key)) {
                throw new Refusal(number, 1, "the unknown key '%s' in the frontmatter".formatted(echo(key)));
            }
            var rest = text.substring(colon + 1);
            String value;
            int column;
            if (rest.isEmpty()) {
                value = "";
                column = colon + 2;
            } else if (rest.charAt(0) != ' ') {
                throw new Refusal(number, colon + 2, "the key '%s' is not followed by ': '".formatted(echo(key)));
            } else {
                column = colon + 3;
                value = value(rest.substring(1), number, column);
            }
            field(key, value, number, column);
            previousKey = key;
        }

        private void field(String key, String value, int number, int column) {
            if (SINGLE_KEYS.contains(key) && singles.containsKey(key)) {
                throw new Refusal(number, 1, "the key '%s' is repeated in the frontmatter".formatted(echo(key)));
            }
            switch (key) {
                case KEY_NAME -> checkLength(key, value, 3, 100, number, column);
                case KEY_DESCRIPTION -> checkLength(key, value, 1, 1000, number, column);
                case KEY_TYPE -> type = UnitType.fromWireName(value).orElseThrow(() -> new Refusal(number, column,
                        "the type '%s' is none of %s".formatted(echo(value), VALUE_TYPES)));
                case KEY_ENTRY -> entries.add(element(KEY_ENTRY, value, entryNames, number, column));
                case KEY_EXIT -> {
                    if (STEPPED_OUT.equals(value)) {
                        throw new Refusal(number, column,
                                "'stepped_out' is the implicit control exit and is never declared");
                    }
                    exits.add(element(KEY_EXIT, value, exitNames, number, column));
                }
                case KEY_ENTRY_DESCRIPTION -> describe(KEY_ENTRY, entries, value, number, column);
                case KEY_EXIT_DESCRIPTION -> describe(KEY_EXIT, exits, value, number, column);
                case KEY_MODIFICATION_DATE -> modificationDate = date(value, number, column);
                case KEY_VERSION -> checkVersion(value, number, column);
                case KEY_AUTHOR -> checkLength(key, value, 1, 100, number, column);
                default -> {
                    // the signature is carried and never verified
                }
            }
            if (SINGLE_KEYS.contains(key)) {
                singles.put(key, value);
            }
        }

        private Element element(String kind, String name, Set<String> names, int number, int column) {
            if (!IDENTIFIER.matcher(name).matches()) {
                throw new Refusal(number, column,
                        "the %s '%s' is no identifier of at most 32 characters".formatted(kind, echo(name)));
            }
            if (!names.add(name)) {
                throw new Refusal(number, column, "the %s '%s' is declared twice".formatted(kind, echo(name)));
            }
            return new Element(name);
        }

        /** Gives the element on the line before its description, which must be the element's line itself. */
        private void describe(String elementKey, List<Element> elements, String value, int number, int column) {
            var descriptionKey = elementKey + "_description";
            if (descriptionKey.equals(previousKey)) {
                throw new Refusal(number, 1, "a second description of the %s '%s'".formatted(elementKey,
                        echo(elements.getLast().name)));
            }
            if (!elementKey.equals(previousKey)) {
                throw new Refusal(number, 1,
                        "the %s does not stand directly below its %s line".formatted(descriptionKey, elementKey));
            }
            checkLength(descriptionKey, value, 1, 500, number, column);
            elements.getLast().description = value;
        }

        private static LocalDate date(String value, int number, int column) {
            var invalid = "the modification_date '%s' is no valid calendar date YYYY-MM-DD".formatted(echo(value));
            if (!DATE.matcher(value).matches()) {
                throw new Refusal(number, column, invalid);
            }
            LocalDate date;
            try {
                date = LocalDate.parse(value);
            } catch (DateTimeParseException _) {
                throw new Refusal(number, column, invalid);
            }
            if (date.isAfter(LocalDate.now(ZoneOffset.UTC))) {
                throw new Refusal(number, column,
                        "the modification_date '%s' lies after the day of the validation".formatted(echo(value)));
            }
            return date;
        }

        private static void checkLength(String key, String value, int min, int max, int number, int column) {
            var length = value.codePointCount(0, value.length());
            if (length < min || length > max) {
                throw new Refusal(number, column, "the %s has %d characters, not %d to %d".formatted(key, length, min,
                        max));
            }
        }

        private UnitFrontmatter complete(int closingLine) {
            for (var key : REQUIRED_KEYS) {
                if (!singles.containsKey(key)) {
                    throw new Refusal(closingLine, 1, "the frontmatter lacks the key '%s'".formatted(key));
                }
            }
            return new UnitFrontmatter(singles.get(KEY_NAME), singles.get(KEY_DESCRIPTION), type,
                    entries.stream().map(Element::toInterfaceElement).toList(),
                    exits.stream().map(Element::toInterfaceElement).toList(), modificationDate,
                    singles.get(KEY_VERSION), singles.get(KEY_AUTHOR),
                    Optional.ofNullable(singles.get(KEY_SIGNATURE)), new SourceLocation(resourceName, 1, 1));
        }

        private static void checkVersion(String value, int number, int column) {
            if (value.length() > MAX_RELEASE_VERSION || !RELEASE_VERSION.matcher(value).matches()) {
                throw new Refusal(number, column,
                        "the version '%s' is no release version such as 1.0.0".formatted(echo(value)));
            }
        }

        /** Reads the value after {@code ": "}, which starts at the given column. */
        private static String value(String raw, int number, int column) {
            if (raw.isEmpty() || raw.charAt(0) == ' ') {
                throw new Refusal(number, column - 1, "a blank after ': ' that starts no value");
            }
            if (raw.charAt(0) == '"') {
                return quoted(raw, number, column);
            }
            rejectIndicator(raw, number, column);
            if (raw.endsWith(" ")) {
                throw new Refusal(number, column + raw.length() - 1, "a plain value with a trailing blank");
            }
            var comment = raw.indexOf(" #");
            if (comment >= 0) {
                throw new Refusal(number, column + comment + 1, "a comment in the frontmatter");
            }
            var mapping = raw.indexOf(": ");
            if (mapping >= 0) {
                throw new Refusal(number, column + mapping,
                        "a plain value that contains ': '; write the value double-quoted");
            }
            return raw;
        }

        /** Reads a double-quoted value, which admits the escapes {@code \"} and {@code \\} only. */
        private static String quoted(String raw, int number, int column) {
            var value = new StringBuilder();
            var index = 1;
            while (index < raw.length()) {
                var character = raw.charAt(index);
                if (character == '\\') {
                    if (index + 1 < raw.length() && (raw.charAt(index + 1) == '"' || raw.charAt(index + 1) == '\\')) {
                        value.append(raw.charAt(index + 1));
                        index += 2;
                        continue;
                    }
                    throw new Refusal(number, column + index,
                            "a double-quoted value admits the escapes \\\" and \\\\ only");
                }
                if (character == '"') {
                    if (index != raw.length() - 1) {
                        throw new Refusal(number, column + index + 1,
                                "content after the closing quote of the value");
                    }
                    return value.toString();
                }
                value.append(character);
                index++;
            }
            throw new Refusal(number, column, "the double-quoted value is not closed");
        }

        /**
         * Refuses a key or value that opens with a YAML indicator character, naming the construct it would open in
         * YAML.
         */
        private static void rejectIndicator(String text, int number, int column) {
            var first = text.charAt(0);
            var construct = switch (first) {
                case '!' -> "a tag";
                case '&' -> "an anchor";
                case '*' -> "an alias";
                case '{', '[' -> "a flow collection";
                case '|', '>' -> "a block scalar";
                case '#' -> "a comment";
                default -> INDICATORS.indexOf(first) >= 0
                        ? "the YAML indicator '%c', which opens neither a key nor a plain or double-quoted value"
                        .formatted(first)
                        : null;
            };
            if (construct != null) {
                throw new Refusal(number, column, construct + " in the frontmatter");
            }
        }

        private static void rejectForbiddenCharacter(String text, int number) {
            for (var index = 0; index < text.length(); index++) {
                var codePoint = text.codePointAt(index);
                var category = category(codePoint);
                if (category != null) {
                    throw new Refusal(number, index + 1,
                            "the %s U+%04X in the frontmatter".formatted(category, codePoint));
                }
            }
        }

        /**
         * @return what the character cannot be used for in a frontmatter shown to the operator, or {@code null} when it
         *         is admitted: an ISO control character, a format character (Cf, such as a bidi override or a
         *         zero-width space), a line separator (Zl) or a paragraph separator (Zp)
         */
        private static String category(int codePoint) {
            if (Character.isISOControl(codePoint)) {
                return "control character";
            }
            return switch (Character.getType(codePoint)) {
                case Character.FORMAT -> "format character";
                case Character.LINE_SEPARATOR -> "line separator";
                case Character.PARAGRAPH_SEPARATOR -> "paragraph separator";
                default -> null;
            };
        }

        /**
         * @param text a text of the source a refusal echoes
         * @return the text, cut at {@value #MAX_ECHO_LENGTH} characters and marked with {@code ...} when it is longer
         */
        private static String echo(String text) {
            if (text.codePointCount(0, text.length()) <= MAX_ECHO_LENGTH) {
                return text;
            }
            return text.substring(0, text.offsetByCodePoints(0, MAX_ECHO_LENGTH)) + "...";
        }
    }

    /**
     * Tells whether a frontmatter value can be written as a plain scalar that this reader reads back unchanged: it is
     * not empty, has no leading or trailing blank, does not start with a YAML indicator character, and holds neither
     * {@code ": "} nor {@code " #"}. This is the one definition of the plain-value rule; {@link WorkflowPrinter} writes
     * every other value double-quoted.
     *
     * @param value the value
     * @return whether the value can be written plain
     */
    static boolean isPlain(String value) {
        return !value.isEmpty() && !value.startsWith(" ") && !value.endsWith(" ")
                && INDICATORS.indexOf(value.charAt(0)) < 0 && !value.contains(": ") && !value.contains(" #");
    }

    /** An entry or exit while its frontmatter is read; its description may follow on the next line. */
    private static final class Element {

        private final String name;
        private String description;

        private Element(String name) {
            this.name = name;
        }

        private InterfaceElement toInterfaceElement() {
            return new InterfaceElement(name, Optional.ofNullable(description));
        }
    }

    /**
     * The first refusal, carried out of the reader and turned into a {@link Diagnostic} by {@link #read}; it never
     * leaves the reader.
     */
    private static final class Refusal extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private final int line;
        private final int column;

        private Refusal(int line, int column, String message) {
            super(message, null, false, false);
            this.line = line;
            this.column = column;
        }
    }
}
