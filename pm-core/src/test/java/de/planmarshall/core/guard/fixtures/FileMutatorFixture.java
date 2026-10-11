/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.guard.fixtures;

import java.io.File;
import java.io.IOException;

/**
 * Control of the analysis boundary guard: a class calling each mutating method of {@link File}, on which the rule
 * must fail once per method.
 */
public final class FileMutatorFixture {

    private FileMutatorFixture() {
    }

    /**
     * @param file   the file to change
     * @param target the name the file is renamed to
     * @return whether the last change succeeded
     * @throws IOException if the file cannot be created
     */
    public static boolean mutate(File file, File target) throws IOException {
        file.deleteOnExit();
        var changed = file.createNewFile() && file.mkdir() && file.mkdirs() && file.setWritable(true)
                && file.setReadOnly() && file.setLastModified(0L) && file.setExecutable(false);
        return changed && file.renameTo(target) && target.delete();
    }
}
