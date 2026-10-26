/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import de.planmarshall.core.SpecificationDocument;

/**
 * The finding record keeps parity with the schema {@code FindingRecord} of the specification (PM-IMPL-2): its record
 * components and those of its nested records are the schema's members in schema order, and every enumeration of
 * the record equals the schema's {@code enum} of its member. The schema is read from the specification of this repository
 * below {@value SpecificationDocument#ROOT_PROPERTY}.
 */
@DisplayName("Finding schema parity")
class FindingSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/03-findings-and-review-records.adoc";
    private static final String HEADING = "== Findings Ledger Record";
    private static final Pattern SNAKE = Pattern.compile("_([a-z0-9])");
    /** The members whose name is a Java keyword, and the component that carries them. */
    private static final Map<String, String> KEYWORD_MEMBERS = Map.of("class", "evidenceClass");

    private static Map<?, ?> schema;

    @BeforeAll
    static void readSchema() {
        schema = SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, HEADING)));
    }

    /** One member of the schema with an {@code enum}, and the enumeration of the record that must equal it. */
    private record EnumMember(String path, Class<?> type, List<?> schemaValues) {
    }

    /** A record and the schema object that describes it, at a path of the schema. */
    private record RecordObject(String path, Class<?> type, Map<?, ?> schemaObject) {
    }

    private static String componentName(String member) {
        return KEYWORD_MEMBERS.getOrDefault(member,
                SNAKE.matcher(member).replaceAll(match -> match.group(1).toUpperCase()));
    }

    private static Map<String, RecordComponent> componentsOf(Class<?> type) {
        var components = new LinkedHashMap<String, RecordComponent>();
        for (var component : type.getRecordComponents()) {
            components.put(component.getName(), component);
        }
        return components;
    }

    private static Map<?, ?> properties(Map<?, ?> object) {
        return SpecificationDocument.object(object.get("properties"));
    }

    /** Walks the record and its nested records along the schema, collecting the objects and the enum members. */
    private static void walk(RecordObject object, List<RecordObject> objects, List<EnumMember> enums) {
        objects.add(object);
        var components = componentsOf(object.type());
        properties(object.schemaObject()).forEach((name, value) -> {
            var member = SpecificationDocument.object(value);
            var path = object.path().isEmpty() ? String.valueOf(name) : object.path() + "." + name;
            var component = components.get(componentName(String.valueOf(name)));
            if (component == null) {
                return;
            }
            var type = component.getType();
            if (member.get("enum") != null) {
                enums.add(new EnumMember(path, type, SpecificationDocument.array(member.get("enum"))));
            } else if (type.isRecord() && member.get("properties") != null) {
                walk(new RecordObject(path, type, member), objects, enums);
            } else if (List.class.equals(type) && member.get("items") instanceof Map<?, ?> items
                    && items.get("properties") != null
                    && component.getGenericType() instanceof ParameterizedType generic
                    && generic.getActualTypeArguments()[0] instanceof Class<?> element && element.isRecord()) {
                walk(new RecordObject(path + "[]", element, items), objects, enums);
            }
        });
    }

    private static Set<String> wireNames(Class<?> enumType) {
        var names = new TreeSet<String>();
        for (var constant : enumType.getEnumConstants()) {
            names.add(wireName(constant));
        }
        return names;
    }

    private static String wireName(Object constant) {
        try {
            return String.valueOf(constant.getClass().getMethod("wireName").invoke(constant));
        } catch (NoSuchMethodException _) {
            return ((Enum<?>) constant).name();
        } catch (IllegalAccessException | InvocationTargetException e) {
            return fail("wire name of " + constant + " cannot be read", e);
        }
    }

    private static List<RecordObject> objects() {
        var objects = new ArrayList<RecordObject>();
        walk(new RecordObject("", Finding.class, schema), objects, new ArrayList<>());
        return objects;
    }

    private static List<EnumMember> enums() {
        var enums = new ArrayList<EnumMember>();
        walk(new RecordObject("", Finding.class, schema), new ArrayList<>(), enums);
        return enums;
    }

    @Disabled("the specification added submitted_severity and security to the finding; "
            + "re-enabled by the work package security-audit-and-code-scanning")
    @Test
    @DisplayName("the components of the record and its nested records are the schema's members in schema order")
    void componentsInSchemaOrder() {
        var objects = objects();

        assertAll(objects.stream().map(object -> (Executable) () -> {
            var expected = properties(object.schemaObject()).keySet().stream()
                    .map(name -> componentName(String.valueOf(name))).toList();
            var actual = List.copyOf(componentsOf(object.type()).keySet());
            assertEquals(expected, actual, () -> "components of " + object.type().getName() + " at '"
                    + object.path() + "'");
        }));
    }

    @Test
    @DisplayName("every required member of the schema is a component")
    void requiredMembersAreComponents() {
        var objects = objects();

        assertAll(objects.stream().filter(object -> object.schemaObject().get("required") != null)
                .map(object -> (Executable) () -> {
                    var components = componentsOf(object.type()).keySet();
                    var missing = SpecificationDocument.array(object.schemaObject().get("required")).stream()
                            .map(name -> componentName(String.valueOf(name)))
                            .filter(name -> !components.contains(name)).toList();
                    assertEquals(List.of(), missing, () -> "required members missing at '" + object.path() + "'");
                }));
    }

    @Test
    @DisplayName("the walk reaches the record and each of its nested records")
    void walkReachesNestedRecords() {
        var types = objects().stream().map(RecordObject::type).collect(Collectors.toSet());

        assertEquals(recordTree(Finding.class), types);
    }

    /** The record and every record reachable from its components, derived from the types themselves. */
    private static Set<Class<?>> recordTree(Class<?> type) {
        var types = new HashSet<Class<?>>();
        collect(type, types);
        return types;
    }

    private static void collect(Class<?> type, Set<Class<?>> types) {
        if (!types.add(type)) {
            return;
        }
        for (var component : type.getRecordComponents()) {
            var componentType = component.getType();
            if (componentType.isRecord()) {
                collect(componentType, types);
            } else if (List.class.equals(componentType)
                    && component.getGenericType() instanceof ParameterizedType generic
                    && generic.getActualTypeArguments()[0] instanceof Class<?> element && element.isRecord()) {
                collect(element, types);
            }
        }
    }

    @Test
    @DisplayName("every enumeration of the record equals the schema's enum of its member")
    void enumerationsEqualSchema() {
        var enums = enums();

        assertAll(enums.stream().map(member -> (Executable) () -> {
            assertTrue(member.type().isEnum(), () -> "the component at '" + member.path() + "' is an enumeration");
            var expected = member.schemaValues().stream().filter(Objects::nonNull).map(String::valueOf)
                    .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(expected, wireNames(member.type()), () -> "values of " + member.type().getName()
                    + " at '" + member.path() + "'");
        }));
    }

    @Test
    @DisplayName("every enumeration component of the record is checked, the source kind included")
    void everyEnumerationChecked() {
        var checked = enums().stream().map(EnumMember::type).collect(Collectors.toSet());
        var declared = objects().stream()
                .flatMap(object -> Arrays.stream(object.type().getRecordComponents()))
                .map(RecordComponent::getType).filter(Class::isEnum).collect(Collectors.toSet());

        assertAll(
                () -> assertTrue(checked.contains(FindingSourceKind.class), "source is checked"),
                () -> assertEquals(declared, checked));
    }
}
