/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SpecificationDocument;
import de.planmarshall.core.queue.ActivityEntry.LinkRelation;
import de.planmarshall.core.queue.ActivityEntry.Source;
import de.planmarshall.core.queue.ActivityEntry.StopReason;
import de.planmarshall.core.queue.QueueTaskRecord.AttemptEnd;
import de.planmarshall.core.queue.QueueTaskRecord.ConsultationStatus;
import de.planmarshall.core.queue.QueueTaskRecord.ContinuationForm;
import de.planmarshall.core.queue.QueueTaskRecord.DeliveredTo;
import de.planmarshall.core.queue.QueueTaskRecord.FallbackReason;
import de.planmarshall.core.queue.QueueTaskRecord.LostReason;
import de.planmarshall.core.queue.QueueTaskRecord.Outcome;
import de.planmarshall.core.queue.QueueTaskRecord.ScopeType;
import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.queue.QueueTaskRecord.TaskClass;

/**
 * The codecs keep parity with Store Schemas § Queue Task Record and § Activity Journal: the fields they read and write
 * in schema order, the fields they require, the values of every enum, the format version 1, and every document they
 * write is valid against its schema. The schemas are read from the specification of this repository below
 * {@value SpecificationDocument#ROOT_PROPERTY}.
 */
@SuppressWarnings("java:S5778") // the schema table builds each value under test in its assertion, by design
@DisplayName("Task queue schema parity")
class TaskQueueSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/16-task-queue.adoc";

    private static Map<?, ?> schema(String anchor) {
        return SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, anchor)));
    }

    private static List<String> members(Map<?, ?> schema) {
        return SpecificationDocument.object(schema.get("properties")).keySet().stream().map(String::valueOf).toList();
    }

    private static List<String> required(Map<?, ?> schema) {
        return SpecificationDocument.array(schema.get("required")).stream().map(String::valueOf).toList();
    }

    private static Map<?, ?> member(Map<?, ?> schema, String name) {
        return SpecificationDocument.object(SpecificationDocument.object(schema.get("properties")).get(name));
    }

    private static Set<String> enumOf(Map<?, ?> schema, String name) {
        return SpecificationDocument.array(member(schema, name).get("enum")).stream().filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    /** The enum of an object member of an object member, such as {@code scope.type}. */
    private static Set<String> nestedEnum(Map<?, ?> schema, String container, String name) {
        return enumOf(member(schema, container), name);
    }

    /** The enum of a member of the items of an array member, such as {@code signals[].signal}. */
    private static Set<String> itemEnum(Map<?, ?> schema, String array, String name) {
        var items = SpecificationDocument.object(member(schema, array).get("items"));
        return enumOf(items, name);
    }

    private static <E extends Enum<E>> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    private static JsonSchema validator(String anchor) {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(SpecificationDocument.jsonBlockAfter(DOCUMENT, anchor));
    }

    private static void assertValid(JsonSchema validator, byte[] document) {
        var written = new String(document, UTF_8);

        assertEquals(Set.of(), validator.validate(written, InputFormat.JSON), () -> "violations of " + written);
    }

    @Nested
    @DisplayName("queue task record")
    class Records {

        private static final String ANCHOR = "[#queue-task-record]";

        @Test
        @DisplayName("the fields of the codec are the members of the schema in schema order, the required ones too")
        void members() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertFalse(TaskQueueSchemaParityTest.members(schema).isEmpty()),
                    () -> assertEquals(QueueTaskRecordCodec.FIELDS, TaskQueueSchemaParityTest.members(schema)),
                    () -> assertEquals(QueueTaskRecordCodec.REQUIRED, required(schema)));
        }

        @Test
        @DisplayName("the enums of the record are the closed sets of the schema")
        void enums() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertEquals(Set.of("work", "consultation"),
                            SpecificationDocument.array(member(schema, "kind").get("enum")).stream()
                                    .map(String::valueOf).collect(Collectors.toUnmodifiableSet())),
                    () -> assertEquals(wireNames(QueueTaskRecord.Kind.values(), QueueTaskRecord.Kind::wireName),
                            enumOf(schema, "kind")),
                    () -> assertEquals(wireNames(TaskClass.values(), TaskClass::wireName),
                            enumOf(schema, "class")),
                    () -> assertEquals(wireNames(Status.values(), Status::wireName), enumOf(schema, "status")),
                    () -> assertEquals(wireNames(Outcome.values(), Outcome::wireName), enumOf(schema, "outcome")),
                    () -> assertEquals(wireNames(LostReason.values(), LostReason::wireName),
                            enumOf(schema, "reason")),
                    () -> assertEquals(wireNames(ScopeType.values(), ScopeType::wireName),
                            nestedEnum(schema, "scope", "type")),
                    () -> assertEquals(wireNames(ContinuationForm.values(), ContinuationForm::wireName),
                            nestedEnum(schema, "continuation", "form")),
                    () -> assertEquals(wireNames(FallbackReason.values(), FallbackReason::wireName),
                            nestedEnum(schema, "continuation", "fallback_reason")),
                    () -> assertEquals(wireNames(AttemptEnd.values(), AttemptEnd::wireName),
                            itemEnum(schema, "attempts", "end")),
                    () -> assertEquals(wireNames(SignalName.values(), SignalName::wireName),
                            itemEnum(schema, "signals", "signal")),
                    () -> assertEquals(wireNames(SignalAction.values(), SignalAction::wireName),
                            itemEnum(schema, "signals", "action")),
                    () -> assertEquals(wireNames(ConsultationStatus.values(), ConsultationStatus::wireName),
                            itemEnum(schema, "consultations", "status")),
                    () -> assertEquals(wireNames(DeliveredTo.values(), DeliveredTo::wireName),
                            itemEnum(schema, "consultations", "delivered_to")));
        }

        @Test
        @DisplayName("the format version stays 1, and every written record is valid against the schema")
        void documents() {
            var validator = validator(ANCHOR);
            var asking = QueueFixtures.queued("qt-0000000000000001")
                    .consultations(List.of(QueueFixtures.answeredConsultation("qt-0000000000000002")))
                    .build();
            var records = List.of(
                    QueueFixtures.queued("qt-0000000000000003").build(),
                    QueueFixtures.closedSubmitted("qt-0000000000000004"),
                    QueueFixtures.lostAttemptsSpent("qt-0000000000000005"),
                    QueueFixtures.lostRoleUndefined("qt-0000000000000006"),
                    QueueFixtures.suspended("qt-0000000000000007"),
                    QueueFixtures.consultation("qt-0000000000000008", "qt-0000000000000001"),
                    asking);

            assertAll(() -> assertEquals(1, ((Number) member(schema(ANCHOR), "format_version").get("const"))
                    .intValue()), () -> records.forEach(task -> assertValid(validator,
                    QueueTaskRecordCodec.write(task))));
        }
    }

    @Nested
    @DisplayName("activity entry")
    class Activity {

        private static final String ANCHOR = "[#activity-journal]";

        @Test
        @DisplayName("the fields of the codec are the members of the schema in schema order, the required ones too")
        void members() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertEquals(ActivityEntryCodec.FIELDS, TaskQueueSchemaParityTest.members(schema)),
                    () -> assertEquals(ActivityEntryCodec.REQUIRED, required(schema)));
        }

        @Test
        @DisplayName("the enums of the entry are the closed sets of the schema")
        void enums() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertEquals(wireNames(Source.values(), Source::wireName), enumOf(schema, "source")),
                    () -> assertEquals(wireNames(ActivityEntry.Kind.values(), ActivityEntry.Kind::wireName),
                            enumOf(schema, "kind")),
                    () -> assertEquals(wireNames(StopReason.values(), StopReason::wireName),
                            enumOf(schema, "stop_reason")),
                    () -> assertEquals(wireNames(LinkRelation.values(), LinkRelation::wireName),
                            nestedEnum(schema, "link", "rel")),
                    () -> assertEquals(wireNames(SignalName.values(), SignalName::wireName),
                            nestedEnum(schema, "signal", "signal")),
                    () -> assertEquals(wireNames(SignalAction.values(), SignalAction::wireName),
                            nestedEnum(schema, "signal", "action")));
        }

        @Test
        @DisplayName("an agent_started signal entry is valid against the schema")
        void agentStarted() {
            var validator = validator(ANCHOR);
            var entry = ActivityEntry.builder()
                    .seq(1)
                    .attempt(1)
                    .source(Source.SERVER)
                    .kind(ActivityEntry.Kind.SIGNAL)
                    .signal(new ActivityEntry.Signal(SignalName.AGENT_STARTED, SignalAction.RESTART))
                    .at(QueueFixtures.AT)
                    .build();

            assertValid(validator, ActivityEntryCodec.write(entry));
        }
    }
}
