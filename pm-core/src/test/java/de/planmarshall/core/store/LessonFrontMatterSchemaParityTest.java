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
import static org.junit.jupiter.api.Assertions.assertNull;

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
import de.planmarshall.core.store.LessonFrontMatter.Audience;
import de.planmarshall.core.store.LessonFrontMatter.Category;
import de.planmarshall.core.store.LessonFrontMatter.LastFilingFailure;
import de.planmarshall.core.store.LessonFrontMatter.Promotion;
import de.planmarshall.core.store.LessonFrontMatter.Provenance;
import de.planmarshall.core.store.LessonFrontMatter.Remedy;
import de.planmarshall.core.store.LessonFrontMatter.Retired;
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonFrontMatter.Status;

/**
 * The codec of the lesson file keeps parity with the schema of Store Schemas § Lesson Record (PM-IMPL-2): the members
 * of the record and of its typed objects in schema order, the required members, the values of every enumeration, the
 * {@code lesson_id} pattern at all three places it occurs, the format version and the bounds of the record. A member,
 * an enumeration value or a bound changed in the schema block without the code fails here, which is why the block is
 * test input and stays byte-stable (R10).
 */
@DisplayName("Lesson front matter schema parity")
class LessonFrontMatterSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/10-job-and-lesson-records.adoc";
    private static final String HEADING = "== Lesson Record";

    private static Map<?, ?> schema;
    private static Map<?, ?> properties;
    private static Map<?, ?> definitions;

    @BeforeAll
    static void readSchema() {
        schema = SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, HEADING)));
        properties = SpecificationDocument.object(schema.get("properties"));
        definitions = SpecificationDocument.object(schema.get("$defs"));
    }

    @Test
    @DisplayName("the members of the record are the members of the schema in schema order")
    void membersEqualSchema() {
        assertAll(
                () -> assertFalse(MEMBERS().isEmpty(), "the schema declares members"),
                () -> assertEquals("LessonFrontMatter", schema.get("title")),
                () -> assertEquals(LessonCodec.MEMBERS, MEMBERS()),
                () -> assertEquals(Set.copyOf(LessonCodec.REQUIRED_MEMBERS),
                        Set.copyOf(names(SpecificationDocument.array(schema.get("required"))))));
    }

    @Test
    @DisplayName("the members of the typed objects are those of the schema in schema order")
    void objectMembersEqualSchema() {
        var source = member(definitions, "source");
        var remedy = member(properties, "remedy");
        var promotion = item(properties, "promotions");
        var revision = item(properties, "revisions");
        var mergedFrom = item(properties, "merged_from");
        var claim = member(properties, "claim");
        var retired = member(properties, "retired");

        assertAll(
                () -> assertEquals(LessonCodec.SOURCE_MEMBERS, keys(source)),
                () -> assertEquals(LessonCodec.SOURCE_REQUIRED_MEMBERS, required(source)),
                () -> assertEquals(LessonCodec.REMEDY_MEMBERS, keys(remedy)),
                () -> assertEquals(LessonCodec.REMEDY_REQUIRED_MEMBERS, required(remedy)),
                () -> assertEquals(LessonCodec.PROMOTION_MEMBERS, keys(promotion)),
                () -> assertEquals(LessonCodec.PROMOTION_REQUIRED_MEMBERS, required(promotion)),
                () -> assertEquals(LessonCodec.REVISION_MEMBERS, keys(revision)),
                () -> assertEquals(LessonCodec.REVISION_REQUIRED_MEMBERS, required(revision)),
                () -> assertEquals(LessonCodec.MERGED_FROM_MEMBERS, keys(mergedFrom)),
                () -> assertEquals(LessonCodec.MERGED_FROM_REQUIRED_MEMBERS, required(mergedFrom)),
                () -> assertEquals(LessonCodec.CLAIM_MEMBERS, keys(claim)),
                () -> assertEquals(LessonCodec.CLAIM_REQUIRED_MEMBERS, required(claim)),
                () -> assertEquals(LessonCodec.RETIRED_MEMBERS, keys(retired)),
                () -> assertEquals(LessonCodec.RETIRED_REQUIRED_MEMBERS, required(retired)));
    }

    @Test
    @DisplayName("the values of every enumeration are those of the schema")
    void enumerationsEqualSchema() {
        var source = SpecificationDocument.object(member(definitions, "source").get("properties"));
        var remedy = SpecificationDocument.object(member(properties, "remedy").get("properties"));
        var promotion = SpecificationDocument.object(item(properties, "promotions").get("properties"));
        var retired = SpecificationDocument.object(member(properties, "retired").get("properties"));

        assertAll(
                () -> assertEquals(enumOf(properties, "category"),
                        wireNames(Category.values(), Category::wireName)),
                () -> assertEquals(enumOf(properties, "status"), wireNames(Status.values(), Status::wireName)),
                () -> assertEquals(enumOf(properties, "provenance"),
                        wireNames(Provenance.values(), Provenance::wireName)),
                () -> assertEquals(enumOf(properties, "audience"), wireNames(Audience.values(), Audience::wireName)),
                () -> assertEquals(enumOf(properties, "last_filing_failure"),
                        wireNames(LastFilingFailure.values(), LastFilingFailure::wireName)),
                () -> assertEquals(enumOf(source, "kind"), wireNames(Source.Kind.values(), Source.Kind::wireName)),
                () -> assertEquals(enumOf(remedy, "kind"), wireNames(Remedy.Kind.values(), Remedy.Kind::wireName)),
                () -> assertEquals(enumOf(promotion, "kind"),
                        wireNames(Promotion.Kind.values(), Promotion.Kind::wireName)),
                () -> assertEquals(enumOf(retired, "verdict"),
                        wireNames(Retired.Verdict.values(), Retired.Verdict::wireName)),
                () -> assertEquals(enumOf(retired, "lesson_state"),
                        wireNames(Retired.LessonState.values(), Retired.LessonState::wireName)));
    }

    @Test
    @DisplayName("the lesson id grammar is the schema's at every place it occurs")
    void lessonIdGrammarEqualsSchema() {
        var mergedFrom = SpecificationDocument.object(item(properties, "merged_from").get("properties"));
        var retired = SpecificationDocument.object(member(properties, "retired").get("properties"));

        assertAll(
                () -> assertEquals(LessonId.PATTERN, member(properties, "lesson_id").get("pattern")),
                () -> assertEquals(LessonId.MAX_LENGTH, number(member(properties, "lesson_id").get("maxLength"))),
                () -> assertEquals(LessonId.PATTERN, patternOf(mergedFrom, "lesson_id"),
                        "the merged-from id pattern"),
                () -> assertEquals(LessonId.PATTERN, patternOf(retired, "into"), "the retirement id pattern"));
    }

    @Test
    @DisplayName("the format version, the bounds and the patterns are those of the schema")
    void boundsEqualSchema() {
        var remedy = SpecificationDocument.object(member(properties, "remedy").get("properties"));
        var revision = SpecificationDocument.object(item(properties, "revisions").get("properties"));
        var retired = SpecificationDocument.object(member(properties, "retired").get("properties"));

        assertAll(
                () -> assertEquals(LessonCodec.FORMAT_VERSION, number(member(properties, "format_version").get("const"))),
                () -> assertEquals(LessonFrontMatter.MIN_REMEDY_STATEMENT,
                        number(SpecificationDocument.object(remedy.get("statement")).get("minLength"))),
                () -> assertEquals(LessonFrontMatter.MAX_REMEDY_STATEMENT,
                        number(SpecificationDocument.object(remedy.get("statement")).get("maxLength"))),
                () -> assertEquals(LessonFrontMatter.MIN_REMEDY_CHECK,
                        number(SpecificationDocument.object(remedy.get("check")).get("minLength"))),
                () -> assertEquals(LessonFrontMatter.MAX_REMEDY_CHECK,
                        number(SpecificationDocument.object(remedy.get("check")).get("maxLength"))),
                () -> assertEquals(LessonFrontMatter.MIN_RECURRENCE_COUNT,
                        number(member(properties, "recurrence_count").get("minimum"))),
                () -> assertEquals(LessonFrontMatter.MIN_REVISION,
                        number(member(properties, "revision").get("minimum"))),
                () -> assertEquals(LessonFrontMatter.MIN_SOURCES, number(member(properties, "sources").get("minItems"))),
                () -> assertEquals(LessonFrontMatter.MIN_REVISIONS,
                        number(member(properties, "revisions").get("minItems"))),
                () -> assertEquals(LessonFrontMatter.MIN_REVISION,
                        number(SpecificationDocument.object(revision.get("n")).get("minimum"))),
                () -> assertEquals(LessonFrontMatter.MIN_SOURCES,
                        number(SpecificationDocument.object(revision.get("sources")).get("minItems"))),
                () -> assertEquals(LessonFrontMatter.DIGEST_PATTERN, patternOf(
                        SpecificationDocument.object(member(definitions, "source").get("properties")), "digest")),
                () -> assertEquals(LessonFrontMatter.DIGEST_PATTERN, patternOf(revision, "body_sha256")),
                () -> assertEquals(LessonFrontMatter.PLAN_ID_PATTERN, patternOf(
                        SpecificationDocument.object(member(properties, "claim").get("properties")), "plan_id")),
                () -> assertEquals(1, number(SpecificationDocument.object(retired.get("by")).get("minLength"))),
                () -> assertEquals(1, number(SpecificationDocument.object(retired.get("reason")).get("minLength"))));
    }

    @Test
    @DisplayName("the schema declares no dependent-required pair after the cut")
    void noDependentRequiredPair() {
        assertNull(schema.get("dependentRequired"),
                "The golden_label/labelled_at pair left the schema with the amendment");
    }

    private static List<String> MEMBERS() {
        return names(properties.keySet());
    }

    private static Map<?, ?> member(Map<?, ?> owner, String name) {
        return SpecificationDocument.object(owner.get(name));
    }

    private static Map<?, ?> item(Map<?, ?> owner, String name) {
        return SpecificationDocument.object(member(owner, name).get("items"));
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

    private static String patternOf(Map<?, ?> owner, String name) {
        return String.valueOf(SpecificationDocument.object(owner.get(name)).get("pattern"));
    }

    private static <E> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    private static int number(Object value) {
        return ((Number) Objects.requireNonNull(value, "a schema number")).intValue();
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
