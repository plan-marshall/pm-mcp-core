/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.runtime.launch.HarnessEnrolment;

/**
 * The weights identity in the harness enrolment (PM-WORK-6, Client Profiles § Weights Identity): a model whose identity
 * changed is re-recorded and the change is audited {@code harness_updated} naming the model and both identities, and an
 * identity that cannot be read leaves the enrolment as it is.
 * <p>
 * The two weights identities are generated; the model tier {@code mid}, the executable's own digest and the audit detail
 * keys are the contracts.
 */
@EnableGeneratorController
@DisplayName("Endpoint enrolment")
class EndpointEnrolmentTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final List<HarnessEnrolment> written = new ArrayList<>();
    private final List<AuditEvent> audited = new ArrayList<>();

    private String oldWeights;

    private String newWeights;

    @BeforeEach
    void draw() {
        oldWeights = Generators.strings("abcdef0123456789", 6, 12).next();
        newWeights = oldWeights + Generators.letterStrings(1, 4).next();
    }

    private EndpointEnrolment enrolment() {
        return new EndpointEnrolment(new WeightsReader(), enrolment -> {
            written.add(enrolment);
            return enrolment;
        }, audited::add, InstantSource.fixed(NOW));
    }

    private static HarnessEnrolment recorded(Map<String, String> weights) {
        var map = new LinkedHashMap<String, HarnessEnrolment.Weights>();
        weights.forEach((model, identity) -> map.put(model, new HarnessEnrolment.Weights(identity, Instant.EPOCH)));
        return new HarnessEnrolment("local-runner", Path.of(EndpointFixtures.executablePath()), "a".repeat(64),
                "1.0.0", Instant.EPOCH, Instant.EPOCH,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(), map);
    }

    private JsonNode listing() {
        return JsonDocument.parse(("[{\"id\":\"mid\",\"digest\":\"" + newWeights + "\"}]")
                .getBytes(StandardCharsets.UTF_8), "models");
    }

    @Test
    @DisplayName("a changed identity is re-recorded and audited with the model and both identities")
    void changedIdentityRecordedAndAudited() {
        var refreshed = enrolment().refresh(EndpointFixtures.profile(), recorded(Map.of("mid", oldWeights)), listing());

        assertAll(
                () -> assertEquals(List.of("mid"), refreshed.changedModels(),
                        "the changed model is reported"),
                () -> assertTrue(refreshed.written(), "the enrolment is written again"),
                () -> assertEquals(1, written.size(), "exactly one enrolment is written"),
                () -> assertEquals(newWeights, written.getFirst().weights().get("mid").weightsId(),
                        "the write carries the new weights identity"),
                () -> assertEquals(NOW, written.getFirst().weights().get("mid").readAt(),
                        "the write records the read time"),
                () -> assertEquals(1, audited.size(), "exactly one audit event is recorded"),
                () -> assertEquals(AuditEvent.HARNESS_UPDATED, audited.getFirst().event(),
                        "the audit event is harness_updated"),
                () -> assertEquals("mid", audited.getFirst().details().get(AuditEvent.DETAIL_MODEL),
                        "the audit names the model"),
                () -> assertEquals(oldWeights, audited.getFirst().details().get(AuditEvent.DETAIL_OLD_WEIGHTS_ID),
                        "the audit carries the old weights identity"),
                () -> assertEquals(newWeights, audited.getFirst().details().get(AuditEvent.DETAIL_NEW_WEIGHTS_ID),
                        "the audit carries the new weights identity"));
    }

    @Test
    @DisplayName("an unchanged identity writes nothing and audits nothing")
    void unchangedIdentityNotWritten() {
        var refreshed = enrolment().refresh(EndpointFixtures.profile(), recorded(Map.of("mid", newWeights)), listing());

        assertAll(
                () -> assertTrue(refreshed.changedModels().isEmpty(), "an unchanged identity reports no change"),
                () -> assertFalse(refreshed.written(), "an unchanged identity writes nothing"),
                () -> assertTrue(written.isEmpty(), "no enrolment is written"),
                () -> assertTrue(audited.isEmpty(), "no audit event is recorded"));
    }

    @Test
    @DisplayName("a first read of a model records it without an audit, because nothing changed")
    void firstReadRecordedWithoutAudit() {
        var refreshed = enrolment().refresh(EndpointFixtures.profile(), recorded(Map.of()), listing());

        assertAll(
                () -> assertTrue(refreshed.changedModels().isEmpty(), "a first read reports no change"),
                () -> assertTrue(refreshed.written(), "the first read writes the enrolment"),
                () -> assertEquals(newWeights, written.getFirst().weights().get("mid").weightsId(),
                        "the first read records the weights identity"),
                () -> assertTrue(audited.isEmpty(), "a first read audits nothing"));
    }

    @Test
    @DisplayName("an unreadable identity leaves the recorded one in place")
    void unreadIdentityKept() {
        var refreshed = enrolment().refresh(EndpointFixtures.profile(), recorded(Map.of("mid", oldWeights)),
                JsonDocument.parse("[]".getBytes(StandardCharsets.UTF_8), "models"));

        assertAll(
                () -> assertFalse(refreshed.written(), "an unread identity writes nothing"),
                () -> assertEquals(oldWeights, refreshed.weights().get("mid").weightsId(),
                        "the recorded identity is kept"));
    }

    @Test
    @DisplayName("a profile without a weights probe is left as it is")
    void withoutProbe() {
        var base = EndpointFixtures.profile();
        var withoutProbe = new EndpointProfile(base.clientId(), base.executable(), base.identity(),
                base.updateEvidence(), base.endpoint(), base.modelTiers(), null);

        var refreshed = enrolment().refresh(withoutProbe, recorded(Map.of("mid", oldWeights)), listing());

        assertAll(
                () -> assertFalse(refreshed.written(), "a profile without a probe writes nothing"),
                () -> assertTrue(refreshed.detail().contains("no weights probe"),
                        "the detail says no weights probe is set"));
    }
}
