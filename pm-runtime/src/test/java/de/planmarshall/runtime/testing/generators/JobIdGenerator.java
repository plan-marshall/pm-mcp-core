/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import java.util.HexFormat;
import java.util.Objects;

import de.planmarshall.core.store.JobId;

/**
 * Generates a job id of one kind in its one written form, {@code ^[bcglpv]-[0-9a-f]{16}$}: the prefix of the kind,
 * {@code -} and 16 lower-case hexadecimal digits. The digits are drawn from the generator, so the id names no job a
 * store allocated; a test uses it where the code under test takes a job id, a worker generation or a job log name.
 *
 * @since 0.1
 */
public final class JobIdGenerator implements TypedGenerator<String> {

    /** The number of bytes behind the 16 hexadecimal digits of an id. */
    private static final int DIGIT_BYTES = 8;

    private final JobId.Kind kind;

    /**
     * @param kind the kind whose prefix every generated id carries
     */
    public JobIdGenerator(JobId.Kind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Override
    public String next() {
        var bytes = new byte[DIGIT_BYTES];
        var generator = Generators.bytes();
        for (var index = 0; index < bytes.length; index++) {
            bytes[index] = generator.next();
        }
        return kind.prefix() + "-" + HexFormat.of().formatHex(bytes);
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
