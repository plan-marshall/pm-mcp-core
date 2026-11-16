/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.config.Consent;
import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentKind;
import de.planmarshall.core.config.ConsentStore;
import de.planmarshall.core.config.SettingsConflict;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.ProviderVerification;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.test.AuditSinkDoubles;

/**
 * The engine half of the machine's settings states (State Catalogue § The Machine's Settings, UC-ONB-10): the facts of
 * {@code settings.choose}, the withdrawal of a remembered consent with its audit record and its asking again,
 * {@code settings.store-credential}'s drop of the tokens the old secret derived, and {@code settings.verify-providers}
 * over every recorded provider with the revision rule.
 */
@EnableGeneratorController
@DisplayName("Machine settings states")
class MachineSettingsStatesTest {

    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CREDENTIAL_KEY = "github";

    @TempDir
    Path base;

    private MachineConfigurationStore configuration;
    private ConsentStore consentStore;
    private Capture<AuditEvent> captured;
    private MachineSettingsStates settings;

    private final long revision = Generators.longs(1, 1_000_000).next();
    private final String secret = Generators.letterStrings(24, 48).next();

    @BeforeEach
    void createStates() {
        captured = newCapture(CaptureType.ALL);
        configuration = new MachineConfigurationStore(MachineConfigurationStore.file(base), new TestLocks());
        consentStore = new ConsentStore(ConsentStore.file(base), new TestLocks(),
                AuditSinkDoubles.capturing(captured));
        SecretStore secrets = createMock(SecretStore.class);
        ProviderVerification.ProviderProbe probe = createMock(ProviderVerification.ProviderProbe.class);
        ProviderVerification.ToolProbe tools = createMock(ProviderVerification.ToolProbe.class);
        settings = new MachineSettingsStates(configuration, consentStore, secrets, List.of(),
                new ProviderVerification(probe, tools), CLOCK);
    }

