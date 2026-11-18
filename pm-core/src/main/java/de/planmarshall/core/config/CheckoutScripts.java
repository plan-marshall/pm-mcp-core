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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.experimental.UtilityClass;

/**
 * The digests a {@link ConsentKind#RUN_CHECKOUT_SCRIPTS run_checkout_scripts} consent is keyed by (Store Schemas §
 * Consent Before Acting): before the consent state is reached, every script the state would run is digested from the
 * checkout, and the remembered entry is passed only while it lists each of them with that digest.
 * <p>
 * The digests come from the checkout and change nothing. A script is read as the regular file it is, at its
 * repository-relative path, and the whole path is checked without following a link: a symbolic link between the checkout
 * root and the script, a path that leaves the checkout, an absent file and a directory are refused rather than digested,
 * so no consent ever covers a file outside the checkout. The file is opened with the same no-follow rule as the check, so
 * the file digested is the file that was checked. The recorded path is spelled with {@code /} on every platform.
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
     *                                  another path, names no regular file, or reaches it through a symbolic link
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
        requireNoSymbolicLink(root, path);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("A checkout script is a regular file: " + given);
        }
        try {
            return new ConsentScript(portable(path), sha256(file));
        } catch (IOException e) {
            throw new UncheckedIOException("A checkout script cannot be read: " + given, e);
        }
    }

    /** Refuses a symbolic link anywhere between the checkout root and the script. */
    private static void requireNoSymbolicLink(Path root, Path relative) {
        var current = root;
        for (var component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(
                        "A checkout script reaches no symbolic link: " + relative);
            }
        }
    }

    /** The path a consent records, spelled with {@code /} on every platform. */
    private static String portable(Path relative) {
        return relative.toString().replace(File.separatorChar, '/');
    }

    private static String sha256(Path file) throws IOException {
        try (var channel = Files.newByteChannel(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                var digest = new DigestInputStream(Channels.newInputStream(channel),
                        MessageDigest.getInstance("SHA-256"))) {
            digest.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JVM carries SHA-256", e);
        }
    }
}
