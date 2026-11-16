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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A plan's own values, its {@code overrides.json} (Workflow DSL § Plan Overrides, Store Schemas § Plan Overrides):
 * per role its {@code harness}, {@code model} and {@code effort}; the variant of each plan behaviour axis; the plan's
 * posture and base branch; the variant of an integration; and the optional providers the plan uses. A key the document
 * does not hold defers to the project's and the machine's value, so every member beyond the revision may be absent.
 * <p>
 * Only the operator writes the document, in either client, and a write names the {@code revision} it changes; the first
 * change wins and a later one against an older revision is refused with {@code conflict} and the current value
 * ({@link de.planmarshall.core.config.SettingsConflict}).
 *
 * @param revision     the {@code revision} of the document, raised by one with every write; {@value #NO_DOCUMENT}
 *                     for the value a missing document reads as
 * @param roles        the per-role values, by role name
 * @param axes         the variant of each plan behaviour axis that the plan's own level sets
 * @param posture      the plan's posture; {@code null} when the plan sets none
 * @param baseBranch   the plan's base branch; {@code null} when the plan sets none
 * @param integrations per integration the variant the plan uses, by integration name
 * @param providers    the optional providers the plan uses
 * @param updatedAt    when the document was last written; {@code null} only for the value a missing document reads as
 * @param updatedBy    the channel and device of the last write; {@code null} only for the value a missing document
 *                     reads as
 * @since 0.1
 */
@Builder(toBuilder = true)
public record PlanOverrides(long revision, Map<String, Role> roles, Map<Axis, String> axes,
@Nullable Posture posture, @Nullable String baseBranch, Map<String, Integration> integrations, Providers providers,
@Nullable Instant updatedAt, @Nullable UpdatedBy updatedBy) {

    /** The format version of the plan overrides document. */
    public static final int FORMAT_VERSION = 1;

    /** The revision a document that does not exist reads as. */
    public static final long NO_DOCUMENT = 0L;

    /**
     * @param revision     the revision, never negative
     * @param roles        the per-role values
     * @param axes         the variants the plan sets
     * @param posture      the posture, {@code null} for none
     * @param baseBranch   the base branch, {@code null} for none
     * @param integrations the integration variants
     * @param providers    the optional providers
     * @param updatedAt    the time of the last write, {@code null} for the value of a missing document
     * @param updatedBy    the channel of the last write, {@code null} for the value of a missing document
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the revision is negative
     */
    public PlanOverrides {
        if (revision < 0) {
            throw new IllegalArgumentException("A plan overrides document is at revision 0 or more: " + revision);
        }
        roles = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(roles, "roles")));
        axes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(axes, "axes")));
        integrations = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(integrations, "integrations")));
        Objects.requireNonNull(providers, "providers");
    }

    /**
     * @return the value a document that does not exist reads as: revision {@value #NO_DOCUMENT} and no value
     */
    public static PlanOverrides absent() {
        return PlanOverrides.builder()
                .revision(NO_DOCUMENT)
                .roles(Map.of())
                .axes(Map.of())
                .integrations(Map.of())
                .providers(Providers.none())
                .build();
    }

    /**
     * The plan behaviour axes of the composition (Workflow DSL § Plan Behaviour Axes), the keys of {@link #axes()}.
     *
     * @since 0.1
     */
    public enum Axis {

        /** How the plan lands. */
        LANDING("landing", "landing"),

        /** The order in which the plan works. */
        METHOD("method", "method"),

        /** How often the plan waits for the operator. */
        ASKING("asking", "ask");

        private final String key;

        private final String prefix;

        Axis(String key, String prefix) {
            this.key = key;
            this.prefix = prefix;
        }

        /**
         * @return the key of the axis in {@code overrides.json}
         */
        public String key() {
            return key;
        }

        /**
         * @return the prefix its variants are named with
         */
        public String prefix() {
            return prefix;
        }

        /**
         * @param value a variant
         * @return whether the variant is named with this axis's prefix
         */
        public boolean names(String value) {
            return value.startsWith(prefix + ".");
        }

        /**
         * @param key the key of an axis
         * @return the axis of that key; empty for a key the schema does not admit
         */
        public static Optional<Axis> of(String key) {
            for (var axis : values()) {
                if (axis.key.equals(key)) {
                    return Optional.of(axis);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The plan's posture (Workflow DSL § Posture and Base Branch): it prunes steps, so it is no axis, but it resolves by
     * the same levels.
     *
     * @since 0.1
     */
    public enum Posture {

        /** The smallest process. */
        MINIMAL("minimal"),

        /** The standard process. */
        STANDARD("standard"),

        /** The full process. */
        FULL("full");

        private final String wireName;

        Posture(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the wire name, the value of the schema's {@code enum}
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName a wire name
         * @return the posture of that name; empty for a value the schema does not admit
         */
        public static Optional<Posture> of(String wireName) {
            for (var posture : values()) {
                if (posture.wireName.equals(wireName)) {
                    return Optional.of(posture);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The variant of an integration the plan uses.
     *
     * @since 0.1
     */
    public enum Integration {

        /** The native implementation of the integration. */
        NATIVE("native"),

        /** The integration through its command-line tool. */
        CLI("cli");

        private final String wireName;

        Integration(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the wire name, the value of the schema's {@code enum}
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName a wire name
         * @return the variant of that name; empty for a value the schema does not admit
         */
        public static Optional<Integration> of(String wireName) {
            for (var variant : values()) {
                if (variant.wireName.equals(wireName)) {
                    return Optional.of(variant);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The channel of a write of the document.
     *
     * @since 0.1
     */
    public enum Channel {

        /** The terminal client. */
        CLI("cli"),

        /** A paired device or the browser on the loopback listener. */
        WEB("web");

        private final String wireName;

        Channel(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the wire name, the value of the schema's {@code enum}
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName a wire name
         * @return the channel of that name; empty for a value the schema does not admit
         */
        public static Optional<Channel> of(String wireName) {
            for (var channel : values()) {
                if (channel.wireName.equals(wireName)) {
                    return Optional.of(channel);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The plan's own values of one role: any subset of its {@code harness}, {@code model} and {@code effort}, at least
     * one of them. A member it does not set keeps the project's and the machine's value.
     *
     * @param harness the harness; {@code null} where the plan sets none
     * @param model   the model; {@code null} where the plan sets none
     * @param effort  the effort; {@code null} where the plan sets none
     * @since 0.1
     */
    public record Role(@Nullable String harness, @Nullable String model, @Nullable String effort) {

        /**
         * @param harness the harness
         * @param model   the model
         * @param effort  the effort
         * @throws IllegalArgumentException if the role sets no value
         */
        public Role {
            if (harness == null && model == null && effort == null) {
                throw new IllegalArgumentException("A plan's role override sets a harness, a model or an effort");
            }
        }
    }

    /**
     * The optional providers the plan uses (Workflow DSL § Plan Overrides).
     *
     * @param codeAnalysis the code-analysis providers
     * @param reviewBots   the review bots
     * @since 0.1
     */
    public record Providers(List<String> codeAnalysis, List<String> reviewBots) {

        /**
         * @param codeAnalysis the code-analysis providers
         * @param reviewBots   the review bots
         */
        public Providers {
            codeAnalysis = List.copyOf(codeAnalysis);
            reviewBots = List.copyOf(reviewBots);
        }

        /**
         * @return the providers a plan that uses none names
         */
        public static Providers none() {
            return new Providers(List.of(), List.of());
        }
    }

    /**
     * Who last wrote the document.
     *
     * @param channel  the client the write came through
     * @param deviceId the paired device; {@code null} for the terminal client or the loopback listener
     * @since 0.1
     */
    public record UpdatedBy(Channel channel, @Nullable String deviceId) {

        /** Rejects a missing channel. */
        public UpdatedBy {
            Objects.requireNonNull(channel, "channel");
        }
    }
}
