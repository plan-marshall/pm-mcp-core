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

import java.util.Objects;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;

/**
 * Installs and uninstalls the per-user service of the service-managed start mode (PM-ARCH-7), the work behind
 * {@code pm-operator runtime install} and {@code pm-operator runtime uninstall}.
 * <p>
 * Install writes the unit, replacing an existing one, and loads it, so running install again after an upgrade is the
 * way to update the unit. When loading fails the unit stays written and the failure is reported; install can be run
 * again. Uninstall disables the unit, removes the file and unloads the unit; when disabling or unloading fails the
 * file is still removed, so a later install starts clean, and the failure is reported. Uninstall without a unit
 * issues no command and is no failure. A failing command of the service manager is logged as
 * {@code SERVICE_MANAGER_FAILED} and rethrown.
 * <p>
 * Install and uninstall may run inside the runtime the service started, and the command that stops the service then
 * ends the caller. The file work therefore comes before every command that can stop the service: install writes the
 * unit before loading it, and uninstall removes the unit before unloading it, so an ended caller never leaves a
 * stale or missing unit behind. On macOS a reinstall issued from such a runtime ends it after the old job is unloaded
 * and before the new one is loaded, so the job stays unloaded until the next login or the next install.
 *
 * @param unit    the unit file
 * @param manager the service manager of the unit's platform
 * @since 0.1
 */
public record ServiceUnitInstaller(ServiceUnitFile unit, ServiceManager manager) {

    private static final CuiLogger LOGGER = new CuiLogger(ServiceUnitInstaller.class);

    /**
     * @param unit    the unit file
     * @param manager the service manager of the unit's platform
     */
    public ServiceUnitInstaller {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(manager, "manager");
    }

    /**
     * Writes the unit and loads it.
     *
     * @throws ServiceUnitException if the unit cannot be written or the service manager fails
     */
    public void install() {
        unit.write();
        var path = unit.path();
        try {
            manager.load(path);
        } catch (ServiceUnitException e) {
            LOGGER.error(e, ERROR.SERVICE_MANAGER_FAILED, "load", e.getMessage());
            throw e;
        }
        LOGGER.info(INFO.SERVICE_UNIT_INSTALLED, path, unit.daemon());
    }

    /**
     * Disables the unit, removes its file and unloads it, if a unit is present. When the file cannot be removed the
     * unit is not unloaded, so the service keeps running with its unit in place.
     *
     * @throws ServiceUnitException if the service manager fails or the unit cannot be removed; the first failure is
     *                              thrown with the later ones suppressed
     */
    public void uninstall() {
        var path = unit.path();
        if (unit.exists()) {
            var failure = callManager("disable", () -> manager.disable(path));
            try {
                unit.remove();
            } catch (ServiceUnitException e) {
                throw firstOf(failure, e);
            }
            var unloadFailure = callManager("unload", () -> manager.unload(path));
            if (failure != null || unloadFailure != null) {
                throw firstOf(failure, unloadFailure);
            }
        }
        LOGGER.info(INFO.SERVICE_UNIT_UNINSTALLED, path);
    }

    /**
     * Runs one step of the service manager and logs its failure.
     *
     * @return the failure, or {@code null} if the step succeeded
     */
    private static ServiceUnitException callManager(String operation, Runnable step) {
        try {
            step.run();
            return null;
        } catch (ServiceUnitException e) {
            LOGGER.error(e, ERROR.SERVICE_MANAGER_FAILED, operation, e.getMessage());
            return e;
        }
    }

    /**
     * @return {@code first} with {@code later} suppressed, or {@code later} when there is no first failure
     */
    private static ServiceUnitException firstOf(ServiceUnitException first, ServiceUnitException later) {
        if (first == null) {
            return later;
        }
        if (later != null) {
            first.addSuppressed(later);
        }
        return first;
    }
}
