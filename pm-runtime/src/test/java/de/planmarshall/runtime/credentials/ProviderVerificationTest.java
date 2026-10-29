/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.Consent;
import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentStore;
import de.planmarshall.runtime.config.Provider;
import de.planmarshall.runtime.config.ProviderDefault;
import de.planmarshall.runtime.config.ProviderVariant;

/**
 * The provider verification (Credentials &amp; Enrolment § Provider Verification, State Catalogue § Providers): one line per
 * check of the provider's kind, the CLI variant's identity comparison, and the consent {@code verify_providers} that
 * precedes every check.
 */
@DisplayName("Provider verification")
class ProviderVerificationTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final List<String> calls = new ArrayList<>();
    private boolean originReachable = true;
    private boolean credentialOk = true;
    private boolean toolLoggedIn = true;
    private String toolIdentity = "octocat";

    private final ProviderVerification.ProviderProbe probe = new ProviderVerification.ProviderProbe() {

        @Override
        public ProviderVerification.Answer origin(Provider provider) {
            calls.add("origin:" + provider.wireName());
            return originReachable ? ProviderVerification.Answer.ok("200") : ProviderVerification.Answer.failed("503");
        }

        @Override
        public ProviderVerification.Answer identity(Provider provider, String credentialKey) {
            calls.add("identity:" + provider.wireName() + ":" + credentialKey);
            return credentialOk ? ProviderVerification.Answer.ok("octocat")
                    : ProviderVerification.Answer.failed("401 Bad credentials");
        }

        @Override
        public ProviderVerification.Answer permissions(Provider provider, String credentialKey) {
            calls.add("permissions:" + provider.wireName());
            return ProviderVerification.Answer.ok("contents:write");
        }

        @Override
        public ProviderVerification.Answer expiry(Provider provider, String credentialKey) {
            calls.add("expiry:" + provider.wireName());
            return ProviderVerification.Answer.ok("none");
        }
    };

    private final ProviderVerification.ToolProbe tools = new ProviderVerification.ToolProbe() {

        @Override
        public ProviderVerification.Answer version(Provider provider) {
            calls.add("version:" + provider.wireName());
            return ProviderVerification.Answer.ok("2.60.0");
        }

        @Override
        public ProviderVerification.Answer loginIdentity(Provider provider) {
            calls.add("login:" + provider.wireName());
            return toolLoggedIn ? ProviderVerification.Answer.ok(toolIdentity)
                    : ProviderVerification.Answer.failed("not logged in");
        }
    };

    private final ProviderVerification verification = new ProviderVerification(probe, tools);

    private ConsentGate gate() {
        return new ConsentGate(new ConsentStore.Snapshot(List.of(), 0L), CLOCK);
    }

    private static ProviderDefault defaults(ProviderVariant variant, String credentialKey) {
        return new ProviderDefault(true, Optional.of(variant),
                credentialKey == null ? Optional.empty() : Optional.of(credentialKey));
    }

    private ProviderVerification.Verification verify(ProviderDefault defaults, ConsentGate.Answer answer) {
        return verification.verify(gate(), answer, Consent.Channel.CLI, Optional.empty(), Provider.GITHUB, defaults);
    }

    @Test
    @DisplayName("the native variant runs the origin, the identity, the permissions and the expiry checks")
    void nativeVariant() {
        var result = verify(defaults(ProviderVariant.NATIVE, "github"), ConsentGate.Answer.YES);

        assertAll(
                () -> assertTrue(result.ran()),
                () -> assertTrue(result.passed()),
                () -> assertEquals(4, result.lines().size()),
                () -> assertEquals(List.of("origin:github", "identity:github:github", "permissions:github",
                        "expiry:github"), calls),
                () -> assertTrue(result.toRecord().isEmpty()));
    }

    @Test
    @DisplayName("the CLI variant adds the tool's version and the same identity as the credential")
    void cliVariant() {
        var result = verify(defaults(ProviderVariant.CLI, "github"), ConsentGate.Answer.YES);

        assertAll(
                () -> assertTrue(result.passed()),
                () -> assertEquals(6, result.lines().size()),
                () -> assertTrue(calls.contains("version:github")),
                () -> assertTrue(calls.contains("login:github")));
    }

    @Test
    @DisplayName("a tool logged in as another identity than the credential fails the verification")
    void identityDiffers() {
        toolIdentity = "somebody-else";

        var result = verify(defaults(ProviderVariant.CLI, "github"), ConsentGate.Answer.YES);

        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals("provider_verification.tool_identity.differs", result.lines().getLast().messageKey()));
    }

    @Test
    @DisplayName("a tool that is not logged in fails the identity line")
    void toolNotLoggedIn() {
        toolLoggedIn = false;

        var result = verify(defaults(ProviderVariant.CLI, "github"), ConsentGate.Answer.YES);

        assertFalse(result.passed());
        assertEquals("provider_verification.tool_identity.failed", result.lines().getLast().messageKey());
    }

    @Test
    @DisplayName("a credential that did not authenticate leaves the tool identity unproven")
    void identityUnknown() {
        credentialOk = false;

        var result = verify(defaults(ProviderVariant.CLI, "github"), ConsentGate.Answer.YES);

        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertFalse(result.lines().getLast().ok()),
                () -> assertEquals("provider_verification.tool_identity.credential_unknown",
                        result.lines().getLast().messageKey()));
    }

    @Test
    @DisplayName("an unreachable origin and a refused credential fail with the provider's own answer")
    void failures() {
        originReachable = false;
        credentialOk = false;

        var result = verify(defaults(ProviderVariant.NATIVE, "github"), ConsentGate.Answer.YES);

        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals("provider_verification.origin.failed", result.lines().getFirst().messageKey()),
                () -> assertEquals(Optional.of("503"), result.lines().getFirst().answer()),
                () -> assertEquals("provider_verification.whoami.failed", result.lines().get(1).messageKey()),
                () -> assertEquals(Optional.of("401 Bad credentials"), result.lines().get(1).answer()));
    }

    @Test
    @DisplayName("a provider without a credential key fails its credential line without calling the provider")
    void missingCredentialKey() {
        var result = verify(defaults(ProviderVariant.NATIVE, null), ConsentGate.Answer.YES);

        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals(2, result.lines().size()),
                () -> assertEquals("provider_verification.credential.missing", result.lines().getLast().messageKey()),
                () -> assertEquals(List.of("origin:github"), calls));
    }

    @Test
    @DisplayName("no to the consent runs no check and leaves the scope in the consent state")
    void consentNo() {
        var result = verify(defaults(ProviderVariant.NATIVE, "github"), ConsentGate.Answer.NO);

        assertAll(
                () -> assertFalse(result.ran()),
                () -> assertFalse(result.passed()),
                () -> assertTrue(result.lines().isEmpty()),
                () -> assertTrue(calls.isEmpty()));
    }

    @Test
    @DisplayName("yes_remember hands the consent back for the store to record")
    void consentRemembered() {
        var result = verify(defaults(ProviderVariant.NATIVE, "github"), ConsentGate.Answer.YES_REMEMBER);

        assertAll(
                () -> assertTrue(result.passed()),
                () -> assertEquals(NOW, result.toRecord().orElseThrow().grantedAt()),
                () -> assertEquals(Consent.Channel.CLI, result.toRecord().orElseThrow().channel()));
    }
}
