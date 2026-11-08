/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.SchemaResource;

/**
 * The parity assertions the schema-file tests share: the members a codec writes, the members and required members a
 * schema file declares, and the two negative controls that prove the comparison reports a member present in the file
 * and absent from the record, and the reverse.
 * <p>
 * The schema files are loaded through {@link SchemaResource} (ADR {@code doc/adr/016-schemas-are-files-of-the-implementing-module.adoc}).
 * A member removed from the record's document and refused by its codec is a required member; a member the codec accepts
 * the removal of is optional.
 */
@UtilityClass
class SchemaParity {

    private static final JsonFactory JSON = new JsonFactory();
    private static final Path FILE = Path.of("schema-parity.json");

    /**
     * @param resource the absolute class-path location of a schema file
     * @return the schema object in member order
     */
    static Map<?, ?> load(String resource) {
        return SchemaResource.load(resource);
    }

    /**
     * @param schema a schema object
     * @return the names of its {@code properties} in schema order
     */
    static List<String> properties(Map<?, ?> schema) {
        return names(object(schema.get("properties")));
    }

    /**
     * @param schema a schema object
     * @return its {@code required} member names in schema order
     */
    static List<String> required(Map<?, ?> schema) {
        return strings(schema.get("required"));
    }

    /**
     * @param schema a schema object
     * @param name   a member of its {@code properties}
     * @return the member's declaration
     */
    static Map<?, ?> member(Map<?, ?> schema, String name) {
        return object(object(schema.get("properties")).get(name));
    }

    /**
     * @param document a written record document
     * @return the document as a map in member order
     */
    static Map<?, ?> document(byte[] document) {
        return object(SchemaResource.parse(new String(document, UTF_8)));
    }

    /**
     * @param document a written record document
     * @return its top-level member names in the order the codec wrote them
     */
    static List<String> members(byte[] document) {
        return names(document(document));
    }

    /**
     * @param document a record document as a map
     * @return its member names in the order the map holds them
     */
    static List<String> members(Map<?, ?> document) {
        return names(document);
    }

    /**
     * @param recorded the member names of the written record
     * @param schema   the schema file's object
     * @return one {@code file-only:} or {@code record-only:} entry per differing member, empty when they agree
     */
    static List<String> memberDiffs(List<String> recorded, Map<?, ?> schema) {
        var expected = properties(schema);
        var diffs = new ArrayList<String>();
        minus(expected, recorded).forEach(name -> diffs.add("file-only:" + name));
        minus(recorded, expected).forEach(name -> diffs.add("record-only:" + name));
        return diffs;
    }

    /**
     * The first negative control: a member present in the file and absent from the record.
     *
     * @param recorded the member names of the written record
     * @param schema   the unchanged schema
     * @param added    the member to add to the file
     * @return the differences the comparison reports
     */
    static List<String> withFileOnly(List<String> recorded, Map<?, ?> schema, String added) {
        var properties = new LinkedHashMap<String, Object>();
        object(schema.get("properties")).forEach((name, value) -> properties.put(String.valueOf(name), value));
        properties.put(added, Map.of("type", "string"));
        return memberDiffs(recorded, withProperties(schema, properties));
    }

    /**
     * The second negative control: a member present in the record and absent from the file.
     *
     * @param recorded the member names of the written record
     * @param schema   the unchanged schema
     * @param removed  the member to remove from the file
     * @return the differences the comparison reports
     */
    static List<String> withRecordOnly(List<String> recorded, Map<?, ?> schema, String removed) {
        var properties = new LinkedHashMap<String, Object>();
        object(schema.get("properties")).forEach((name, value) -> {
            if (!String.valueOf(name).equals(removed)) {
                properties.put(String.valueOf(name), value);
            }
        });
        return memberDiffs(recorded, withProperties(schema, properties));
    }

