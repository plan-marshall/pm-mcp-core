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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.role.corpus.SourceRow.MalformedSource;
import de.planmarshall.core.store.StrictJson;

/**
 * Reads the rows of a JSON Lines source file (PM-WORK-6): one JSON object per line, UTF-8 encoded.
 * <p>
 * A field whose value is a string keeps the string; any other value (a number, a boolean, {@code null}, a nested
 * object or array) keeps its JSON text exactly as the line writes it: a number is never converted ({@code 2.50} stays
 * {@code 2.50}, {@code 1e3} stays {@code 1e3}), a nested object or array is the text it spans. Lines end with LF or
 * CRLF; the last line may be blank. Any other line that is not exactly one JSON object, with each key once in the row
 * and in every object nested in it, is refused with its line number.
 *
 * @since 0.1
 */
@UtilityClass
public class JsonlRowReader {

    /**
     * @param content the file, UTF-8 encoded
     * @return the rows, in the order of the file; unmodifiable
     * @throws MalformedSource if a line is not one JSON object, or the file is not UTF-8
     */
    public static List<SourceRow> read(byte[] content) {
        Objects.requireNonNull(content, "content");
        var lines = decode(content).split("\n", -1);
        var rows = new ArrayList<SourceRow>();
        for (var index = 0; index < lines.length; index++) {
            var number = index + 1;
            var text = lines[index].endsWith("\r") ? lines[index].substring(0, lines[index].length() - 1)
                    : lines[index];
            if (text.isBlank()) {
                if (index == lines.length - 1) {
                    break;
                }
                throw new MalformedSource(number, "a blank line before the last line", null);
            }
            rows.add(new SourceRow(number, readLine(text, number)));
        }
        return List.copyOf(rows);
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            throw new MalformedSource(1, "the file is not UTF-8", e);
        }
    }

    private static LinkedHashMap<String, String> readLine(String text, int number) {
        var fields = new LinkedHashMap<String, String>();
        try (var parser = StrictJson.factory().createParser(text)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new MalformedSource(number, "not a JSON object", null);
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var name = parser.currentName();
                parser.nextToken();
                if (fields.containsKey(name)) {
                    throw new MalformedSource(number, "field '%s' twice".formatted(name), null);
                }
                fields.put(name, value(parser, text, number));
            }
            if (parser.nextToken() != null) {
                throw new MalformedSource(number, "content after the JSON object", null);
            }
        } catch (JsonProcessingException e) {
            throw new MalformedSource(number, "not JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new MalformedSource(number, "not JSON: " + e.getMessage(), e);
        }
        return fields;
    }

    /**
     * @return the string of a string value; the text of the line the value spans otherwise. A number is never
     *         converted, so {@code 2.50} stays {@code 2.50} and {@code 1e3} stays {@code 1e3}.
     */
    private static String value(JsonParser parser, String text, int number) throws IOException {
        var token = parser.currentToken();
        if (!token.isStructStart()) {
            // A scalar: the parser keeps the text of a number as written; true, false and null have one text only.
            return parser.getText();
        }
        var start = parser.currentTokenLocation().getCharOffset();
        requireUniqueKeys(parser, number);
        var end = parser.currentLocation().getCharOffset();
        return text.substring(Math.toIntExact(start), Math.toIntExact(end));
    }

    /**
     * Walks the object or array the parser stands at to its end, refusing a key named twice in any object of it.
     */
    private static void requireUniqueKeys(JsonParser parser, int number) throws IOException {
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            var names = new HashSet<String>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var name = parser.currentName();
                if (!names.add(name)) {
                    throw new MalformedSource(number, "field '%s' twice in a nested object".formatted(name), null);
                }
                walkValue(parser, parser.nextToken(), number);
            }
            return;
        }
        for (var token = parser.nextToken(); token != JsonToken.END_ARRAY; token = parser.nextToken()) {
            walkValue(parser, token, number);
        }
    }

    private static void walkValue(JsonParser parser, JsonToken token, int number) throws IOException {
        if (token == null) {
            throw new MalformedSource(number, "not JSON: the line ends inside a value", null);
        }
        if (token.isStructStart()) {
            requireUniqueKeys(parser, number);
        }
    }
}
