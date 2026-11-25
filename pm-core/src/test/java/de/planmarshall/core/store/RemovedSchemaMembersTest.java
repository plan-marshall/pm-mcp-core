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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * The members and enumeration values the merged specification dropped are absent from their schema files, and the
 * object that held each of them is still closed, so a stored document carrying the dropped member fails the schema
 * instead of being tolerated (PLAN-V2-188). Each negative control on a member or an enumeration value is matched with a
 * positive one beside it: a sibling member or value of the same enumeration stays, so the assertion cannot pass because
 * the reader lost the file. The deleted schema file has no such sibling; it is checked against the resource tree with a
 * surviving sibling resource as witness.
 * <p>
 * Six cases assert the corrected descriptions instead of a member: the {@code change_type} description of the status,
 * the {@code decision_point} example, the outline's {@code change_type} description, the {@code scope_class} text, and
 * the two descriptions of the message event. Each carries the statement it must keep beside the one it must lose.
 * <p>
 * Every schema file here is test input (R10): the members it declares are the contract this test pins.
 */
// Every datum in this class is a contract: the schema files and their member and value sets.
@DisplayName("The schema members the specification dropped are gone")
class RemovedSchemaMembersTest {

    private static final String STORE = "de/planmarshall/core/store/";
    private static final String FINDINGS = "de/planmarshall/core/findings/";
    private static final String DOMAIN = "de/planmarshall/core/domain/";

    @Nested
    @DisplayName("Status schema")
    class StatusSchema {

        @Test
        @DisplayName("the assessment identifier counter is gone and the counter object stays closed")
        void assessmentCounterIsGone() {
            var counters = object(properties(status()).get("counters"));

            assertAll("The assessment counter is gone",
                    () -> assertFalse(properties(counters).containsKey("assessment_id"),
                            "the assessment counter should be absent"),
                    () -> assertEquals(Boolean.FALSE, counters.get("additionalProperties"),
                            "the counter object should stay closed"),
                    () -> assertTrue(properties(counters).containsKey("finding_id"),
                            "the finding counter should stay declared"));
        }

        @Test
        @DisplayName("the request aspect and the stale title flag are gone and metadata stays closed")
        void metadataMembersAreGone() {
            var metadata = object(properties(status()).get("metadata"));

            assertAll("The dropped metadata members are gone",
                    () -> assertFalse(properties(metadata).containsKey("request_aspect"),
                            "the request aspect should be absent"),
                    () -> assertFalse(properties(metadata).containsKey("pr_title_stale"),
                            "the stale title flag should be absent"),
                    () -> assertEquals(Boolean.FALSE, metadata.get("additionalProperties"),
                            "the metadata object should stay closed"),
                    () -> assertTrue(properties(metadata).containsKey("pr_title"),
                            "the pull-request title should stay declared"));
        }

        @Test
        @DisplayName("the change type names the server's derivation at outline acceptance")
        void changeTypeNamesItsSingleWriter() {
            var changeType = object(properties(object(properties(status()).get("metadata"))).get("change_type"));
            var description = String.valueOf(changeType.get("description"));

            assertAll("The change type names its single writer",
                    () -> assertTrue(description.contains("outline acceptance"),
                            "the description should name the outline acceptance"),
                    () -> assertTrue(description.contains("Server-Derived change_type"),
                            "the description should name the derivation section"),
                    () -> assertFalse(description.contains("classify task"),
                            "the removed classify submit should be gone"));
        }
    }

    @Nested
    @DisplayName("Queue row schema")
    class QueueRowSchema {

        @Test
        @DisplayName("the re-grounding members are gone and the row stays closed")
        void regroundingMembersAreGone() {
            var row = schema(STORE + "queue-row.schema.json");
            var required = strings(row, "required");

            assertAll("The re-grounding members are gone",
                    () -> assertFalse(properties(row).containsKey("regrounded_at_sha"),
                            "the re-grounding commit should be absent"),
                    () -> assertFalse(properties(row).containsKey("reground_verdict"),
                            "the re-grounding verdict should be absent"),
                    () -> assertFalse(required.contains("regrounded_at_sha"),
                            "the re-grounding commit should not be required"),
                    () -> assertFalse(required.contains("reground_verdict"),
                            "the re-grounding verdict should not be required"),
                    () -> assertEquals(Boolean.FALSE, row.get("additionalProperties"),
                            "the queue row should stay closed"),
                    () -> assertTrue(required.contains("done_when"),
                            "the completion criterion should stay required"));
        }
    }