    /**
     * @param recorded the member names of the written record
     * @param schema   a schema object with an {@code enum} on the named member
     * @param name     the member that carries the enumeration
     * @return the values of that member's {@code enum}, as a set
     */
    static Set<String> enumOf(Map<?, ?> schema, String name) {
        return values(member(schema, name));
    }

    /**
     * @param declaration a schema declaration that carries an {@code enum}
     * @return the values of that {@code enum}, in file order
     */
    static Set<String> values(Object declaration) {
        return Set.copyOf(strings(object(declaration).get("enum")));
    }

    /**
     * @param declaration a schema declaration that carries a numeric bound
     * @param name        the bound's member, such as {@code minimum}
     * @return the bound
     */
    static int bound(Object declaration, String name) {
        return ((Number) object(declaration).get(name)).intValue();
    }

    /**
     * @param values    the values of an enumeration
     * @param wireName  the value's name in the record
     * @param <E>       the enumeration
     * @return the wire names
     */
    static <E extends Enum<E>> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Removes each member of the written document in turn and reads the document again.
     *
     * @param written the written document
     * @param reader  the codec's read method, which refuses a document it cannot read
     * @return the members whose absence the codec refuses, sorted by name
     */
    static Set<String> requiredByCodec(byte[] written, BiFunction<Path, byte[], ?> reader) {
        var document = document(written);
        var required = new TreeSet<String>();
        for (var name : document.keySet()) {
            var without = new LinkedHashMap<String, Object>();
            document.forEach((member, value) -> {
                if (!String.valueOf(member).equals(String.valueOf(name))) {
                    without.put(String.valueOf(member), value);
                }
            });
            try {
                reader.apply(FILE, write(without));
            } catch (StoreRefusal _) {
                required.add(String.valueOf(name));
            }
        }
        return required;
    }

    /**
     * @param value a parsed JSON value
     * @return the value as an object
     */
    static Map<?, ?> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("not a JSON object: " + value);
    }

    /**
     * @param value a parsed JSON value
     * @return the value as a list
     */
    static List<?> array(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException("not a JSON array: " + value);
    }

    private static Map<?, ?> withProperties(Map<?, ?> schema, Map<String, Object> properties) {
        var copy = new LinkedHashMap<String, Object>();
        schema.forEach((name, value) -> copy.put(String.valueOf(name), value));
        copy.put("properties", properties);
        return copy;
    }

    private static List<String> names(Map<?, ?> object) {
        return object.keySet().stream().map(String::valueOf).toList();
    }

    private static List<String> strings(Object value) {
        return array(value).stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private static Set<String> minus(List<String> values, List<String> without) {
        var result = new LinkedHashMap<String, Boolean>();
        values.forEach(value -> result.putIfAbsent(value, Boolean.TRUE));
        without.forEach(result::remove);
        return new LinkedHashSet<>(result.keySet());
    }

    private static byte[] write(Object value) {
        var out = new ByteArrayOutputStream();
        try (var generator = JSON.createGenerator(out, JsonEncoding.UTF8)) {
            writeValue(generator, value);
        } catch (IOException e) {
            throw new UncheckedIOException("the document cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeValue(JsonGenerator generator, Object value) throws IOException {
        switch (value) {
            case null -> generator.writeNull();
            case Map<?, ?> map -> {
                generator.writeStartObject();
                for (var entry : map.entrySet()) {
                    generator.writeFieldName(String.valueOf(entry.getKey()));
                    writeValue(generator, entry.getValue());
                }
                generator.writeEndObject();
            }
            case List<?> list -> {
                generator.writeStartArray();
                for (var element : list) {
                    writeValue(generator, element);
                }
                generator.writeEndArray();
            }
            case String text -> generator.writeString(text);
            case Boolean flag -> generator.writeBoolean(flag);
            case Integer number -> generator.writeNumber(number);
            case Long number -> generator.writeNumber(number);
            case BigDecimal number -> generator.writeNumber(number);
            case Double number -> generator.writeNumber(number);
            default -> throw new IllegalArgumentException("the document holds an unsupported value: " + value.getClass());
        }
    }
}
