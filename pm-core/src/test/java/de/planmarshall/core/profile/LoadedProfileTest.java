/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The value semantics of a loaded profile and of a shipped profile: the document bytes are compared by their content,
 * a class is runnable exactly when the document declares it, and a headless path resolves on the document.
 */
class LoadedProfileTest {

    @Test
    @DisplayName("two profiles of the same document are equal, hash alike and name their bytes")
    void sameDocumentIsEqual() {
        var first = loaded("Acme CLI");
        var second = loaded("Acme CLI");

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.hashCode(), second.hashCode()),
                () -> assertEquals(first.digest(), second.digest()),
                () -> assertTrue(first.toString().contains("bytes=[")));
    }

    @Test
    @DisplayName("a different document is a different profile")
    void differentDocumentIsNotEqual() {
        assertNotEquals(loaded("Acme CLI"), loaded("Acme CLI Other"));
    }

    @Test
    @DisplayName("a class is runnable exactly when the profile declares it")
    void classRunnableFollowsTheDocument() {
        var loaded = loaded("Acme CLI");

        assertAll(
                () -> assertTrue(loaded.classRunnable("writer")),
                () -> assertTrue(loaded.classRunnable("read_only")),
                () -> assertTrue(loaded.classRunnable("zero_tool")),
                () -> assertFalse(loaded.classRunnable("absent")));
    }

    @Test
    @DisplayName("a headless path resolves, and a profile without a headless block has none")
    void headlessPathResolves() {
        var loaded = loaded("Acme CLI");
        var withoutHeadless = loaded(ProfileFixtures.valid().set("/headless", null).set("/worker/eligible", false));

        assertAll(
                () -> assertEquals("acme", loaded.headless("/executable").flatMap(JsonNode::text).orElse("")),
                () -> assertTrue(withoutHeadless.headless("/executable").isEmpty()));
    }

    @Test
    @DisplayName("a state names itself on the wire")
    void stateWireNames() {
        assertAll(
                () -> assertEquals("verified", LoadedProfile.State.VERIFIED.wireName()),
                () -> assertEquals("verified_on_other_build", LoadedProfile.State.VERIFIED_ON_OTHER_BUILD.wireName()));
    }

    @Test
    @DisplayName("a shipped profile compares its document by content")
    void shippedProfileComparesContent() {
        var first = new ProfileSets.ShippedProfile(ProfileFixtures.validBytes());
        var second = new ProfileSets.ShippedProfile(ProfileFixtures.validBytes());
        var other = new ProfileSets.ShippedProfile("{}".getBytes(UTF_8));

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(first.hashCode(), second.hashCode()),
                () -> assertNotEquals(first, other),
                () -> assertTrue(first.toString().contains("bytes")));
    }

    private static LoadedProfile loaded(String displayName) {
        return loaded(ProfileFixtures.valid().set("/display_name", displayName));
    }

    private static LoadedProfile loaded(ProfileFixtures.ProfileFixture fixture) {
        var bytes = fixture.bytes();
        var tree = ProfileValidator.validate(bytes, "profiles/acme-cli.json", ProfileRules.none());
        return new LoadedProfile("acme-cli", ProfileSetKind.SHIPPED, tree, bytes, LoadedProfile.State.VERIFIED, null);
    }
}
