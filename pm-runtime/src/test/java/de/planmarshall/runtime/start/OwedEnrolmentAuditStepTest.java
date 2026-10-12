/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.runtime.start.EnrolmentFixture.SinkMode;
import de.planmarshall.runtime.start.RecoveryStep.Name;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Step 2 of the per-project recovery (PM-IMPL-7): the owed audit records of the project's enrolment writes are handed
 * over under its lock; a record the sink refuses again stays owed and is no failure, a bad intent fails the step. Its
 * background pass hands over the records of projects no longer enrolled.
 */
@EnableTestLogger
@DisplayName("Owed enrolment audit step")
class OwedEnrolmentAuditStepTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String OTHER = "other-project";
    private static final String GONE = "gone-project";
    private static final String NOT_JSON = "not json";

    @TempDir
    Path base;

    private EnrolmentFixture fixture;
    private OwedEnrolmentAuditStep step;

    @BeforeEach
    void create() throws IOException {
        fixture = new EnrolmentFixture(base);
        step = new OwedEnrolmentAuditStep(fixture.enrolments);
    }

    private static String handedOver(int count) {
        return count + " owed audit records handed over";
    }

    /** Enrols the project while the sink refuses, so its record stays owed. */
    private void enrolOwing(String projectId) {
        fixture.mode = SinkMode.REFUSING;
        fixture.enrol(projectId, false);
        fixture.mode = SinkMode.WORKING;
    }

    private boolean owes(String projectId) {
        return Files.exists(fixture.store.auditIntent(projectId));
    }

    @Test
    @DisplayName("with nothing owed nothing is handed over")
    void nothingOwed() {
        fixture.enrol(PROJECT, false);
        fixture.events.clear();

        var outcome = step.run(PROJECT);

        assertAll(
                () -> assertEquals(Name.OWED_ENROLMENT_AUDIT, step.name()),
                () -> assertEquals(new Outcome.Done(handedOver(0)), outcome),
                () -> assertTrue(fixture.events.isEmpty()));
    }

    @Test
    @DisplayName("an owed record is handed over once with its transaction id, and a second run hands over nothing")
    void handedOverOnce() {
        enrolOwing(PROJECT);

        var first = step.run(PROJECT);
        var second = step.run(PROJECT);

        assertAll(
                () -> assertEquals(new Outcome.Done(handedOver(1)), first),
                () -> assertEquals(new Outcome.Done(handedOver(0)), second),
                () -> assertEquals(1, fixture.events.size()),
                () -> assertEquals(AuditEvent.ENROLMENT_WRITTEN, fixture.events.getFirst().event()),
                () -> assertTrue(fixture.events.getFirst().details().containsKey(AuditEvent.DETAIL_TRANSACTION_ID)),
                () -> assertFalse(owes(PROJECT)));
    }

    @Test
    @DisplayName("a record the sink refuses again stays owed, and the step is still done")
    void refusedAgain() {
        enrolOwing(PROJECT);
        fixture.mode = SinkMode.REFUSING;

        var outcome = step.run(PROJECT);

        assertAll(
                () -> assertEquals(new Outcome.Done(handedOver(0)), outcome),
                () -> assertTrue(owes(PROJECT)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {NOT_JSON, "{\"format_version\":2,\"owed_events\":[]}"})
    @DisplayName("an unreadable intent or one of another version fails the step and is kept")
    void badIntent(String content) throws IOException {
        var intent = Files.writeString(fixture.store.auditIntent(PROJECT), content);

        var outcome = step.run(PROJECT);

        assertAll(
                () -> assertTrue(assertInstanceOf(Outcome.Failed.class, outcome).reason()
                        .contains(intent.toString()), outcome::toString),
                () -> assertEquals(content, Files.readString(intent)));
    }

    @Test
    @DisplayName("an intent that fails on I/O fails the step and is kept")
    void intentIoFailure() throws IOException {
        var intent = Files.createDirectory(fixture.store.auditIntent(PROJECT));

        var outcome = step.run(PROJECT);

        assertAll(
                () -> assertInstanceOf(Outcome.Failed.class, outcome),
                () -> assertTrue(Files.isDirectory(intent)));
    }

    @Test
    @DisplayName("a bad intent of another project does not matter to this project's run")
    void otherProjectIsolated() throws IOException {
        enrolOwing(PROJECT);
        Files.writeString(fixture.store.auditIntent(OTHER), NOT_JSON);

        assertEquals(new Outcome.Done(handedOver(1)), step.run(PROJECT));
    }

    @Nested
    @DisplayName("the background pass of the projects no longer enrolled")
    class UnenrolledPass {

        /** Leaves an owed enrolment_removed record of a project that is no longer enrolled. */
        private void unenrolOwing(String projectId) {
            fixture.enrol(projectId, false);
            fixture.mode = SinkMode.REFUSING;
            fixture.enrolments.unenrol(projectId);
            fixture.mode = SinkMode.WORKING;
        }

        @Test
        @DisplayName("hands over what an unenrolled project owes and leaves an enrolled project's intent alone")
        void handsOverUnenrolledOnly() {
            unenrolOwing(PROJECT);
            enrolOwing(OTHER);
            fixture.events.clear();

            step.unenrolledPass().run();

            assertAll(
                    () -> assertEquals(List.of(AuditEvent.ENROLMENT_REMOVED),
                            fixture.events.stream().map(AuditEvent::event).toList()),
                    () -> assertFalse(owes(PROJECT)),
                    () -> assertTrue(owes(OTHER)),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.ENROLMENT_AUDIT_REPLAY_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a bad intent of an unenrolled project is logged, nothing is thrown, the good ones are handed over")
        void badIntentLogged() throws IOException {
            unenrolOwing(PROJECT);
            var bad = Files.writeString(fixture.store.auditIntent(GONE), NOT_JSON);
            fixture.events.clear();
            var pass = step.unenrolledPass();

            pass.run();

            assertAll(
                    () -> assertFalse(owes(PROJECT)),
                    () -> assertEquals(1, fixture.events.size()),
                    () -> assertEquals(NOT_JSON, Files.readString(bad)),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_AUDIT_REPLAY_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a stop of the sink is not caught")
        void stopNotCaught() {
            unenrolOwing(PROJECT);
            fixture.mode = SinkMode.STOPPING;
            var pass = step.unenrolledPass();

            assertThrows(EnrolmentFixture.ProcessStopped.class, pass::run);
            assertTrue(owes(PROJECT));
        }
    }
}
