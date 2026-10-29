/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The consent-before-acting rule (PM-SEC-8, Store Schemas § Consent Before Acting): an action runs only through its
 * consent state answered {@code yes} or {@code yes_remember}, or where its kind is remembered; a {@code no} records
 * nothing; an act of the operator's own needs no consent.
 */
@DisplayName("Consent gate")
class ConsentGateTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SHA = "a".repeat(64);

    private static ConsentGate gate(Consent... consents) {
        return new ConsentGate(new ConsentStore.Snapshot(List.of(consents), 0L), CLOCK);
    }

    private static ConsentGate empty() {
        return gate();
    }

    private static ConsentGate remembered(ConsentKind kind, String project, List<ConsentScript> scripts) {
        if (kind.projectScoped()) {
            return gate(kind.scriptScoped()
                    ? Consent.checkoutScripts(project, scripts, NOW, Consent.Channel.CLI, Optional.empty())
                    : Consent.project(kind, project, NOW, Consent.Channel.CLI, Optional.empty()));
        }
        return gate(Consent.machine(kind, NOW, Consent.Channel.CLI, Optional.empty()));
    }

    @Test
    @DisplayName("yes runs the action and records nothing")
    void yes() {
        var decision = empty().answer(ConsentGate.Request.machine(ConsentKind.SCAN_HARNESSES),
                ConsentGate.Answer.YES, Consent.Channel.CLI, Optional.empty());

        assertAll(
                () -> assertTrue(decision.runs()),
                () -> assertTrue(decision.toRecord().isEmpty()));
    }

    @Test
    @DisplayName("no records nothing and leaves the scope in the consent state")
    void no() {
        var decision = empty().answer(ConsentGate.Request.machine(ConsentKind.CHECK_CREDENTIAL_STORE),
                ConsentGate.Answer.NO, Consent.Channel.CLI, Optional.empty());

        assertAll(
                () -> assertFalse(decision.runs()),
                () -> assertTrue(decision.toRecord().isEmpty()));
    }

    @Test
    @DisplayName("yes_remember records the entry the store keeps")
    void yesRemember() {
        var decision = empty().answer(ConsentGate.Request.machine(ConsentKind.VERIFY_PROVIDERS),
                ConsentGate.Answer.YES_REMEMBER, Consent.Channel.CLI, Optional.empty());

        var recorded = decision.toRecord().orElseThrow();
        assertAll(
                () -> assertTrue(decision.runs()),
                () -> assertEquals(ConsentKind.VERIFY_PROVIDERS, recorded.kind()),
                () -> assertEquals(NOW, recorded.grantedAt()),
                () -> assertEquals(Consent.Channel.CLI, recorded.channel()),
                () -> assertTrue(recorded.projectId().isEmpty()));
    }

    @Test
    @DisplayName("a remembered kind passes without a question, whatever the answer")
    void rememberedPasses() {
        var request = ConsentGate.Request.machine(ConsentKind.CHECK_CREDENTIAL_STORE);

        var decision = remembered(ConsentKind.CHECK_CREDENTIAL_STORE, "", List.of())
                .answer(request, ConsentGate.Answer.NO, Consent.Channel.CLI, Optional.empty());

        assertAll(
                () -> assertTrue(decision.runs()),
                () -> assertTrue(decision.toRecord().isEmpty()));
    }

    @Test
    @DisplayName("a remembered project kind passes for its project only")
    void rememberedProjectOnly() {
        var gate = remembered(ConsentKind.INSPECT_CHECKOUT, "pm-mcp-core", List.of());

        assertAll(
                () -> assertTrue(gate.answer(ConsentGate.Request.project(ConsentKind.INSPECT_CHECKOUT, "pm-mcp-core"),
                        ConsentGate.Answer.NO, Consent.Channel.CLI, Optional.empty()).runs()),
                () -> assertFalse(gate.answer(ConsentGate.Request.project(ConsentKind.INSPECT_CHECKOUT, "other"),
                        ConsentGate.Answer.NO, Consent.Channel.CLI, Optional.empty()).runs()));
    }

    @Test
    @DisplayName("run_checkout_scripts passes only for the scripts and digests it covers")
    void rememberedScripts() {
        var granted = new ConsentScript("bin/build.sh", SHA);
        var gate = remembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, "pm-mcp-core", List.of(granted));

        assertAll(
                () -> assertTrue(gate.answer(ConsentGate.Request.checkoutScripts("pm-mcp-core", List.of(granted)),
                        ConsentGate.Answer.NO, Consent.Channel.CLI, Optional.empty()).runs()),
                () -> assertFalse(gate.answer(ConsentGate.Request.checkoutScripts("pm-mcp-core",
                                List.of(new ConsentScript("bin/build.sh", "b".repeat(64)))),
                        ConsentGate.Answer.NO, Consent.Channel.CLI, Optional.empty()).runs()));
    }

    @Test
    @DisplayName("yes_remember is refused for a kind that is never remembered")
    void neverRemembered() {
        var verifyProfileRequest = ConsentGate.Request.machine(ConsentKind.VERIFY_HARNESS_PROFILE);
        var autoRunRequest = ConsentGate.Request.machine(ConsentKind.AUTO_RUN);
        var harnessProfile = assertThrows(IllegalArgumentException.class,
                () -> empty().answer(verifyProfileRequest, ConsentGate.Answer.YES_REMEMBER, Consent.Channel.CLI,
                        Optional.empty()));
        var autoRun = assertThrows(IllegalArgumentException.class,
                () -> empty().answer(autoRunRequest, ConsentGate.Answer.YES_REMEMBER, Consent.Channel.CLI,
                        Optional.empty()));
        var harnessMessage = harnessProfile.getMessage();
        var autoRunMessage = autoRun.getMessage();

        assertAll(
                () -> assertTrue(harnessMessage.contains("verify_harness_profile")),
                () -> assertTrue(autoRunMessage.contains("auto_run")));
    }

    @Test
    @DisplayName("a project kind names its project, and a machine kind names none")
    void scope() {
        var inspect = ConsentGate.Request.machine(ConsentKind.INSPECT_CHECKOUT);
        var withProject = new ConsentGate.Request(ConsentKind.SCAN_HARNESSES, Optional.of("pm-mcp-core"), List.of());
        var withScripts = new ConsentGate.Request(ConsentKind.SCAN_HARNESSES, Optional.empty(),
                List.of(new ConsentScript("bin/a.sh", SHA)));
        var machineKindWithProject = assertThrows(IllegalArgumentException.class,
                () -> empty().answer(inspect, ConsentGate.Answer.YES, Consent.Channel.CLI, Optional.empty()));
        var projectKindWithoutProject = assertThrows(IllegalArgumentException.class,
                () -> empty().answer(withProject, ConsentGate.Answer.YES, Consent.Channel.CLI, Optional.empty()));
        var scriptsOnAnotherKind = assertThrows(IllegalArgumentException.class,
                () -> empty().answer(withScripts, ConsentGate.Answer.YES, Consent.Channel.CLI, Optional.empty()));
        var machineMessage = machineKindWithProject.getMessage();
        var projectMessage = projectKindWithoutProject.getMessage();
        var scriptsMessage = scriptsOnAnotherKind.getMessage();

        assertAll(
                () -> assertTrue(machineMessage.contains("inspect_checkout")),
                () -> assertTrue(projectMessage.contains("scan_harnesses")),
                () -> assertTrue(scriptsMessage.contains("scripts")));
    }

    @Test
    @DisplayName("a paired device belongs to the web channel and its grammar")
    void device() {
        var request = ConsentGate.Request.machine(ConsentKind.SCAN_PROVIDERS);
        var remembered = empty().answer(request, ConsentGate.Answer.YES_REMEMBER, Consent.Channel.WEB,
                Optional.of("dev-0123456789ab"));
        var device = remembered.toRecord().orElseThrow().deviceId();
        var deviceOnCli = assertThrows(IllegalArgumentException.class, () -> empty().answer(request,
                ConsentGate.Answer.YES_REMEMBER, Consent.Channel.CLI, Optional.of("dev-0123456789ab")));
        var notADevice = assertThrows(IllegalArgumentException.class, () -> empty().answer(request,
                ConsentGate.Answer.YES_REMEMBER, Consent.Channel.WEB, Optional.of("laptop")));
        var deviceOnCliMessage = deviceOnCli.getMessage();
        var notADeviceMessage = notADevice.getMessage();

        assertAll(
                () -> assertEquals(Optional.of("dev-0123456789ab"), device),
                () -> assertTrue(deviceOnCliMessage.contains("dev-0123456789ab")),
                () -> assertTrue(notADeviceMessage.contains("laptop")));
    }

    @Test
    @DisplayName("an act of the operator's own needs no consent, and a typed credential is stored on that act")
    void operatorAct() {
        var decision = ConsentGate.operatorAct();

        assertAll(
                () -> assertTrue(decision.runs()),
                () -> assertTrue(decision.toRecord().isEmpty()));
    }

    @Test
    @DisplayName("the answer links are the closed set of the consent state")
    void answers() {
        assertAll(
                () -> assertEquals(ConsentGate.Answer.YES, ConsentGate.Answer.of("yes").orElseThrow()),
                () -> assertEquals(ConsentGate.Answer.YES_REMEMBER,
                        ConsentGate.Answer.of("yes_remember").orElseThrow()),
                () -> assertEquals(ConsentGate.Answer.NO, ConsentGate.Answer.of("no").orElseThrow()),
                () -> assertTrue(ConsentGate.Answer.of("maybe").isEmpty()),
                () -> assertEquals("yes_remember", ConsentGate.Answer.YES_REMEMBER.wireName()));
    }
}
