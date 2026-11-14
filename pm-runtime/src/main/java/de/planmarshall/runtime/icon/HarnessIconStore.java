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

import de.cuioss.tools.logging.CuiLogger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import de.planmarshall.core.id.HarnessId;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.PosixModes;

/**
 * The machine's harness icon store, {@code <PM_MCP_BASE>/icons/} (Store Schemas § Harness Icon Store): one sanitised file
 * per harness, {@code <harness_id>.<ext>} with mode {@code 0600}, and the closed index {@code index.json} beside them.
 * <p>
 * The store takes a candidate's bytes from a caller and opens no connection: the outbound fetch of an icon a profile
 * declares by its {@code source} form is deferred (Later Improvements L-21). {@link #accept} runs the accept step of
 * {@link HarnessIconSanitiser} outside the lock and then, under {@code locks/icons.lock}, replaces the file and the index
 * entry in one transaction. The index is read before anything is written, and the file is written before the index: a stop
 * in between leaves the old index, which no longer matches the new file, so nothing is served until the index is written —
 * the fail-closed direction.
 * <p>
 * Every write is the store's one atomic write — a temporary file beside the store, synced, moved over it — and every read
 * verifies the file against its entry: a file whose length or digest differs from the entry, or one that is a symbolic
 * link, is not served. The harness id that names the file is a harness id before it reaches a path, and the path is the
 * icon directory's own.
 *
 * @since 0.1
 */
public final class HarnessIconStore {

    /** The name of the icon directory below the base. */
    public static final String DIRECTORY_NAME = "icons";

    /** The name of the index file below the icon directory. */
    public static final String INDEX_FILE = "index.json";

    private static final CuiLogger LOGGER = new CuiLogger(HarnessIconStore.class);

    private static final int READ_BUFFER = 8192;

    private final Path base;
    private final Path directory;
    private final LockManager lockManager;
    private final Clock clock;

    private HarnessIconStore(Path base, LockManager lockManager, Clock clock) {
        this.base = base;
        this.directory = base.resolve(DIRECTORY_NAME);
        this.lockManager = lockManager;
        this.clock = clock;
    }

    /**
     * @param base        the base directory of the runtime
     * @param lockManager the lock manager of the runtime
     * @return the store of the base's icons, with the system clock
     * @throws NullPointerException if a component is {@code null}
     */
    public static HarnessIconStore at(Path base, LockManager lockManager) {
        return at(base, lockManager, Clock.systemUTC());
    }

