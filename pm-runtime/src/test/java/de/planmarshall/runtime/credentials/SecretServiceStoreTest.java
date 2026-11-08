/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.runtime.credentials.dbus.FakeBus;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The Secret Service backend against an in-memory secret service on a fake bus (runs on every
 * platform); {@code SecretServiceIT} runs the same operations against a real keyring on Linux.
 * <p>
 * The project id and the secret values are generated; the service name, the attribute keys, the wire member names and
 * the error reasons are the contracts.
 */
@EnableGeneratorController
@DisplayName("SecretServiceStore over a fake session bus")
class SecretServiceStoreTest {

    private static final long UID = 4242;
    private static final String SERVICE = "de.planmarshall.mcp";
    private static final CredentialAccount GITHUB = CredentialAccount.global("github");

    @TempDir
    Path temp;

    private final FakeSecretService secrets = new FakeSecretService();
    private FakeBus bus;
    private SecretServiceStore store;
    private CredentialAccount projectGithub;
    private String projectId;
    private String otherProject;
    private String firstSecret;
    private String secondSecret;
    private String projectSecret;
    private String globalSecret;

    @BeforeEach
    void start() throws Exception {
        projectId = RuntimeGenerators.projectIds().next();
        otherProject = projectId + "-other";
        projectGithub = CredentialAccount.project(projectId, "github");
        firstSecret = Generators.letterStrings(1, 20).next();
        secondSecret = Generators.letterStrings(1, 20).next();
        projectSecret = Generators.letterStrings(1, 20).next();
        globalSecret = Generators.letterStrings(1, 20).next();
        bus = new FakeBus(temp.resolve("bus"), UID, secrets);
        store = new SecretServiceStore(temp.resolve("bus"), UID, SERVICE, Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() throws Exception {
        bus.close();
    }

    @Test
    @DisplayName("the service object path is the bus name as a path")
    void servicePathIsTheBusNameAsAPath() {
        assertEquals("/org/freedesktop/secrets", SecretServiceStore.SERVICE_PATH);
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("put creates an item with label and attributes, get reads it, delete removes it")
        void putGetDelete() {
            store.put(GITHUB, "tok€n");

            var item = secrets.items.values().iterator().next();
            assertEquals(Map.of("service", SERVICE, "credential_key", "github"), item.attributes);
            assertEquals(SERVICE + " github", item.label);
            assertEquals(Optional.of("tok€n"), store.get(GITHUB));
            assertTrue(store.delete(GITHUB));
            assertFalse(store.delete(GITHUB));
            assertEquals(Optional.empty(), store.get(GITHUB));
            assertEquals(SecretStore.SECRET_SERVICE, store.name());
        }

        @Test
        @DisplayName("a second put replaces the secret of the same item")
        void replace() {
            store.put(GITHUB, firstSecret);
            store.put(GITHUB, secondSecret);

            assertEquals(1, secrets.items.size());
            assertTrue(secrets.members.contains("SetSecret"));
            assertEquals(Optional.of(secondSecret), store.get(GITHUB));
        }

        @Test
        @DisplayName("a project entry carries the project attribute and resolves before the global one")
        void projectEntry() {
            store.put(projectGithub, projectSecret);

            assertEquals(Optional.empty(), store.get(GITHUB), "global lookup ignores project items");
            assertEquals(Optional.of(projectSecret), store.resolve(projectId, "github"));
            store.put(GITHUB, globalSecret);
            assertEquals(Optional.of(projectSecret), store.resolve(projectId, "github"));
            assertEquals(Optional.of(globalSecret), store.resolve(otherProject, "github"));
            assertEquals(2, secrets.items.size());
        }
    }

    @Nested
    @DisplayName("entries")
    class Entries {

        private void foreignItem(String path, Map<String, String> attributes) {
            secrets.items.put(FakeSecretService.COLLECTION + "/" + path,
                    new FakeSecretService.Item(attributes, "foreign", new byte[]{1}));
        }

        @Test
        @DisplayName("an empty collection lists no entry")
        void empty() {
            assertEquals(List.of(), store.entries());
        }

        @Test
        @DisplayName("lists the global and project entries of its service name in account order")
        void globalAndProject() {
            store.put(GITHUB, globalSecret);
            store.put(projectGithub, projectSecret);
            store.put(CredentialAccount.global("enrolment-integrity-key"), firstSecret);

            assertEquals(List.of(CredentialAccount.global("enrolment-integrity-key"), GITHUB, projectGithub),
                    store.entries());
        }

        @Test
        @DisplayName("lists no item of another service name and no item without a credential key")
        void foreignItems() {
            store.put(GITHUB, globalSecret);
            foreignItem("other-service", Map.of("service", SERVICE + "/0123456789abcdef", "credential_key", "sonar"));
            foreignItem("no-key", Map.of("service", SERVICE));

            assertEquals(List.of(GITHUB), store.entries());
        }

        @Test
        @DisplayName("an item with an attribute that is no credential name fails")
        void invalidName() {
            foreignItem("bad", Map.of("service", SERVICE, "credential_key", "bad name"));

            var e = assertThrows(SecretStoreException.class, store::entries);
            assertEquals(SecretStoreException.Reason.FAILED, e.reason());
        }

        @Test
        @DisplayName("locked matching items report LOCKED")
        void locked() {
            store.put(GITHUB, globalSecret);
            secrets.itemsLocked = true;

            var e = assertThrows(SecretStoreException.class, store::entries);
            assertEquals(SecretStoreException.Reason.LOCKED, e.reason());
            assertEquals("credential_store_locked", e.reason().code());
        }

        @Test
        @DisplayName("an unknown service fails with FAILED")
        void serviceUnknown() {
            secrets.serviceUnknown = true;

            var e = assertThrows(SecretStoreException.class, store::entries);
            assertEquals(SecretStoreException.Reason.FAILED, e.reason());
        }
    }

    @Nested
    @DisplayName("locked keyring")
    class Locked {

        @Test
        @DisplayName("put into a locked collection reports LOCKED and never calls Unlock")
        void lockedCollection() {
            secrets.collectionLocked = true;

            var e = assertThrows(SecretStoreException.class, () -> store.put(GITHUB, firstSecret));
            assertEquals(SecretStoreException.Reason.LOCKED, e.reason());
            assertFalse(secrets.members.contains("Unlock"));
        }

        @Test
        @DisplayName("get of a locked item reports LOCKED")
        void lockedItem() {
            store.put(GITHUB, firstSecret);
            secrets.itemsLocked = true;

            var e = assertThrows(SecretStoreException.class, () -> store.get(GITHUB));
            assertEquals(SecretStoreException.Reason.LOCKED, e.reason());
        }

        @Test
        @DisplayName("a CreateItem that needs a prompt reports LOCKED")
        void prompt() {
            secrets.promptOnCreate = true;

            var e = assertThrows(SecretStoreException.class, () -> store.put(GITHUB, firstSecret));
            assertEquals(SecretStoreException.Reason.LOCKED, e.reason());
        }
    }

    @Nested
    @DisplayName("availability")
    class Availability {

        @Test
        @DisplayName("available with an unlocked default collection")
        void available() {
            assertTrue(store.available());
        }

        @Test
        @DisplayName("not available when locked, without a default collection, or without the service")
        void notAvailable() {
            secrets.collectionLocked = true;
            assertFalse(store.available());
            secrets.collectionLocked = false;
            secrets.noDefaultCollection = true;
            assertFalse(store.available());
            secrets.serviceUnknown = true;
            assertFalse(store.available());
        }

        @Test
        @DisplayName("an unknown service fails operations with FAILED")
        void serviceUnknown() {
            secrets.serviceUnknown = true;

            var e = assertThrows(SecretStoreException.class, () -> store.get(GITHUB));
            assertEquals(SecretStoreException.Reason.FAILED, e.reason());
            assertTrue(e.getMessage().contains("ServiceUnknown"), e.getMessage());
        }

        @Test
        @DisplayName("not available without a bus")
        void noBus() {
            var missing = new SecretServiceStore(temp.resolve("none"), UID, SERVICE, Duration.ofSeconds(1));

            assertFalse(missing.available());
            assertThrows(SecretStoreException.class, () -> missing.delete(GITHUB));
        }
    }
}
