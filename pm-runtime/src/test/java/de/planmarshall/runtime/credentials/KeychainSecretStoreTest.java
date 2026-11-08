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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Real login-keychain items under a unique test service name, created, read, replaced and deleted
 * by this process, so the creator ACL never prompts. Gate 4 on the JVM.
 * <p>
 * The service suffix, the account names, the project id and the secrets are generated; the keychain's status codes and
 * the {@code github} fixture account are the contracts.
 */
@EnabledOnOs(OS.MAC)
@EnableGeneratorController
@DisplayName("KeychainSecretStore against the macOS login keychain")
class KeychainSecretStoreTest {

    private String service;

    private KeychainSecretStore store;

    private String projectId;

    private String otherProject;

    private String firstSecret;

    private String secondSecret;

    private String globalSecret;

    private String projectSecret;

    private CredentialAccount global;

    private CredentialAccount project;

    @BeforeEach
    void create() {
        service = "de.planmarshall.mcp.test/"
                + Generators.strings("0123456789abcdef", 16, 16).next();
        store = new KeychainSecretStore(service);
        projectId = RuntimeGenerators.projectIds().next();
        otherProject = projectId + "-other";
        firstSecret = Generators.letterStrings(1, 20).next();
        secondSecret = Generators.letterStrings(1, 20).next();
        globalSecret = Generators.letterStrings(1, 20).next();
        projectSecret = Generators.letterStrings(1, 20).next();
        global = CredentialAccount.global("github");
        project = CredentialAccount.project(projectId, "github");
    }

    @AfterEach
    void cleanUp() {
        store.delete(global);
        store.delete(project);
    }

    @Test
    @DisplayName("put, get, replace and delete a generic-password item")
    void roundTrip() {
        assertEquals(Optional.empty(), store.get(global));

        store.put(global, firstSecret);
        assertEquals(Optional.of(firstSecret), store.get(global));
        store.put(global, secondSecret);
        assertEquals(Optional.of(secondSecret), store.get(global));

        assertTrue(store.delete(global));
        assertFalse(store.delete(global));
        assertEquals(Optional.empty(), store.get(global));
        assertEquals(SecretStore.KEYCHAIN, store.name());
    }

    @Test
    @DisplayName("project and global entries are separate accounts, the project one resolves first")
    void projectAccount() {
        store.put(global, globalSecret);
        store.put(project, projectSecret);

        assertEquals(Optional.of(projectSecret), store.resolve(projectId, "github"));
        assertEquals(Optional.of(globalSecret), store.resolve(otherProject, "github"));
    }

    @Test
    @DisplayName("an empty service name lists no entry")
    void entriesEmpty() {
        assertEquals(List.of(), store.entries());
    }

    @Test
    @DisplayName("lists the global and project entries of its service name and none of another")
    void entries() {
        var other = new KeychainSecretStore(service + "-other");
        var foreign = CredentialAccount.global("foreign");
        store.put(project, projectSecret);
        store.put(global, globalSecret);
        other.put(foreign, Generators.letterStrings(1, 20).next());
        try {
            assertEquals(List.of(global, project), store.entries());
            assertEquals(List.of(foreign), other.entries());
        } finally {
            other.delete(foreign);
        }
    }

    @Test
    @DisplayName("finding: the data-protection keychain refuses an unentitled binary (errSecMissingEntitlement)")
    void dataProtectionKeychainNeedsEntitlement() {
        var dataProtection = new KeychainSecretStore(service, true);

        var e = assertThrows(KeychainSecretStore.KeychainException.class,
                () -> dataProtection.put(global, Generators.letterStrings(1, 20).next()));
        assertEquals(MacSecurity.MISSING_ENTITLEMENT, e.status());
        assertEquals(SecretStoreException.Reason.FAILED, e.reason());
    }
}
