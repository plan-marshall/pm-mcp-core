/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Objects;

/**
 * A role artifact or role assignment refused as a whole (PM-WORK-1). The refusal names the role, the closed reason of the
 * validation table, and the JSON Pointer of the value that broke a rule where a pointer exists.
 *
 * @since 0.1
 */
public final class RoleRefusal extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final RoleValidationReason reason;
    private final String role;
    private final String pointer;
    private final String detail;

    private RoleRefusal(RoleValidationReason reason, String role, @Nullable String pointer,
            @Nullable String detail) {
        super(message(reason, role, pointer, detail));
        this.reason = Objects.requireNonNull(reason, "reason");
        this.role = Objects.requireNonNull(role, "role");
        this.pointer = pointer == null ? "" : pointer;
        this.detail = detail == null ? "" : detail;
    }

    /**
     * @param role    the role name, as the file is named
     * @param pointer the JSON Pointer of the value that broke a rule
     * @param detail  what is wrong with it
     * @return a refusal of the schema
     */
    public static RoleRefusal schemaInvalid(String role, String pointer, String detail) {
        return new RoleRefusal(RoleValidationReason.SCHEMA_INVALID, role, pointer, detail);
    }

    /**
     * @param reason  the reason of the validation table
     * @param role    the role name
     * @param pointer the JSON Pointer of the value that broke a rule; empty for a refusal of the role as a whole
     * @param detail  what is wrong with it
     * @return the refusal
     */
    public static RoleRefusal invalid(RoleValidationReason reason, String role, String pointer, String detail) {
        return new RoleRefusal(reason, role, pointer, detail);
    }

    /** @return the reason of the validation table */
    public RoleValidationReason reason() {
        return reason;
    }

    /** @return the refused role */
    public String role() {
        return role;
    }

    /** @return the JSON Pointer of the value that broke a rule; empty for a refusal of the role as a whole */
    public String pointer() {
        return pointer;
    }

    /** @return what is wrong, naming the value */
    public String detail() {
        return detail;
    }

    private static String message(RoleValidationReason reason, String role, @Nullable String pointer,
            @Nullable String detail) {
        var where = pointer == null || pointer.isEmpty() ? "" : " at '%s'".formatted(pointer);
        return "%s: %s%s: %s".formatted(role, reason.wireName(), where, detail == null ? "" : detail);
    }
}
