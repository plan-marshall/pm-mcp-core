/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.lock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.ERROR;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.LockLevel;
import de.planmarshall.core.store.LockOrder;
import de.planmarshall.runtime.start.PosixModes;

/**
 * The one lock manager of the runtime (PM-IMPL-7). Every transaction lock of the runtime is acquired through one
 * instance of it; a second instance in the same process, or a lock taken on a lock file past it, breaks the rules
 * below.
 * <p>
 * A lock of the operating system excludes other processes but not the threads of its own process: a second lock on
 * the same file from the same process fails with {@link OverlappingFileLockException}, and closing any channel on a
 * file releases every lock the process holds on it. The manager therefore keeps two things:
 * <ul>
 * <li>One {@link ReentrantLock} per lock key. The threads of the runtime wait on it, so the operating system is
 * asked for a lock by one of them only.</li>
 * <li>At most one open channel and one lock of the operating system per lock file, counted by its holders: the first
 * holder opens the channel and takes the lock, the last one closes it, and it is never closed while a holder
 * remains.</li>
 * </ul>
 * <p>
 * The order assertion and the acquisition are the one call {@link LockTransaction#acquire(LockKey)}. A key that
 * must not be acquired after the keys the transaction holds is refused: the refusal is logged, a
 * {@code lock_order_violation} record is handed to the audit sink, nothing is acquired and no lock file is created,
 * and the call fails with {@link InternalFaultException}.
 * <p>
 * A lock file below a {@code locks/} directory is created with mode {@code 0600} and its directories with mode
 * {@code 0700}. A leaf key names an append-only file of a store, which is locked itself: the manager creates that
 * file where it is absent, but never its directory, which belongs to the store. Opening an existing lock file
 * changes neither its content nor its modification time.
 * <p>
 * The locks of a thread are held per transaction. A thread that opens a second transaction while the first holds a
 * key may acquire that key in the second one as well; the order is asserted within each transaction only.
 *
 * @since 0.1
 */
public final class FileLockManager implements LockManager {

    private static final CuiLogger LOGGER = new CuiLogger(FileLockManager.class);

    private final AuditSink auditSink;

    /** One entry per key ever acquired; an entry is small and is kept for the lifetime of the runtime. */
    private final ConcurrentMap<LockKey, ReentrantLock> threadLocks = new ConcurrentHashMap<>();

    private final ConcurrentMap<Path, OsLock> osLocks = new ConcurrentHashMap<>();

    /**
     * @param auditSink where the record of a refused acquisition is handed over
     */
    public FileLockManager(AuditSink auditSink) {
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    @Override
    public LockTransaction openTransaction() {
        return new Transaction();
    }

    private void lock(LockKey key) {
        var threadLock = threadLocks.computeIfAbsent(key, _ -> new ReentrantLock());
        threadLock.lock();
        var taken = false;
        try {
            osLocks.computeIfAbsent(key.lockFile(), _ -> new OsLock()).retain(key);
            taken = true;
        } finally {
            if (!taken) {
                threadLock.unlock();
            }
        }
    }

    private void unlock(LockKey key) {
        try {
            osLocks.get(key.lockFile()).release(key.lockFile());
        } finally {
            threadLocks.get(key).unlock();
        }
    }

    private InternalFaultException refuse(LockOrder.Violation violation) {
        var event = AuditEvent.lockOrderViolation(violation.held(), violation.requested());
        LOGGER.error(ERROR.LOCK_ORDER_VIOLATION, event.details().get(AuditEvent.DETAIL_REQUESTED),
                event.details().get(AuditEvent.DETAIL_HELD));
        auditSink.append(event);
        return InternalFaultException.lockOrderViolation(violation.held(), violation.requested());
    }

    /** The locks of one transaction, in the order they were acquired, which is the lock order. */
    private final class Transaction implements LockTransaction {

        private final List<LockKey> held = new ArrayList<>();
        private boolean closed;

        @Override
        public void acquire(LockKey key) {
            if (closed) {
                throw new IllegalStateException("The transaction is closed");
            }
            switch (LockOrder.check(held, key)) {
                case LockOrder.Reentry _ -> {
                    // held already: one acquisition, one release
                }
                case LockOrder.Violation violation -> throw refuse(violation);
                case LockOrder.Permitted _ -> {
                    lock(key);
                    held.add(key);
                }
            }
        }

        @Override
        public boolean holds(LockKey key) {
            return held.contains(key);
        }

        @Override
        public void close() {
            closed = true;
            UncheckedIOException failure = null;
            while (!held.isEmpty()) {
                try {
                    unlock(held.removeLast());
                } catch (UncheckedIOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** The one channel and the one lock of the operating system on a lock file, counted by its holders. */
    private static final class OsLock {

        private final ReentrantLock guard = new ReentrantLock();
        private FileChannel channel;
        private int holders;

        void retain(LockKey key) {
            guard.lock();
            try {
                if (holders == 0) {
                    channel = openAndLock(key);
                }
                holders++;
            } finally {
                guard.unlock();
            }
        }

        void release(Path lockFile) {
            guard.lock();
            try {
                holders--;
                if (holders == 0) {
                    var closing = channel;
                    channel = null;
                    closing.close();
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Lock cannot be released: " + lockFile, e);
            } finally {
                guard.unlock();
            }
        }

        private static FileChannel openAndLock(LockKey key) {
            var lockFile = key.lockFile();
            try {
                if (key.level() != LockLevel.LEAF) {
                    Files.createDirectories(lockFile.getParent(), PosixModes.directoryAttribute());
                }
                var opened = open(lockFile);
                try {
                    opened.lock();
                    return opened;
                } catch (IOException e) {
                    closeAfterFailure(opened, e);
                    throw e;
                } catch (OverlappingFileLockException e) {
                    closeAfterFailure(opened, e);
                    throw new IllegalStateException(
                            "This process holds a lock on '%s' that was not taken through the lock manager"
                                    .formatted(lockFile), e);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Lock cannot be acquired: " + lockFile, e);
            }
        }

        // Opening an existing lock file must change neither its content nor its modification time.
        private static FileChannel open(Path lockFile) throws IOException {
            if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) {
                return FileChannel.open(lockFile, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            }
            return FileChannel.open(lockFile, Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                    PosixModes.fileAttribute());
        }

        private static void closeAfterFailure(FileChannel opened, Exception failure) {
            try {
                opened.close();
            } catch (IOException suppressed) {
                failure.addSuppressed(suppressed);
            }
        }
    }
}
