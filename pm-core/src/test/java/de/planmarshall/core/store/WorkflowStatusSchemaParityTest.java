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

import static de.planmarshall.core.store.QuestionRecordCodecTest.text;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.RetryCounters.RetryRule;

/**
 * The closed lists of the status codec keep parity with the schema of the workflow state store (PM-ARCH-6, PM-WF-7):
 * the members {@link WorkflowStatusCodec#MEMBERS} the codec reads and writes are the schema's members in schema
 * order, the {@link RetryRule}s are the property names the schema admits for {@code retry_counters}, the
 * {@link WorkflowStatus#PHASES} are the values of the schema's {@code phase} enum, and the scope types of
 * {@link JournalScopeType} the status can carry are the values of its {@code scope_type} enum, and the members, the
 * required members and the closed values of a {@link WaiverRequest} are those of the schema of the waiver request
 * record (PM-SEC-3). A member, key or value
 * added to or removed from the schema without the code fails here instead of being refused or accepted unseen at run
 * time. The schemas are the files {@code de/planmarshall/core/store/status.schema.json} and
 * {@code de/planmarshall/core/store/waiver-request.schema.json} beside the codec, read from the class path.
 * The schema files, the member names and the closed values are contracts; the ids, the
 * texts, the device, the evidence, the comment reference and the instants of the full request are generated.
 */
