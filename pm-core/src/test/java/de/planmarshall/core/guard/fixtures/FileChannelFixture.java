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

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.spi.FileSystemProvider;

/**
 * Control of the analysis boundary guard: a class writing through {@link Files#newByteChannel}, an
 * {@link AsynchronousFileChannel} and a {@link FileSystemProvider}, on which the rule must fail for each.
 */
public final class FileChannelFixture {

    private FileChannelFixture() {
    }

    /**
     * @param file     the file to write
     * @param provider the provider of the file system the file is deleted through
     * @throws IOException if the file cannot be opened or deleted
     */
    public static void write(Path file, FileSystemProvider provider) throws IOException {
        try (var channel = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
            channel.truncate(0L);
        }
        try (var channel = AsynchronousFileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.truncate(0L);
        }
        provider.delete(file);
    }
}
