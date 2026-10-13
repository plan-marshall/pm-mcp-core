/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;

import de.planmarshall.core.store.ScopeSessionRow;

/**
 * One entry of the session generation record {@code queue/session-generations.json} (PM-WORK-5): one generation of
 * an interactive session, issued for its session identity by {@code pm-mcp enter}, by the first binding of a session
 * relay or by a new host session the relay reports.
 * <p>
 * The entry has a closed schema in {@code format_version} 1 ({@link SessionGenerationCodec}). A generation is current
 * until a newer generation of the same {@link #sessionKey()} is issued; that issue fences it in the same write, and
 * the {@link #fence()} records when, and whether the fencing generation was issued by {@code pm-mcp enter}. The record
 * itself holds only the instant of the fence; whether an {@code enter} fenced the generation is what the record says
 * about the generation of the same key issued at that instant, and the codec derives it when it reads the record.
 * <p>
 * A new generation has the defaults of its issue ({@link #issued}): no scope and no working directory, no delivered
 * skill, offers not suspended, not fenced. A session gets its scope and working directory when it enters a scope; a
 * session without a scope has no working directory.
 *
 * @param id              the generation, the key of the entry
 * @param sessionKey      the SHA-256 of the session identity, 64 lower-case hexadecimal digits; the generations of one
 *                        key fence each other
 * @param client          the declared client of the session
 * @param clientSessionId the host session identifier; {@code null} when the host provides none, and the identity is
 *                        the relay instance then
 * @param cause           what issued the generation
 * @param issuedAt        the instant of the issue
 * @param sessionTasks    whether the client profile marks the host eligible for session tasks
 * @param scope           the scope the session entered; {@code null} until it enters one
 * @param workdir         the working directory the session entered from; {@code null} while the scope is
 * @param deliveredSkills the skill delivery record of the generation, in the order of delivery
 * @param offersSuspended whether offers are suspended after a clear or compaction the relay reported
 * @param fence           how the generation was fenced; {@code null} for a current generation
 * @since 0.1
 */
