/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import java.util.Objects;
import java.util.Optional;

/**
 * How a start takes its documents into its source directory, the machine's {@code sources.documents_mode} (State
 * Catalogue § Documents as a Source, Store Schemas § Machine Configuration Store). The machine's onboarding and its
 * later run from the machine's settings record it; a start reads it.
 *
 * @since 0.1
 */
public enum DocumentsMode {

    /** The documents are copied into the source directory; the originals stay where they are. */
    COPY("copy"),

    /** The documents are moved into the source directory. */
    MOVE("move"),

    /** The start asks for each document set, and answering always writes {@link #COPY} or {@link #MOVE} here. */
    ASK("ask");

    /** The schema default, applied while the key is absent. */
    public static final DocumentsMode DEFAULT = ASK;

    private final String wireName;

    DocumentsMode(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return the wire name of the mode, the value of the schema's {@code enum}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName a wire name
     * @return the mode of that name; empty for a value the schema does not admit
     */
    public static Optional<DocumentsMode> of(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        for (var mode : values()) {
            if (mode.wireName.equals(wireName)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
