/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Objects;

/**
 * A document that is not one JSON value, or that names a member of an object twice, as {@link JsonValue#parse(byte[])}
 * refuses it.
 *
 * @since 0.1
 */
public final class MalformedJson extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param detail what is malformed
     * @param cause  the failure that found it; {@code null} if there is none
     */
    public MalformedJson(String detail, @Nullable Throwable cause) {
        super(Objects.requireNonNull(detail, "detail"), cause);
    }
}
