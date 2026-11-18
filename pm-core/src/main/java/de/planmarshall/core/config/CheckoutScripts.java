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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.StoreDigest;

/**
 * The digests a {@link ConsentKind#RUN_CHECKOUT_SCRIPTS run_checkout_scripts} consent is keyed by (Store Schemas §
 * Consent Before Acting): before the consent state is reached, every script the state would run is digested from the
 * checkout, and the remembered entry is passed only while it lists each of them with that digest.
 * <p>
 * The digests come from the checkout and change nothing. A script is read as the regular file it is, at its
 * repository-relative path; a path that leaves the checkout, a symbolic link, and an absent file are refused rather than
 * digested, so no consent ever covers a script the state cannot run.
 *
 * @since 0.1
 */
@UtilityClass
public final class CheckoutScripts {

    /**
     * Digests the scripts a {@code run_checkout_scripts} state would run.
     *
     * @param checkoutRoot the root of the checkout
     * @param scriptPaths  the scripts, relative to the root, in the order the state names them
     * @return one {@link ConsentScript} per path, in the same order
     * @throws NullPointerException     if an argument or a path is {@code null}
     * @throws IllegalArgumentException if a path is not relative, leaves the checkout, names no script or repeats
     *                                  another path, or names no regular file
     * @throws UncheckedIOException     if a script exists and cannot be read
     */
    public static List<ConsentScript> digest(Path checkoutRoot, List<String> scriptPaths) {
        Objects.requireNonNull(checkoutRoot, "checkoutRoot");
        Objects.requireNonNull(scriptPaths, "scriptPaths");
        var root = checkoutRoot.toAbsolutePath().normalize();
        var seen = new HashSet<String>();
        var digests = new ArrayList<ConsentScript>(scriptPaths.size());
        for (var scriptPath : scriptPaths) {
            var script = resolve(root, scriptPath);
            if (!seen.add(script.path())) {
                throw new IllegalArgumentException(
                        "A consent names each checkout script once: " + script.path());
            }
            digests.add(script);
        }
        return List.copyOf(digests);
    }

    private static ConsentScript resolve(Path root, String scriptPath) {
        var given = Objects.requireNonNull(scriptPath, "scriptPath");
        if (given.isEmpty() || Path.of(given).isAbsolute()) {
            throw new IllegalArgumentException("A checkout script is named relative to the checkout: " + given);
        }
        var path = Path.of(given).normalize();
        if (path.toString().isEmpty() || ".".equals(path.toString()) || path.startsWith("..")) {
            throw new IllegalArgumentException("A checkout script lies inside the checkout: " + given);
        }
        var file = root.resolve(path).normalize();
        if (!file.startsWith(root) || file.equals(root)) {
            throw new IllegalArgumentException("A checkout script lies inside the checkout: " + given);
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("A checkout script is a regular file: " + given);
        }
        try {
            return new ConsentScript(path.toString(), StoreDigest.of(Files.readAllBytes(file)).sha256());
        } catch (IOException e) {
            throw new UncheckedIOException("A checkout script cannot be read: " + given, e);
        }
    }
}
