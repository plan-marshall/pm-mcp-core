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

import java.util.Objects;

/**
 * One stored harness icon as the store hands it out (Store Schemas § Harness Icon Store): the harness it belongs to, the
 * index entry that vouches for it, and the file's bytes.
 * <p>
 * The store builds it only after it has verified the file against its entry — same length, same digest — so a caller never
 * sees bytes the index does not stand for. The type is a class rather than a record because it carries an array: a record's
 * generated {@code equals} and {@code hashCode} would compare the array by identity.
 *
 * @since 0.1
 */
public final class HarnessIcon {

    private final String harnessId;
    private final IconIndexEntry entry;
    private final byte[] bytes;

    /**
     * @param harnessId the harness the icon belongs to
     * @param entry     the index entry the file was verified against
     * @param bytes     the file's bytes; kept as a copy
     * @throws NullPointerException if a component is {@code null}
     */
    HarnessIcon(String harnessId, IconIndexEntry entry, byte[] bytes) {
        this.harnessId = Objects.requireNonNull(harnessId, "harnessId");
        this.entry = Objects.requireNonNull(entry, "entry");
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
    }

    /** @return the harness the icon belongs to */
    public String harnessId() {
        return harnessId;
    }

    /** @return the index entry the file was verified against */
    public IconIndexEntry entry() {
        return entry;
    }

    /** @return the format of the icon, read from its entry's media type */
    public IconFormat format() {
        return IconFormat.ofMediaType(entry.mediaType()).orElseThrow(
                () -> new IllegalStateException("The entry's media type is outside the closed set: "
                        + entry.mediaType()));
    }

    /** @return the file's bytes; a copy */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** @return the length of the file in bytes */
    public long size() {
        return bytes.length;
    }
}