    private void document() throws Exception {
        Files.writeString(base.resolve(MachineConfigurationStore.FILE_NAME), """
                {"format_version":1,"revision":%d,"max_build_slots":2,"lsp_idle_timeout_seconds":900,
                 "sources":{"documents_mode":"move"},
                 "providers":{"github":{"available":false,"variant":"cli","credential_key":"github"}}}""".formatted(revision),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the settings facts name the recorded values, the remembered consents and the links")
    void facts() throws Exception {
        document();
        consentStore.grant(Consent.builder()
                .kind(ConsentKind.SCAN_HARNESSES)
                .scripts(List.of())
                .grantedAt(NOW)
                .channel(Consent.Channel.CLI)
                .build(), 0L);

        var facts = settings.facts();

        assertAll(
                () -> assertEquals(DocumentsMode.MOVE, facts.documentsMode(), "The documents mode is the recorded one"),
                () -> assertEquals(CleanupDurations.defaults(), facts.cleanup(), "An absent cleanup reads at its default"),
                () -> assertEquals(PlanDefaults.shipped(), facts.planDefaults(),
                        "An absent plan default reads at its default"),
                () -> assertEquals(1, facts.remembered().size(), "The remembered consent is shown"),
                () -> assertTrue(facts.links().contains("reconfigure:providers"),
                        "The settings offer the aspects to reconfigure"),
                () -> assertTrue(facts.links().contains("withdraw:scan_harnesses"),
                        "A remembered consent offers its withdrawal"),
                () -> assertTrue(facts.links().contains("credential:github"),
                        "The provider's credential key offers its entry"),
                () -> assertTrue(facts.links().contains("verify_providers"),
                        "The settings offer the provider re-check"),
                () -> assertEquals("done", facts.links().getLast(), "The settings end with the done link"));
    }

    @Test
    @DisplayName("a withdrawal removes the remembered consent, is audited, and its action asks again")
    void withdrawal() throws Exception {
        consentStore.grant(Consent.builder()
                .kind(ConsentKind.SCAN_HARNESSES)
                .scripts(List.of())
                .grantedAt(NOW)
                .channel(Consent.Channel.CLI)
                .build(), 0L);
        var offered = settings.withdrawOffer(ConsentKind.SCAN_HARNESSES, null);

        var transition = settings.removeConsent(ConsentKind.SCAN_HARNESSES, null, 1L);

        assertAll(
                () -> assertTrue(offered.isPresent(), "The remembered consent is offered for withdrawal"),
                () -> assertEquals(MachineSettingsStates.State.CHOOSE, transition.next(),
                        "The removal returns to the settings"),
                () -> assertTrue(transition.succeeded(), "The removal did what it says"),
                () -> assertFalse(consentStore.read().isRemembered(ConsentKind.SCAN_HARNESSES, null),
                        "The action asks again because nothing is remembered"),
                () -> assertEquals("consent_withdrawn", AuditSinkDoubles.eventNames(captured).getLast(),
                        "The withdrawal is audited"));
    }

    @Test
    @DisplayName("a withdrawal that finds no remembered consent changes nothing")
    void withdrawalWithoutEntry() throws Exception {
        var transition = settings.removeConsent(ConsentKind.SCAN_HARNESSES, null, 0L);

        assertAll(
                () -> assertEquals(MachineSettingsStates.State.CHOOSE, transition.next(),
                        "The state returns to the settings"),
                () -> assertEquals(MachineSettingsStates.NOT_REMEMBERED, transition.reason(),
                        "The refusal names the absent consent"),
                () -> assertTrue(AuditSinkDoubles.eventNames(captured).isEmpty(),
                        "No audit record is written for it"));
    }

    @Test
    @DisplayName("two withdrawals against one revision: the second is refused with the current entries")
    void withdrawalConflict() {
        consentStore.grant(Consent.builder()
                .kind(ConsentKind.SCAN_HARNESSES)
                .scripts(List.of())
                .grantedAt(NOW)
                .channel(Consent.Channel.CLI)
                .build(), 0L);
        settings.removeConsent(ConsentKind.SCAN_HARNESSES, null, 1L);

        var conflict = assertThrows(SettingsConflict.class,
                () -> settings.removeConsent(ConsentKind.SCAN_HARNESSES, null, 1L),
                "A change against the consumed revision is refused");

        assertAll(
                () -> assertEquals(SettingsConflict.ERROR_CODE, conflict.code(), "The refusal carries the conflict code"),
                () -> assertNotNull(conflict.currentValue(), "The refusal names the current entries"));
    }

    @Test
    @DisplayName("a stored credential is written and every derived token cache of its key is dropped")
    void storeCredential() {
        SecretStore secrets = createMock(SecretStore.class);
        MachineSettingsStates.DerivedTokenCache first = createMock(MachineSettingsStates.DerivedTokenCache.class);
        MachineSettingsStates.DerivedTokenCache second = createMock(MachineSettingsStates.DerivedTokenCache.class);
        ProviderVerification.ProviderProbe unusedProbe = createMock(ProviderVerification.ProviderProbe.class);
        ProviderVerification.ToolProbe unusedTools = createMock(ProviderVerification.ToolProbe.class);
        expect(secrets.name()).andReturn(SecretStore.FILE).anyTimes();
        secrets.put(CredentialAccount.global(CREDENTIAL_KEY), secret);
        expectLastCall();
        first.drop(CREDENTIAL_KEY);
        expectLastCall();
        second.drop(CREDENTIAL_KEY);
        expectLastCall();
        replay(secrets, first, second);
        var withCaches = new MachineSettingsStates(configuration, consentStore, secrets, List.of(first, second),
                new ProviderVerification(unusedProbe, unusedTools), CLOCK);

        var transition = withCaches.storeCredential(CredentialAccount.global(CREDENTIAL_KEY), secret);

        assertAll(
                () -> assertEquals(MachineSettingsStates.State.VERIFY_PROVIDERS, transition.next(),
                        "A stored secret verifies the providers next"),
                () -> assertTrue(transition.succeeded(), "The store accepted the write"));
        verify(secrets, first, second);
    }

    @Test
    @DisplayName("a store that refuses the write drops no derived token")
    void storeCredentialRefused() {
        SecretStore secrets = createMock(SecretStore.class);
        MachineSettingsStates.DerivedTokenCache cache = createMock(MachineSettingsStates.DerivedTokenCache.class);
        ProviderVerification.ProviderProbe unusedProbe = createMock(ProviderVerification.ProviderProbe.class);
        ProviderVerification.ToolProbe unusedTools = createMock(ProviderVerification.ToolProbe.class);
        expect(secrets.name()).andReturn(SecretStore.FILE).anyTimes();
        secrets.put(CredentialAccount.global(CREDENTIAL_KEY), secret);
        expectLastCall().andThrow(new SecretStoreException(SecretStoreException.Reason.UNAVAILABLE,
                "not available now"));
        replay(secrets, cache);
        var refusing = new MachineSettingsStates(configuration, consentStore, secrets, List.of(cache),
                new ProviderVerification(unusedProbe, unusedTools), CLOCK);

        var transition = refusing.storeCredential(CredentialAccount.global(CREDENTIAL_KEY), secret);

        assertAll(
                () -> assertEquals(MachineSettingsStates.State.STORE_CREDENTIAL, transition.next(),
                        "The refusal leaves the scope in the state"),
                () -> assertEquals(MachineSettingsStates.CREDENTIAL_STORE_UNAVAILABLE, transition.reason(),
                        "The refusal names the unavailable store"));
        verify(secrets, cache);
    }

    @Test
    @DisplayName("the providers checked again are recorded against the change's revision")
    void verifyProviders() throws Exception {
        document();
        ProviderVerification.ProviderProbe probe = createMock(ProviderVerification.ProviderProbe.class);
        ProviderVerification.ToolProbe tools = createMock(ProviderVerification.ToolProbe.class);
        expect(probe.origin(Provider.GITHUB)).andReturn(ProviderVerification.Answer.ok("reachable"));
        expect(probe.identity(Provider.GITHUB, CREDENTIAL_KEY))
                .andReturn(ProviderVerification.Answer.ok("ci-whoami"));
        expect(probe.permissions(Provider.GITHUB, CREDENTIAL_KEY))
                .andReturn(ProviderVerification.Answer.ok("repo"));
        expect(probe.expiry(Provider.GITHUB, CREDENTIAL_KEY))
                .andReturn(ProviderVerification.Answer.ok("2027-01-01"));
        expect(tools.version(Provider.GITHUB)).andReturn(ProviderVerification.Answer.ok("gh version 2.0"));
        expect(tools.loginIdentity(Provider.GITHUB)).andReturn(ProviderVerification.Answer.ok("ci-whoami"));
        replay(probe, tools);
        SecretStore unusedSecrets = createMock(SecretStore.class);
        var verifying = new MachineSettingsStates(configuration, consentStore, unusedSecrets, List.of(),
                new ProviderVerification(probe, tools), CLOCK);
        var gate = new ConsentGate(consentStore.read(), CLOCK);

        var recheck = verifying.verifyProviders(gate, ConsentGate.Answer.YES, Consent.Channel.CLI, null, revision);
        var read = configuration.read();

        assertAll(
                () -> assertEquals(MachineSettingsStates.State.CHOOSE, recheck.transition().next(),
                        "The re-check returns to the settings"),
                () -> assertEquals(1, recheck.verifications().size(), "One result per recorded provider"),
                () -> assertTrue(read.providers().providers().get(Provider.GITHUB).available(),
                        "A passed check records the provider available"),
                () -> assertEquals(revision + 1, read.revision(), "The result is written with the revision"),
                () -> assertEquals(NOW, read.providers().providers().get(Provider.GITHUB).verification().at(),
                        "The result carries the instant it was taken"));
        verify(probe);
    }

    @Test
    @DisplayName("a re-check against a consumed revision is refused and writes nothing")
    void verifyProvidersConflict() throws Exception {
        document();
        ProviderVerification.ProviderProbe probe = createMock(ProviderVerification.ProviderProbe.class);
        ProviderVerification.ToolProbe tools = createMock(ProviderVerification.ToolProbe.class);
        expect(probe.origin(Provider.GITHUB)).andReturn(ProviderVerification.Answer.ok("reachable"));
        expect(probe.identity(Provider.GITHUB, CREDENTIAL_KEY))
                .andReturn(ProviderVerification.Answer.ok("ci-whoami"));
        expect(probe.permissions(Provider.GITHUB, CREDENTIAL_KEY))
                .andReturn(ProviderVerification.Answer.ok("repo"));
        expect(probe.expiry(Provider.GITHUB, CREDENTIAL_KEY))
                .andReturn(ProviderVerification.Answer.ok("2027-01-01"));
        expect(tools.version(Provider.GITHUB)).andReturn(ProviderVerification.Answer.ok("gh version 2.0"));
        expect(tools.loginIdentity(Provider.GITHUB)).andReturn(ProviderVerification.Answer.ok("ci-whoami"));
        replay(probe, tools);
        SecretStore unusedSecrets = createMock(SecretStore.class);
        var verifying = new MachineSettingsStates(configuration, consentStore, unusedSecrets, List.of(),
                new ProviderVerification(probe, tools), CLOCK);
        var gate = new ConsentGate(consentStore.read(), CLOCK);
        configuration.writeDocumentsMode(DocumentsMode.COPY, revision);

        var conflict = assertThrows(SettingsConflict.class, () -> verifying.verifyProviders(gate,
                        ConsentGate.Answer.YES, Consent.Channel.CLI, null, revision),
                "A re-check against the consumed revision is refused");

        assertAll(
                () -> assertEquals(DocumentsMode.COPY, configuration.read().documentsMode(),
                        "The refused re-check wrote nothing"),
                () -> assertFalse(configuration.read().providers().providers().get(Provider.GITHUB).available(),
                        "The provider's recorded availability is unchanged"),
                () -> assertEquals(revision + 1, conflict.currentRevision(),
                        "The refusal names the revision the document holds"));
        verify(probe);
    }
}
