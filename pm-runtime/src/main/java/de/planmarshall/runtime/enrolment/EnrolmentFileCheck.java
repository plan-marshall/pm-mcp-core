/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.start.PosixModes;

/**
 * The per-use checks of an enrolment file and its sidecar (PM-SEC-4): the file is owned by the running user, has no
 * permission bit outside {@code 0600} and is not a symbolic link. A narrower mode such as {@code 0400} passes; a
 * broader one such as {@code 0644} is refused. Every check reads the file's own attributes and never follows a link.
 * <p>
 * The runtime binds the checks and the read to one no-follow open ({@code snapshot(Path)}): the attributes are read
 * without following a link, the file is opened without following a link and read from that open, and the attributes
 * are read again and compared with the first ones (file key, size, last-modified time); the per-use checks apply to
 * both reads. The bytes of a symbolic link's target, of a file that fails a check or of a file that changed while it
 * was read are never handed out. The JDK has no {@code fstat} on an open handle, so the identity of the opened file is
 * inferred from the attributes read before the open and after the read: this narrows the window between check and
 * read, it does not make it zero, and the mode of the operator-owned enrolments directory stays the outer boundary.
 * <p>
 * A file that fails a check is refused with {@code enrolment_insecure}, which blocks only its project, never the
 * runtime start.
 *
 * @param user the name of the running user, the expected owner
 * @since 0.1
 */
public record EnrolmentFileCheck(String user) {

    /** The reason of a refused symbolic link. */
    static final String SYMBOLIC_LINK = "symbolic link";

    /** The reason of a file that was replaced or changed between the checks and the end of its read. */
    static final String CHANGED_WHILE_READ = "file replaced or changed while it was read";

    private static final String ATTRIBUTES_UNREADABLE = "Attributes of %s cannot be read";

    /**
     * @throws NullPointerException if the user is {@code null}
     */
    public EnrolmentFileCheck {
        Objects.requireNonNull(user, "user");
    }

    /**
     * @param file an existing enrolment file or sidecar
     * @throws StoreRefusal         as {@code enrolment_insecure}, naming the file and the reason, if a check fails
     * @throws UncheckedIOException if the attributes of the file cannot be read
     */
    public void check(Path file) {
        var violation = violation(file);
        if (violation.isPresent()) {
            throw StoreRefusal.enrolmentInsecure(file, violation.get());
        }
    }

    /**
     * @param file an existing file
     * @return why the file fails a per-use check: a symbolic link, a foreign owner, a mode broader than {@code 0600};
     *         empty if it passes
     * @throws UncheckedIOException if the attributes of the file cannot be read
     */
    Optional<String> violation(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            return violation(attributes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(ATTRIBUTES_UNREADABLE.formatted(file), e);
        }
    }

    /**
     * Reads the file once, bound to the per-use checks of the file that is opened.
     *
     * @param file an enrolment file or sidecar, which need not exist
     * @return the snapshot: absent, refused with the reason, or read with the bytes
     * @throws UncheckedIOException if the attributes cannot be read, or the file cannot be read for another reason
     *                              than a symbolic link or its removal
     */
    EnrolmentSnapshot snapshot(Path file) {
        return snapshot(file, () -> {
        });
    }

    /**
     * The seam of {@link #snapshot(Path)}: {@code afterRead} runs between the read of the bytes and the second read of
     * the attributes.
     */
    EnrolmentSnapshot snapshot(Path file, Runnable afterRead) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(afterRead, "afterRead");
        var before = attributesIfPresent(file);
        if (before.isEmpty()) {
            return EnrolmentSnapshot.absent();
        }
        var early = violation(before.get());
        if (early.isPresent()) {
            return EnrolmentSnapshot.refused(early.get());
        }
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readAllBytes();
        } catch (IOException e) {
            return refusedOnRead(file, e);
        }
        afterRead.run();
        var after = attributesIfPresent(file);
        if (after.isEmpty() || !unchanged(before.get(), after.get(), bytes.length)) {
            return EnrolmentSnapshot.refused(CHANGED_WHILE_READ);
        }
        var late = violation(after.get());
        return late.map(EnrolmentSnapshot::refused).orElseGet(() -> EnrolmentSnapshot.read(bytes));
    }

    private static EnrolmentSnapshot refusedOnRead(Path file, IOException failure) {
        var now = attributesIfPresent(file);
        if (now.isEmpty()) {
            return EnrolmentSnapshot.refused(CHANGED_WHILE_READ);
        }
        if (now.get().isSymbolicLink()) {
            return EnrolmentSnapshot.refused(SYMBOLIC_LINK);
        }
        throw new UncheckedIOException("Store cannot be read: " + file, failure);
    }

    private static boolean unchanged(PosixFileAttributes before, PosixFileAttributes after, int length) {
        return Objects.equals(before.fileKey(), after.fileKey()) && after.size() == length
                && before.lastModifiedTime().equals(after.lastModifiedTime());
    }

    private static Optional<PosixFileAttributes> attributesIfPresent(Path file) {
        try {
            return Optional.of(attributes(file));
        } catch (NoSuchFileException _) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(ATTRIBUTES_UNREADABLE.formatted(file), e);
        }
    }

    private static PosixFileAttributes attributes(Path file) throws IOException {
        return Files.readAttributes(file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private Optional<String> violation(PosixFileAttributes attributes) {
        if (attributes.isSymbolicLink()) {
            return Optional.of(SYMBOLIC_LINK);
        }
        var owner = attributes.owner().getName();
        if (!owner.equals(user)) {
            return Optional.of("foreign owner %s, not %s".formatted(owner, user));
        }
        var permissions = attributes.permissions();
        var outside = new HashSet<>(permissions);
        outside.removeAll(PosixModes.FILE);
        if (!outside.isEmpty()) {
            return Optional.of("mode %s broader than %s".formatted(PosixModes.octal(permissions),
                    PosixModes.octal(PosixModes.FILE)));
        }
        return Optional.empty();
    }
}
