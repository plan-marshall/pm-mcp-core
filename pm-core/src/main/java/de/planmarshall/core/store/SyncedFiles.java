/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The file writes every store write is made of (PM-IMPL-7), each forced to disk before it returns: a new file, an
 * append, a cut, and the directory entry of a file.
 * <p>
 * A file these writes create is readable and writable by its owner only.
 *
 * @since 0.1
 */
@UtilityClass
class SyncedFiles {

    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    /**
     * Writes a file that does not exist yet and forces it to disk.
     *
     * @param file    the file to create
     * @param content its content
     * @throws IOException if the file exists or cannot be written
     */
    static void writeNew(Path file, byte[] content) throws IOException {
        try (var channel = FileChannel.open(file, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                     OWNER_ONLY)) {
            writeAll(channel, content);
            channel.force(true);
        }
    }

    /**
     * Appends to a file in one write on a channel opened for appending and forces it to disk. A file that does not
     * exist is created, and its directory is forced to disk as well.
     *
     * @param file    the file to append to
     * @param content the bytes to append
     * @throws IOException if the file cannot be written
     */
    static void append(Path file, byte[] content) throws IOException {
        var created = !Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        try (var channel = FileChannel.open(file,
                     Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND), OWNER_ONLY)) {
            writeAll(channel, content);
            channel.force(true);
        }
        if (created) {
            forceDirectory(file.getParent());
        }
    }

    /**
     * Cuts a file back to a size and forces it to disk.
     *
     * @param file the file
     * @param size the size to cut it back to; a file that is not longer is left as it is
     * @throws IOException if the file does not exist or cannot be written
     */
    static void truncate(Path file, long size) throws IOException {
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.truncate(size);
            channel.force(true);
        }
    }

    /**
     * Forces a directory to disk: a rename, a new and a deleted file are entries of the directory, on disk when the
     * directory is.
     *
     * @param directory the directory
     * @throws IOException if the directory cannot be opened or forced
     */
    static void forceDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void writeAll(FileChannel channel, byte[] content) throws IOException {
        var buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
