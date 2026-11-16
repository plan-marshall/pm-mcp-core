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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The value semantics of a loaded profile and of a shipped profile: the document bytes are compared by their content,
 * a class is runnable exactly when the document declares it, and a headless path resolves on the document.
 */
@EnableGeneratorController
class LoadedProfileTest {

    private static final String DISPLAY_NAME = "Acme CLI " + Generators.letterStrings(3, 8).next();
    private static final String OTHER_DISPLAY_NAME = DISPLAY_NAME + " other";

    @Test
    @DisplayName("two profiles of the same document are equal, hash alike and name their bytes")
    void sameDocumentIsEqual() {
        var first = loaded(DISPLAY_NAME);
        var second = loaded(DISPLAY_NAME);

        assertAll(
                () -> assertEquals(first, second, "two loads of one document are equal"),
                () -> assertEquals(first.hashCode(), second.hashCode(), "equal loaded profiles share a hash"),
                () -> assertEquals(first.digest(), second.digest(), "equal loaded profiles share a digest"),
                () -> assertTrue(first.toString().contains("bytes=["), "toString names the document bytes"));
    }

    @Test
    @DisplayName("a different document is a different profile")
    void differentDocumentIsNotEqual() {
        assertNotEquals(loaded(DISPLAY_NAME), loaded(OTHER_DISPLAY_NAME),
                "different display name makes a different profile");
    }

    @Test
    @DisplayName("a class is runnable exactly when the profile declares it")
    void classRunnableFollowsTheDocument() {
        var loaded = loaded(DISPLAY_NAME);

        assertAll(
                () -> assertTrue(loaded.classRunnable("writer"), "declared writer class is runnable"),
                () -> assertTrue(loaded.classRunnable("read_only"), "declared read_only class is runnable"),
                () -> assertTrue(loaded.classRunnable("zero_tool"), "declared zero_tool class is runnable"),
                () -> assertFalse(loaded.classRunnable("absent"), "undeclared class is not runnable"));
    }

    @Test
    @DisplayName("a headless path resolves, and a profile without a headless block has none")
    void headlessPathResolves() {
        var loaded = loaded(DISPLAY_NAME);
        var withoutHeadless = loaded(ProfileFixtures.valid().set("/headless", null).set("/worker/eligible", false));

        assertAll(
                () -> assertEquals("acme", loaded.headless("/executable").flatMap(JsonNode::text).orElse(""),
                        "headless executable resolves on the document"),
                () -> assertTrue(withoutHeadless.headless("/executable").isEmpty(),
                        "no headless block yields no path"));
    }

    @Test
    @DisplayName("a state names itself on the wire")
    void stateWireNames() {
        assertAll(
                () -> assertEquals("verified", LoadedProfile.State.VERIFIED.wireName(),
                        "verified state spells verified on the wire"),
                () -> assertEquals("verified_on_other_build", LoadedProfile.State.VERIFIED_ON_OTHER_BUILD.wireName(),
                        "other-build state keeps its wire name"));
    }

    @Test
    @DisplayName("a shipped profile compares its document by content")
    void shippedProfileComparesContent() {
        var first = new ProfileSets.ShippedProfile(ProfileFixtures.validBytes());
        var second = new ProfileSets.ShippedProfile(ProfileFixtures.validBytes());
        var other = new ProfileSets.ShippedProfile("{}".getBytes(UTF_8));

        assertAll(
                () -> assertEquals(first, second, "same document bytes are equal profiles"),
                () -> assertEquals(first.hashCode(), second.hashCode(), "equal shipped profiles share a hash"),
                () -> assertNotEquals(first, other, "different bytes are different profiles"),
                () -> assertTrue(first.toString().contains("bytes"), "toString mentions the document bytes"));
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
