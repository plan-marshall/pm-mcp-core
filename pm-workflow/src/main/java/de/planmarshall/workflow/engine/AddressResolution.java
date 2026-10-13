/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.util.Objects;
import java.util.Optional;

/**
 * What resolving an {@link EntityAddress} yields (PM-ARCH-4, PM-WF-1): the entity it names with its entity kind, or a
 * refusal with a closed reason. A refusal is a value, never an exception, and an address is never resolved on a record
 * that could not be read.
 *
 * @since 0.1
 */
public sealed interface AddressResolution permits AddressResolution.Resolved, AddressResolution.Refused {

    /** The entity kind of a pending plan bootstrap. */
    String BOOTSTRAP_PLAN = "bootstrap-plan";

    /** The entity kind of a pending epic bootstrap. */
    String BOOTSTRAP_EPIC = "bootstrap-epic";

    /**
     * The address names an entity of its scope.
     *
     * @param address     the resolved address
     * @param entityKind  {@value #BOOTSTRAP_PLAN} or {@value #BOOTSTRAP_EPIC} for a pending bootstrap, else the
     *                    registered entity kind of the task state that hosts the task
     * @param queueTaskId the queue task of a task entity; empty for a pending bootstrap
     * @since 0.1
     */
    record Resolved(EntityAddress address, String entityKind, Optional<String> queueTaskId)
            implements AddressResolution {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the entity kind is blank
         */
        public Resolved {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(entityKind, "entityKind");
            Objects.requireNonNull(queueTaskId, "queueTaskId");
            if (entityKind.isBlank()) {
                throw new IllegalArgumentException("entityKind must not be blank");
            }
        }
    }

    /**
     * The address names nothing that can be resolved.
     *
     * @param reason the closed reason
     * @param detail what was refused, for the caller's report
     * @since 0.1
     */
    record Refused(Reason reason, String detail) implements AddressResolution {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the detail is blank
         */
        public Refused {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
            if (detail.isBlank()) {
                throw new IllegalArgumentException("detail must not be blank");
            }
        }
    }

    /**
     * The closed reasons of a refusal.
     *
     * @since 0.1
     */
    enum Reason {

        /** The entity identifier violates its grammar. */
        INVALID_ARGUMENTS("invalid_arguments"),

        /**
         * The identifier is valid and names nothing in the addressed scope: an entity of another scope, a bootstrap
         * entry that ended or was swept, or a task the scope does not hold.
         */
        SCOPE_NOT_FOUND("scope_not_found"),

        /** The pending entry record the identifier names exists and could not be read. */
        ENTRY_UNREADABLE("entry_unreadable");

        private final String wireName;

        Reason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the reason in a tool result */
        public String wireName() {
            return wireName;
        }
    }
}
