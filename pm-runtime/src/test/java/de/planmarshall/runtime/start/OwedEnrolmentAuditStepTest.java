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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.EnrolmentFixture.SinkMode;
import de.planmarshall.runtime.start.RecoveryStep.Name;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;

/**
 * Step 2 of the per-project recovery (PM-IMPL-7): the owed audit records of the project's enrolment writes are handed
 * over under its lock; a record the sink refuses again stays owed and is no failure, a bad intent fails the step. Its
 * background pass hands over the records of projects no longer enrolled.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Owed enrolment audit step")
class OwedEnrolmentAuditStepTest {

    /** Contract literal: a content that is no JSON, the smallest intent that cannot be read. */
    private static final String NOT_JSON = "not json";

    @TempDir
    Path base;

    // The prefixes order the projects as the pass visits them: the gone project, the other one, the project
    private final String gone = "a-" + name();
    private final String other = "b-" + name();
    private final String project = "c-" + name();
    private EnrolmentFixture fixture;
    private OwedEnrolmentAuditStep step;

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @BeforeEach
    void create() throws IOException {
        fixture = new EnrolmentFixture(base);
        step = new OwedEnrolmentAuditStep(fixture.enrolments);
    }

    /** @return the specified report of the step, a contract literal */
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
        fixture.enrol(project, false);
        fixture.events.clear();

        var outcome = step.run(project);

        assertAll(
                () -> assertEquals(Name.OWED_ENROLMENT_AUDIT, step.name(), "the step must carry its specified name"),
                () -> assertEquals(new Outcome.Done(handedOver(0)), outcome, "nothing owed must hand over nothing"),
                () -> assertTrue(fixture.events.isEmpty(), "no record must reach the sink"));
    }

    @Test
    @DisplayName("an owed record is handed over once with its transaction id, and a second run hands over nothing")
    void handedOverOnce() {
        enrolOwing(project);

        var first = step.run(project);
        var second = step.run(project);

        assertAll(
                () -> assertEquals(new Outcome.Done(handedOver(1)), first, "the first run must hand over one record"),
                () -> assertEquals(new Outcome.Done(handedOver(0)), second, "a second run must hand over nothing"),
                () -> assertEquals(1, fixture.events.size(), "the record must reach the sink once"),
                () -> assertEquals(AuditEvent.ENROLMENT_WRITTEN, fixture.events.getFirst().event(),
                        "the record must be the enrolment write"),
                () -> assertTrue(fixture.events.getFirst().details().containsKey(AuditEvent.DETAIL_TRANSACTION_ID),
                        "the record must carry its transaction id"),
                () -> assertFalse(owes(project), "the project must owe nothing any more"));
    }

    @Test
    @DisplayName("a record the sink refuses again stays owed, and the step is still done")
    void refusedAgain() {
        enrolOwing(project);
        fixture.mode = SinkMode.REFUSING;

        var outcome = step.run(project);

        assertAll(
                () -> assertEquals(new Outcome.Done(handedOver(0)), outcome, "a refused record is no failure"),
                () -> assertTrue(owes(project), "a refused record must stay owed"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {NOT_JSON, "{\"format_version\":2,\"owed_events\":[]}"})
    @DisplayName("an unreadable intent or one of another version fails the step and is kept "
            + "(the contents are the schema of the intent)")
    void badIntent(String content) throws Exception {
        var intent = Files.writeString(fixture.store.auditIntent(project), content);

        var outcome = step.run(project);

        assertAll(
                () -> assertTrue(assertInstanceOf(Outcome.Failed.class, outcome, "a bad intent must fail the step")
                        .reason().contains(intent.toString()), "the reason must name the intent"),
                () -> assertEquals(content, Files.readString(intent), "the bad intent must be kept"));
    }

    @Test
    @DisplayName("an intent that fails on I/O fails the step and is kept")
    void intentIoFailure() throws Exception {
        var intent = Files.createDirectory(fixture.store.auditIntent(project));

        var outcome = step.run(project);

        assertAll(
                () -> assertInstanceOf(Outcome.Failed.class, outcome, "an I/O failure must fail the step"),
                () -> assertTrue(Files.isDirectory(intent), "the failing intent must be kept"));
    }

    @Test
    @DisplayName("a bad intent of another project does not matter to this project's run")
    void otherProjectIsolated() throws Exception {
        enrolOwing(project);
        Files.writeString(fixture.store.auditIntent(other), NOT_JSON);

        assertEquals(new Outcome.Done(handedOver(1)), step.run(project),
                "a bad intent of another project must not matter");
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
            unenrolOwing(project);
            enrolOwing(other);
            fixture.events.clear();

            step.unenrolledPass().run();

            assertAll(
                    () -> assertEquals(List.of(AuditEvent.ENROLMENT_REMOVED),
                            fixture.events.stream().map(AuditEvent::event).toList(),
                            "only the removal record must be handed over"),
                    () -> assertFalse(owes(project), "the unenrolled project must owe nothing"),
                    () -> assertTrue(owes(other), "the enrolled project must keep its intent"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.ENROLMENT_AUDIT_REPLAY_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a bad intent of an unenrolled project is logged, nothing is thrown, the good ones are handed over")
        void badIntentLogged() throws Exception {
            unenrolOwing(project);
            var bad = Files.writeString(fixture.store.auditIntent(gone), NOT_JSON);
            fixture.events.clear();
            var pass = step.unenrolledPass();

            pass.run();

            assertAll(
                    () -> assertFalse(owes(project), "the good intent must be handed over"),
                    () -> assertEquals(1, fixture.events.size(), "one record must reach the sink"),
                    () -> assertEquals(NOT_JSON, Files.readString(bad), "the bad intent must be kept"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_AUDIT_REPLAY_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("settles an interrupted unenrol: delivers its record, removes the orphan sidecar, spares an enrolled one")
        void settlesAnInterruptedUnenrol() throws Exception {
            var unconfirmed = fixture.interruptUnenrol(project);
            enrolOwing(other);
            fixture.events.clear();

            step.unenrolledPass().run();

            assertAll(
                    () -> assertEquals(List.of(unconfirmed), fixture.events,
                            "the record of the unenrol must be delivered"),
                    () -> assertFalse(Files.exists(fixture.store.sidecar(project)),
                            "the orphan sidecar must be removed"),
                    () -> assertFalse(owes(project), "the unenrolled project must owe nothing"),
                    () -> assertTrue(owes(other), "the enrolled project must keep its intent"),
                    () -> assertTrue(Files.exists(fixture.store.file(other)),
                            "the enrolled project must keep its file"),
                    () -> assertTrue(Files.exists(fixture.store.sidecar(other)),
                            "the enrolled project must keep its sidecar"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.ENROLMENT_AUDIT_REPLAY_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("an orphan sidecar that cannot be removed is logged once, nothing is thrown, the others are settled")
        void undeletableOrphanSidecarLogged() throws Exception {
            var unconfirmed = fixture.interruptUnenrol(gone);
            var blocker = fixture.undeletableSidecar(gone);
            unenrolOwing(project);
            fixture.events.clear();
            var pass = step.unenrolledPass();

            pass.run();

            assertAll(
                    () -> assertEquals(2, fixture.events.size(), "both records must reach the sink"),
                    () -> assertEquals(unconfirmed, fixture.events.getFirst(),
                            "the unenrol record of the gone project is first"),
                    () -> assertEquals(AuditEvent.ENROLMENT_REMOVED, fixture.events.getLast().event(),
                            "the removal record of the project is last"),
                    () -> assertFalse(owes(project), "the unenrolled project must owe nothing"),
                    () -> assertTrue(Files.isDirectory(blocker), "the undeletable sidecar must stay"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.ENROLMENT_AUDIT_REPLAY_FAILED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a stop of the sink is not caught")
        void stopNotCaught() {
            unenrolOwing(project);
            fixture.mode = SinkMode.STOPPING;
            var pass = step.unenrolledPass();

            assertThrows(EnrolmentFixture.ProcessStopped.class, pass::run, "a stop of the sink must not be caught");
            assertTrue(owes(project), "the record must stay owed after a stop");
        }
    }
}
