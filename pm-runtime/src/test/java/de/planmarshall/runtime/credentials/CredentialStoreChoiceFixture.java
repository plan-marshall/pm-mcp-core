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

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import lombok.experimental.UtilityClass;

import de.planmarshall.api.MachinePaths;

/**
 * A {@link CredentialStoreChoice} for tests outside this package, whose OS store is a store the test supplies, so a
 * test of a caller of the choice runs on every OS without a keyring.
 */
@UtilityClass
public final class CredentialStoreChoiceFixture {

    /**
     * @param base    the base directory of the runtime
     * @param service the service name of the instance
     * @param osStore the store that serves as the Keychain of a macOS machine
     * @return the choice over {@code base}, whose OS store is {@code osStore}
     */
    public static CredentialStoreChoice withOsStore(Path base, String service, SecretStore osStore) {
        return withOsStoreWhile(base, service, osStore, () -> true);
    }

    /**
     * @param base      the base directory of the runtime
     * @param service   the service name of the instance
     * @param osStore   the store that serves as the Keychain of a macOS machine
     * @param available whether the Keychain loads at a probe; a probe while it does not fails as an unloadable
     *                  Keychain
     * @return the choice over {@code base}, whose OS store is {@code osStore} while {@code available} holds
     */
    public static CredentialStoreChoice withOsStoreWhile(Path base, String service, SecretStore osStore,
            BooleanSupplier available) {
        Objects.requireNonNull(osStore, "osStore");
        Objects.requireNonNull(available, "available");
        return new CredentialStoreChoice(base, service, MachinePaths.Os.MACOS, Map.of(),
                new CredentialStoreChoice.Keyrings() {

                    @Override
                    public SecretStore keychain(String name) {
                        if (!available.getAsBoolean()) {
                            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                                    "the Keychain of the fixture is unavailable");
                        }
                        return osStore;
                    }

                    @Override
                    public Optional<SecretStore> secretService(Path socket, String name) {
                        return Optional.empty();
                    }
                });
    }
}
