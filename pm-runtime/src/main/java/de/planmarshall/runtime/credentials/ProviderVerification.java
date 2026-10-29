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

import de.cuioss.tools.logging.CuiLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.config.Consent;
import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentKind;
import de.planmarshall.runtime.config.Provider;
import de.planmarshall.runtime.config.ProviderDefault;
import de.planmarshall.runtime.config.ProviderVariant;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The provider verification of the machine's provider step (PM-CRED-1, Credentials &amp; Enrolment § Provider
 * Verification): per selected provider, on the consent {@link ConsentKind#VERIFY_PROVIDERS}, the checks of its kind, each
 * reported as one check line — the origin is reachable; the credential authenticates and as whom; its permissions and its
 * expiry; and, for the CLI variant, the tool's version and that it is logged in as the same identity as the credential.
 * <p>
 * The verification records nothing: it answers the lines and whether every one of them passed, and the aspect's
 * {@code providers.record} state writes the machine's provider defaults from that. A provider whose checks fail is
 * recorded only on the operator's word, and then as not available.
 * <p>
 * <b>The checks reach the providers through a seam.</b> The CI contract has no module of this repository yet, and no
 * provider module answers {@code ci.whoami} here, so {@link ProviderProbe} and {@link ToolProbe} are the seam the
 * assembly implements over the provider modules and the enrolled CLI tools. The verification is specified against that
 * seam; a provider whose probe cannot answer yet reports the failed line it got, never a silent pass.
 * <p>
 * A {@code no} to the consent leaves the scope in {@code providers.verify-consent}: {@link #verify} runs no check then,
 * and the verification reports {@link Verification#ran()} {@code false}.
 *
 * @since 0.1
 */
public final class ProviderVerification {

    /** The prefix of every message key of a check line. */
    public static final String MESSAGE_KEY_PREFIX = "provider_verification.";

    private static final CuiLogger LOGGER = new CuiLogger(ProviderVerification.class);

    private static final String OK = "ok";
    private static final String FAILED = "failed";

    /** One answer a probe gives: whether the check passed and the provider's or the tool's own answer. */
    public record Answer(boolean ok, String detail) {

        /** Rejects a {@code null} answer. */
        public Answer {
            Objects.requireNonNull(detail, "detail");
        }

        /**
         * @param detail the provider's own answer, verbatim
         * @return a passed check
         */
        public static Answer ok(String detail) {
            return new Answer(true, detail);
        }

        /**
         * @param detail why the check failed, or the provider's refusal
         * @return a failed check
         */
        public static Answer failed(String detail) {
            return new Answer(false, detail);
        }
    }

    /**
     * The seam to the provider modules: the origin reachability and the credential checks of one provider.
     *
     * @since 0.1
     */
    public interface ProviderProbe {

        /**
         * @param provider the provider
         * @return whether the provider's origin is reachable
         */
        Answer origin(Provider provider);

        /**
         * @param provider      the provider
         * @param credentialKey the credential entry the provider uses
         * @return whether the credential authenticates, and as whom ({@code ci.whoami})
         */
        Answer identity(Provider provider, String credentialKey);

        /**
         * @param provider      the provider
         * @param credentialKey the credential entry the provider uses
         * @return the credential's permissions
         */
        Answer permissions(Provider provider, String credentialKey);

        /**
         * @param provider      the provider
         * @param credentialKey the credential entry the provider uses
         * @return the credential's expiry
         */
        Answer expiry(Provider provider, String credentialKey);
    }

    /**
     * The seam to the enrolled CLI tools of the CLI variant.
     *
     * @since 0.1
     */
    public interface ToolProbe {

        /**
         * @param provider the provider whose tool is enrolled
         * @return the tool's version
         */
        Answer version(Provider provider);

        /**
         * @param provider the provider whose tool is enrolled
         * @return the identity the tool is logged in as
         */
        Answer loginIdentity(Provider provider);
    }

    /**
     * One check line.
     *
     * @param check      the name of the check
     * @param ok         whether it passed
     * @param messageKey the message key of the line, {@code provider_verification.<check>.<ok|failed>}
     * @param arguments  the arguments of the message: the provider and what the check measured
     * @param answer     the provider's or the tool's own answer where there is one
     * @since 0.1
     */
    public record CheckLine(String check, boolean ok, String messageKey, List<String> arguments,
    Optional<String> answer) {

        /** Keeps the arguments unmodifiable and rejects {@code null} components. */
        public CheckLine {
            Objects.requireNonNull(check, "check");
            Objects.requireNonNull(messageKey, "messageKey");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(answer, "answer");
        }
    }

    /**
     * The result of one provider's verification.
     *
     * @param provider the provider
     * @param ran      whether the consent allowed the checks to run
     * @param lines    the check lines, one per check that ran
     * @param toRecord the consent to record through {@code ConsentStore}, for a {@code yes_remember}
     * @since 0.1
     */
    public record Verification(Provider provider, boolean ran, List<CheckLine> lines, Optional<Consent> toRecord) {

        /** Keeps the lines unmodifiable and rejects {@code null} components. */
        public Verification {
            Objects.requireNonNull(provider, "provider");
            lines = List.copyOf(lines);
            Objects.requireNonNull(toRecord, "toRecord");
        }

        /** @return whether the checks ran and every one of them passed */
        public boolean passed() {
            return ran && !lines.isEmpty() && lines.stream().allMatch(CheckLine::ok);
        }
    }

    private final ProviderProbe probe;
    private final ToolProbe tools;

    /**
     * @param probe the seam to the provider modules
     * @param tools the seam to the enrolled CLI tools
     */
    public ProviderVerification(ProviderProbe probe, ToolProbe tools) {
        this.probe = Objects.requireNonNull(probe, "probe");
        this.tools = Objects.requireNonNull(tools, "tools");
    }

    /**
     * Verifies one provider, after the consent {@code verify_providers}.
     *
     * @param gate     the gate read from the consent store before the question
     * @param answer   the answer the operator gave
     * @param channel  the channel it came through
     * @param deviceId the paired device, for an answer through the web app
     * @param provider the provider to verify
     * @param defaults the provider's recorded default, which names its variant and its credential key
     * @return the check lines and the consent to record; no check ran when the answer did not allow them
     * @throws IllegalArgumentException if the answer cannot belong to the kind, its scope or its channel
     */
    public Verification verify(ConsentGate gate, ConsentGate.Answer answer, Consent.Channel channel,
            Optional<String> deviceId, Provider provider, ProviderDefault defaults) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(defaults, "defaults");
        var decision = Objects.requireNonNull(gate, "gate").answer(
                ConsentGate.Request.machine(ConsentKind.VERIFY_PROVIDERS), answer, channel, deviceId);
        if (!decision.runs()) {
            return new Verification(provider, false, List.of(), Optional.empty());
        }
        var lines = new ArrayList<CheckLine>();
        lines.add(line(provider, "origin", probe.origin(provider), "reachability"));
        var credentialKey = defaults.credentialKey();
        if (credentialKey.isEmpty()) {
            lines.add(new CheckLine("credential", false, MESSAGE_KEY_PREFIX + "credential.missing",
                    List.of(provider.wireName()), Optional.empty()));
        } else {
            var identity = probe.identity(provider, credentialKey.get());
            lines.add(line(provider, "whoami", identity, "identity"));
            lines.add(line(provider, "permissions", probe.permissions(provider, credentialKey.get()), "permissions"));
            lines.add(line(provider, "expiry", probe.expiry(provider, credentialKey.get()), "expiry"));
            if (defaults.variant().filter(ProviderVariant.CLI::equals).isPresent()) {
                lines.add(line(provider, "tool_version", tools.version(provider), "version"));
                var login = tools.loginIdentity(provider);
                lines.add(sameIdentity(provider, identity, login));
            }
        }
        var verification = new Verification(provider, true, lines, decision.toRecord());
        if (verification.passed()) {
            LOGGER.info(INFO.PROVIDER_VERIFIED, provider.wireName(), lines.size());
        } else {
            LOGGER.warn(WARN.PROVIDER_VERIFICATION_FAILED, provider.wireName(),
                    lines.stream().filter(line -> !line.ok()).map(CheckLine::messageKey).toList());
        }
        return verification;
    }

    /**
     * The CLI variant is verified as a whole: its tool must be logged in as the same identity as the credential, which
     * the two answers of {@code ci.whoami} and the tool's status decide.
     */
    private static CheckLine sameIdentity(Provider provider, Answer credential, Answer login) {
        if (!login.ok() || !credential.ok()) {
            return line(provider, "tool_identity", login, "identity");
        }
        var same = credential.detail().equals(login.detail());
        return new CheckLine("tool_identity", same,
                MESSAGE_KEY_PREFIX + "tool_identity." + (same ? OK : "differs"), List.of(provider.wireName(),
                        credential.detail(), login.detail()),
                Optional.of(login.detail()));
    }

    private static CheckLine line(Provider provider, String check, Answer answer, String measured) {
        return new CheckLine(check, answer.ok(), MESSAGE_KEY_PREFIX + check + "." + (answer.ok() ? OK : FAILED),
                List.of(provider.wireName(), measured), Optional.of(answer.detail()));
    }
}