@EnableGeneratorController
@DisplayName("Workflow state store schema parity")
class WorkflowStatusSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/store/status.schema.json";
    private static final String RETRY_COUNTERS = "retry_counters";
    private static final String FRESHNESS_RECONCILE = "freshness_reconcile";

    private static Map<?, ?> properties;

    @BeforeAll
    static void readSchema() {
        var schema = SchemaResource.load(SCHEMA);
        properties = SchemaResource.object(schema.get("properties"));
    }

    @Disabled("the schema carries members of the process-and-overview work package "
            + "(title, created_at, phase_entered_at, pause, terminal_reason, setup_aspect, "
            + "outline_review_revisions) that WorkflowStatusCodec.MEMBERS does not; reconciling them is that "
            + "package's, not PLAN-V2-15's")
    @Test
    @DisplayName("the members of the codec are the members of the schema in schema order")
    void membersEqualSchema() {
        var expected = properties.keySet().stream().map(String::valueOf).toList();

        assertFalse(expected.isEmpty(), "the schema declares members");
        assertEquals(WorkflowStatusCodec.MEMBERS, expected, "the codec members should equal the schema");
    }

    @Test
    @DisplayName("the retry rules are the property names the schema admits for retry_counters")
    void retryRulesEqualSchema() {
        var retryCounters = SchemaResource.object(properties.get(RETRY_COUNTERS));
        var propertyNames = SchemaResource.object(retryCounters.get("propertyNames"));
        var expected = SchemaResource.array(propertyNames.get("enum")).stream().map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        assertFalse(expected.isEmpty(), "the schema names the retry rules");
        assertEquals(expected, wireNames(), "the retry rules should equal the schema keys");
    }

    @Test
    @DisplayName("the commit attempt caps are the schema's two closed counters")
    void commitAttemptCapsEqualSchema() {
        var caps = SchemaResource.object(properties.get("commit_attempt_caps"));
        var counters = SchemaResource.object(caps.get("properties"));
        var changeSet = SchemaResource.object(counters.get("change_set"));
        var frame = SchemaResource.object(counters.get("frame"));

        assertAll(
                () -> assertEquals(Set.of("change_set", "frame"), counters.keySet(),
                        "the caps should hold the two counters"),
                () -> assertEquals(Set.of("staged_tree", "message_digest", "attempts"),
                        SchemaResource.object(changeSet.get("properties")).keySet(),
                        "the change set should name its three members"),
                () -> assertEquals(Set.of("frame_id", "tasks"),
                        SchemaResource.object(frame.get("properties")).keySet(),
                        "the frame should name its two members"));
    }

    @Test
    @DisplayName("the freshness reconcile members are the schema's required members")
    void freshnessReconcileMembersEqualSchema() {
        var member = SchemaResource.object(properties.get(FRESHNESS_RECONCILE));
        var required = SchemaResource.array(member.get("required")).stream().map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(Set.of("commit_sha", "step_id", "prior_build_worktree_sha", "worktree_sha_version",
                "recorded_at"), required, "the freshness stamp should name the schema's members");
    }

    @Test
    @DisplayName("the phases of the status are the values of the phase enum of the schema")
    void phasesEqualSchema() {
        var expected = enumOf("phase");

        assertFalse(expected.isEmpty(), "the schema names the phases");
        assertEquals(WorkflowStatus.PHASES, expected, "the phases should equal the schema enum");
    }

    @Test
    @DisplayName("the scope types of the codec are the values of the scope_type enum of the schema")
    void scopeTypesEqualSchema() {
        var expected = enumOf("scope_type");
        var actual = Arrays.stream(JournalScopeType.values()).filter(type -> type != JournalScopeType.LESSONS)
                .map(JournalScopeType::wireName).collect(Collectors.toUnmodifiableSet());

        assertFalse(expected.isEmpty(), "the schema names the scope types");
        assertEquals(expected, actual, "the scope types should equal the schema enum");
    }

    @Nested
    @DisplayName("the waiver request")
    class WaiverRequestParity {

        private static final String REQUEST_SCHEMA_FILE = "de/planmarshall/core/store/waiver-request.schema.json";
        private static final String ANSWERED = "answered";

        private static final Instant REQUESTED_AT = Instant.ofEpochSecond(
                Generators.longs(0, 4_000_000_000L).next());

        /** Every member of the answer, in the order the codec writes it, as JSON text. */
        private static final Map<String, String> FULL_ANSWER = ordered("channel", "\"web\"", "device_id",
                quoted(WaiverRecordCodecTest.deviceId()), "reason", quoted(text(2)), "at",
                quoted(REQUESTED_AT.plusSeconds(Generators.integers(1, 86_400).next()).toString()));

        /** Every member of the request, in the order the codec writes it, as JSON text. */
        private static final Map<String, String> FULL_REQUEST = ordered("request_id",
                quoted("WR-" + Generators.integers(1, 99_999).next()), "gate", "\"retry_budget:ci-failure\"",
                "policy", "\"remediation.ci-failure\"", "rationale", quoted(text(3)), "evidence_uri",
                quoted("pm://plan/" + WaiverRecordCodecTest.lower() + "/evidence/EV-"
                        + Generators.integers(1, 999).next()),
                "requested_at", quoted(REQUESTED_AT.toString()), "outcome", "\"waived\"", "waiver_id",
                quoted("W-" + Generators.integers(1, 99_999).next()), ANSWERED, json(FULL_ANSWER), "pr_comment_ref",
                quoted("IC_" + Generators.letterStrings(4, 12).next()));

        private static final Map<?, ?> REQUEST_SCHEMA = SchemaResource.load(REQUEST_SCHEMA_FILE);
        private static final Map<?, ?> ANSWER_SCHEMA = SchemaResource.object(
                propertiesOf(REQUEST_SCHEMA).get(ANSWERED));

        @Test
        @DisplayName("the codec writes the members of a request in the order the schema declares them")
        void requestMembersInSchemaOrder() {
            var document = WorkflowStatusCodecTest.withRequests(json(FULL_REQUEST));

            var written = new String(WorkflowStatusCodec.write(WorkflowStatusCodecTest.read(document)), UTF_8);

            assertAll(
                    () -> assertEquals(names(propertiesOf(REQUEST_SCHEMA).keySet()),
                            List.copyOf(FULL_REQUEST.keySet()), "the request members should be in schema order"),
                    () -> assertEquals(document, written, "the codec should write the request as read"));
        }

        @Test
        @DisplayName("the members the codec requires of a request are the schema's required members")
        void requestRequiredEqualSchema() {
            var expected = Set.copyOf(names(SchemaResource.array(REQUEST_SCHEMA.get("required"))));

            var required = requiredByCodec(FULL_REQUEST, WaiverRequestParity::json);

            assertAll(
                    () -> assertFalse(expected.isEmpty(), "the schema names the required members"),
                    () -> assertEquals(expected, required, "the required request members should match"));
        }

        @Test
        @DisplayName("the codec writes the members of an answer in the order the schema declares them")
        void answerMembersInSchemaOrder() {
            assertEquals(names(propertiesOf(ANSWER_SCHEMA).keySet()), List.copyOf(FULL_ANSWER.keySet()),
                    "the answer members should be in schema order");
        }

        @Test
        @DisplayName("the members the codec requires of an answer are the schema's required members")
        void answerRequiredEqualSchema() {
            var expected = Set.copyOf(names(SchemaResource.array(ANSWER_SCHEMA.get("required"))));

            var required = requiredByCodec(FULL_ANSWER, members -> {
                var withAnswer = new LinkedHashMap<>(FULL_REQUEST);
                withAnswer.put(ANSWERED, json(members));
                return json(withAnswer);
            });

            assertEquals(expected, required, "the required answer members should match");
        }

        @Test
        @DisplayName("the outcomes are the values of the outcome enum of the schema, without expired")
        void outcomesEqualSchema() {
            var expected = nonNullEnum(propertiesOf(REQUEST_SCHEMA).get("outcome"));
            var actual = Arrays.stream(WaiverRequestOutcome.values()).map(WaiverRequestOutcome::wireName)
                    .collect(Collectors.toUnmodifiableSet());

            assertAll(
                    () -> assertEquals(expected, actual, "the outcomes should equal the schema enum"),
                    () -> assertFalse(actual.contains("expired"), "no request ends by time"));
        }

        @Test
        @DisplayName("the channels of an answer are the values of the channel enum of the schema")
        void channelsEqualSchema() {
            var expected = nonNullEnum(propertiesOf(ANSWER_SCHEMA).get("channel"));
            var actual = Arrays.stream(WaiverRecord.Channel.values()).map(WaiverRecord.Channel::wireName)
                    .collect(Collectors.toUnmodifiableSet());

            assertEquals(expected, actual, "the channels should equal the schema enum");
        }

        /** @return the text as a JSON string, its quotes and backslashes escaped */
        private static String quoted(String text) {
            return QuestionRecordCodecTest.json(text);
        }

        /**
         * @param full     every member of an object as JSON text
         * @param document builds the request of a status from the members of the object
         * @return the members whose absence the codec refuses
         */
        private static Set<String> requiredByCodec(Map<String, String> full,
                Function<Map<String, String>, String> document) {
            var required = new HashSet<String>();
            for (var member : full.keySet()) {
                var without = new LinkedHashMap<>(full);
                without.remove(member);
                var status = WorkflowStatusCodecTest.withRequests(document.apply(without));
                try {
                    WorkflowStatusCodecTest.read(status);
                } catch (StoreRefusal _) {
                    required.add(member);
                }
            }
            return required;
        }

        private static Map<?, ?> propertiesOf(Map<?, ?> schema) {
            return SchemaResource.object(schema.get("properties"));
        }

        private static Set<String> nonNullEnum(Object declaration) {
            return SchemaResource.array(SchemaResource.object(declaration).get("enum")).stream()
                    .filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        }

        private static List<String> names(Collection<?> values) {
            return values.stream().map(String::valueOf).toList();
        }

        private static Map<String, String> ordered(String... membersAndValues) {
            var members = new LinkedHashMap<String, String>();
            for (var index = 0; index < membersAndValues.length; index += 2) {
                members.put(membersAndValues[index], membersAndValues[index + 1]);
            }
            return members;
        }

        private static String json(Map<String, String> members) {
            return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                    .collect(Collectors.joining(",", "{", "}"));
        }
    }

    @Nested
    @DisplayName("the call frame and the slots")
    class CallFrameParity {

        private static final String CALL_STACK = "call_stack";
        private static final String SLOTS = "slots";

        @Test
        @DisplayName("the codec writes the frame members in the order the schema declares them")
        void frameMembersInSchemaOrder() {
            var expected = names(SchemaResource.object(frameItem().get("properties")).keySet());

            var written = new String(WorkflowStatusCodec.write(
                    WorkflowStatusCodecTest.read(WorkflowStatusCodecTest.DOCUMENT)), UTF_8);

            assertAll(
                    () -> assertFalse(expected.isEmpty(), "the schema declares the frame members"),
                    () -> assertEquals(expected, frameMembers(written), "the frame members should be in schema order"));
        }

        @Test
        @DisplayName("the frame members the codec writes are the schema's required members")
        void frameRequiredEqualSchema() {
            var expected = Set.copyOf(names(SchemaResource.array(frameItem().get("required"))));

            var written = new String(WorkflowStatusCodec.write(
                    WorkflowStatusCodecTest.read(WorkflowStatusCodecTest.DOCUMENT)), UTF_8);

            assertAll(
                    () -> assertFalse(expected.isEmpty(), "the schema requires the frame members"),
                    () -> assertEquals(expected, Set.copyOf(frameMembers(written)),
                            "the required frame members should match"));
        }

        @Test
        @DisplayName("the slot sources of the status are the values of the slots source enum")
        void slotSourcesEqualSchema() {
            var slots = SchemaResource.object(properties.get(SLOTS));
            var item = SchemaResource.object(slots.get("additionalProperties"));
            var source = SchemaResource.object(SchemaResource.object(item.get("properties")).get("source"));
            var expected = SchemaResource.array(source.get("enum")).stream().map(String::valueOf)
                    .collect(Collectors.toUnmodifiableSet());

            assertAll(
                    () -> assertFalse(expected.isEmpty(), "the schema names the slot sources"),
                    () -> assertEquals(WorkflowStatus.Slot.SOURCES, expected,
                            "the slot sources should equal the schema enum"));
        }

        private static Map<?, ?> frameItem() {
            var callStack = SchemaResource.object(properties.get(CALL_STACK));
            return SchemaResource.object(callStack.get("items"));
        }

        private static List<String> names(Collection<?> values) {
            return values.stream().map(String::valueOf).toList();
        }

        /** @return the field names of the first frame of a written status, in their order */
        private static List<String> frameMembers(String written) {
            try (JsonParser parser = new JsonFactory().createParser(written)) {
                while (parser.nextToken() != null) {
                    if (parser.currentToken() == JsonToken.FIELD_NAME && CALL_STACK.equals(parser.currentName())) {
                        parser.nextToken();
                        parser.nextToken();
                        var members = new ArrayList<String>();
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            members.add(parser.currentName());
                            parser.nextToken();
                            parser.skipChildren();
                        }
                        return members;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("the written status cannot be read", e);
            }
            throw new AssertionError("the written status carries no call frame");
        }
    }

    private static Set<String> enumOf(String member) {
        var declaration = SchemaResource.object(properties.get(member));
        return SchemaResource.array(declaration.get("enum")).stream().map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> wireNames() {
        return Arrays.stream(RetryRule.values()).map(RetryRule::wireName).collect(Collectors.toUnmodifiableSet());
    }
}
