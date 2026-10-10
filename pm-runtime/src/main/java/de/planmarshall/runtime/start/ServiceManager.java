/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The per-user service manager of the platform, which loads a written {@link ServiceUnitFile} so the service
 * starts at login and restarts on failure, and unloads it again.
 * <p>
 * Install and uninstall may run inside the runtime the unit manages, so a command that stops the service can end
 * the caller. Only {@link #load(Path)} and {@link #unload(Path)} may stop the service, and both run after the file
 * work is done: {@code load} after the unit is written, {@code unload} after it is removed.
 * {@link #disable(Path)} runs before the removal and never stops the service.
 *
 * @since 0.1
 */
public interface ServiceManager {

    /**
     * Loads and enables the unit, replacing a job of the same unit that is already loaded.
     *
     * @param unit the written unit file
     * @throws ServiceUnitException if a command of the service manager fails
     */
    void load(Path unit);

    /**
     * Takes the unit out of the start at login while its file is still present, without stopping the service. The
     * default does nothing, for a service manager that reads the start at login from the unit file alone.
     *
     * @param unit the unit file, still present
     * @throws ServiceUnitException if a command of the service manager fails
     */
    default void disable(Path unit) {
        // nothing beside the unit file to take back
    }

    /**
     * Stops and unloads the unit after its file was removed; a unit that is not loaded is no failure.
     *
     * @param unit the location of the removed unit file
     * @throws ServiceUnitException if a command of the service manager fails
     */
    void unload(Path unit);

    /**
     * @param platform the platform of the unit
     * @param call     the call that runs the commands of the service manager
     * @param uid      the user id of the runtime's user, which names the {@code launchd} GUI domain
     * @return the service manager of the platform
     */
    static ServiceManager forPlatform(ServiceUnitFile.Platform platform, ProcessCall call, long uid) {
        Objects.requireNonNull(call, "call");
        return switch (platform) {
            case LAUNCHD -> new LaunchdServiceManager(call, uid);
            case SYSTEMD -> new SystemdServiceManager(call);
        };
    }
}
