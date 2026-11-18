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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * A {@code yes_remember} of a project's onboarding, held before the project had its id (Store Schemas § Consent Before
 * Acting): only a remembered project kind is held, the {@code run_checkout_scripts} hold lists its scripts, and the
 * hold is materialized under the confirmed {@code project_id} at {@code project.enrol}.
 */
@EnableGeneratorController
@DisplayName("Consent held before the project's id")
class PendingConsentTest {

    private static final Instant GRANTED = Instant.parse("2026-11-10T10:00:00Z");

    private static final String PROJECT = "my-project";

    @Test
    @DisplayName("Should write the remembered script consent under the confirmed project id")
    void resolvesTheScriptConsentUnderTheConfirmedId() {
        var script = script();

        var consent = PendingConsent.builder()
                .kind(ConsentKind.RUN_CHECKOUT_SCRIPTS)
                .scripts(List.of(script))
                .grantedAt(GRANTED)
                .channel(Consent.Channel.CLI)
                .build()
                .resolvedFor(PROJECT);

        assertAll(
                () -> assertEquals(ConsentKind.RUN_CHECKOUT_SCRIPTS, consent.kind(), "The held kind is written"),
                () -> assertEquals(PROJECT, consent.projectId(), "The confirmed project id is written"),
                () -> assertEquals(List.of(script), consent.scripts(), "The covered scripts are written"),
                () -> assertEquals(GRANTED, consent.grantedAt(), "The grant instant is kept"),
                () -> assertEquals(Consent.Channel.CLI, consent.channel(), "The answering channel is kept"));
    }

    @Test
    @DisplayName("Should write the remembered checkout inspection under the confirmed project id")
    void resolvesTheInspectionConsentUnderTheConfirmedId() {
        var consent = PendingConsent.builder()
                .kind(ConsentKind.INSPECT_CHECKOUT)
                .scripts(List.of())
                .grantedAt(GRANTED)
                .channel(Consent.Channel.WEB)
                .deviceId("dev-0123456789ab")
                .build()
                .resolvedFor(PROJECT);

        assertAll(
                () -> assertEquals(ConsentKind.INSPECT_CHECKOUT, consent.kind(), "The held kind is written"),
                () -> assertEquals(List.of(), consent.scripts(), "An inspection consent covers no scripts"),
                () -> assertEquals("dev-0123456789ab", consent.deviceId(), "The paired device is kept"));
    }

    @Test
    @DisplayName("Should refuse a machine kind, which is never held before an enrolment")
    void refusesAMachineKind() {
        var builder = PendingConsent.builder()
                .kind(ConsentKind.SCAN_HARNESSES)
                .scripts(List.of())
                .grantedAt(GRANTED)
                .channel(Consent.Channel.CLI);

        assertThrows(IllegalArgumentException.class, builder::build,
                "A machine kind cannot be held before a project's id");
    }

    @Test
    @DisplayName("Should refuse a kind that is never remembered")
    void refusesANeverRememberedKind() {
        var builder = PendingConsent.builder()
                .kind(ConsentKind.AUTO_RUN)
                .scripts(List.of())
                .grantedAt(GRANTED)
                .channel(Consent.Channel.CLI);

        assertThrows(IllegalArgumentException.class, builder::build,
                "A never-remembered kind has no yes_remember hold");
    }

    @Test
    @DisplayName("Should refuse scripts where its kind covers none and the empty list where it does")
    void refusesScriptsAndKindThatDoNotBelongTogether() {
        var withScript = PendingConsent.builder()
                .kind(ConsentKind.INSPECT_CHECKOUT)
                .scripts(List.of(script()))
                .grantedAt(GRANTED)
                .channel(Consent.Channel.CLI);
        var withoutScript = PendingConsent.builder()
                .kind(ConsentKind.RUN_CHECKOUT_SCRIPTS)
                .scripts(List.of())
                .grantedAt(GRANTED)
                .channel(Consent.Channel.CLI);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, withScript::build,
                        "An inspection consent covers no scripts"),
                () -> assertThrows(IllegalArgumentException.class, withoutScript::build,
                        "A script consent lists the scripts it covers"));
    }

    @Test
    @DisplayName("Should refuse a paired device on an answer that did not come through web")
    void refusesADeviceOnACliAnswer() {
        var builder = PendingConsent.builder()
                .kind(ConsentKind.INSPECT_CHECKOUT)
                .scripts(List.of())
                .grantedAt(GRANTED)
                .channel(Consent.Channel.CLI)
                .deviceId("dev-0123456789ab");

        assertThrows(IllegalArgumentException.class, builder::build,
                "Only a web answer names a paired device");
    }

    @Test
    @DisplayName("Should refuse a device id that is no paired device")
    void refusesAnInvalidDevice() {
        var builder = PendingConsent.builder()
                .kind(ConsentKind.INSPECT_CHECKOUT)
                .scripts(List.of())
                .grantedAt(GRANTED)
                .channel(Consent.Channel.WEB)
                .deviceId("not-a-device");

        assertThrows(IllegalArgumentException.class, builder::build,
                "A web answer names a paired device id");
    }

    private static ConsentScript script() {
        var content = Generators.letterStrings(4, 20).next();
        return new ConsentScript("scripts/" + Generators.letterStrings(1, 8).next() + ".sh", sha256(content));
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JVM carries SHA-256", e);
        }
    }
}
