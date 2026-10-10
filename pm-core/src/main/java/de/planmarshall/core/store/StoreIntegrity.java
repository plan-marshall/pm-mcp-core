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

/**
 * What a lock-free snapshot read can say about the content it returns (PM-IMPL-7). A reader without the lock of the
 * store may observe a transaction in flight, so a difference from the recorded digest is reported as unverified and
 * never as an edit of the store: whether a store was edited is decided only by a reader that holds its lock.
 *
 * @since 0.1
 */
public enum StoreIntegrity {
    /** No recorded digest was given; the content was not compared with anything. */
    UNCHECKED("unchecked"),
    /** The content has the recorded size and SHA-256. */
    VERIFIED("verified"),
    /** The content differs from the recorded size or SHA-256; the next locked access of the store decides. */
    DIGEST_UNVERIFIED("digest_unverified");

    private final String code;

    StoreIntegrity(String code) {
        this.code = code;
    }

    /** @return the name of the value in a representation */
    public String code() {
        return code;
    }
}
