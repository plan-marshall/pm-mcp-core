/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * One entry row of the scope session log {@code sessions.jsonl} (PM-ARCH-4, PM-CLIENT-3): what one
 * {@code pm-mcp enter} on the scope recorded about the session that entered it.
 * <p>
 * The row has a closed schema in {@code format_version} 1 ({@link ScopeSessionRowCodec}). It is history and audit
 * data: no call resolves anything from it. No component holds a path of a checkout: where the session entered from is
 * the kind {@link #enteredFrom()} and, for a worktree, the name of the worktree {@link #worktreeOf()}, never the
 * directory itself.
 * <p>
 * Two rules tie components together, as the schema of the row states them: {@link #worktreeOf()} is present exactly
 * when the session entered from a worktree, and a session whose model is unknown ({@link #modelSource()}
 * {@code null}) has neither a model family nor a model id, while a known model always has its family.
 *
 * @param clientSessionId      the host session identifier; {@code null} when the host provides none
 * @param client               the declared client
 * @param clientDetected       the client detected from the environment signature; {@code null} when nothing matched
 * @param environmentMatchTier how the declared client and the environment agree
 * @param clientBuild          the exact host build the version probe printed; {@code null} when there is none
 * @param modelFamily          the model family of the session; {@code null} exactly when the model source is
 * @param modelId              the exact model identifier; {@code null} when unknown
 * @param modelSource          where the model is known from; {@code null} when unknown
 * @param pmMcpVersion         the release version of the runtime that wrote the row
 * @param bundleVersion        the installed client bundle version; {@code null} when no bundle is installed
 * @param projectId            the enrolment identifier of the project
 * @param enteredFrom          whether the session entered from the main checkout or a worktree
 * @param worktreeOf           the name of the worktree, a plan id or {@code _epics}; {@code null} for the main
 *                             checkout
 * @param generationId         the session generation the entry issued, {@code sg-} and 16 hex digits
 * @param enteredAt            the instant of the entry
 * @since 0.1
 */
@Builder(toBuilder = true)
public record ScopeSessionRow(String clientSessionId, Client client, Client clientDetected,
EnvironmentMatchTier environmentMatchTier, String clientBuild, ModelFamily modelFamily, String modelId,
ModelSource modelSource, String pmMcpVersion, String bundleVersion, String projectId, EnteredFrom enteredFrom,
String worktreeOf, String generationId, Instant enteredAt) {

    /** The format version of a session row that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The name of the epic worktree among the worktree names. */
    public static final String EPICS_WORKTREE = "_epics";

    private static final Pattern CLIENT_SESSION_ID = Pattern.compile("[\\x21-\\x7E]{1,128}");
    private static final Pattern GENERATION_ID = Pattern.compile("sg-[0-9a-f]{16}");

    /**
     * A client the session can declare or the environment can match, by its client profile id.
     *
     * @since 0.1
     */
    public enum Client {
        /** Claude Code. */
        CLAUDE("claude"),
        /** OpenCode. */
        OPENCODE("opencode"),
        /** Antigravity. */
        ANTIGRAVITY("antigravity"),
        /** Codex. */
        CODEX("codex"),
        /** The neutral profile. */
        NEUTRAL("neutral");

        private final String wireName;

        Client(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a row */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a row
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Client> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * How the declared client and the detected environment agree (PM-CLIENT-3).
     *
     * @since 0.1
     */
    public enum EnvironmentMatchTier {
        /** The environment signature matches the declared client. */
        MATCHED,
        /** The environment shows no signature that confirms or contradicts the declared client. */
        UNVERIFIED,
        /** The environment signature names another client than the declared one. */
        CONTRADICTED;

        /** @return the name of the value in a row */
        public String wireName() {
            return name();
        }

        /**
         * @param wireName the name of a value in a row
         * @return the value of that name; empty if no value has it
         */
        public static Optional<EnvironmentMatchTier> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.name().equals(wireName)).findFirst();
        }
    }

    /**
     * The family of the model of a session.
     *
     * @since 0.1
     */
    public enum ModelFamily {
        /** Opus. */
        OPUS("opus"),
        /** Sonnet. */
        SONNET("sonnet"),
        /** Haiku. */
        HAIKU("haiku"),
        /** Fable. */
        FABLE("fable"),
        /** GPT. */
        GPT("gpt"),
        /** Gemini. */
        GEMINI("gemini"),
        /** Any other family. */
        OTHER("other");

        private final String wireName;

        ModelFamily(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a row */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a row
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ModelFamily> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Where the model of a session is known from.
     *
     * @since 0.1
     */
    public enum ModelSource {
        /** The host exposes it. */
        HOST("host");

        private final String wireName;

        ModelSource(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a row */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a row
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ModelSource> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Where the session entered from.
     *
     * @since 0.1
     */
    public enum EnteredFrom {
        /** The main checkout of the project. */
        MAIN_CHECKOUT("main_checkout"),
        /** A worktree below {@code .marshall/local/worktrees/}. */
        WORKTREE("worktree");

        private final String wireName;

        EnteredFrom(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a row */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a row
         * @return the value of that name; empty if no value has it
         */
        public static Optional<EnteredFrom> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * @throws NullPointerException     if the client, the environment match tier, the runtime version, the project
     *                                  id, the entered-from kind, the generation id or the entry instant is
     *                                  {@code null}
     * @throws IllegalArgumentException if a value breaks its form, if the worktree name is present for an entry from
     *                                  the main checkout or missing for one from a worktree, or if the model family
     *                                  and model id do not follow the model source
     */
    public ScopeSessionRow {
        if (clientSessionId != null && !CLIENT_SESSION_ID.matcher(clientSessionId).matches()) {
            throw new IllegalArgumentException("Not a client session id (1 to 128 printable ASCII characters): "
                    + clientSessionId);
        }
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(environmentMatchTier, "environmentMatchTier");
        requireNotEmpty(clientBuild, "clientBuild");
        requireNotEmpty(modelId, "modelId");
        requireNotEmpty(Objects.requireNonNull(pmMcpVersion, "pmMcpVersion"), "pmMcpVersion");
        requireNotEmpty(bundleVersion, "bundleVersion");
        LockKey.identifier(Objects.requireNonNull(projectId, "projectId"), "projectId");
        Objects.requireNonNull(enteredFrom, "enteredFrom");
        requireWorktree(enteredFrom, worktreeOf);
        Objects.requireNonNull(generationId, "generationId");
        if (!GENERATION_ID.matcher(generationId).matches()) {
            throw new IllegalArgumentException("Not a generation id (sg- and 16 hex digits): " + generationId);
        }
        Objects.requireNonNull(enteredAt, "enteredAt");
        if (modelSource == null && (modelFamily != null || modelId != null)) {
            throw new IllegalArgumentException("A session whose model source is unknown has no model family or id");
        }
        if (modelSource != null && modelFamily == null) {
            throw new IllegalArgumentException("A session whose model source is known has a model family");
        }
    }

    private static void requireWorktree(EnteredFrom enteredFrom, String worktreeOf) {
        if (enteredFrom == EnteredFrom.MAIN_CHECKOUT) {
            if (worktreeOf != null) {
                throw new IllegalArgumentException("An entry from the main checkout names no worktree: "
                        + worktreeOf);
            }
            return;
        }
        if (worktreeOf == null) {
            throw new IllegalArgumentException("An entry from a worktree names its worktree");
        }
        if (!EPICS_WORKTREE.equals(worktreeOf)) {
            LockKey.identifier(worktreeOf, "worktreeOf");
        }
    }

    private static void requireNotEmpty(String value, String name) {
        if (value != null && value.isEmpty()) {
            throw new IllegalArgumentException("'%s' of a session row is empty".formatted(name));
        }
    }

    /**
     * @param other a session row, typically the latest row of the log
     * @return whether the declared client of this row differs from the other's, the change the log owes an
     *         {@code entry_client_changed} record for
     */
    public boolean clientDiffersFrom(ScopeSessionRow other) {
        return client != Objects.requireNonNull(other, "other").client;
    }
}
