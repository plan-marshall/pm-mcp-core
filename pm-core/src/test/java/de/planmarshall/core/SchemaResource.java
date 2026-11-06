/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Loads a JSON schema file from the class path of a test (test helper, published in the {@code tests} artifact of
 * {@code pm-core}).
 * <p>
 * A schema is a file of the module that implements it, beside the code it describes, and is read by its class-path
 * location (ADR {@code doc/adr/016-schemas-are-files-of-the-implementing-module.adoc}). The schema belongs to the
 * module, so an absent resource or a file that is not a JSON object is a failure of the test, never a skipped test.
 * <p>
 * A resource path is absolute on the class path and carries no leading slash, for example
 * {@code de/planmarshall/core/store/audit.schema.json}.
 */
@UtilityClass
public class SchemaResource {

    private static final JsonFactory JSON = new JsonFactory();

    /**
     * Reads a class-path resource as UTF-8 text.
     *
     * @param resourcePath the absolute class-path location of the resource, without a leading slash
     * @return the content of the resource, unchanged
     */
    public static String text(String resourcePath) {
        try (var stream = SchemaResource.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return fail("the schema resource is absent from the class path: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fail("the schema resource cannot be read: " + resourcePath, e);
        }
    }

    /**
     * Reads a class-path resource and parses it as a JSON object.
     *
     * @param resourcePath the absolute class-path location of the resource, without a leading slash
     * @return the object as a map in member order
     */
    public static Map<?, ?> load(String resourcePath) {
        Object value;
        try {
            value = parse(text(resourcePath));
        } catch (UncheckedIOException e) {
            return fail("the schema resource is not JSON: " + resourcePath, e);
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return fail("the schema resource is not a JSON object: " + resourcePath);
    }

    /**
     * Parses a JSON document into maps (in member order), lists, strings, numbers, booleans and {@code null} for a
     * JSON {@code null}.
     *
     * @param json the JSON document
     * @return the parsed value
     */
    public static Object parse(String json) {
        try (var parser = JSON.createParser(json)) {
            parser.nextToken();
            return read(parser);
        } catch (IOException e) {
            throw new UncheckedIOException("specification schema is not JSON", e);
        }
    }

    private static Object read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        return switch (token) {
            case START_OBJECT -> {
                var object = new LinkedHashMap<String, Object>();
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    var name = parser.currentName();
                    parser.nextToken();
                    object.put(name, read(parser));
                }
                yield object;
            }
            case START_ARRAY -> {
                var array = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    array.add(read(parser));
                }
                yield array;
            }
            case VALUE_STRING -> parser.getText();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getNumberValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            default -> throw new IOException("unexpected token " + token);
        };
    }
}
