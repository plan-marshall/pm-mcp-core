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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import de.planmarshall.core.role.corpus.SourceRow.MalformedSource;

/**
 * Reads the rows of a CSV source file as RFC 4180 defines it (PM-WORK-6), written by hand without a CSV library.
 * <p>
 * The file is UTF-8 encoded, with an optional byte order mark. The first record is the header and names the fields;
 * each name is unique and not empty. Fields are separated by commas; a field enclosed in double quotes may contain
 * commas, line breaks and doubled double quotes, which stand for one. Records end with CRLF or LF; the last record
 * may end without one. A record with another number of fields than the header, an unterminated quoted field, a quote
 * inside an unquoted field, or a character after a closing quote other than a separator is refused with the line the
 * record starts on.
 *
 * @since 0.1
 */
public final class CsvRowReader {

    private static final char QUOTE = '"';
    private static final char COMMA = ',';
    private static final char CR = '\r';
    private static final char LF = '\n';
    private static final char BOM = '﻿';

    private final String text;
    private int position;
    private int line = 1;

    private CsvRowReader(String text) {
        this.text = text;
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            position = 1;
        }
    }

    /**
     * @param content the file, UTF-8 encoded
     * @return the rows after the header, in the order of the file, each named by the header; unmodifiable
     * @throws MalformedSource if the file is not RFC 4180 CSV with a header, or not UTF-8
     */
    public static List<SourceRow> read(byte[] content) {
        Objects.requireNonNull(content, "content");
        return new CsvRowReader(decode(content)).rows();
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            throw new MalformedSource(1, "the file is not UTF-8", e);
        }
    }

    private List<SourceRow> rows() {
        if (atEnd()) {
            throw new MalformedSource(1, "the header is missing", null);
        }
        var header = record();
        var names = new HashSet<String>();
        for (var name : header) {
            if (name.isEmpty()) {
                throw new MalformedSource(1, "a field of the header has no name", null);
            }
            if (!names.add(name)) {
                throw new MalformedSource(1, "the header names the field '%s' twice".formatted(name), null);
            }
        }
        var rows = new ArrayList<SourceRow>();
        while (!atEnd()) {
            var start = line;
            var values = record();
            if (values.size() != header.size()) {
                throw new MalformedSource(start, "the record has %d fields, the header %d".formatted(values.size(),
                        header.size()), null);
            }
            var fields = new LinkedHashMap<String, String>();
            for (var index = 0; index < header.size(); index++) {
                fields.put(header.get(index), values.get(index));
            }
            rows.add(new SourceRow(start, fields));
        }
        return List.copyOf(rows);
    }

    private boolean atEnd() {
        return position >= text.length();
    }

    /** Reads one record and its line end; leaves the position after it. */
    private List<String> record() {
        var start = line;
        var values = new ArrayList<String>();
        while (true) {
            values.add(atEnd() || text.charAt(position) != QUOTE ? unquoted(start) : quoted(start));
            if (atEnd()) {
                return values;
            }
            var next = text.charAt(position);
            if (next == COMMA) {
                position++;
            } else {
                lineEnd(start);
                return values;
            }
        }
    }

    private String unquoted(int start) {
        var from = position;
        while (!atEnd()) {
            var current = text.charAt(position);
            if (current == COMMA || current == LF || current == CR) {
                break;
            }
            if (current == QUOTE) {
                throw new MalformedSource(start, "a quote inside an unquoted field", null);
            }
            position++;
        }
        return text.substring(from, position);
    }

    private String quoted(int start) {
        position++;
        var value = new StringBuilder();
        while (true) {
            if (atEnd()) {
                throw new MalformedSource(start, "an unterminated quoted field", null);
            }
            var current = text.charAt(position);
            if (current == QUOTE) {
                if (position + 1 < text.length() && text.charAt(position + 1) == QUOTE) {
                    value.append(QUOTE);
                    position += 2;
                    continue;
                }
                position++;
                if (!atEnd() && text.charAt(position) != COMMA && text.charAt(position) != LF
                        && text.charAt(position) != CR) {
                    throw new MalformedSource(start, "a character after a closing quote", null);
                }
                return value.toString();
            }
            if (current == LF) {
                line++;
            }
            value.append(current);
            position++;
        }
    }

    /** Consumes a CRLF or LF at the position. */
    private void lineEnd(int start) {
        if (text.charAt(position) == CR) {
            if (position + 1 >= text.length() || text.charAt(position + 1) != LF) {
                throw new MalformedSource(start, "a carriage return without a line feed", null);
            }
            position++;
        }
        position++;
        line++;
    }
}