    @Nested
    @DisplayName("Event schemas")
    class EventSchemas {

        @Test
        @DisplayName("the epic overlap report is gone and the wait report stays")
        void epicOverlapReportIsGone() {
            var event = schema(STORE + "report-event.schema.json");
            var kinds = strings(object(properties(event).get("kind")), "enum");

            assertAll("The epic overlap report is gone",
                    () -> assertFalse(kinds.contains("epic.overlap_found"),
                            "the overlap report should be absent"),
                    () -> assertTrue(kinds.contains("epic.waits_overlap"),
                            "the wait report should stay declared"),
                    () -> assertEquals(Boolean.FALSE, event.get("additionalProperties"),
                            "the report event should stay closed"));
        }

        @Test
        @DisplayName("the overlap message kind and its two descriptions are gone")
        void overlapMessageKindIsGone() {
            var event = schema(STORE + "message-event.schema.json");
            var kind = object(properties(event).get("kind"));
            var payload = object(properties(event).get("payload"));
            var kindDescription = String.valueOf(kind.get("description"));
            var payloadDescription = String.valueOf(payload.get("description"));

            assertAll("The overlap message kind is gone",
                    () -> assertFalse(strings(kind, "enum").contains("overlap_found"),
                            "the overlap kind should be absent"),
                    () -> assertTrue(strings(kind, "enum").contains("advisory"),
                            "the advisory kind should stay declared"),
                    () -> assertFalse(kindDescription.contains("overlap_found"),
                            "the kind description should not name overlap"),
                    () -> assertTrue(kindDescription.contains("advisory"),
                            "the kind description should still name advisory"),
                    () -> assertFalse(payloadDescription.contains("other_plan_id"),
                            "the payload description should not name the sibling"),
                    () -> assertTrue(payloadDescription.contains("landing_notification"),
                            "the payload description should stay described"),
                    () -> assertEquals(Boolean.FALSE, event.get("additionalProperties"),
                            "the message event should stay closed"));
        }
    }

    @Nested
    @DisplayName("Domain descriptor schema")
    class DomainDescriptorSchema {

        @Test
        @DisplayName("the python edge resolver is gone and pyproject stays")
        void pythonEdgeResolverIsGone() {
            var descriptor = schema(DOMAIN + "domain-descriptor-v1.schema.json");
            var edgeResolvers = object(properties(descriptor).get("edge_resolvers"));
            var resolvers = strings(object(edgeResolvers.get("items")), "enum");

            assertAll("The python edge resolver is gone",
                    () -> assertFalse(resolvers.contains("python"),
                            "the python resolver should be absent"),
                    () -> assertTrue(resolvers.contains("pyproject"),
                            "the pyproject resolver should stay declared"));
        }
    }

    @Nested
    @DisplayName("Review bot registry schema")
    class ReviewBotRegistrySchema {

        @Test
        @DisplayName("the structural caps are gone and the registry stays closed")
        void structuralCapsAreGone() {
            var registry = schema(FINDINGS + "review-bot-registry.schema.json");

            assertAll("The structural caps are gone",
                    () -> assertFalse(properties(registry).containsKey("structural_caps"),
                            "the structural caps should be absent"),
                    () -> assertEquals(Boolean.FALSE, registry.get("additionalProperties"),
                            "the registry should stay closed"),
                    () -> assertTrue(properties(registry).containsKey("rate_limit_class"),
                            "the rate-limit class should stay declared"));
        }
    }

    @Nested
    @DisplayName("Assessment record schema")
    class AssessmentRecordSchema {

