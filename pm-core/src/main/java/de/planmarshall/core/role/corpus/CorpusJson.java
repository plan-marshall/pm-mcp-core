/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.unreadable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.StrictJson;

/**
 * The reading helpers the codecs of the corpus formats share beside the field helpers of {@link StrictJson}.
 */
@UtilityClass
class CorpusJson {

    /**
     * The pattern of every date of the schemas of the package, compiled with {@link PythonRegex}: a four-digit year,
     * no sign, so a date {@link LocalDate#parse} reads beyond it ({@code +12345-01-01}, {@code -0001-01-01}) is
     * refused as the schema refuses it.
     */
    static final Pattern DATE_PATTERN = PythonRegex.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");

    /**
     * Turns a value a record refuses into the refusal of the document.
     *
     * @param constructor creates the record
     * @param <V>         the record
     * @return the record
     */
    static <V> V construct(Supplier<V> constructor) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    /**
     * Accepts the one spelling of a date the schemas admit and the writer produces: {@code YYYY-MM-DD} with a
     * four-digit year ({@link #DATE_PATTERN}), naming a day of the calendar.
     *
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the date
     * @throws IOException if the document does not parse as JSON
     */
    static LocalDate date(JsonParser parser, String field) throws IOException {
        var text = string(parser, field);
        if (!DATE_PATTERN.matcher(text).matches()) {
            throw unreadable("'%s' is not in the one shape of a date (YYYY-MM-DD): %s".formatted(field, text));
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeException e) {
            throw unreadable("'%s' is not a date: %s".formatted(field, text), e);
        }
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the date; empty for a JSON {@code null}
     * @throws IOException if the document does not parse as JSON
     */
    static Optional<LocalDate> nullableDate(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? Optional.empty() : Optional.of(date(parser, field));
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the strings of the array; empty for a JSON {@code null}
     * @throws IOException if the document does not parse as JSON
     */
    static Optional<List<String>> nullableStringList(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? Optional.empty()
                : Optional.of(StrictJson.stringList(parser, field));
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the number, as written in the document
     * @throws IOException if the document does not parse as JSON
     */
    static BigDecimal number(JsonParser parser, String field) throws IOException {
        var token = parser.currentToken();
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            throw unreadable("'%s' must be a number".formatted(field));
        }
        return parser.getDecimalValue();
    }

    /**
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @return the number; empty for a JSON {@code null}
     * @throws IOException if the document does not parse as JSON
     */
    static Optional<BigDecimal> nullableNumber(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? Optional.empty() : Optional.of(number(parser, field));
    }

    /**
     * Reads the name of a member of a closed set.
     *
     * @param parser the parser, positioned on the value of the field
     * @param field  the name of the field
     * @param lookup finds the member of a name
     * @param <V>    the member
     * @return the member
     * @throws IOException if the document does not parse as JSON
     */
    static <V> V wireValue(JsonParser parser, String field, Function<String, Optional<V>> lookup)
            throws IOException {
        var wireName = string(parser, field);
        return lookup.apply(wireName).orElseThrow(
                () -> unreadable("'%s' has the undeclared value '%s'".formatted(field, wireName)));
    }

    /**
     * Writes a string, or a JSON {@code null}.
     *
     * @param generator the generator, inside an object
     * @param field     the name of the field
     * @param value     the value; {@code null} writes a JSON {@code null}
     * @throws IOException if the generator cannot write
     */
    static void writeNullableString(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    /**
     * Writes an array of strings.
     *
     * @param generator the generator, inside an object
     * @param field     the name of the field
     * @param values    the strings
     * @throws IOException if the generator cannot write
     */
    static void writeStringList(JsonGenerator generator, String field, List<String> values) throws IOException {
        generator.writeArrayFieldStart(field);
        for (var value : values) {
            generator.writeString(value);
        }
        generator.writeEndArray();
    }
}
