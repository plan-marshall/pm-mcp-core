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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

import de.planmarshall.core.SpecificationDocument;
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
 * time. The schema is read from the specification of this repository below {@value SpecificationDocument#ROOT_PROPERTY}.
 */
@DisplayName("Workflow state store schema parity")
class WorkflowStatusSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/02-workflow-state-store.adoc";
    private static final String HEADING = "== Workflow State Store";
    private static final String RETRY_COUNTERS = "retry_counters";

    private static Map<?, ?> properties;

    @BeforeAll
    static void readSchema() {
        var schema = SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, HEADING)));
        properties = SpecificationDocument.object(schema.get("properties"));
    }

    @Disabled("the specification added members of the status store; "
            + "re-enabled by the work package process-and-overview")
    @Test
    @DisplayName("the members of the codec are the members of the schema in schema order")
    void membersEqualSchema() {
        var expected = properties.keySet().stream().map(String::valueOf).toList();

        assertFalse(expected.isEmpty(), "the schema declares members");
        assertEquals(WorkflowStatusCodec.MEMBERS, expected);
    }

    @Test
    @DisplayName("the retry rules are the property names the schema admits for retry_counters")
    void retryRulesEqualSchema() {
        var retryCounters = SpecificationDocument.object(properties.get(RETRY_COUNTERS));
        var propertyNames = SpecificationDocument.object(retryCounters.get("propertyNames"));
        var expected = SpecificationDocument.array(propertyNames.get("enum")).stream().map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        assertFalse(expected.isEmpty(), "the schema names the retry rules");
        assertEquals(expected, wireNames());
    }

    @Test
    @DisplayName("the phases of the status are the values of the phase enum of the schema")
    void phasesEqualSchema() {
        var expected = enumOf("phase");

        assertFalse(expected.isEmpty(), "the schema names the phases");
        assertEquals(WorkflowStatus.PHASES, expected);
    }

    @Test
    @DisplayName("the scope types of the codec are the values of the scope_type enum of the schema")
    void scopeTypesEqualSchema() {
        var expected = enumOf("scope_type");
        var actual = Arrays.stream(JournalScopeType.values()).filter(type -> type != JournalScopeType.LESSONS)
                .map(JournalScopeType::wireName).collect(Collectors.toUnmodifiableSet());

        assertFalse(expected.isEmpty(), "the schema names the scope types");
        assertEquals(expected, actual);
    }

    @Nested
    @DisplayName("the waiver request")
    class WaiverRequestParity {

        private static final String REQUEST_DOCUMENT = "store-schemas/14-plan-instance-waivers-questions.adoc";
        private static final String REQUEST_HEADING = "=== Waiver Request Record";
        private static final String ANSWERED = "answered";

        /** Every member of the answer, in the order the codec writes it, as JSON text. */
        private static final Map<String, String> FULL_ANSWER = ordered("channel", "\"web\"", "device_id",
                "\"dev-0123456789ab\"", "reason", "\"accepted\"", "at", "\"2026-10-12T09:40:00Z\"");

        /** Every member of the request, in the order the codec writes it, as JSON text. */
        private static final Map<String, String> FULL_REQUEST = ordered("request_id", "\"WR-1\"", "gate",
                "\"retry_budget:ci-failure\"", "policy", "\"remediation.ci-failure\"", "rationale", "\"flaky runner\"",
                "evidence_uri", "\"pm://plan/workflow-engine/evidence/EV-3\"", "requested_at",
                "\"2026-10-12T09:30:00Z\"", "outcome", "\"waived\"", "waiver_id", "\"W-2\"", ANSWERED,
                json(FULL_ANSWER), "pr_comment_ref", "\"IC_kwDO12\"");

        private static final Map<?, ?> REQUEST_SCHEMA = SpecificationDocument.object(SpecificationDocument.parse(
                SpecificationDocument.jsonBlockAfter(REQUEST_DOCUMENT, REQUEST_HEADING)));
        private static final Map<?, ?> ANSWER_SCHEMA = SpecificationDocument.object(
                propertiesOf(REQUEST_SCHEMA).get(ANSWERED));

        @Test
        @DisplayName("the codec writes the members of a request in the order the schema declares them")
        void requestMembersInSchemaOrder() {
            var document = WorkflowStatusCodecTest.withRequests(json(FULL_REQUEST));

            var written = new String(WorkflowStatusCodec.write(WorkflowStatusCodecTest.read(document)), UTF_8);

            assertAll(
                    () -> assertEquals(names(propertiesOf(REQUEST_SCHEMA).keySet()),
                            List.copyOf(FULL_REQUEST.keySet())),
                    () -> assertEquals(document, written));
        }

        @Test
        @DisplayName("the members the codec requires of a request are the schema's required members")
        void requestRequiredEqualSchema() {
            var expected = Set.copyOf(names(SpecificationDocument.array(REQUEST_SCHEMA.get("required"))));

            var required = requiredByCodec(FULL_REQUEST, WaiverRequestParity::json);

            assertAll(
                    () -> assertFalse(expected.isEmpty(), "the schema names the required members"),
                    () -> assertEquals(expected, required));
        }

        @Test
        @DisplayName("the codec writes the members of an answer in the order the schema declares them")
        void answerMembersInSchemaOrder() {
            assertEquals(names(propertiesOf(ANSWER_SCHEMA).keySet()), List.copyOf(FULL_ANSWER.keySet()));
        }

        @Test
        @DisplayName("the members the codec requires of an answer are the schema's required members")
        void answerRequiredEqualSchema() {
            var expected = Set.copyOf(names(SpecificationDocument.array(ANSWER_SCHEMA.get("required"))));

            var required = requiredByCodec(FULL_ANSWER, members -> {
                var withAnswer = new LinkedHashMap<>(FULL_REQUEST);
                withAnswer.put(ANSWERED, json(members));
                return json(withAnswer);
            });

            assertEquals(expected, required);
        }

        @Test
        @DisplayName("the outcomes are the values of the outcome enum of the schema, without expired")
        void outcomesEqualSchema() {
            var expected = nonNullEnum(propertiesOf(REQUEST_SCHEMA).get("outcome"));
            var actual = Arrays.stream(WaiverRequestOutcome.values()).map(WaiverRequestOutcome::wireName)
                    .collect(Collectors.toUnmodifiableSet());

            assertAll(
                    () -> assertEquals(expected, actual),
                    () -> assertFalse(actual.contains("expired"), "no request ends by time"));
        }

        @Test
        @DisplayName("the channels of an answer are the values of the channel enum of the schema")
        void channelsEqualSchema() {
            var expected = nonNullEnum(propertiesOf(ANSWER_SCHEMA).get("channel"));
            var actual = Arrays.stream(WaiverRecord.Channel.values()).map(WaiverRecord.Channel::wireName)
                    .collect(Collectors.toUnmodifiableSet());

            assertEquals(expected, actual);
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
            return SpecificationDocument.object(schema.get("properties"));
        }

        private static Set<String> nonNullEnum(Object declaration) {
            return SpecificationDocument.array(SpecificationDocument.object(declaration).get("enum")).stream()
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

    private static Set<String> enumOf(String member) {
        var declaration = SpecificationDocument.object(properties.get(member));
        return SpecificationDocument.array(declaration.get("enum")).stream().map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> wireNames() {
        return Arrays.stream(RetryRule.values()).map(RetryRule::wireName).collect(Collectors.toUnmodifiableSet());
    }
}
