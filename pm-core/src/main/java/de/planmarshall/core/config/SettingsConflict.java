/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.io.Serial;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A change of a recorded setting refused because the document changed since the caller read it (CLI &amp; Security §
 * Concurrent Changes of a Setting, Store Schemas § Machine Configuration, Operator Files, Credentials &amp; Security
 * Audit Stores): every change names the {@code revision} it was made against, the first change wins, and a later one
 * against an older revision is refused with {@code conflict} ({@code 409}) and the current value, which the operator may
 * change again on top of it.
 * <p>
 * Neither value is merged into the document and nothing is overwritten unseen. The refusal carries the current revision
 * and the current value of the setting, so a client can show what the change collided with and offer the act again.
 * <p>
 * The current value is typed by the store that refused: the raw document value of the setting that was written, as the
 * store reads it (for the machine configuration's provider defaults the runtime's {@code ProviderDefaults}, for the
 * consent store its list of {@link Consent} entries).
 *
 * @since 0.1
 */
public final class SettingsConflict extends RuntimeException {

    /** The error code of the local API. */
    public static final String ERROR_CODE = "conflict";

    /** The HTTP status of the error code. */
    public static final int HTTP_STATUS = 409;

    @Serial
    private static final long serialVersionUID = 1L;

    /** The refused document; a path is not serializable, so it is not kept across serialization. */
    private final transient Path file;

    private final long expectedRevision;

    private final long currentRevision;

    /** The current value of the setting; not serializable, so it is not kept across serialization. */
    private final transient Object currentValue;

    /**
     * @param file            the refused document
     * @param expectedRevision the revision the change named
     * @param currentRevision  the revision the document holds
     * @param currentValue     the current value of the setting
     */
    public SettingsConflict(Path file, long expectedRevision, long currentRevision, Object currentValue) {
        super(message(file, expectedRevision, currentRevision));
        this.file = file;
        this.expectedRevision = expectedRevision;
        this.currentRevision = currentRevision;
        this.currentValue = Objects.requireNonNull(currentValue, "currentValue");
    }

    /** Validates the revision pair and renders the refusal's message before the constructor's call to {@code super}. */
    private static String message(Path file, long expectedRevision, long currentRevision) {
        Objects.requireNonNull(file, "file");
        if (expectedRevision == currentRevision) {
            throw new IllegalArgumentException("A conflict names another revision than the current one: "
                    + expectedRevision);
        }
        return "%s: %s: the write names revision %d, the document holds %d".formatted(file, ERROR_CODE,
                expectedRevision, currentRevision);
    }

    /** @return the error code of the local API, {@value #ERROR_CODE} */
    public String code() {
        return ERROR_CODE;
    }

    /** @return the HTTP status of the error code, {@value #HTTP_STATUS} */
    public int status() {
        return HTTP_STATUS;
    }

    /** @return the refused document; {@code null} only after the refusal was deserialized */
    public Path file() {
        return file;
    }

    /** @return the revision the change named */
    public long expectedRevision() {
        return expectedRevision;
    }

    /** @return the revision the document holds */
    public long currentRevision() {
        return currentRevision;
    }

    /** @return the current value of the setting, typed by the store that refused */
    public Object currentValue() {
        return currentValue;
    }
}
