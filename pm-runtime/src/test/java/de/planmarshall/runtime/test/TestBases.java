/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.test;

import de.cuioss.test.generator.Generators;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Short private machine roots for tests: {@code /tmp/<prefix>-<8 hex>}, so that the socket path stays far below
 * the {@code sun_path} limit. The directory itself is not created: the runtime creates it.
 * <p>
 * The form {@code /tmp/<prefix>-<8 hex>} is the contract of the class; the eight hex digits are generated, and a path
 * that already exists is never returned, so a base stays unique among the bases of concurrent tests.
 */
public final class TestBases {

    private TestBases() {
    }

    /**
     * @param prefix a short prefix
     * @return a fresh, not yet existing base path
     */
    public static Path create(String prefix) {
        while (true) {
            // Contract literal: the short temporary directory that keeps the socket path below the sun_path limit
            var base = Path.of("/tmp", prefix + "-" + "%08x".formatted(Generators.integers().next()));
            if (!Files.exists(base)) {
                return base;
            }
        }
    }

    /**
     * Deletes a base recursively; a missing base is fine.
     *
     * @param base the base
     */
    public static void delete(Path base) {
        if (base == null || !Files.exists(base)) {
            return;
        }
        try (var walk = Files.walk(base)) {
            for (var path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
