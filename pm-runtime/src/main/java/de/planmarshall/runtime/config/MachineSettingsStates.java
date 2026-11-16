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

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.config.Consent;
import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentKind;
import de.planmarshall.core.config.ConsentStore;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.ProviderVerification;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;

/**
 * The engine half of the machine's settings states the onboarding's settings unit reaches (State Catalogue § The
 * Machine's Settings, UC-ONB-10): {@code settings.choose} with what the machine recorded, {@code settings.withdraw} and
 * {@code settings.remove-consent} for a remembered consent, {@code settings.credential} and
 * {@code settings.store-credential} for a rotated or replaced secret, and {@code settings.verify-providers} for the
 * providers checked again. The states themselves are units of the machine's onboarding, which lives in
 * {@code plan-marshall-mcp}; this class is what those units run over the stores of this repository, and it is provable
 * over fixtures without a workflow.
 * <p>
 * <b>The two writers this class does not carry.</b> {@code settings.documents-mode} and
 * {@code settings.cleanup-durations} write through {@link MachineConfigurationStore#writeDocumentsMode} and
 * {@link MachineConfigurationStore#writeCleanupDurations}; this class only reports their recorded values. The settings'
 * audit record (the channel and the paired device of a change) is written by the local API that serves the operator, not
 * here: a withdrawal is audited by {@link ConsentStore#withdraw} under the consent store's own transaction.
 * <p>
 * <b>A rotated secret drops its derived tokens.</b> {@code settings.store-credential} writes the new secret into the
 * recorded store and then drops every token the old secret derived, through the caches the assembly registers
 * ({@link DerivedTokenCache}): a plan held on a token of the old secret goes on once its hold's retry observes the new
 * one. Nothing is dropped when the store refuses the write.
 *
 * @since 0.1
 */
public final class MachineSettingsStates {

    /** The step of the machine's onboarding whose states this class runs. */
    public static final String STEP = "settings";

    /** The reason of a credential store that is not available now, the refusal of {@code settings.store-credential}. */
    public static final String CREDENTIAL_STORE_UNAVAILABLE = "credential_store_unavailable";

    /** The reason of a withdrawal that found no remembered consent. */
    public static final String NOT_REMEMBERED = "not_remembered";

    /** The aspects the settings offer as {@code reconfigure:<aspect>}, in the order of the guided run. */
    public static final List<String> ASPECTS = List.of("language", "harnesses", "models", "roles",
            "credential-store", "providers", "plan-defaults");

    /**
     * A cache of tokens derived from a credential's secret, registered by the assembly.
     *
     * @since 0.1
     */
    public interface DerivedTokenCache {

        /**
         * Drops every token derived from the secret of the credential key.
         *
         * @param credentialKey the credential key whose secret was rotated or replaced
         */
        void drop(String credentialKey);
    }

    /** The states of the settings unit this class runs, each with the id the catalogue gives it. */
    public enum State {
        /** {@code settings.choose}: what the machine recorded and the links the operator may take. */
        CHOOSE("settings.choose"),
        /** {@code settings.withdraw}: the remembered consent and the point its state asks again at. */
        WITHDRAW("settings.withdraw"),
        /** {@code settings.remove-consent}: the removal from {@code consents.json}, audited. */
        REMOVE_CONSENT("settings.remove-consent"),
        /** {@code settings.credential}: a new secret for an entry, or a new entry of another kind. */
        CREDENTIAL("settings.credential"),
        /** {@code settings.store-credential}: the write into the recorded store and the drop of derived tokens. */
        STORE_CREDENTIAL("settings.store-credential"),
        /** {@code settings.verify-providers}: the providers checked again and recorded. */
        VERIFY_PROVIDERS("settings.verify-providers");

        private final String stateId;

        State(String stateId) {
            this.stateId = stateId;
        }

        /**
         * @return the id of the state in the state catalogue
         */
        public String stateId() {
            return stateId;
        }
    }

    /**
     * What one state answered.
     *
     * @param next   the next state
     * @param reason the reason code of a refusal or of a withdrawal that found nothing; {@code null} otherwise
     * @param detail what the operator reads of the reason; {@code null} when there is none
     * @since 0.1
     */
    @Builder
    public record Transition(State next, @Nullable String reason, @Nullable String detail) {

        /**
         * @param next the next state
         * @return the transition of a state that did what it says
         */
        public static Transition to(State next) {
            return new Transition(Objects.requireNonNull(next, "next"), null, null);
        }