        @Test
        @DisplayName("the assessment record schema is no longer a resource of the module")
        void assessmentRecordSchemaIsGone() {
            var resources = Path.of("src/main/resources");

            assertAll("The assessment record schema is gone",
                    () -> assertTrue(Files.exists(resources.resolve(FINDINGS + "review-bot-registry.schema.json")),
                            "the findings resources should stay readable"),
                    () -> assertFalse(Files.exists(resources.resolve(FINDINGS + "assessment-record.schema.json")),
                            "the assessment record schema should be gone"));
        }

        @Test
        @DisplayName("the footprint description no longer names the assessment comparison")
        void footprintDescriptionDropsTheAssessment() {
            var qualityReport = schema(STORE + "quality-report.schema.json");
            var description = String.valueOf(object(properties(qualityReport).get("footprint"))
                    .get("description"));

            assertAll("The footprint drops the assessment",
                    () -> assertFalse(description.contains("assessment"),
                            "the assessment comparison should be absent"),
                    () -> assertTrue(description.contains("reconciliation"),
                            "the reconciliation should stay described"));
        }
    }

    @Nested
    @DisplayName("Corrected descriptions")
    class CorrectedDescriptions {

        @Test
        @DisplayName("the decision record no longer offers the removed classify point")
        void decisionPointsDropTheClassifyExample() {
            var decision = schema(STORE + "decision-record.schema.json");
            var description = String.valueOf(object(properties(decision).get("decision_point"))
                    .get("description"));

            assertAll("The removed decision point is gone",
                    () -> assertFalse(description.contains("outline.classify-change-type"),
                            "the removed classify point should be absent"),
                    () -> assertTrue(description.contains("remediation.choose"),
                            "a declared decision point should stay named"));
        }

        @Test
        @DisplayName("the outline record no longer names the scope disagreement refusal")
        void outlineChangeTypeDropsTheRefusal() {
            var outline = schema(STORE + "outline-record-v1.schema.json");
            var deliverables = object(properties(outline).get("deliverables"));
            var description = String.valueOf(object(properties(object(deliverables.get("items")))
                    .get("change_type")).get("description"));

            assertAll("The refusal is gone from the description",
                    () -> assertFalse(description.contains("Scope Disagreement Refusal"),
                            "the removed refusal should be absent"),
                    () -> assertTrue(description.contains("reconciled with the plan"),
                            "the reconciliation should stay described"));
        }

        @Test
        @DisplayName("the finding record reads the realized footprint, unplanned changes included")
        void scopeClassReadsTheRealizedFootprint() {
            var finding = schema(FINDINGS + "finding-record.schema.json");
            var description = String.valueOf(object(properties(finding).get("scope_class"))
                    .get("description"));

            assertAll("The scope class reads the realized footprint",
                    () -> assertTrue(description.contains("realized footprint"),
                            "the description should name the realized footprint"),
                    () -> assertTrue(description.contains("unplanned"),
                            "the description should include unplanned changes"));
        }
    }

    @Nested
    @DisplayName("Positive controls")
    class PositiveControls {

        @Test
        @DisplayName("the neighbouring members the specification kept are still declared")
        void neighboursAreStillDeclared() {
            var registerItem = schema(STORE + "register-item.schema.json");
            var trigger = object(properties(registerItem).get("trigger"));
            var epic = schema(STORE + "epic.schema.json");

            assertAll("The kept neighbours are still declared",
                    () -> assertTrue(strings(object(properties(trigger).get("kind")), "enum")
                                    .contains("base_advanced"),
                            "the base advance trigger should stay declared"),
                    () -> assertTrue(properties(epic).containsKey("overlap_max_files"),
                            "the overlap limit should stay declared"));
        }
    }

    private static Map<?, ?> status() {
        return schema(STORE + "status.schema.json");
    }

    private static Map<?, ?> schema(String resource) {
        return SchemaResource.load(resource);
    }

    private static Map<?, ?> properties(Map<?, ?> node) {
        return SchemaResource.object(node.get("properties"));
    }

    private static Map<?, ?> object(Object node) {
        return SchemaResource.object(node);
    }

    private static List<String> strings(Map<?, ?> node, String member) {
        return SchemaResource.array(node.get(member)).stream().map(String::valueOf).toList();
    }
}
