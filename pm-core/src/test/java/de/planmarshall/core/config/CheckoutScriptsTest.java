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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The digests a {@code run_checkout_scripts} consent is keyed by (Store Schemas § Consent Before Acting): every script
 * the state would run is digested from the checkout at its repository-relative path, and a script that leaves the
 * checkout, repeats another, or is no regular file is refused rather than digested.
 */
@DisplayName("Checkout script digests")
class CheckoutScriptsTest {

    @TempDir
    Path checkout;

    @Test
    @DisplayName("Should digest every named script in the order of the state")
    void digestsEachNamedScript() throws IOException {
        var first = "#!/bin/sh\necho first\n".getBytes(UTF_8);
        var second = "#!/bin/sh\necho second\n".getBytes(UTF_8);
        Files.createDirectories(checkout.resolve("scripts"));
        Files.write(checkout.resolve("scripts/first.sh"), first);
        Files.write(checkout.resolve("scripts/second.sh"), second);

        var digests = CheckoutScripts.digest(checkout, List.of("scripts/second.sh", "scripts/first.sh"));

        assertAll(
                () -> assertEquals(2, digests.size(), "Both named scripts are digested"),
                () -> assertEquals("scripts/second.sh", digests.getFirst().path(), "The state's order is kept"),
                () -> assertEquals(sha256(second), digests.getFirst().sha256(), "A digest is the file's SHA-256"),
                () -> assertEquals(sha256(first), digests.getLast().sha256(), "Each script gets its own digest"));
    }

    @Test
    @DisplayName("Should digest a script named with a redundant relative prefix")
    void normalizesTheRelativePath() throws IOException {
        Files.write(checkout.resolve("run.sh"), "#!/bin/sh\n".getBytes(UTF_8));

        var digests = CheckoutScripts.digest(checkout, List.of("./run.sh"));

        assertEquals("run.sh", digests.getFirst().path(), "The stored path is the normalized one");
    }

    @Test
    @DisplayName("Should refuse a path that leaves the checkout or is absolute")
    void refusesAPathOutsideTheCheckout() {
        var absolute = checkout.resolve("outside.sh").toString();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CheckoutScripts.digest(checkout, List.of("../outside.sh")),
                        "A parent-relative script is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CheckoutScripts.digest(checkout, List.of(absolute)),
                        "An absolute script path is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CheckoutScripts.digest(checkout, List.of(".")),
                        "The checkout itself is no script"));
    }

    @Test
    @DisplayName("Should refuse an absent script and a directory as script")
    void refusesWhatIsNoRegularFile() throws IOException {
        Files.createDirectories(checkout.resolve("scripts"));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CheckoutScripts.digest(checkout, List.of("scripts/absent.sh")),
                        "An absent script is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CheckoutScripts.digest(checkout, List.of("scripts")),
                        "A directory is refused as a script"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("Should refuse a symbolic link rather than follow it")
    void refusesASymbolicLink() throws IOException {
        Files.write(checkout.resolve("real.sh"), "#!/bin/sh\n".getBytes(UTF_8));
        Files.createSymbolicLink(checkout.resolve("link.sh"), checkout.resolve("real.sh"));

        assertThrows(IllegalArgumentException.class,
                () -> CheckoutScripts.digest(checkout, List.of("link.sh")),
                "A symbolic link is refused as a consent script");
    }

    @Test
    @DisplayName("Should refuse the same script named twice")
    void refusesADuplicatePath() throws IOException {
        Files.write(checkout.resolve("run.sh"), "#!/bin/sh\n".getBytes(UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> CheckoutScripts.digest(checkout, List.of("run.sh", "./run.sh")),
                "A consent names each script once");
    }

    @Test
    @DisplayName("Should not pass a remembered consent that lacks an added script")
    void remembersOnlyTheListedDigests() {
        var first = new ConsentScript("scripts/first.sh", sha256("first".getBytes(UTF_8)));
        var added = new ConsentScript("scripts/added.sh", sha256("added".getBytes(UTF_8)));
        var snapshot = new ConsentStore.Snapshot(List.of(Consent.checkoutScripts("my-project", List.of(first),
                Instant.parse("2026-11-18T00:00:00Z"), Consent.Channel.CLI, null)), 1);

        assertAll(
                () -> assertTrue(snapshot.isRemembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, "my-project",
                        List.of(first)), "The remembered list passes the scripts it covers"),
                () -> assertFalse(snapshot.isRemembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, "my-project",
                        List.of(first, added)), "An added script asks again"));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JVM carries SHA-256", e);
        }
    }
}
