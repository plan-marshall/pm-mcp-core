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

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.time.Instant;
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
 * the closed enumerations, the format version 1, and the validity of every written document. The optional
 * {@code judge_check} member is written where the record carries it and absent otherwise; the {@code thin} mark of a
 * topic result is derived from the count the record stores.
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
    private static final String JUDGE_CHECK_MEMBER = "judge_check";

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

    /**
     * @param schema a schema document
     * @return the members the file declares beyond its required ones, in schema order
     */
    private static List<String> optionalMembers(Map<?, ?> schema) {
        var required = required(schema);
        return memberNames(schema).stream().filter(name -> !required.contains(name)).toList();
    }

    private static Set<String> enumOf(Map<?, ?> schema, String name) {        return SchemaResource.array(member(schema, name).get("enum")).stream().filter(Objects::nonNull)
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
     * @return the same document with the {@code thin} mark of the first topic removed
     */
    private static byte[] withoutMark(byte[] document) {
        var parsed = mutableObject(SchemaParity.document(document));
        var forms = mutableObject(parsed.get(FORMS));
        var formName = forms.keySet().iterator().next();
        var form = mutableObject(forms.get(formName));
        var topics = mutableObject(form.get(TOPICS));
        var topicName = topics.keySet().iterator().next();
        var topicValues = mutableObject(topics.get(topicName));
        topicValues.remove(THIN);
        topics.put(topicName, topicValues);
        form.put(TOPICS, topics);
        forms.put(formName, form);
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

    @Nested
    @DisplayName("role qualification record")
    class Qualification {

        private static QualificationRecord sample() {
            var agreement = BigDecimal.valueOf(Generators.integers(0, 100).next());
            return QualificationFixtures.qualification(Map.of(Topic.OWN, agreement));
        }

        @Test
        @DisplayName("the codec members are the schema's, in schema order, and the judge check is the optional member")
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
                    () -> assertEquals(QualificationRecordCodec.TOPIC_MEMBERS,
                            memberNames(definition(schema, "topicResult")),
                            "the topic members are the schema's, the thin mark included"),
                    () -> assertEquals(List.of(),
                            SchemaParity.memberDiffs(QualificationRecordCodec.TOPIC_MEMBERS,
                                    definition(schema, "topicResult")),
                            "the topic result declares no member the file lacks"),
                    () -> assertEquals(optionalMembers(schema).stream().map(name -> "file-only:" + name).toList(),
                            SchemaParity.memberDiffs(QualificationRecordCodec.MEMBERS, schema),
                            "the optional members are those beyond the required set"),
                    () -> assertEquals(QualificationRecordCodec.OPTIONAL_MEMBERS, optionalMembers(schema),
                            "the codec declares the optional members the file has"));
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
                    () -> assertEquals(QualificationRecordCodec.TOPIC_MEMBERS,
                            required(definition(schema, "topicResult")),
                            "the topic's required members are the codec's"));
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
                            "the checkouts should equal the file's"),
                    () -> assertEquals(wireNames(QualificationRecord.JudgeCheckResult.values(),
                            QualificationRecord.JudgeCheckResult::wireName),
                            enumOf(schema, JUDGE_CHECK_MEMBER),
                            "the judge check results should equal the file's"));
        }

        @Test
        @DisplayName("the format version is one, every written document is valid and a flipped mark is refused")
        void documents() {
            var validator = validator(QUALIFICATION);
            var written = QualificationRecordCodec.write(sample());
            var judged = QualificationRecordCodec.write(judged());

            assertAll(
                    () -> assertEquals(1, ((Number) member(schema(QUALIFICATION), "format_version").get("const"))
                            .intValue(), "the format version constant is one"),
                    () -> assertValid(validator, written),
                    () -> assertValid(validator, judged),
                    () -> assertFalse(violations(validator, withoutMark(written)).isEmpty(),
                            "a document without the thin mark is invalid"),
                    () -> assertEquals(QualificationRecord.TopicResult.THIN_LIMIT, THIN_LIMIT,
                            "the compiled thin limit is the schema's"));
        }

        /**
         * @return the sample record with a passed judge check, as a grading normally writes it
         */
        private static QualificationRecord judged() {
            var base = sample();
            return QualificationRecord.builder().role(base.role())
                    .qualificationDigest(base.qualificationDigest()).configuration(base.configuration())
                    .corpusRevision(base.corpusRevision()).missedSources(base.missedSources())
                    .forms(base.forms()).warmFreshGap(base.warmFreshGap()).ungraded(base.ungraded())
                    .judgeCheck(QualificationRecord.JudgeCheckResult.PASSED).gradedAt(base.gradedAt())
                    .items(base.items()).build();
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
