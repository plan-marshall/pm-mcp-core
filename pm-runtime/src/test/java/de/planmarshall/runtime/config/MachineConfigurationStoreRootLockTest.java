/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;

/**
 * The machine configuration store refuses a lock whose file lies directly below the file-system root: such a lock has
 * no base above its lock directory, so the store cannot name the document it guards, and the refusal is an
 * {@link IllegalArgumentException} with its reason instead of a {@link NullPointerException} (runtime-config developer
 * guide).
 * <p>
 * The lock level, the lock id {@code machine-config} and the file names are the store's contract; the root is the
 * file-system root of the machine running the test.
 */
@DisplayName("Machine configuration store: a root-level lock key")
class MachineConfigurationStoreRootLockTest {

    /** The name of the lock file of the machine configuration (contract: the lock id plus {@code .lock}). */
    private static final String LOCK_FILE = "machine-config.lock";

    @Test
    @DisplayName("a lock file without a grandparent is refused with IllegalArgumentException naming the reason")
    void rootLevelLockKeyRefused() {
        var root = Path.of("").toAbsolutePath().getRoot();
        var lockFile = root.resolve(LOCK_FILE);
        var key = new LockKey(LockLevel.MACHINE_STORE, lockFile, "machine-config");
        var storeFile = new AtomicStoreFile(root.resolve(MachineConfigurationStore.FILE_NAME), key);
        LockManager locks = createMock(LockManager.class);
        replay(locks);

        var refusal = assertThrows(IllegalArgumentException.class,
                () -> new MachineConfigurationStore(storeFile, locks),
                "a root-level lock key must be refused");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains("no base above its lock directory"),
                        "the refusal names the missing base"),
                () -> assertTrue(refusal.getMessage().contains(lockFile.toString()),
                        "the refusal names the lock file"),
                () -> assertEquals(IllegalArgumentException.class, refusal.getClass(),
                        "the refusal is no NullPointerException"));
        verify(locks);
    }
}
