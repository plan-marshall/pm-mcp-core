/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.SchemaValidation;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.qualification.JudgeCheck.Label;
import de.planmarshall.core.store.SchemaParity;

/**
 * The two grading codecs keep parity with the schema files {@code qualification-record.schema.json} and
 * {@code judge-check-record.schema.json}, read from the class path: the members they write and require in schema order,
 * the closed enumerations, the format version 1, and the validity of every written document — the judge-check document
 * as written, the qualification document but for the pinned {@code thin} mark. Two drifts are pinned, each with its
 * owner: the file requires the {@code thin} mark on a topic result and declares an optional {@code judge_check} member
 * of the record, and a codec writes neither (owner: the work package {@code qualification-evidence}).
 */
@EnableGeneratorController
@DisplayName("Qualification schema parity")
class QualificationSchemaParityTest {

    private static final String QUALIFICATION = "de/planmarshall/core/role/qualification/qualification-record.schema.json";
    private static final String JUDGE_CHECK = "de/planmarshall/core/role/qualification/judge-check-record.schema.json";

    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String FORMS = "forms";
    private static final String TOPICS = "topics";
    private static final String ITEMS = "items";
    private static final String THIN = "thin";

    /** The compiled item count below which a topic result is {@code thin} ({@code qualification-record.schema.json}). */
    private static final int THIN_LIMIT = 20;

    private static Map<?, ?> schema(String resource) {
        return SchemaResource.load(resource);
    }

    private static Map<?, ?> properties(Map<?, ?> schema) {
        return SchemaResource.object(schema.get(PROPERTIES));
    }

    private static List<String> memberNames(Map<?, ?> schema) {
        return properties(schema).keySet().stream().map(String::valueOf).toList();
    }

    private static List<String> required(Map<?, ?> schema) {
        return SchemaResource.array(schema.get(REQUIRED)).stream().map(String::valueOf).toList();
    }

    private static Map<?, ?> member(Map<?, ?> schema, String name) {
        return SchemaResource.object(properties(schema).get(name));
    }

    private static Map<?, ?> definition(Map<?, ?> schema, String name) {
        return SchemaResource.object(SchemaResource.object(schema.get("$defs")).get(name));
    }

    private static Map<?, ?> itemOf(Map<?, ?> schema, String name) {
        return SchemaResource.object(member(schema, name).get(ITEMS));
    }

