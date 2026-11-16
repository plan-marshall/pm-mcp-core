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
import java.util.stream.Stream;

import de.planmarshall.core.config.LimitProperties;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.InputDecl;
import de.planmarshall.workflow.ast.InputType;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.UnitFrontmatter;
import de.planmarshall.workflow.ast.UnitFrontmatter.InterfaceElement;
import de.planmarshall.workflow.ast.UnitType;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

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
 * (Zp), an unknown key, a key of its own missing ({@code name}, {@code description}, {@code type}, {@code icon},
 * {@code modification_date}, {@code version}, {@code author}) or repeated (those and {@code signature}), a value that
 * is neither plain nor double-quoted, an {@code entry_description} or {@code exit_description} that does not stand
 * directly below its {@code entry} or {@code exit} line, or is the second description of one element, a {@code
 * reads_config} or {@code reads_fact} value outside its grammar or twice, more than 32 such values, an {@code input}
 * whose name is no {@code param_name}, is a reserved keyword or is declared twice, more than 16 inputs, an {@code
 * input_type} that does not stand directly below its {@code input} or is missing or repeats, an {@code input_default}
 * or {@code input_fallback} out of its order, beside the optional form or outside the input's type, and an {@code
 * input_description} out of its order or beyond its bounds.
 * <p>
 * The reader reads the frontmatter from the head of the source alone and never the body. A frontmatter holds at most
 * the value of {@link #MAX_FRONTMATTER_LINES} lines, its two delimiters included; the text a refusal echoes from the
 * source is cut at the value of {@link #MAX_ECHO_LENGTH} characters and marked with {@code ...}.
 * <p>
 * It checks every field: {@code name} 3 to 100 characters, {@code description} 1 to 1000, each entry and exit
 * description 1 to 500, {@code type} one of the {@link UnitType} wire names, {@code icon} an icon id
 * ({@code [a-z][a-z0-9._-]{0,63}} without {@code ..}, so no path, no blank and no separator but the dot, the dash and
 * the underscore), an entry an identifier and an exit an outcome id (the identifier grammar of the Identifier
 * Registry, at most 32 characters), each unique, an exit never
 * {@code stepped_out}, {@code modification_date} a valid calendar date {@code YYYY-MM-DD} not after the day of the
 * validation in UTC, {@code version} in the release version grammar, and {@code author} 1 to 100 characters. The
 * {@code signature} is carried and never verified.
 * <p>
 * A refusal is a {@link Result.Refused} with the rule {@link RefusalRule#FRONTMATTER}, the resource, the line and the
 * column; no exception escapes {@link #read}.
 *
 * @since 0.1
 */
@UtilityClass
public final class UnitFrontmatterReader {

    /** The line that opens and closes the frontmatter. */
    static final String DELIMITER = "---";

    /**
     * The most lines a frontmatter holds, its two {@code ---} delimiters included, read from the module's limits
     * resource when the class loads.
     */
    public static final int MAX_FRONTMATTER_LINES = Math.toIntExact(
            LimitProperties.read(UnitLimits.RESOURCE, UnitLimits.MAX_FRONTMATTER_LINES));

    /**
     * The most characters of the source a refusal echoes; a longer text is cut at this bound and marked with
     * {@code ...}, read from the module's limits resource when the class loads.
     */
    public static final int MAX_ECHO_LENGTH = Math.toIntExact(
            LimitProperties.read(UnitLimits.RESOURCE, UnitLimits.MAX_ECHO_LENGTH));

    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_TYPE = "type";
    private static final String KEY_ICON = "icon";
    private static final String KEY_ENTRY = "entry";
    private static final String KEY_ENTRY_DESCRIPTION = "entry_description";
    private static final String KEY_EXIT = "exit";
    private static final String KEY_EXIT_DESCRIPTION = "exit_description";
    private static final String KEY_READS_CONFIG = "reads_config";
    private static final String KEY_READS_FACT = "reads_fact";
    private static final String KEY_INPUT = "input";
    private static final String KEY_INPUT_TYPE = "input_type";
    private static final String KEY_INPUT_DEFAULT = "input_default";
    private static final String KEY_INPUT_FALLBACK = "input_fallback";
    private static final String KEY_INPUT_DESCRIPTION = "input_description";
    private static final String KEY_MODIFICATION_DATE = "modification_date";
    private static final String KEY_VERSION = "version";
    private static final String KEY_AUTHOR = "author";
    private static final String KEY_SIGNATURE = "signature";

    /** The most inputs one frontmatter declares. */
    static final int MAX_INPUTS = 16;

    /** The most values one {@code reads_config} or {@code reads_fact} key carries. */
    static final int MAX_READS = 32;

    /** The longest {@code input_description}. */
    static final int MAX_INPUT_DESCRIPTION = 500;

    /** The keys that occur exactly once, in the order of the field table. */
    private static final List<String> REQUIRED_KEYS = List.of(KEY_NAME, KEY_DESCRIPTION, KEY_TYPE, KEY_ICON,
            KEY_MODIFICATION_DATE, KEY_VERSION, KEY_AUTHOR);

    /** The keys that occur at most once. */
    private static final Set<String> SINGLE_KEYS = Set.of(KEY_NAME, KEY_DESCRIPTION, KEY_TYPE, KEY_ICON,
            KEY_MODIFICATION_DATE, KEY_VERSION, KEY_AUTHOR, KEY_SIGNATURE);

    /** The keys that are one field of the input they stand below. */
    private static final Set<String> INPUT_KEYS = Set.of(KEY_INPUT_TYPE, KEY_INPUT_DEFAULT, KEY_INPUT_FALLBACK,
            KEY_INPUT_DESCRIPTION);

    /** Every admitted key: the fields of this reader plus the input sub-keys, which are defined once. */
    private static final Set<String> KEYS = Stream.concat(Stream.of(KEY_NAME, KEY_DESCRIPTION, KEY_TYPE, KEY_ICON,
            KEY_ENTRY, KEY_ENTRY_DESCRIPTION, KEY_EXIT, KEY_EXIT_DESCRIPTION, KEY_READS_CONFIG, KEY_READS_FACT,
            KEY_INPUT, KEY_MODIFICATION_DATE, KEY_VERSION, KEY_AUTHOR, KEY_SIGNATURE), INPUT_KEYS.stream())
            .collect(Collectors.toUnmodifiableSet());

    /** The outcome id of the implicit control exit, which no frontmatter declares. */
    private static final String STEPPED_OUT = "stepped_out";

    /** The DSL identifier of the Identifier Registry, with its bound of 32 characters. */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_-]{0,31}");

    /** The free parameter name of the Identifier Registry, which admits no hyphen, with its bound of 32 characters. */
    private static final Pattern PARAM_NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    /** An enum value of the DSL, which admits upper case and the hyphen. */
    private static final Pattern ENUM_VALUE = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    /** The low bound of an int range: zero or a positive integer without a leading zero. */
    private static final Pattern RANGE_LOW = Pattern.compile("0|[1-9]\\d*");

    /** The high bound of an int range: a positive integer without a leading zero. */
    private static final Pattern RANGE_HIGH = Pattern.compile("[1-9]\\d*");

    /** A non-negative integer default. */
    private static final Pattern INTEGER = Pattern.compile("0|[1-9]\\d*");

    /** The icon id: lowercase, at most 64 characters, and never a path. */
    private static final Pattern ICON_ID = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    /** The release version of the Identifier Registry; its bound of 64 characters is checked apart. */
    private static final Pattern RELEASE_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.-]+)?");

    private static final int MAX_RELEASE_VERSION = 64;

    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /** The characters a plain YAML scalar may not start with, the one definition the reader and the printer share. */
    static final String INDICATORS = "-?:,[]{}#&*!|>'\"%@`";

    private static final String VALUE_TYPES = Arrays.stream(UnitType.values()).map(UnitType::wireName)
            .collect(Collectors.joining(", "));

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
        private final List<String> readsConfig = new ArrayList<>();
        private final List<String> readsFact = new ArrayList<>();
        private final List<Input> inputs = new ArrayList<>();
        private final Set<String> entryNames = new HashSet<>();
        private final Set<String> exitNames = new HashSet<>();
        private final Set<String> inputNames = new HashSet<>();
        private @Nullable UnitType type;
        private @Nullable LocalDate modificationDate;
        private @Nullable String previousKey;
        private @Nullable Input input;

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
                var text = last ? source.substring(start) : withoutCarriageReturn(source.substring(start, end));
                rejectForbiddenCharacter(text, number);
                if (number == 1) {
                    requireOpening(text);
                } else if (DELIMITER.equals(text)) {
                    return new Result.Read(complete(number), number + 1);
                } else if (last && text.isEmpty()) {
                    throw unclosed(number);
                } else {
                    line(text, number);
                }
                if (last) {
                    throw unclosed(number);
                }
                start = end + 1;
                number++;
            }
        }

        private static String withoutCarriageReturn(String text) {
            return !text.isEmpty() && text.charAt(text.length() - 1) == '\r' ? text.substring(0, text.length() - 1)
                    : text;
        }

        private static void requireOpening(String text) {
            if (!DELIMITER.equals(text)) {
                throw new Refusal(1, 1, "the unit does not open with its frontmatter, a line '---'");
            }
        }

        private static Refusal unclosed(int number) {
            return new Refusal(number, 1, "the frontmatter is not closed by a line '---'");
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
                value = KEY_INPUT_DEFAULT.equals(key) && "[]".equals(rest.substring(1)) ? "[]"
                        : value(rest.substring(1), number, column);
            }
            field(key, value, number, column);
            previousKey = key;
        }

        private void field(String key, String value, int number, int column) {
            if (KEY_INPUT.equals(key)) {
                closeInput(number, 1);
                openInput(value, number, column);
                return;
            }
            if (INPUT_KEYS.contains(key)) {
                inputField(key, value, number, column);
                return;
            }
            closeInput(number, 1);
            if (SINGLE_KEYS.contains(key) && singles.containsKey(key)) {
                throw new Refusal(number, 1, "the key '%s' is repeated in the frontmatter".formatted(echo(key)));
            }
            switch (key) {
                case KEY_NAME -> checkLength(key, value, 3, 100, number, column);
                case KEY_DESCRIPTION -> checkLength(key, value, 1, 1000, number, column);
                case KEY_TYPE -> type = UnitType.fromWireName(value).orElseThrow(() -> new Refusal(number, column,
                        "the type '%s' is none of %s".formatted(echo(value), VALUE_TYPES)));
                case KEY_ICON -> checkIcon(value, number, column);
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
                case KEY_READS_CONFIG -> readsConfig.add(read(KEY_READS_CONFIG, readsConfig, configKey(value, number,
                        column), number, column));
                case KEY_READS_FACT -> readsFact.add(read(KEY_READS_FACT, readsFact, factKind(value, number, column),
                        number, column));
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

        /** Opens the declaration of one input: its name, the first of its lines. */
        private void openInput(String value, int number, int column) {
            if (inputs.size() >= MAX_INPUTS) {
                throw new Refusal(number, column,
                        "the frontmatter declares more than %d inputs".formatted(MAX_INPUTS));
            }
            if (!PARAM_NAME.matcher(value).matches()) {
                throw new Refusal(number, column,
                        "the input '%s' is no name of lowercase letters, digits and underscores, at most 32"
                                .formatted(echo(value)));
            }
            if (WorkflowParser.RESERVED_KEYWORDS.contains(value)) {
                throw new Refusal(number, column, "the input '%s' is a reserved keyword".formatted(echo(value)));
            }
            if (!inputNames.add(value)) {
                throw new Refusal(number, column, "the input '%s' is declared twice".formatted(echo(value)));
            }
            input = new Input(value, new SourceLocation(resourceName, number, column));
        }

        /**
         * Applies one sub-key of the input its {@code input} line opened, refusing an order the grammar closes. Each key
         * follows the one before it, so a repeated sub-key stands out of that order and is refused there
         * ({@link #outOfOrder}); this is the one owner of the order, the sub-keys have no second reading.
         */
        private void inputField(String key, String value, int number, int column) {
            var open = requireInput(key, number);
            switch (key) {
                case KEY_INPUT_TYPE -> {
                    if (!KEY_INPUT.equals(previousKey)) {
                        throw outOfOrder(key, KEY_INPUT, number);
                    }
                    open.optional = value.endsWith("?");
                    open.type = inputType(value, number, column);
                }
                case KEY_INPUT_DEFAULT -> {
                    if (!KEY_INPUT_TYPE.equals(previousKey)) {
                        throw outOfOrder(key, KEY_INPUT_TYPE, number);
                    }
                    refuseBesideOptional(open, key, number, column);
                    open.defaultValue = inputDefault(open, value, number, column);
                }
                case KEY_INPUT_FALLBACK -> {
                    if (!KEY_INPUT_TYPE.equals(previousKey) && !KEY_INPUT_DEFAULT.equals(previousKey)) {
                        throw outOfOrder(key, KEY_INPUT_TYPE, number);
                    }
                    refuseBesideOptional(open, key, number, column);
                    open.fallback = configKey(value, number, column);
                }
                default -> {
                    if (!KEY_INPUT_TYPE.equals(previousKey) && !KEY_INPUT_DEFAULT.equals(previousKey)
                            && !KEY_INPUT_FALLBACK.equals(previousKey)) {
                        throw outOfOrder(key, KEY_INPUT_TYPE, number);
                    }
                    checkLength(key, value, 1, MAX_INPUT_DESCRIPTION, number, column);
                    open.description = value;
                }
            }
        }

        private Input requireInput(String key, int number) {
            if (input == null) {
                throw new Refusal(number, 1, "the %s does not stand below an 'input' line".formatted(key));
            }
            return input;
        }

        private static Refusal outOfOrder(String key, String expected, int number) {
            return new Refusal(number, 1,
                    "the %s stands out of the order of its input; it follows '%s'".formatted(key, expected));
        }

        private static void refuseBesideOptional(Input input, String key, int number, int column) {
            if (input.optional) {
                throw new Refusal(number, column,
                        "the input '%s' is optional and declares a %s; two ways to say the same".formatted(input.name,
                                key));
            }
        }

        /** Ends the declaration of the input its {@code input} line opened, requiring the type it must carry. */
        private void closeInput(int number, int column) {
            if (input == null) {
                return;
            }
            if (input.type == null) {
                throw new Refusal(number, column,
                        "the input '%s' lacks its input_type".formatted(input.name));
            }
            inputs.add(input);
            input = null;
        }

        /** Adds a repeatable read value, refusing a value the list already holds and one past the bound. */
        private static String read(String key, List<String> values, String value, int number, int column) {
            if (values.size() >= MAX_READS) {
                throw new Refusal(number, column,
                        "the frontmatter declares more than %d %s values".formatted(MAX_READS, key));
            }
            if (values.contains(value)) {
                throw new Refusal(number, column, "the %s value '%s' is declared twice".formatted(key, echo(value)));
            }
            return value;
        }

        /** Checks a {@code config_key}: one or more identifier segments, each of at most 32 characters and no reserved
         * keyword, joined by dots (the same rule the parser applies to {@code depends_on_config}). */
        private static String configKey(String value, int number, int column) {
            var segments = value.split("\\.", -1);
            for (var segment : segments) {
                if (!IDENTIFIER.matcher(segment).matches()) {
                    throw new Refusal(number, column,
                            "the configuration key '%s' is no dotted key of identifier segments".formatted(echo(value)));
                }
                if (WorkflowParser.RESERVED_KEYWORDS.contains(segment)) {
                    throw new Refusal(number, column,
                            "the configuration key '%s' names the reserved keyword '%s'".formatted(echo(value),
                                    segment));
                }
            }
            return value;
        }

        private static String factKind(String value, int number, int column) {
            if (!IDENTIFIER.matcher(value).matches()) {
                throw new Refusal(number, column,
                        "the fact kind '%s' is no identifier of at most 32 characters".formatted(echo(value)));
            }
            return value;
        }

        /**
         * Reads an {@code input_type}: {@code enum[...]}, {@code int} with its optional range, {@code list<enum[...]>}
         * and the trailing {@code ?} of the optional form.
         */
        private static InputType inputType(String value, int number, int column) {
            var text = value.endsWith("?") ? value.substring(0, value.length() - 1) : value;
            if (text.startsWith("enum[") && text.endsWith("]")) {
                return new InputType.Enumeration(enumValues(
                        text.substring("enum[".length(), text.length() - 1), number, column));
            }
            if ("int".equals(text)) {
                return new InputType.IntRange(null, null);
            }
            if (text.startsWith("int[") && text.endsWith("]")) {
                return intRange(text.substring("int[".length(), text.length() - 1), number, column);
            }
            if (text.startsWith("list<enum[") && text.endsWith("]>")) {
                return new InputType.EnumList(enumValues(
                        text.substring("list<enum[".length(), text.length() - 2), number, column));
            }
            throw new Refusal(number, column,
                    "the input_type '%s' is none of enum[...], int with its optional range and list<enum[...]>"
                            .formatted(echo(value)));
        }

        private static List<String> enumValues(String text, int number, int column) {
            var values = new ArrayList<String>();
            for (var value : text.split("\\|", -1)) {
                if (!ENUM_VALUE.matcher(value).matches()) {
                    throw new Refusal(number, column,
                            "the enum value '%s' is no value of upper or lower case letters, digits, '-' and '_'"
                                    .formatted(echo(value)));
                }
                if (values.contains(value)) {
                    throw new Refusal(number, column, "the enum value '%s' occurs twice".formatted(echo(value)));
                }
                values.add(value);
            }
            return values;
        }

        private static InputType.IntRange intRange(String text, int number, int column) {
            var separator = text.indexOf("..");
            if (separator < 0) {
                throw new Refusal(number, column,
                        "the input_type range '%s' is no 'int[<low>..<high>]'".formatted(echo(text)));
            }
            var low = text.substring(0, separator);
            var high = text.substring(separator + 2);
            if (!RANGE_LOW.matcher(low).matches() || !RANGE_HIGH.matcher(high).matches()) {
                throw new Refusal(number, column,
                        "the input_type range '%s' is no 'int[<low>..<high>]'".formatted(echo(text)));
            }
            try {
                var range = new InputType.IntRange(Integer.parseInt(low), Integer.parseInt(high));
                if (range.min() != null && range.min() > range.max()) {
                    throw new Refusal(number, column,
                            "the input_type range '%s' has its low bound above its high bound".formatted(echo(text)));
                }
                return range;
            } catch (NumberFormatException _) {
                throw new Refusal(number, column, "the input_type range '%s' is out of range".formatted(echo(text)));
            }
        }

        /** Reads an {@code input_default} and refuses one outside the input's type. */
        private static CallValue inputDefault(Input input, String value, int number, int column) {
            CallValue parsed;
            if ("[]".equals(value)) {
                parsed = new CallValue.EnumList(List.of());
            } else if (INTEGER.matcher(value).matches()) {
                try {
                    parsed = new CallValue.IntValue(Integer.parseInt(value));
                } catch (NumberFormatException _) {
                    throw new Refusal(number, column,
                            "the input_default '%s' is out of the range of an integer".formatted(echo(value)));
                }
            } else if (ENUM_VALUE.matcher(value).matches()) {
                parsed = new CallValue.EnumValue(value);
            } else {
                throw new Refusal(number, column,
                        "the input_default '%s' is no enum value, no integer and no '[]'".formatted(echo(value)));
            }
            if (!fits(input.type, parsed)) {
                throw new Refusal(number, column, "the input_default '%s' is outside the type %s of the input '%s'"
                        .formatted(echo(value), input.type.sourceText(), input.name));
            }
            return parsed;
        }

        /** @return whether a literal value is compatible with the input type */
        private static boolean fits(InputType type, CallValue value) {
            return switch (type) {
                case InputType.Enumeration(var values) -> value instanceof CallValue.EnumValue(var single)
                        && values.contains(single);
                case InputType.IntRange(var min, var max) -> value instanceof CallValue.IntValue(var integer)
                        && (min == null || integer >= min) && (max == null || integer <= max);
                case InputType.EnumList(var values) -> value instanceof CallValue.EnumList(var list)
                        && values.containsAll(list);
            };
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
            closeInput(closingLine, 1);
            for (var key : REQUIRED_KEYS) {
                if (!singles.containsKey(key)) {
                    throw new Refusal(closingLine, 1, "the frontmatter lacks the key '%s'".formatted(key));
                }
            }
            return new UnitFrontmatter(singles.get(KEY_NAME), singles.get(KEY_DESCRIPTION), type,
                    singles.get(KEY_ICON), entries.stream().map(Element::toInterfaceElement).toList(),
                    exits.stream().map(Element::toInterfaceElement).toList(), readsConfig, readsFact,
                    inputs.stream().map(Input::toInputDecl).toList(),
                    Objects.requireNonNull(modificationDate, "modificationDate"),
                    singles.get(KEY_VERSION), singles.get(KEY_AUTHOR),
                    singles.get(KEY_SIGNATURE), new SourceLocation(resourceName, 1, 1));
        }

        private static void checkVersion(String value, int number, int column) {
            if (value.length() > MAX_RELEASE_VERSION || !RELEASE_VERSION.matcher(value).matches()) {
                throw new Refusal(number, column,
                        "the version '%s' is no release version such as 1.0.0".formatted(echo(value)));
            }
        }

        /**
         * Checks an icon id: {@code [a-z][a-z0-9._-]{0,63}}, never a path. The grammar already excludes the slash,
         * the backslash, the blank and the colon, so only the dot-dot segment needs a rule of its own.
         */
        private static void checkIcon(String value, int number, int column) {
            if (!ICON_ID.matcher(value).matches() || value.contains("..")) {
                throw new Refusal(number, column,
                        "the icon '%s' is no icon id such as phase.init".formatted(echo(value)));
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
                if (category.isPresent()) {
                    throw new Refusal(number, index + 1,
                            "the %s U+%04X in the frontmatter".formatted(category.get(), codePoint));
                }
            }
        }

        /**
         * @return what the character cannot be used for in a frontmatter shown to the operator, or empty when it
         *         is admitted: an ISO control character, a format character (Cf, such as a bidi override or a
         *         zero-width space), a line separator (Zl) or a paragraph separator (Zp)
         */
        private static Optional<String> category(int codePoint) {
            if (Character.isISOControl(codePoint)) {
                return Optional.of("control character");
            }
            return switch (Character.getType(codePoint)) {
                case Character.FORMAT -> Optional.of("format character");
                case Character.LINE_SEPARATOR -> Optional.of("line separator");
                case Character.PARAGRAPH_SEPARATOR -> Optional.of("paragraph separator");
                default -> Optional.empty();
            };
        }

        /**
         * @param text a text of the source a refusal echoes
         * @return the text, cut at the value of {@link UnitFrontmatterReader#MAX_ECHO_LENGTH} characters and marked with
         *         {@code ...} when it is longer
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

    /** An input while its frontmatter is read; its sub-keys follow it in the order the grammar closes. */
    private static final class Input {

        private final String name;
        private final SourceLocation location;
        private @Nullable InputType type;
        private boolean optional;
        private @Nullable CallValue defaultValue;
        private @Nullable String fallback;
        private @Nullable String description;

        private Input(String name, SourceLocation location) {
            this.name = name;
            this.location = location;
        }

        private InputDecl toInputDecl() {
            return InputDecl.builder()
                    .name(name)
                    .type(Objects.requireNonNull(type, "type"))
                    .optional(optional)
                    .defaultValue(defaultValue)
                    .fallback(fallback)
                    .description(description)
                    .location(location)
                    .build();
        }
    }

    /** An entry or exit while its frontmatter is read; its description may follow on the next line. */
    private static final class Element {
        private final String name;
        private @Nullable String description;

        private Element(String name) {
            this.name = name;
        }

        private InterfaceElement toInterfaceElement() {
            return new InterfaceElement(name, description);
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
