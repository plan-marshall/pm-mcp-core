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

import java.time.Instant;
import java.util.Objects;

/**
 * The identity record {@code store.json} of a per-project machine store {@code projects/<project_id>/} (PM-ARCH-1,
 * PM-IMPL-2): the main checkout the store belongs to, the project's name for the operator, and its unenrolment.
 * <p>
 * The record has a closed schema in {@code format_version} 1 ({@link ProjectStoreIdentityCodec}). The project id and
 * the project root are validated as an enrolment validates them ({@link ProjectEnrolment}), and they never change
 * after the store was created ({@link ProjectStore}).
 *
 * @param projectId    the project, by the identifier grammar; equals the name of the store directory
 * @param projectRoot  the canonical absolute path of the main checkout at the enrolment that created the store
 * @param displayName  the project's name for the operator, 1 to {@value #MAX_DISPLAY_NAME_LENGTH} characters; display
 *                     only, never an identifier
 * @param createdAt    when the store was created
 * @param unenrolledAt when the project was unenrolled; {@code null} while it is enrolled
 * @since 0.1
 */
public record ProjectStoreIdentity(String projectId, String projectRoot, String displayName, Instant createdAt,
Instant unenrolledAt) {

    /** The format version of a store identity that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The longest display name, in characters (code points). */
    public static final int MAX_DISPLAY_NAME_LENGTH = 128;

    /**
     * @throws NullPointerException     if a component other than the unenrolment is {@code null}
     * @throws IllegalArgumentException if the project id breaks the identifier grammar, the project root is not a
     *                                  normalized absolute path, or the display name is empty or too long
     */
    public ProjectStoreIdentity {
        LockKey.identifier(projectId, "project_id");
        ProjectEnrolment.requireAbsolutePath(projectRoot, "project_root");
        Objects.requireNonNull(displayName, "display_name");
        var length = displayName.codePointCount(0, displayName.length());
        if (length == 0 || length > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("The display name of a project has 1 to %d characters, not %d"
                    .formatted(MAX_DISPLAY_NAME_LENGTH, length));
        }
        Objects.requireNonNull(createdAt, "created_at");
    }
}