        /**
         * @param next   the state the act leaves the scope in
         * @param reason the reason code
         * @param detail what the operator reads
         * @return the transition of a refused act
         */
        public static Transition refused(State next, String reason, String detail) {
            return new Transition(Objects.requireNonNull(next, "next"), Objects.requireNonNull(reason, "reason"),
                    Objects.requireNonNull(detail, "detail"));
        }

        /**
         * @return whether the state did what it says
         */
        public boolean succeeded() {
            return reason == null;
        }
    }

    /**
     * What {@code settings.choose} shows (State Catalogue § The Machine's Settings): the machine's recorded values, the
     * remembered consents and the links the operator may take. The credentials' status is the recorded verification of
     * each provider; the state of a plan a credential holds is the hold's own record, which this class does not read.
     *
     * @param documentsMode how a start takes its documents
     * @param cleanup       the durations of the one cleanup
     * @param planDefaults  the machine's variant of each plan behaviour axis
     * @param providers     the machine's provider defaults with their last verification
     * @param remembered    the remembered consents
     * @param links         the offered links, in the order of the state catalogue
     * @since 0.1
     */
    @Builder
    public record Facts(DocumentsMode documentsMode, CleanupDurations cleanup, PlanDefaults planDefaults,
    ProviderDefaults providers, List<Consent> remembered, List<String> links) {

        /** Keeps the collections unmodifiable. */
        public Facts {
            Objects.requireNonNull(documentsMode, "documentsMode");
            Objects.requireNonNull(cleanup, "cleanup");
            Objects.requireNonNull(planDefaults, "planDefaults");
            Objects.requireNonNull(providers, "providers");
            remembered = List.copyOf(remembered);
            links = List.copyOf(links);
        }
    }

    /**
     * What one provider verification pass answered.
     *
     * @param transition    the next state, {@code settings.choose} after the results were written
     * @param verifications one result per provider the pass checked, in the order of the machine's defaults
     * @since 0.1
     */
    @Builder
    public record Recheck(Transition transition, List<ProviderVerification.Verification> verifications) {

        /** Keeps the results unmodifiable. */
        public Recheck {
            Objects.requireNonNull(transition, "transition");
            verifications = List.copyOf(verifications);
        }
    }

    private final MachineConfigurationStore configuration;
    private final ConsentStore consents;
    private final SecretStore secrets;
    private final List<DerivedTokenCache> derivedTokenCaches;
    private final ProviderVerification verification;
    private final Clock clock;

    /**
     * @param configuration      the machine configuration store
     * @param consents           the consent store
     * @param secrets            the recorded credential store
     * @param derivedTokenCaches the caches of tokens derived from a secret, in the order they are dropped
     * @param verification       the provider verification of the provider step
     * @param clock              the clock of a recorded verification
     */
    public MachineSettingsStates(MachineConfigurationStore configuration, ConsentStore consents, SecretStore secrets,
            List<DerivedTokenCache> derivedTokenCaches, ProviderVerification verification, Clock clock) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.consents = Objects.requireNonNull(consents, "consents");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.derivedTokenCaches = List.copyOf(derivedTokenCaches);
        this.verification = Objects.requireNonNull(verification, "verification");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * {@code settings.choose}: what the machine recorded and the links the operator may take.
     *
     * @return the facts of the state
     */
    public Facts facts() {
        var stored = configuration.read();
        var remembered = consents.read().consents();
        var credentialKeys = new LinkedHashSet<String>();
        stored.providers().providers().values().forEach(default_ -> {
            if (default_.credentialKey() != null) {
                credentialKeys.add(default_.credentialKey());
            }
        });
        var links = new ArrayList<String>();
        ASPECTS.forEach(aspect -> links.add("reconfigure:" + aspect));
        remembered.forEach(consent -> {
            var link = "withdraw:" + consent.kind().wireName();
            if (!links.contains(link)) {
                links.add(link);
            }
        });
        links.add("documents_mode");
        links.add("cleanup_durations");
        credentialKeys.forEach(key -> links.add("credential:" + key));
        links.add("verify_providers");
        links.add("done");
        return Facts.builder()
                .documentsMode(stored.documentsMode())
                .cleanup(stored.cleanup())
                .planDefaults(stored.planDefaults())
                .providers(stored.providers())
                .remembered(remembered)
                .links(links)
                .build();
    }

