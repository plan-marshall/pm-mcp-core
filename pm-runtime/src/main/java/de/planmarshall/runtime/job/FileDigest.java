/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import lombok.experimental.UtilityClass;

/**
 * The SHA-256 of a file as the pipeline checks it against its enrolled digest (PM-EXEC-1, PM-EXEC-2): the file is
 * read without following a symbolic link, a missing file is {@link Absent}, and a symbolic link or anything but a
 * regular file is {@link NotRegular}, which never matches a digest.
 *
 * @since 0.1
 */
@UtilityClass
class FileDigest {

    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * What was found at a path.
     *
     * @since 0.1
     */
    sealed interface Result permits Regular, Absent, NotRegular {

        /**
         * @param sha256 an enrolled digest, lowercase hex
         * @return whether a regular file with exactly this digest was found
         */
        default boolean matches(String sha256) {
            return this instanceof Regular(var digest) && digest.equals(sha256);
        }
    }

    /**
     * A regular file.
     *
     * @param sha256 its SHA-256, lowercase hex
     */
    record Regular(String sha256) implements Result {

        Regular {
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    /** Nothing exists at the path. */
    record Absent() implements Result {
    }

    /** A symbolic link, a directory or another file that is not regular exists at the path, or a part of it is one. */
    record NotRegular() implements Result {
    }

    /**
     * Digests a file by its absolute path; the last name of the path is not followed if it is a symbolic link.
     *
     * @param file the file
     * @return what was found
     * @throws UncheckedIOException if the file exists and cannot be read
     */
    static Result of(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                return new NotRegular();
            }
            try (var channel = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                return new Regular(digest(channel));
            }
        } catch (NoSuchFileException | NotDirectoryException _) {
            return new Absent();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Digests a file of a tree by its path relative to the root of the tree. No name of the path is followed if it is
     * a symbolic link, so the file read lies inside the tree; a path that is absolute, that is empty or that names
     * {@code .} or {@code ..} is {@link NotRegular}.
     *
     * @param root         the canonical root of the tree
     * @param relativePath the path of the file relative to the root
     * @return what was found
     * @throws UncheckedIOException if a part of the path exists and cannot be read
     */
    static Result of(Path root, String relativePath) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(relativePath, "relativePath");
        Path relative;
        try {
            relative = Path.of(relativePath);
        } catch (InvalidPathException _) {
            return new NotRegular();
        }
        if (relativePath.isEmpty() || relative.isAbsolute()) {
            return new NotRegular();
        }
        var current = root;
        for (var index = 0; index < relative.getNameCount(); index++) {
            var name = relative.getName(index).toString();
            if (".".equals(name) || "..".equals(name)) {
                return new NotRegular();
            }
            current = current.resolve(name);
            if (index < relative.getNameCount() - 1) {
                var notDirectory = notDirectory(current);
                if (notDirectory.isPresent()) {
                    return notDirectory.get();
                }
            }
        }
        return of(current);
    }

    /**
     * @return empty for a directory that is no symbolic link; {@link Absent} if nothing exists at the path,
     *         {@link NotRegular} for anything else
     */
    private static Optional<Result> notDirectory(Path path) {
        try {
            var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attributes.isDirectory() ? Optional.empty() : Optional.of(new NotRegular());
        } catch (NoSuchFileException | NotDirectoryException _) {
            return Optional.of(new Absent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String digest(SeekableByteChannel channel) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is available on every Java platform", e);
        }
        var buffer = ByteBuffer.allocate(BUFFER_SIZE);
        while (channel.read(buffer) >= 0) {
            buffer.flip();
            digest.update(buffer);
            buffer.clear();
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
