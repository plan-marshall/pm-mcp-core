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
import java.nio.file.Path;
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
 * A file that fails a check is refused with {@code enrolment_insecure}, which blocks only its project, never the
 * runtime start.
 *
 * @param user the name of the running user, the expected owner
 * @since 0.1
 */
public record EnrolmentFileCheck(String user) {

    /** The reason of a refused symbolic link. */
    static final String SYMBOLIC_LINK = "symbolic link";

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
            if (Files.isSymbolicLink(file)) {
                return Optional.of(SYMBOLIC_LINK);
            }
            var owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS).getName();
            if (!owner.equals(user)) {
                return Optional.of("foreign owner %s, not %s".formatted(owner, user));
            }
            var permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
            var outside = new HashSet<>(permissions);
            outside.removeAll(PosixModes.FILE);
            if (!outside.isEmpty()) {
                return Optional.of("mode %s broader than %s".formatted(PosixModes.octal(permissions),
                        PosixModes.octal(PosixModes.FILE)));
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Attributes of %s cannot be read".formatted(file), e);
        }
    }
}