    /**
     * {@code settings.withdraw}: the remembered consent of a scope, which the state shows with the point its action asks
     * it again at.
     *
     * @param kind      the consent kind
     * @param projectId the project, for a kind remembered for a project; {@code null} for the workspace scope
     * @return the remembered consent; empty when the scope remembers none
     */
    public Optional<Consent> withdrawOffer(ConsentKind kind, @Nullable String projectId) {
        Objects.requireNonNull(kind, "kind");
        return consents.read().consents().stream()
                .filter(consent -> consent.kind() == kind)
                .filter(consent -> kind.projectScoped()
                        ? projectId != null && consent.belongsTo(projectId)
                        : consent.projectId() == null)
                .findFirst();
    }

    /**
     * {@code settings.remove-consent}: removes the entry of the scope from {@code consents.json}, audited. Nothing
     * recorded with the consent changes, and its consent state asks it again the next time its action runs, because the
     * store no longer remembers it.
     *
     * @param kind      the consent kind to withdraw
     * @param projectId the project, for a kind remembered for a project; {@code null} for the workspace scope
     * @param revision  the revision the operator read
     * @return the next state, {@code settings.choose}; {@code not_remembered} where the scope remembered nothing
     * @throws de.planmarshall.core.config.SettingsConflict if the store holds another revision than {@code revision}
     */
    public Transition removeConsent(ConsentKind kind, @Nullable String projectId, long revision) {
        Objects.requireNonNull(kind, "kind");
        if (consents.withdraw(kind, projectId, revision)) {
            return Transition.to(State.CHOOSE);
        }
        return Transition.refused(State.CHOOSE, NOT_REMEMBERED,
                "no remembered consent of the kind '%s' for the scope".formatted(kind.wireName()));
    }

    /**
     * {@code settings.store-credential}: writes the secret into the recorded store and drops every token derived from
     * the old one, so no consumer keeps a token of the replaced secret.
     *
     * @param account the entry the operator entered the secret for
     * @param secret  the secret; neither logged nor put into a refusal
     * @return {@code settings.verify-providers} after the write and the drop; the state itself with
     *         {@value #CREDENTIAL_STORE_UNAVAILABLE} where the store refused the write, nothing dropped
     */
    public Transition storeCredential(CredentialAccount account, String secret) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(secret, "secret");
        try {
            secrets.put(account, secret);
        } catch (SecretStoreException e) {
            return Transition.refused(State.STORE_CREDENTIAL, CREDENTIAL_STORE_UNAVAILABLE,
                    "the credential store '%s' refused the write".formatted(secrets.name()));
        }
        derivedTokenCaches.forEach(cache -> cache.drop(account.key()));
        return Transition.to(State.VERIFY_PROVIDERS);
    }

    /**
     * {@code settings.verify-providers}: checks every provider the machine recorded again, records each result with the
     * change's revision, and answers the state {@code settings.choose}. The caller records the consent each verification
     * answers with {@link ProviderVerification.Verification#toRecord()}.
     *
     * @param gate     the gate read from the consent store before the question
     * @param answer   the answer the operator gave
     * @param channel  the channel it came through
     * @param deviceId the paired device, for an answer through the web app; {@code null} otherwise
     * @param revision the revision the operator read
     * @return the next state and one verification per recorded provider
     * @throws de.planmarshall.core.config.SettingsConflict if the document holds another revision than {@code revision}
     */
    public Recheck verifyProviders(ConsentGate gate, ConsentGate.Answer answer, Consent.Channel channel,
            @Nullable String deviceId, long revision) {
        var current = configuration.read();
        var results = new EnumMap<Provider, ProviderDefault>(Provider.class);
        var verifications = new ArrayList<ProviderVerification.Verification>();
        current.providers().providers().forEach((provider, default_) -> {
            var result = verification.verify(gate, answer, channel, deviceId, provider, default_);
            verifications.add(result);
            if (result.ran()) {
                results.put(provider, new ProviderDefault(result.passed(), default_.variant(),
                        default_.credentialKey(), result.toResult(clock.instant())));
            } else {
                results.put(provider, default_);
            }
        });
        configuration.writeProviders(new ProviderDefaults(results), revision);
        return Recheck.builder()
                .transition(Transition.to(State.CHOOSE))
                .verifications(verifications)
                .build();
    }
}
