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

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One no-follow snapshot of an enrolment file or sidecar ({@link EnrolmentFileCheck#snapshot}), in one of three
 * states: absent (the file does not exist), refused (a per-use check failed, or the file changed while it was read;
 * its bytes are not handed out) or read (the checks passed and the bytes are the ones of the checked file).
 *
 * @since 0.1
 */
final class EnrolmentSnapshot {

    private static final EnrolmentSnapshot ABSENT = new EnrolmentSnapshot(null, null);

    private final byte @Nullable [] content;
    private final @Nullable String violation;

    private EnrolmentSnapshot(byte @Nullable [] content, @Nullable String violation) {
        this.content = content;
        this.violation = violation;
    }

    /** @return the snapshot of a file that does not exist */
    static EnrolmentSnapshot absent() {
        return ABSENT;
    }

    /**
     * @param reason why the file is refused
     * @return the snapshot of a refused file, without its bytes
     */
    static EnrolmentSnapshot refused(String reason) {
        return new EnrolmentSnapshot(null, Objects.requireNonNull(reason, "reason"));
    }

    /**
     * @param bytes the bytes read from the checked file
     * @return the snapshot of a file that passed the checks
     */
    static EnrolmentSnapshot read(byte[] bytes) {
        return new EnrolmentSnapshot(Objects.requireNonNull(bytes, "bytes").clone(), null);
    }

    /** @return why the file is refused; empty if it is absent or was read */
    Optional<String> violation() {
        return Optional.ofNullable(violation);
    }

    /** @return a copy of the bytes read; empty if the file is absent or refused */
    Optional<byte[]> content() {
        return content == null ? Optional.empty() : Optional.of(content.clone());
    }

    /** @return whether the file does not exist */
    boolean isAbsent() {
        return content == null && violation == null;
    }
}
