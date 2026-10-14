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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.HolderInstance;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.LeaseStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The provider lease stores of a test base, the merge queue and the review-bot windows, each holding one lease of a
 * live holder. A drain leaves them in place (PM-WATCH-RUN-6): {@link #assertUnchanged(Map)} compares them byte for
 * byte with what {@link #seed(Path)} wrote.
 */
final class ProviderLeaseStores {

    private static final Instant ACQUIRED_AT = Instant.parse("2026-10-11T08:00:00Z");

    private ProviderLeaseStores() {
    }

    /**
     * Claims one lease in each provider lease store of the base. The {@code state/} directory must exist.
     *
     * @param base the {@code PM_MCP_BASE}
     * @return the content of each store after the claim, by its path
     * @throws IOException if a store cannot be read back
     */
    static Map<Path, byte[]> seed(Path base) throws IOException {
        var manager = new FileLockManager(_ -> {
        });
        var self = ProcessHandle.current();
        var holder = new HolderInstance(self.pid(), self.info().startInstant().orElseThrow());
        var stores = new LinkedHashMap<Path, byte[]>();
        for (var store : Map.of("merge-queue.json", LockKey.mergeQueue(base), "review-bot-windows.json",
                LockKey.reviewBotWindows(base)).entrySet()) {
            var file = base.resolve("state").resolve(store.getKey());
            var leaseStore = new LeaseStore(new AtomicStoreFile(file, store.getValue()), manager);
            var owner = new LeaseOwner("pm-mcp-core", ScopeType.PLAN, "a-plan", holder, null);
            assertTrue(leaseStore.claim(new LeaseRecord("repo:main", owner, ACQUIRED_AT, null)).granted(),
                    store.getKey());
            stores.put(file, Files.readAllBytes(file));
        }
        return stores;
    }

    /**
     * @param seeded what {@link #seed(Path)} returned
     * @throws IOException if a store cannot be read
     */
    static void assertUnchanged(Map<Path, byte[]> seeded) throws IOException {
        for (var store : seeded.entrySet()) {
            assertArrayEquals(store.getValue(), Files.readAllBytes(store.getKey()), store.getKey().toString());
        }
    }
}
