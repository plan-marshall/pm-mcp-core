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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import de.planmarshall.runtime.credentials.dbus.DbusConnection;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Gate 5 on the JVM: the Secret Service of the Linux session (gnome-keyring or KWallet) over the
 * JDK Unix domain socket channel, under a unique test service name. Skipped when no session bus
 * address with {@code unix:path=} exists or the default collection is locked.
 * <p>
 * The service suffix, the account names, the project id and the secrets are generated; the D-Bus timeout and the
 * {@code github} fixture account are the contracts.
 */
@EnabledOnOs(OS.LINUX)
@EnableGeneratorController
@DisplayName("SecretServiceStore against the session keyring")
class SecretServiceIT {

    private String service;

    private String projectId;

    private String otherProject;

    private String firstSecret;

    private String secondSecret;

    private String globalSecret;

    private String projectSecret;

    private CredentialAccount global;

    private CredentialAccount project;

    private SecretServiceStore store;

    private void draw() {
        service = "de.planmarshall.mcp.test/"
                + Generators.strings("0123456789abcdef", 16, 16).next();
        projectId = RuntimeGenerators.projectIds().next();
        otherProject = projectId + "-other";
        firstSecret = Generators.letterStrings(1, 20).next();
        secondSecret = Generators.letterStrings(1, 20).next();
        globalSecret = Generators.letterStrings(1, 20).next();
        projectSecret = Generators.letterStrings(1, 20).next();
        global = CredentialAccount.global("github");
        project = CredentialAccount.project(projectId, "github");
    }

    @BeforeEach
    void connect() {
        draw();
        var socket = DbusConnection.sessionBusSocket(System.getenv());
        assumeTrue(socket.isPresent(), "no unix:path session bus address");
        var candidate = new SecretServiceStore(socket.get(), Posix.getuid(), service, SecretServiceStore.BUS_TIMEOUT);
        // A session bus without org.freedesktop.secrets (a CI runner) skips the test; the cleanup then has no store
        assumeTrue(candidate.available(), "Secret Service not reachable or default collection locked");
        store = candidate;
    }

    @AfterEach
    void cleanUp() {
        if (store != null) {
            store.delete(global);
            store.delete(project);
        }
    }

    @Test
    @DisplayName("put, get, replace, resolve and delete")
    void roundTrip() throws Exception {
        long start = System.nanoTime();
        store.put(global, firstSecret);
        long putMicros = (System.nanoTime() - start) / 1000;
        start = System.nanoTime();
        var read = store.get(global);
        long getMicros = (System.nanoTime() - start) / 1000;
        store.put(global, secondSecret);
        store.put(project, projectSecret);

        assertEquals(Optional.of(firstSecret), read);
        assertEquals(Optional.of(secondSecret), store.get(global));
        assertEquals(Optional.of(projectSecret), store.resolve(projectId, "github"));
        assertEquals(Optional.of(secondSecret), store.resolve(otherProject, "github"));
        assertTrue(store.delete(project));
        assertTrue(store.delete(global));
        assertFalse(store.delete(global));

        var values = new LinkedHashMap<String, Object>();
        values.put("put_us", putMicros);
        values.put("get_us", getMicros);
        values.put("round_trip", true);
        VerificationResults.write("gate5-secret-service-jvm-direct", values, true);
    }

    @Test
    @DisplayName("enumerates the entries of its service name only")
    void entries() {
        var other = new SecretServiceStore(DbusConnection.sessionBusSocket(System.getenv()).orElseThrow(),
                Posix.getuid(), service + "-other", SecretServiceStore.BUS_TIMEOUT);
        var foreign = CredentialAccount.global("foreign");
        assertEquals(List.of(), store.entries());
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
}
