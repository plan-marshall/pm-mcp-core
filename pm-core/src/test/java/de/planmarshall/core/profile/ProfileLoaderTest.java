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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.StoreDigest;

/**
 * The loader resolves the three profile sets as whole documents, binds a machine or project profile to its verification
 * record and its HMAC sidecar, and falls back to the next set when the nearest one does not count (PM-CLIENT-5,
 * Profile Sets).
 */
@DisplayName("Client profile sets")
class ProfileLoaderTest {

    private static final String CLIENT = "acme-cli";
    private static final String SHA = "sha256:" + "a".repeat(64);
    private static final String OTHER_SHA = "sha256:" + "b".repeat(64);

    private SecretKey key;
    private EnrolledHarness enrolled;
    private ProfileLoader loader;

    @BeforeEach
    void setUp() {
        key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(UTF_8), "HmacSHA256");
        enrolled = new EnrolledHarness("acme", "1.5.0", OTHER_SHA);
        loader = new ProfileLoader(key, Map.of(CLIENT, enrolled), ProfileRules.none());
    }

    @Test
    @DisplayName("a shipped profile is the effective profile of a machine without an installed one")
    void shippedProfileIsEffective() {
        var sets = ProfileSets.of(Map.of(CLIENT, shipped(ProfileFixtures.validBytes())));

        var loaded = loader.load(sets);

        assertAll(
                () -> assertEquals(LoadedProfile.State.VERIFIED, loaded.state(CLIENT).orElseThrow()),
                () -> assertEquals(ProfileSetKind.SHIPPED, loaded.effective(CLIENT).orElseThrow().kind()),
                () -> assertTrue(loaded.refused().isEmpty(), loaded.refused()::toString));
    }

    @Test
    @DisplayName("a machine profile of a client id the release does not ship adds a harness")
    void machineProfileAddsHarness(@TempDir Path machine) throws Exception {
        var document = ProfileFixtures.validBytes();
        write(machine, CLIENT, document, record(document, "1.5.0", OTHER_SHA), sidecar(key, document));

        var loaded = loader.load(ProfileSets.withMachine(Map.of(), machine));

        assertAll(
                () -> assertTrue(loaded.refused().isEmpty(), loaded.refused()::toString),
                () -> assertEquals(LoadedProfile.State.VERIFIED, loaded.state(CLIENT).orElseThrow()),
                () -> assertEquals(ProfileSetKind.MACHINE, loaded.effective(CLIENT).orElseThrow().kind()));
    }

    @Test
    @DisplayName("a verified machine profile replaces the shipped document as a whole")
    void machineProfileReplacesShipped(@TempDir Path machine) throws Exception {
        var document = ProfileFixtures.valid().set("/display_name", "Acme CLI Pro").bytes();
        write(machine, CLIENT, document, record(document, "1.5.0", OTHER_SHA), sidecar(key, document));
        var shipped = shipped(ProfileFixtures.validBytes());

        var loaded = loader.load(ProfileSets.withMachine(Map.of(CLIENT, shipped), machine));

        assertAll(
                () -> assertTrue(loaded.refused().isEmpty(), loaded.refused()::toString),
                () -> assertEquals(ProfileSetKind.MACHINE, loaded.effective(CLIENT).orElseThrow().kind()),
                () -> assertEquals("Acme CLI Pro", loaded.effective(CLIENT).orElseThrow().document()
                        .member("display_name").flatMap(JsonNode::text).orElseThrow()));
    }

    @Test
    @DisplayName("a machine profile without its record is refused and the shipped profile stays in force")
    void missingRecordFallsBack(@TempDir Path machine) throws Exception {
        var document = ProfileFixtures.validBytes();
        write(machine, CLIENT, document, null, sidecar(key, document));

        var loaded = loader.load(ProfileSets.withMachine(Map.of(CLIENT, shipped(ProfileFixtures.validBytes())),
                machine));

        assertAll(
                () -> assertEquals(ProfileSetKind.SHIPPED, loaded.effective(CLIENT).orElseThrow().kind()),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_UNVERIFIED, refusal(loaded).outcome()),
                () -> assertEquals("profile_unverified", refusal(loaded).code()));
    }

    @Test
    @DisplayName("a machine profile edited after its record is refused and the shipped profile stays in force")
    void editedAfterRecordFallsBack(@TempDir Path machine) throws Exception {
        var verified = ProfileFixtures.validBytes();
        var edited = ProfileFixtures.valid().set("/display_name", "Acme CLI Edited").bytes();
        write(machine, CLIENT, edited, record(verified, "1.5.0", OTHER_SHA), sidecar(key, verified));

        var loaded = loader.load(ProfileSets.withMachine(Map.of(CLIENT, shipped(ProfileFixtures.validBytes())),
                machine));

        assertAll(
                () -> assertEquals(ProfileSetKind.SHIPPED, loaded.effective(CLIENT).orElseThrow().kind()),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_UNVERIFIED, refusal(loaded).outcome()));
    }

    @Test
    @DisplayName("a machine profile whose HMAC sidecar fails is refused and the shipped profile stays in force")
    void failingHmacFallsBack(@TempDir Path machine) throws Exception {
        var document = ProfileFixtures.validBytes();
        var otherKey = new SecretKeySpec("fedcba9876543210fedcba9876543210".getBytes(UTF_8), "HmacSHA256");
        write(machine, CLIENT, document, record(document, "1.5.0", OTHER_SHA), sidecar(otherKey, document));

        var loaded = loader.load(ProfileSets.withMachine(Map.of(CLIENT, shipped(ProfileFixtures.validBytes())),
                machine));

        assertAll(
                () -> assertEquals(ProfileSetKind.SHIPPED, loaded.effective(CLIENT).orElseThrow().kind()),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_UNVERIFIED, refusal(loaded).outcome()));
    }

    @Test
    @DisplayName("a record of another build inside the supported range marks the profile verified_on_other_build")
    void otherBuildInsideRange(@TempDir Path machine) throws Exception {
        var document = ProfileFixtures.validBytes();
        write(machine, CLIENT, document, record(document, "1.0.0", SHA), sidecar(key, document));

        var loaded = loader.load(ProfileSets.withMachine(Map.of(), machine));

        assertAll(
                () -> assertTrue(loaded.refused().isEmpty(), loaded.refused()::toString),
                () -> assertEquals(LoadedProfile.State.VERIFIED_ON_OTHER_BUILD,
                        loaded.state(CLIENT).orElseThrow()),
                () -> assertTrue(loaded.verifiedOnOtherBuild(CLIENT)));
    }

    @Test
    @DisplayName("a record of a build outside the supported range is refused")
    void otherBuildOutsideRangeRefused(@TempDir Path machine) throws Exception {
        var document = ProfileFixtures.validBytes();
        write(machine, CLIENT, document, record(document, "1.5.0", OTHER_SHA), sidecar(key, document));
        var shipped = shipped(ProfileFixtures.validBytes());
        var newer = new ProfileLoader(key, Map.of(CLIENT, new EnrolledHarness("acme", "2.5.0", OTHER_SHA)),
                ProfileRules.none());

        var loaded = newer.load(ProfileSets.withMachine(Map.of(CLIENT, shipped), machine));

        assertAll(
                () -> assertEquals(ProfileSetKind.SHIPPED, loaded.effective(CLIENT).orElseThrow().kind()),
                () -> assertEquals(ProfileRefusal.Outcome.HARNESS_VERSION_UNSUPPORTED, refusal(loaded).outcome()));
    }

    @Test
    @DisplayName("a project profile whose detection differs from the machine one is refused")
    void projectDetectionDiffersRefused(@TempDir Path machine, @TempDir Path project) throws Exception {
        var base = ProfileFixtures.validBytes();
        var projectDocument = ProfileFixtures.valid().set("/headless/argv", List.of("--other"))
                .set("/detection/precedence", 10).bytes();
        write(project, CLIENT, projectDocument, record(projectDocument, "1.5.0", OTHER_SHA),
                sidecar(key, projectDocument));

        var loaded = loader.load(ProfileSets.withProject(
                Map.of(CLIENT, shipped(base)), null, project), "plan-marshall");

        assertAll(
                () -> assertEquals(ProfileSetKind.SHIPPED, loaded.effective(CLIENT).orElseThrow().kind()),
                () -> assertEquals("/detection", refusal(loaded).pointer()));
    }

    @Test
    @DisplayName("a project profile changes how a harness works, not which harness it is")
    void projectProfileReplacesWholeDocument(@TempDir Path project) throws Exception {
        var base = ProfileFixtures.validBytes();
        var projectDocument = ProfileFixtures.valid().set("/display_name", "Acme CLI Project").bytes();
        write(project, CLIENT, projectDocument, record(projectDocument, "1.5.0", OTHER_SHA),
                sidecar(key, projectDocument));

        var loaded = loader.load(ProfileSets.withProject(Map.of(CLIENT, shipped(base)), null, project),
                "plan-marshall");

        assertAll(
                () -> assertEquals(ProfileSetKind.PROJECT, loaded.effective(CLIENT).orElseThrow().kind()),
                () -> assertEquals("Acme CLI Project", loaded.effective(CLIENT).orElseThrow().document()
                        .member("display_name").flatMap(JsonNode::text).orElseThrow()));
    }

    @Test
    @DisplayName("a profile whose client id is not its file name is refused")
    void clientIdMustEqualFileName(@TempDir Path machine) throws Exception {
        var document = ProfileFixtures.valid().set("/client_id", "other-cli").bytes();
        write(machine, CLIENT, document, record(document, "1.5.0", OTHER_SHA), sidecar(key, document));

        var loaded = loader.load(ProfileSets.withMachine(Map.of(CLIENT, shipped(ProfileFixtures.validBytes())),
                machine));

        assertAll(
                () -> assertEquals(ProfileSetKind.SHIPPED, loaded.effective(CLIENT).orElseThrow().kind()),
                () -> assertEquals("/client_id", refusal(loaded).pointer()));
    }

    @Test
    @DisplayName("two profiles of one detection precedence do not both take effect")
    void precedenceIsUnique(@TempDir Path machine) throws Exception {
        var document = ProfileFixtures.validBytes();
        write(machine, CLIENT, document, record(document, "1.5.0", OTHER_SHA), sidecar(key, document));
        var other = ProfileFixtures.valid().set("/client_id", "other-cli").set("/headless/executable", "other")
                .bytes();
        write(machine, "other-cli", other, record(other, "1.5.0", OTHER_SHA, "other-cli", "other"), sidecar(key, other));
        var otherLoader = new ProfileLoader(key, Map.of(CLIENT, enrolled, "other-cli",
                new EnrolledHarness("other", "1.5.0", OTHER_SHA)), ProfileRules.none());

        var loaded = otherLoader.load(ProfileSets.withMachine(Map.of(), machine));

        assertAll(
                () -> assertTrue(loaded.effective(CLIENT).isPresent()),
                () -> assertFalse(loaded.effective("other-cli").isPresent(),
                        "The second profile of the same precedence must not take effect"),
                () -> assertTrue(loaded.refused().stream()
                        .anyMatch(refused -> "/detection/precedence".equals(refused.refusal().pointer()))));
    }

    private static ProfileSets.ShippedProfile shipped(byte[] document) {
        return new ProfileSets.ShippedProfile(document);
    }

    private static ProfileRefusal refusal(ProfileSet loaded) {
        assertEquals(1, loaded.refused().size(), loaded.refused()::toString);
        return loaded.refused().getFirst().refusal();
    }

    private byte[] record(byte[] document, String version, String sha) {
        return record(document, version, sha, CLIENT, "acme");
    }

    private byte[] record(byte[] document, String version, String sha, String clientId) {
        return record(document, version, sha, clientId, "acme");
    }

    private byte[] record(byte[] document, String version, String sha, String clientId, String executable) {
        var profile = JsonDocument.parse(document, clientId);
        var record = new LinkedHashMap<String, Object>();
        record.put("format_version", 1);
        record.put("client_id", clientId);
        record.put("profile_schema_version", 1);
        record.put("profile_digest", CanonicalJson.digest(profile));
        record.put("verified_at", "2026-10-21T08:00:00Z");
        record.put("tool", Map.of("version", "0.4.0", "commit", "02ffcbd"));
        record.put("harness", Map.of("executable", executable,
                "version", version, "executable_sha256", sha, "models", List.of("acme-standard")));
        record.put("platform", Map.of("os", "linux", "arch", "x86_64", "confinement", "landlock"));
        record.put("stages", Map.of("identity", Map.of("status", "passed"),
                "launch", Map.of("status", "passed")));
        record.put("values", List.of());
        return CanonicalJson.write(ProfileFixtures.node(record)).getBytes(UTF_8);
    }

    private static byte[] sidecar(SecretKey key, byte[] document) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            var hmac = HexFormat.of().formatHex(mac.doFinal(document));
            return new EnrolmentSidecar(StoreDigest.of(document), hmac).write();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Path directory, String clientId, byte[] document, byte[] record, byte[] hmac)
            throws IOException {
        Files.createDirectories(directory.resolve("verification"));
        Files.write(directory.resolve(clientId + ".json"), document);
        if (record != null) {
            Files.write(directory.resolve("verification").resolve(clientId + ".json"), record);
        }
        Files.write(directory.resolve(clientId + ".json.hmac"), hmac);
    }
}
