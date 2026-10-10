/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import java.io.Serial;

/**
 * A failure of the per-user service unit: its file could not be written or removed, or a command of the platform
 * service manager failed.
 *
 * @since 0.1
 */
public final class ServiceUnitException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param message the detail, naming the file or the command; never an environment value
     */
    public ServiceUnitException(String message) {
        super(message);
    }

    /**
     * @param message the detail, naming the file or the command; never an environment value
     * @param cause   the cause
     */
    public ServiceUnitException(String message, Throwable cause) {
        super(message, cause);
    }
}
