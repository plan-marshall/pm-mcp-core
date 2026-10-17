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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SpecificationDocument;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.ClampedValue;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;

/**
 * The codec of the job record keeps parity with the schema of Store Schemas § Job Record (PM-EXEC-4): the members of
 * the record and of its typed objects in schema order, the required members, the values of every enumeration, the
 * confinement pattern, the {@code job_id} pattern, the format version and the range of {@code outcome_row}. A member
 * added to or removed from the schema without the code fails here. The schema is read from the specification of this
 * repository below {@value SpecificationDocument#ROOT_PROPERTY}.
 */
@DisplayName("Job record schema parity")
class JobRecordSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/10-job-and-lesson-records.adoc";
    private static final String HEADING = "== Job Record";

    private static Map<?, ?> schema;
    private static Map<?, ?> properties;

    @BeforeAll
    static void readSchema() {
        schema = SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, HEADING)));
        properties = SpecificationDocument.object(schema.get("properties"));
    }

    @Test
    @DisplayName("the members of the record are the members of the schema in schema order, every one required")
    void membersEqualSchema() {
        var expected = names(properties.keySet());

        assertAll(
                () -> assertFalse(expected.isEmpty(), "the schema declares members"),
                () -> assertEquals("JobRecord", schema.get("title")),
                () -> assertEquals(JobRecordCodec.MEMBERS, expected),
                () -> assertEquals(Set.copyOf(JobRecordCodec.MEMBERS),
                        Set.copyOf(names(SpecificationDocument.array(schema.get("required"))))));
    }

    @Test
    @DisplayName("the members of the typed objects are those of the schema in schema order")
    void objectMembersEqualSchema() {
        var scope = member("scope");
        var derivation = member("budget_derivation");
        var contradiction = member("self_report_contradiction");
        var clamped = SpecificationDocument.object(member("clamped_values").get("items"));

        assertAll(
                () -> assertEquals(JobRecordCodec.SCOPE_MEMBERS, keys(scope)),
                () -> assertEquals(JobRecordCodec.SCOPE_MEMBERS, required(scope)),
                () -> assertEquals(JobRecordCodec.BUDGET_DERIVATION_MEMBERS, keys(derivation)),
                () -> assertEquals(List.of("kind"), required(derivation)),
                () -> assertEquals(JobRecordCodec.CONTRADICTION_MEMBERS, keys(contradiction)),
                () -> assertEquals(JobRecordCodec.CONTRADICTION_MEMBERS, required(contradiction)),
                () -> assertEquals(JobRecordCodec.CLAMPED_VALUE_MEMBERS, keys(clamped)),
                () -> assertEquals(JobRecordCodec.CLAMPED_VALUE_MEMBERS, required(clamped)));
    }

    @Test
    @DisplayName("the values of every enumeration are those of the schema")
    void enumerationsEqualSchema() {
        var scopeProperties = SpecificationDocument.object(member("scope").get("properties"));
        var derivationProperties = SpecificationDocument.object(member("budget_derivation").get("properties"));
        var clampedProperties = SpecificationDocument.object(
                SpecificationDocument.object(member("clamped_values").get("items")).get("properties"));

        assertAll(
                () -> assertEquals(enumOf(properties, "kind"), wireNames(JobId.Kind.values(), JobId.Kind::wireName)),
                () -> assertEquals(enumOf(properties, "execution"),
                        wireNames(Execution.values(), Execution::wireName)),
                () -> assertEquals(enumOf(scopeProperties, "type"),
                        wireNames(Scope.Type.values(), Scope.Type::wireName)),
                () -> assertEquals(enumOf(properties, "status"), wireNames(Status.values(), Status::wireName)),
                () -> assertEquals(enumOf(properties, "fate"), wireNames(Fate.values(), Fate::wireName)),
                () -> assertEquals(enumOf(derivationProperties, "kind"),
                        wireNames(BudgetDerivation.Kind.values(), BudgetDerivation.Kind::wireName)),
                () -> assertEquals(enumOf(clampedProperties, "source"),
                        wireNames(ClampedValue.Source.values(), ClampedValue.Source::wireName)));
    }

    @Test
    @DisplayName("the confinement and job id patterns, the format version and the outcome rows are the schema's")
    void patternsEqualSchema() {
        var confinements = Arrays.stream(Confinement.values())
                .map(value -> value.wireName().replace("(", "\\(").replace(")", "\\)"))
                .collect(Collectors.joining("|", "^(", ")$"));
        var prefixes = Arrays.stream(JobId.Kind.values()).map(kind -> String.valueOf(kind.prefix())).sorted()
                .collect(Collectors.joining("", "^[", "]-[0-9a-f]{16}$"));
        var outcomeRow = member("outcome_row");

        assertAll(
                () -> assertEquals(confinements, member("confinement").get("pattern")),
                () -> assertEquals(prefixes, member("job_id").get("pattern")),
                () -> assertEquals(JobRecord.FORMAT_VERSION, number(member("format_version").get("const"))),
                () -> assertEquals(JobRecord.FIRST_OUTCOME_ROW, number(outcomeRow.get("minimum"))),
                () -> assertEquals(JobRecord.LAST_OUTCOME_ROW, number(outcomeRow.get("maximum"))),
                () -> assertEquals(JobRecord.MAX_BUDGET_KEY, number(member("budget_key").get("maxLength"))));
    }

    private static Map<?, ?> member(String name) {
        return SpecificationDocument.object(properties.get(name));
    }

    private static List<String> keys(Map<?, ?> object) {
        return names(SpecificationDocument.object(object.get("properties")).keySet());
    }

    private static List<String> required(Map<?, ?> object) {
        return names(SpecificationDocument.array(object.get("required")));
    }

    private static Set<String> enumOf(Map<?, ?> owner, String name) {
        return SpecificationDocument.array(SpecificationDocument.object(owner.get(name)).get("enum")).stream()
                .filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    private static <E> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
