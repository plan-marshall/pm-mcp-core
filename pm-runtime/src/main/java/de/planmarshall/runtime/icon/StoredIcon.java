/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.icon;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One stored harness icon: the file the store wrote and the index entry that records it (Store Schemas § Harness Icon
 * Store).
 *
 * @param file  the file {@code icons/<harness_id>.<ext>}
 * @param entry the index entry of the harness
 * @since 0.1
 */
public record StoredIcon(Path file, IconIndexEntry entry) {

    /** @throws NullPointerException if a component is {@code null} */
    public StoredIcon {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(entry, "entry");
    }
}