@Builder(toBuilder = true)
public record SessionGeneration(GenerationId id, String sessionKey, ScopeSessionRow.Client client,
String clientSessionId, GenerationCause cause, Instant issuedAt, boolean sessionTasks, Scope scope, Workdir workdir,
List<DeliveredSkill> deliveredSkills, boolean offersSuspended, Fence fence) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CLIENT_SESSION_ID = Pattern.compile("[\\x21-\\x7E]{1,128}");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");
    private static final String SKILL_URI_PREFIX = "skill://pm/";

    /**
     * @throws NullPointerException     if the id, the session key, the client, the cause, the issue instant or the
     *                                  delivered skills are {@code null}
     * @throws IllegalArgumentException if a value breaks its form, or a working directory is given without a scope
     */
    public SessionGeneration {
        Objects.requireNonNull(id, "id");
        requireSha256(Objects.requireNonNull(sessionKey, "sessionKey"), "sessionKey");
        Objects.requireNonNull(client, "client");
        if (clientSessionId != null && !CLIENT_SESSION_ID.matcher(clientSessionId).matches()) {
            throw new IllegalArgumentException("Not a client session id (1 to 128 printable ASCII characters): "
                    + clientSessionId);
        }
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (scope == null && workdir != null) {
            throw new IllegalArgumentException("A session without a scope has no working directory: " + id);
        }
        deliveredSkills = List.copyOf(Objects.requireNonNull(deliveredSkills, "deliveredSkills"));
    }

    /**
     * A new generation with the defaults of its issue: no scope, no working directory, no delivered skill, offers not
     * suspended, not fenced.
     *
     * @param id              the drawn generation
     * @param sessionKey      the SHA-256 of the session identity
     * @param client          the declared client
     * @param clientSessionId the host session identifier; {@code null} when the host provides none
     * @param cause           what issues the generation
     * @param issuedAt        the instant of the issue
     * @param sessionTasks    whether the host is eligible for session tasks
     * @return the new generation
     */
    public static SessionGeneration issued(GenerationId id, String sessionKey, ScopeSessionRow.Client client,
            String clientSessionId, GenerationCause cause, Instant issuedAt, boolean sessionTasks) {
        return new SessionGeneration(id, sessionKey, client, clientSessionId, cause, issuedAt, sessionTasks, null,
                null, List.of(), false, null);
    }

    /** @return whether a newer generation of the same session key fenced this one */
    public boolean isFenced() {
        return fence != null;
    }

    /**
     * @param fencedAt the instant of the issue that fences this generation
     * @param byEnter  whether that issue is a {@code pm-mcp enter}
     * @return this generation, fenced; a generation fenced already keeps its first fence
     */
    public SessionGeneration fencedAt(Instant fencedAt, boolean byEnter) {
        return isFenced() ? this : toBuilder().fence(new Fence(fencedAt, byEnter)).build();
    }

    /**
     * @param newScope   the scope the session entered
     * @param newWorkdir the working directory it entered from
     * @return this generation with the scope and the working directory
     */
    public SessionGeneration withScope(Scope newScope, Workdir newWorkdir) {
        return toBuilder().scope(Objects.requireNonNull(newScope, "newScope"))
                .workdir(Objects.requireNonNull(newWorkdir, "newWorkdir")).build();
    }

    private static void requireSha256(String value, String name) {
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not a SHA-256 (64 hex digits): %s".formatted(name, value));
        }
    }

    private static void requireIdentifier(String value, String name) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a valid identifier for %s: %s".formatted(name, value));
        }
    }

    /**
     * How a generation was fenced.
     *
     * @param at      the instant of the issue that fenced it, {@code fenced_at} of the record
     * @param byEnter whether that issue was a {@code pm-mcp enter}: a request of the generation is then refused as
     *                {@code generation_superseded} at the transport, else as {@code generation_fenced}
     * @since 0.1
     */
    public record Fence(Instant at, boolean byEnter) {

        /** @throws NullPointerException if the instant is {@code null} */
        public Fence {
            Objects.requireNonNull(at, "at");
        }
    }

    /**
     * The scope a session entered.
     *
     * @param type the type of the scope
     * @param id   the plan or epic id; {@code null} for the workspace scope
     * @since 0.1
     */
    public record Scope(Type type, String id) {

        /**
         * @throws NullPointerException     if the type is {@code null}
         * @throws IllegalArgumentException if the id is missing for a plan or an epic, present for the workspace, or
         *                                  not a valid identifier
         */
        public Scope {
            Objects.requireNonNull(type, "type");
            if (type.identified() != (id != null)) {
                throw new IllegalArgumentException("A %s scope %s an id: %s".formatted(type.wireName(),
                        type.identified() ? "requires" : "has no", id));
            }
            if (id != null) {
                requireIdentifier(id, "scope id");
            }
        }

        /**
         * The type of a scope a session enters. The set is closed.
         *
         * @since 0.1
         */
        public enum Type {
            /** A plan, named by its plan id. */
            PLAN("plan", true),
            /** An epic, named by its epic id. */
            EPIC("epic", true),
            /** The one workspace scope of the project. */
            WORKSPACE("workspace", false);

            private final String wireName;
            private final boolean identified;

            Type(String wireName, boolean identified) {
                this.wireName = wireName;
                this.identified = identified;
            }

            /** @return the name of the value in the record */
            public String wireName() {
                return wireName;
            }

            /** @return whether a scope of this type is named by an id */
            public boolean identified() {
                return identified;
            }

            /**
             * @param wireName the name of a value in the record
             * @return the value of that name; empty if no value has it
             */
            public static Optional<Type> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }

    /**
     * The working directory a session entered from, in the shape of the job record's {@code worker.workdir}.
     *
     * @param kind   whether it is a plan worktree or the project checkout
     * @param planId the plan of a plan worktree; {@code null} for the project checkout
     * @since 0.1
     */
    public record Workdir(Kind kind, String planId) {

        /**
         * @throws NullPointerException     if the kind is {@code null}
         * @throws IllegalArgumentException if the plan id is missing for a plan worktree, present for the project
         *                                  checkout, or not a valid identifier
         */
        public Workdir {
            Objects.requireNonNull(kind, "kind");
            if ((kind == Kind.PLAN_WORKTREE) != (planId != null)) {
                throw new IllegalArgumentException("A working directory of kind %s %s a plan id: %s".formatted(
                        kind.wireName(), kind == Kind.PLAN_WORKTREE ? "requires" : "has no", planId));
            }
            if (planId != null) {
                requireIdentifier(planId, "planId");
            }
        }

        /**
         * The kind of a working directory. The set is closed.
         *
         * @since 0.1
         */
        public enum Kind {
            /** The worktree of a plan. */
            PLAN_WORKTREE("plan_worktree"),
            /** The checkout of the project. */
            PROJECT_CHECKOUT("project_checkout");

            private final String wireName;

            Kind(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in the record */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in the record
             * @return the value of that name; empty if no value has it
             */
            public static Optional<Kind> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }

    /**
     * One skill delivered to the generation, by an offer or a {@code pm_skill} load.
     *
     * @param uri         the skill, {@code skill://pm/...}
     * @param digest      the SHA-256 of the delivered content, 64 lower-case hexadecimal digits
     * @param deliveredAt the instant of the delivery
     * @since 0.1
     */
    public record DeliveredSkill(String uri, String digest, Instant deliveredAt) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the uri is not a skill of PM-MCP or the digest is not a SHA-256
         */
        public DeliveredSkill {
            Objects.requireNonNull(uri, "uri");
            if (!uri.startsWith(SKILL_URI_PREFIX)) {
                throw new IllegalArgumentException("Not a skill uri (skill://pm/...): " + uri);
            }
            requireSha256(Objects.requireNonNull(digest, "digest"), "digest");
            Objects.requireNonNull(deliveredAt, "deliveredAt");
        }
    }
}