    private static Set<String> enumOf(Map<?, ?> schema, String name) {
        return SchemaResource.array(member(schema, name).get("enum")).stream().filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    private static <E extends Enum<E>> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    private static Schema validator(String resource) {
        return SchemaValidation.compile(SchemaResource.text(resource));
    }

    private static Set<String> violations(Schema validator, byte[] document) {
        return SchemaValidation.validate(validator, new String(document, UTF_8)).stream()
                .map(Error::getMessage).collect(Collectors.toUnmodifiableSet());
    }

    private static void assertValid(Schema validator, byte[] document) {
        assertEquals(Set.of(), violations(validator, document), () -> "violations of " + new String(document, UTF_8));
    }

    /**
     * @param document a written qualification record
     * @return the same document with the pinned {@code thin} mark supplied where it is absent, as the schema defines it
     */
    private static byte[] withTopicMark(byte[] document) {
        var parsed = mutableObject(SchemaParity.document(document));
        var forms = mutableObject(parsed.get(FORMS));
        forms.replaceAll((name, form) -> {
            var values = mutableObject(form);
            var topics = mutableObject(values.get(TOPICS));
            topics.replaceAll((topic, result) -> {
                var topicValues = mutableObject(result);
                topicValues.putIfAbsent(THIN, ((Number) topicValues.get(ITEMS)).intValue() < THIN_LIMIT);
                return topicValues;
            });
            values.put(TOPICS, topics);
            return values;
        });
        parsed.put(FORMS, forms);
        return SchemaParity.text(parsed).getBytes(UTF_8);
    }

    /**
     * @param value a parsed JSON object
     * @return a mutable copy of it, so a member can be supplied without casting
     */
    private static Map<String, Object> mutableObject(Object value) {
        var copy = new LinkedHashMap<String, Object>();
        SchemaResource.object(value).forEach((name, member) -> copy.put(String.valueOf(name), member));
        return copy;
    }

    /**
     * @return the topic result's required members, with the pinned {@code thin} mark
     */
    private static List<String> topicRequired() {
        var members = new ArrayList<>(QualificationRecordCodec.TOPIC_MEMBERS);
        members.add(THIN);
        return List.copyOf(members);
    }

    @Nested
    @DisplayName("role qualification record")
    class Qualification {

        private static QualificationRecord sample() {
            var agreement = BigDecimal.valueOf(Generators.integers(0, 100).next());
            return QualificationFixtures.qualification(Map.of(Topic.OWN, agreement));
        }

        @Test
        @DisplayName("the codec members are the schema's, in schema order, with two drifts pinned")
        void members() {
            var schema = schema(QUALIFICATION);

            assertAll(
                    () -> assertEquals(QualificationRecordCodec.MEMBERS, required(schema),
                            "the record members are the required ones in order"),
                    () -> assertEquals(QualificationRecordCodec.CONFIGURATION_MEMBERS,
                            memberNames(member(schema, "configuration")),
                            "the configuration members are in schema order"),
                    () -> assertEquals(QualificationRecordCodec.FORM_MEMBERS, memberNames(definition(schema, "formResult")),
                            "the form members are in schema order"),
                    () -> assertEquals(QualificationRecordCodec.ITEM_MEMBERS, memberNames(itemOf(schema, ITEMS)),
                            "the item members are in schema order"),
                    () -> assertEquals(List.of("file-only:" + THIN),
                            SchemaParity.memberDiffs(QualificationRecordCodec.TOPIC_MEMBERS,
                                    definition(schema, "topicResult")),
                            "the topic mark is pinned to qualification-evidence"),
                    () -> assertEquals(List.of("file-only:judge_check"),
                            SchemaParity.memberDiffs(QualificationRecordCodec.MEMBERS, schema),
                            "the judge check is pinned to qualification-evidence"));
        }

        @Test
        @DisplayName("every required member of the record, its form, its item and its topic is written")
        void requiredAreWritten() {
            var schema = schema(QUALIFICATION);

            assertAll(
                    () -> assertEquals(QualificationRecordCodec.CONFIGURATION_MEMBERS,
                            required(member(schema, "configuration")),
                            "the configuration's required members are the codec's"),
                    () -> assertEquals(QualificationRecordCodec.FORM_MEMBERS,
                            required(definition(schema, "formResult")),
                            "the form's required members are the codec's"),
                    () -> assertEquals(QualificationRecordCodec.ITEM_MEMBERS, required(itemOf(schema, ITEMS)),
                            "the item's required members are the codec's"),
                    () -> assertEquals(topicRequired(), required(definition(schema, "topicResult")),
                            "the topic's required members are the codec's and the pin"));
        }

        @Test
        @DisplayName("the closed enumerations of the record are the schema's")
        void enumerations() {
            var schema = schema(QUALIFICATION);
            var item = itemOf(schema, ITEMS);

            assertAll(
                    () -> assertEquals(wireNames(RoleClass.values(), RoleClass::wireName),
                            enumOf(member(schema, "configuration"), "class"),
                            "the role classes should equal the file's"),
                    () -> assertEquals(wireNames(RoleForm.values(), RoleForm::wireName), enumOf(item, "form"),
                            "the role forms should equal the file's"),
                    () -> assertEquals(wireNames(Checkout.values(), Checkout::wireName), enumOf(item, "checkout"),
                            "the checkouts should equal the file's"));
        }

        @Test
        @DisplayName("the format version is one and the document validates but for the pinned topic mark")
        void documents() {
            var validator = validator(QUALIFICATION);
            var written = QualificationRecordCodec.write(sample());

            assertAll(
                    () -> assertEquals(1, ((Number) member(schema(QUALIFICATION), "format_version").get("const"))
                            .intValue(), "the format version constant is one"),
                    () -> assertFalse(violations(validator, written).isEmpty(),
                            "the written record misses the pinned mark"),
                    () -> assertTrue(violations(validator, written).stream()
                                    .allMatch(message -> message.contains("'" + THIN + "'")),
                            "only the pinned topic mark is absent"),
                    () -> assertValid(validator, withTopicMark(written)));
        }

        @Test
        @DisplayName("the comparison reports a member only one side carries")
        void negativeControls() {
            var schema = schema(QUALIFICATION);
            var members = SchemaParity.members(QualificationRecordCodec.write(sample()));

            assertAll(
                    () -> assertEquals(List.of("file-only:judge_check", "file-only:synthetic_member"),
                            SchemaParity.withFileOnly(members, schema, "synthetic_member"),
                            "a file-only member should be reported"),
                    () -> assertEquals(List.of("file-only:judge_check", "record-only:role"),
                            SchemaParity.withRecordOnly(members, schema, "role"),
                            "a record-only member should be reported"));
        }
    }

    @Nested
    @DisplayName("judge check record")
    class JudgeCheckRecord {

        private static JudgeCheck.Record sample() {
            var score = BigDecimal.valueOf(Generators.integers(0, 100).next(), 2);
            return new JudgeCheck.Record(QualificationFixtures.DIGEST, QualificationFixtures.HARNESS,
                    QualificationFixtures.MODEL, QualificationFixtures.CORPUS_REVISION,
                    BigDecimal.valueOf(Generators.integers(0, 100).next()),
                    BigDecimal.valueOf(Generators.integers(0, 100).next()),
                    BigDecimal.valueOf(Generators.integers(0, 100).next()), 0,
                    Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next()),
                    List.of(new JudgeCheck.ItemScore("i" + Generators.letterStrings(3, 8).next(), Label.RIGHT,
                            Grade.scored(score), Grade.scored(score),
                            TokenTotal.measured(Generators.integers(1, 1_000).next()),
                            Generators.longs(0, 1_000).next())));
        }

        @Test
        @DisplayName("the codec members are the schema's, in schema order")
        void members() {
            var schema = schema(JUDGE_CHECK);

            assertAll(
                    () -> assertFalse(properties(schema).isEmpty(), "the schema declares its members"),
                    () -> assertEquals(List.of(), SchemaParity.memberDiffs(JudgeCheckCodec.MEMBERS, schema),
                            "the record members are the schema's"),
                    () -> assertEquals(JudgeCheckCodec.MEMBERS, required(schema),
                            "the record members are the required ones in order"),
                    () -> assertEquals(JudgeCheckCodec.ITEM_MEMBERS, memberNames(itemOf(schema, ITEMS)),
                            "the item members are in schema order"),
                    () -> assertEquals(JudgeCheckCodec.ITEM_MEMBERS, required(itemOf(schema, ITEMS)),
                            "the item's required members are the codec's"));
        }

        @Test
        @DisplayName("the closed label enumeration of the item is the schema's")
        void enumerations() {
            var item = itemOf(schema(JUDGE_CHECK), ITEMS);

            assertEquals(wireNames(Label.values(), Label::wireName), enumOf(item, "label"),
                    "the labels should equal the file's");
        }

        @Test
        @DisplayName("the format version is one and every written document is valid")
        void documents() {
            var validator = validator(JUDGE_CHECK);
            var written = JudgeCheckCodec.write(sample());

            assertAll(
                    () -> assertEquals(1, ((Number) member(schema(JUDGE_CHECK), "format_version").get("const"))
                            .intValue(), "the format version constant is one"),
                    () -> assertValid(validator, written));
        }
    }
}
