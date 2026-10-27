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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 */
@DisplayName("Endpoint enrolment")
class EndpointEnrolmentTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final List<HarnessEnrolment> written = new ArrayList<>();
    private final List<AuditEvent> audited = new ArrayList<>();

    private EndpointEnrolment enrolment() {
        return new EndpointEnrolment(new WeightsReader(), enrolment -> {
            written.add(enrolment);
            return enrolment;
        }, audited::add, InstantSource.fixed(NOW));
    }

    private static HarnessEnrolment recorded(Map<String, String> weights) {
        var map = new LinkedHashMap<String, HarnessEnrolment.Weights>();
        weights.forEach((model, identity) -> map.put(model, new HarnessEnrolment.Weights(identity, Instant.EPOCH)));
        return new HarnessEnrolment("local-runner", Path.of(EndpointFixtures.ENROLLED_EXECUTABLE), "a".repeat(64),
                "1.0.0", Instant.EPOCH, Instant.EPOCH,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(), map);
    }

    private static JsonNode listing() {
        return JsonDocument.parse("[{\"id\":\"mid\",\"digest\":\"new\"}]".getBytes(StandardCharsets.UTF_8), "models");
    }

    @Test
    @DisplayName("a changed identity is re-recorded and audited with the model and both identities")
    void changedIdentityRecordedAndAudited() {
        var refreshed = enrolment().refresh(EndpointFixtures.profile(), recorded(Map.of("mid", "old")), listing());

        assertAll(
                () -> assertEquals(List.of("mid"), refreshed.changedModels()),
                () -> assertTrue(refreshed.written()),
                () -> assertEquals(1, written.size()),
                () -> assertEquals("new", written.getFirst().weights().get("mid").weightsId()),
                () -> assertEquals(NOW, written.getFirst().weights().get("mid").readAt()),
                () -> assertEquals(1, audited.size()),
                () -> assertEquals(AuditEvent.HARNESS_UPDATED, audited.getFirst().event()),
                () -> assertEquals("mid", audited.getFirst().details().get(AuditEvent.DETAIL_MODEL)),
                () -> assertEquals("old", audited.getFirst().details().get(AuditEvent.DETAIL_OLD_WEIGHTS_ID)),
                () -> assertEquals("new", audited.getFirst().details().get(AuditEvent.DETAIL_NEW_WEIGHTS_ID)));
    }

    @Test
    @DisplayName("an unchanged identity writes nothing and audits nothing")
    void unchangedIdentityNotWritten() {
        var refreshed = enrolment().refresh(EndpointFixtures.profile(), recorded(Map.of("mid", "new")), listing());

        assertAll(
                () -> assertTrue(refreshed.changedModels().isEmpty()),
                () -> assertFalse(refreshed.written()),
                () -> assertTrue(written.isEmpty()),
                () -> assertTrue(audited.isEmpty()));
    }

    @Test
    @DisplayName("a first read of a model records it without an audit, because nothing changed")
    void firstReadRecordedWithoutAudit() {
        var refreshed = enrolment().refresh(EndpointFixtures.profile(), recorded(Map.of()), listing());

        assertAll(
                () -> assertTrue(refreshed.changedModels().isEmpty()),
                () -> assertTrue(refreshed.written()),
                () -> assertEquals("new", written.getFirst().weights().get("mid").weightsId()),
                () -> assertTrue(audited.isEmpty()));
    }

    @Test
    @DisplayName("an unreadable identity leaves the recorded one in place")
    void unreadIdentityKept() {
        var refreshed = enrolment().refresh(EndpointFixtures.profile(), recorded(Map.of("mid", "old")),
                JsonDocument.parse("[]".getBytes(StandardCharsets.UTF_8), "models"));

        assertAll(
                () -> assertFalse(refreshed.written()),
                () -> assertEquals("old", refreshed.weights().get("mid").weightsId()));
    }

    @Test
    @DisplayName("a profile without a weights probe is left as it is")
    void withoutProbe() {
        var base = EndpointFixtures.profile();
        var withoutProbe = new EndpointProfile(base.clientId(), base.executable(), base.identity(),
                base.updateEvidence(), base.endpoint(), base.modelTiers(), null);

        var refreshed = enrolment().refresh(withoutProbe, recorded(Map.of("mid", "old")), listing());

        assertAll(
                () -> assertFalse(refreshed.written()),
                () -> assertTrue(refreshed.detail().contains("no weights probe")));
    }
}