    /**
     * @param base        the base directory of the runtime
     * @param lockManager the lock manager of the runtime
     * @param clock       the clock the index records {@code fetched_at} with
     * @return the store of the base's icons
     * @throws NullPointerException if a component is {@code null}
     */
    public static HarnessIconStore at(Path base, LockManager lockManager, Clock clock) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(lockManager, "lockManager");
        Objects.requireNonNull(clock, "clock");
        return new HarnessIconStore(base.toAbsolutePath().normalize(), lockManager, clock);
    }

    /** @return the base directory of the store, absolute and normalized */
    public Path base() {
        return base;
    }

    /** @return the icon directory, absolute and normalized */
    public Path directory() {
        return directory;
    }

    /** @return the index file, absolute and normalized */
    public Path indexFile() {
        return directory.resolve(INDEX_FILE);
    }

    /** @return the key of the lock that guards the icons and their index */
    public LockKey lockKey() {
        return LockKey.icons(base);
    }

    /**
     * Sanitises a candidate and stores it as the harness's icon, replacing an icon of the same harness.
     *
     * @param harnessId the harness the icon belongs to
     * @param candidate the candidate icon's bytes
     * @return the stored icon, as the store now serves it
     * @throws IconRefusal          if the harness id is not a harness id, the path is not a plain file of the icon
     *                              directory, or the candidate is refused
     * @throws NullPointerException if a component is {@code null}
     * @throws StoreRefusal         if the index exists but does not read as the index; nothing was written then
     * @throws UncheckedIOException if the icon or the index cannot be written
     */
    public HarnessIcon accept(String harnessId, byte[] candidate) {
        var id = requireHarnessId(harnessId);
        var sanitised = sanitise(id, candidate);
        var target = fileFor(id, sanitised.format());
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(lockKey());
            var icons = new TreeMap<>(readIndex(transaction));
            createDirectory();
            var entry = new IconIndexEntry(null, sanitised.sha256(), sanitised.size(), sanitised.format().mediaType(),
                    clock.instant());
            new AtomicStoreFile(target, lockKey()).write(transaction, sanitised.bytes());
            icons.put(id, entry);
            new AtomicStoreFile(indexFile(), lockKey()).write(transaction, IconIndexCodec.write(icons));
            return new HarnessIcon(id, entry, sanitised.bytes());
        }
    }

    /**
     * Reads the harness's stored icon without a lock and verifies it against its index entry.
     *
     * @param harnessId the harness whose icon is asked for
     * @return the icon; empty where the machine stores none, or where the file differs from its entry
     * @throws IconRefusal          if the harness id is not a harness id, or the path is not a plain file of the icon
     *                              directory
     * @throws NullPointerException if the harness id is {@code null}
     * @throws StoreRefusal         if the index exists but does not read as the index
     * @throws UncheckedIOException if the index or the icon exists and cannot be read
     */
    public Optional<HarnessIcon> read(String harnessId) {
        var id = requireHarnessId(harnessId);
        var entry = index().get(id);
        if (entry == null) {
            return Optional.empty();
        }
        var format = IconFormat.ofMediaType(entry.mediaType()).orElse(null);
        if (format == null) {
            return Optional.empty();
        }
        var target = fileFor(id, format);
        if (!isPlainFile(target)) {
            return Optional.empty();
        }
        var bytes = readNoFollow(target);
        if (bytes.length != entry.size() || !SanitisedIcon.digest(bytes).equals(entry.sha256())) {
            return Optional.empty();
        }
        return Optional.of(new HarnessIcon(id, entry, bytes));
    }

    private SanitisedIcon sanitise(String harnessId, byte[] candidate) {
        try {
            return HarnessIconSanitiser.sanitise(candidate);
        } catch (IconRefusal e) {
            LOGGER.warn(WARN.ICON_REFUSED, harnessId, e.code(), candidate.length);
            throw e;
        }
    }

    private Map<String, IconIndexEntry> index() {
        return StoreSnapshot.read(indexFile()).content()
                .map(document -> IconIndexCodec.read(indexFile(), document))
                .orElse(Map.of());
    }

    private Map<String, IconIndexEntry> readIndex(LockTransaction transaction) {
        return new AtomicStoreFile(indexFile(), lockKey()).read(transaction)
                .map(document -> IconIndexCodec.read(indexFile(), document))
                .orElse(Map.of());
    }

    private static String requireHarnessId(String harnessId) {
        Objects.requireNonNull(harnessId, "harnessId");
        if (!HarnessId.matches(harnessId)) {
            throw new IconRefusal(IconRefusal.HARNESS_ID_INVALID, harnessId);
        }
        return harnessId;
    }

    private Path fileFor(String harnessId, IconFormat format) {
        var target = directory.resolve(harnessId + "." + format.extension()).normalize();
        if (!directory.equals(target.getParent())) {
            throw new IconRefusal(IconRefusal.PATH_REFUSED, target.toString());
        }
        if (Files.isSymbolicLink(target)) {
            throw new IconRefusal(IconRefusal.PATH_REFUSED, "a symbolic link at " + target.getFileName());
        }
        return target;
    }

    private void createDirectory() {
        if (Files.isDirectory(directory)) {
            return;
        }
        try {
            Files.createDirectories(directory, PosixModes.directoryAttribute());
        } catch (IOException e) {
            throw new UncheckedIOException("The icon directory cannot be created: " + directory, e);
        }
    }

    private static boolean isPlainFile(Path file) {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file);
    }

    private static byte[] readNoFollow(Path file) {
        try (var channel = FileChannel.open(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            var out = new ByteArrayOutputStream();
            var buffer = ByteBuffer.allocate(READ_BUFFER);
            while (true) {
                var read = channel.read(buffer);
                if (read < 0) {
                    break;
                }
                buffer.flip();
                out.write(buffer.array(), 0, buffer.limit());
                buffer.clear();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("The icon cannot be read: " + file, e);
        }
    }
}
