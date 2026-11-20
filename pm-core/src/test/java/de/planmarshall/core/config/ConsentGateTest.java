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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The consent-before-acting rule (PM-SEC-8, Store Schemas § Consent Before Acting): an action runs only through its
 * consent state answered {@code yes} or {@code yes_remember}, or where its kind is remembered; a {@code no} records
 * nothing; an act of the operator's own needs no consent.
 */
@EnableGeneratorController
@DisplayName("Consent gate")
class ConsentGateTest {

    private static final Instant NOW = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SHA = "%064x".formatted(Generators.longs().next());
    private static final String OTHER_SHA = "%064x".formatted(Generators.longs().next());
    private static final String PROJECT = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String OTHER_PROJECT = "q" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String SCRIPT = "bin/" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT)
            + ".sh";
    private static final String OTHER_SCRIPT = "bin/" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT)
            + ".sh";
    private static final String DEVICE = "dev-"
            + "%012x".formatted(Generators.longs(0L, 0xFFFF_FFFF_FFFFL).next());
    private static final String NOT_A_DEVICE = Generators.letterStrings(4, 10).next().toLowerCase(Locale.ROOT);

    private static ConsentGate gate(Consent... consents) {
        return new ConsentGate(new ConsentStore.Snapshot(List.of(consents), 0L), CLOCK);
    }

    private static ConsentGate empty() {
        return gate();
    }

    private static ConsentGate remembered(ConsentKind kind, String project, List<ConsentScript> scripts) {
        if (kind.projectScoped()) {
            return gate(kind.scriptScoped()
                    ? Consent.checkoutScripts(project, scripts, NOW, Consent.Channel.CLI, null)
                    : Consent.project(kind, project, NOW, Consent.Channel.CLI, null));
        }
        return gate(Consent.machine(kind, NOW, Consent.Channel.CLI, null));
    }

    @Test
    @DisplayName("yes runs the action and records nothing")
    void yes() {
        var decision = empty().answer(ConsentGate.Request.machine(ConsentKind.SCAN_HARNESSES),
                ConsentGate.Answer.YES, Consent.Channel.CLI, null);

        assertAll(
                () -> assertTrue(decision.runs(), "a yes answer runs the action"),
                () -> assertNull(decision.toRecord(), "a yes answer records nothing"));
    }

    @Test
    @DisplayName("no records nothing and leaves the scope in the consent state")
    void no() {
        var decision = empty().answer(ConsentGate.Request.machine(ConsentKind.CHECK_CREDENTIAL_STORE),
                ConsentGate.Answer.NO, Consent.Channel.CLI, null);

        assertAll(
                () -> assertFalse(decision.runs(), "a no answer does not run the action"),
                () -> assertNull(decision.toRecord(), "a no answer records nothing"));
    }

    @Test
    @DisplayName("yes_remember records the entry the store keeps")
    void yesRemember() {
        var decision = empty().answer(ConsentGate.Request.machine(ConsentKind.VERIFY_PROVIDERS),
                ConsentGate.Answer.YES_REMEMBER, Consent.Channel.CLI, null);

        var recorded = decision.toRecord();
        assertAll(
                () -> assertTrue(decision.runs(), "a remembered yes runs the action"),
                () -> assertEquals(ConsentKind.VERIFY_PROVIDERS, recorded.kind(), "the record keeps the asked kind"),
                () -> assertEquals(NOW, recorded.grantedAt(), "the record keeps the grant time"),
                () -> assertEquals(Consent.Channel.CLI, recorded.channel(), "the record keeps the cli channel"),
                () -> assertNull(recorded.projectId(), "a machine kind records no project"));
    }

    @Test
    @DisplayName("a remembered kind passes without a question, whatever the answer")
    void rememberedPasses() {
        var request = ConsentGate.Request.machine(ConsentKind.CHECK_CREDENTIAL_STORE);

        var decision = remembered(ConsentKind.CHECK_CREDENTIAL_STORE, "", List.of())
                .answer(request, ConsentGate.Answer.NO, Consent.Channel.CLI, null);

        assertAll(
                () -> assertTrue(decision.runs(), "a remembered kind runs without asking"),
                () -> assertNull(decision.toRecord(), "a remembered pass records nothing"));
    }

    @Test
    @DisplayName("a remembered project kind passes for its project only")
    void rememberedProjectOnly() {
        var gate = remembered(ConsentKind.INSPECT_CHECKOUT, PROJECT, List.of());

        assertAll(
                () -> assertTrue(
                        gate.answer(ConsentGate.Request.project(ConsentKind.INSPECT_CHECKOUT, PROJECT),
                                ConsentGate.Answer.NO, Consent.Channel.CLI, null).runs(),
                        "the remembered project kind runs here"),
                () -> assertFalse(
                        gate.answer(ConsentGate.Request.project(ConsentKind.INSPECT_CHECKOUT, OTHER_PROJECT),
                                ConsentGate.Answer.NO, Consent.Channel.CLI, null).runs(),
                        "another project does not run the kind"));
    }

    @Test
    @DisplayName("run_checkout_scripts passes only for the scripts and digests it covers")
    void rememberedScripts() {
        var granted = new ConsentScript(SCRIPT, SHA);
        var gate = remembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, PROJECT, List.of(granted));

        assertAll(
                () -> assertTrue(
                        gate.answer(ConsentGate.Request.checkoutScripts(PROJECT, List.of(granted)),
                                ConsentGate.Answer.NO, Consent.Channel.CLI, null).runs(),
                        "the remembered script digest runs"),
                () -> assertFalse(
                        gate.answer(ConsentGate.Request.checkoutScripts(PROJECT,
                                        List.of(new ConsentScript(SCRIPT, OTHER_SHA))),
                                ConsentGate.Answer.NO, Consent.Channel.CLI, null).runs(),
                        "a changed script digest asks again"));
    }

    @Test
    @DisplayName("yes_remember is refused for a kind that is never remembered")
    void neverRemembered() {
        var gate = empty();
        var verifyProfileRequest = ConsentGate.Request.machine(ConsentKind.VERIFY_HARNESS_PROFILE);
        var autoRunRequest = ConsentGate.Request.machine(ConsentKind.AUTO_RUN);
        String noDevice = null;
        var harnessProfile = assertThrows(IllegalArgumentException.class,
                () -> gate.answer(verifyProfileRequest, ConsentGate.Answer.YES_REMEMBER, Consent.Channel.CLI,
                        noDevice),
                "verify_harness_profile cannot be remembered");
        var autoRun = assertThrows(IllegalArgumentException.class,
                () -> gate.answer(autoRunRequest, ConsentGate.Answer.YES_REMEMBER, Consent.Channel.CLI, noDevice),
                "auto_run cannot be remembered");
        var harnessMessage = harnessProfile.getMessage();
        var autoRunMessage = autoRun.getMessage();

        assertAll(
                () -> assertTrue(harnessMessage.contains("verify_harness_profile"),
                        "the refusal names verify_harness_profile"),
                () -> assertTrue(autoRunMessage.contains("auto_run"), "the refusal names auto_run"));
    }

    @Test
    @DisplayName("a project kind names its project, and a machine kind names none")
    void scope() {
        var gate = empty();
        var inspect = ConsentGate.Request.machine(ConsentKind.INSPECT_CHECKOUT);
        var withProject = new ConsentGate.Request(ConsentKind.SCAN_HARNESSES, PROJECT, List.of());
        var withScripts = new ConsentGate.Request(ConsentKind.SCAN_HARNESSES, null,
                List.of(new ConsentScript(OTHER_SCRIPT, SHA)));
        String noProject = null;
        var machineKindWithProject = assertThrows(IllegalArgumentException.class,
                () -> gate.answer(inspect, ConsentGate.Answer.YES, Consent.Channel.CLI, noProject),
                "a project kind demands its project");
        var projectKindWithoutProject = assertThrows(IllegalArgumentException.class,
                () -> gate.answer(withProject, ConsentGate.Answer.YES, Consent.Channel.CLI, noProject),
                "a machine kind refuses a project");
        var scriptsOnAnotherKind = assertThrows(IllegalArgumentException.class,
                () -> gate.answer(withScripts, ConsentGate.Answer.YES, Consent.Channel.CLI, noProject),
                "scripts on another kind are refused");
        var machineMessage = machineKindWithProject.getMessage();
        var projectMessage = projectKindWithoutProject.getMessage();
        var scriptsMessage = scriptsOnAnotherKind.getMessage();

        assertAll(
                () -> assertTrue(machineMessage.contains("inspect_checkout"), "the refusal names inspect_checkout"),
                () -> assertTrue(projectMessage.contains("scan_harnesses"), "the refusal names scan_harnesses"),
                () -> assertTrue(scriptsMessage.contains("scripts"), "the refusal names the scripts"));
    }

    @Test
    @DisplayName("a paired device belongs to the web channel and its grammar")
    void device() {
        var gate = empty();
        var request = ConsentGate.Request.machine(ConsentKind.SCAN_PROVIDERS);
        String paired = DEVICE;
        String laptop = NOT_A_DEVICE;
        var remembered = gate.answer(request, ConsentGate.Answer.YES_REMEMBER, Consent.Channel.WEB, paired);
        var device = remembered.toRecord().deviceId();
        var deviceOnCli = assertThrows(IllegalArgumentException.class,
                () -> gate.answer(request, ConsentGate.Answer.YES_REMEMBER, Consent.Channel.CLI, paired),
                "a paired device is refused on the cli");
        var notADevice = assertThrows(IllegalArgumentException.class,
                () -> gate.answer(request, ConsentGate.Answer.YES_REMEMBER, Consent.Channel.WEB, laptop),
                "a malformed device is refused on the web");
        var deviceOnCliMessage = deviceOnCli.getMessage();
        var notADeviceMessage = notADevice.getMessage();

        assertAll(
                () -> assertEquals(paired, device, "the record keeps the paired device"),
                () -> assertTrue(deviceOnCliMessage.contains(DEVICE), "the refusal carries the cli device"),
                () -> assertTrue(notADeviceMessage.contains(NOT_A_DEVICE),
                        "the refusal carries the malformed device"));
    }

    @Test
    @DisplayName("every answer refuses a device on the cli channel and a malformed device before it decides")
    void deviceOnEveryAnswer() {
        var request = ConsentGate.Request.machine(ConsentKind.CHECK_CREDENTIAL_STORE);
        var rememberedGate = remembered(ConsentKind.CHECK_CREDENTIAL_STORE, "", List.of());
        var gates = List.of(empty(), rememberedGate);
        var answers = List.of(ConsentGate.Answer.YES, ConsentGate.Answer.NO,
                ConsentGate.Answer.YES_REMEMBER);

        assertAll(
                () -> gates.forEach(gate -> answers.forEach(answer -> {
                    assertThrows(IllegalArgumentException.class,
                            () -> gate.answer(request, answer, Consent.Channel.CLI, DEVICE),
                            "a device on the cli channel is refused for any answer");
                    assertThrows(IllegalArgumentException.class,
                            () -> gate.answer(request, answer, Consent.Channel.WEB, NOT_A_DEVICE),
                            "a malformed device is refused for any answer");
                })),
                () -> assertTrue(rememberedGate.answer(request, ConsentGate.Answer.NO, Consent.Channel.WEB, DEVICE)
                        .runs(), "a web answer with a paired device is a valid answer"));
    }

    @Test
    @DisplayName("an act of the operator's own needs no consent, and a typed credential is stored on that act")
    void operatorAct() {
        var decision = ConsentGate.operatorAct();

        assertAll(
                () -> assertTrue(decision.runs(), "an operator act runs without consent"),
                () -> assertNull(decision.toRecord(), "an operator act records nothing"));
    }

    @Test
    @DisplayName("the answer links are the closed set of the consent state")
    void answers() {
        assertAll(
                () -> assertEquals(ConsentGate.Answer.YES, ConsentGate.Answer.of("yes").orElseThrow(),
                        "the yes link maps to YES"),
                () -> assertEquals(ConsentGate.Answer.YES_REMEMBER,
                        ConsentGate.Answer.of("yes_remember").orElseThrow(),
                        "the yes_remember link maps to YES_REMEMBER"),
                () -> assertEquals(ConsentGate.Answer.NO, ConsentGate.Answer.of("no").orElseThrow(),
                        "the no link maps to NO"),
                () -> assertTrue(ConsentGate.Answer.of("maybe").isEmpty(), "an unknown answer link is empty"),
                () -> assertEquals("yes_remember", ConsentGate.Answer.YES_REMEMBER.wireName(),
                        "the wire name of YES_REMEMBER is kept"));
    }
}
