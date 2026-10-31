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

import de.cuioss.tools.logging.CuiLogger;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * Installs and uninstalls the per-user service of the service-managed start mode (PM-ARCH-7), the work behind
 * {@code pm-operator runtime install} and {@code pm-operator runtime uninstall}.
 * <p>
 * Install keeps the unit in force beside the unit, writes the new unit, replacing an existing one, and loads it, so
 * running install again after an upgrade is the way to update the unit. When loading fails, the kept unit is written
 * back and loaded again, or, when no unit was installed, the new file is removed and nothing is unloaded; the load
 * failure is then reported with the restore's own failure suppressed, so the installation in force is unchanged and
 * install can be run again. A reinstall that the end of the caller cuts between the unload and the load, as on macOS,
 * leaves the kept unit beside the unit, and {@link #restore()}, the restore step a start runs first, writes it back
 * and loads it (the assembly does not wire that step yet). Uninstall
 * drops what an earlier reinstall kept, disables the unit, removes the file and unloads the unit; when disabling or
 * unloading fails the file is still removed, so a later install starts clean, and the failure is reported. Uninstall
 * without a unit issues no command and is no failure. A failing command of the service manager is logged as
 * {@code SERVICE_MANAGER_FAILED} and rethrown.
 * <p>
 * Install and uninstall may run inside the runtime the service started, and the command that stops the service then
 * ends the caller. The file work therefore comes before every command that can stop the service: install keeps the
 * previous unit and writes the new unit before loading it, the restore writes the kept unit before loading it, and
 * uninstall removes the unit before unloading it, so an ended caller never leaves a stale or missing unit behind. On
 * macOS a reinstall issued from such a runtime ends it after the old job is unloaded and before the new one is loaded;
 * the kept unit then stays beside the unit and the restore step of the next start writes it back and loads it.
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
     * Keeps the unit in force, writes the unit and loads it. When loading the new unit fails, the kept unit is written
     * back and loaded again, or, when no unit was installed, the new unit file is removed; the load failure is thrown
     * with the restore's own failure suppressed. A unit that cannot be written drops a pair this call kept, and
     * leaves a pending pair of an interrupted reinstall as it is.
     *
     * @throws ServiceUnitException if the unit cannot be written or the service manager fails; the load failure is
     *                              thrown with a failing restore suppressed
     */
    public void install() {
        var kept = unit.keepForRestore();
        var path = unit.path();
        try {
            unit.write();
        } catch (ServiceUnitException e) {
            if (kept) {
                throw discardAfter(e);
            }
            throw e;
        }
        try {
            load(path);
        } catch (ServiceUnitException e) {
            try {
                restoreKept();
            } catch (ServiceUnitException restoreFailure) {
                e.addSuppressed(restoreFailure);
            }
            throw e;
        }
        dropAfterLoad(path);
        LOGGER.info(INFO.SERVICE_UNIT_INSTALLED, path, unit.daemon());
    }

    /**
     * Disables the unit, removes its file and unloads it, if a unit is present. When the file cannot be removed the
     * unit is not unloaded, so the service keeps running with its unit in place. What an earlier reinstall kept for a
     * failed load is dropped first, so a later start does not restore a unit that was uninstalled.
     *
     * @throws ServiceUnitException if the service manager fails or the unit cannot be removed; the first failure is
     *                              thrown with the later ones suppressed
     */
    public void uninstall() {
        unit.discardRestore();
        var path = unit.path();
        if (unit.exists()) {
            var failure = callManager("disable", () -> manager.disable(path));
            try {
                unit.remove();
            } catch (ServiceUnitException e) {
                throw firstOf(failure, Optional.of(e)).orElseThrow();
            }
            var unloadFailure = callManager("unload", () -> manager.unload(path));
            if (failure.isPresent() || unloadFailure.isPresent()) {
                throw firstOf(failure, unloadFailure).orElseThrow();
            }
        }
        LOGGER.info(INFO.SERVICE_UNIT_UNINSTALLED, path);
    }

    /**
     * The restore step a start runs first: restores the unit that an interrupted reinstall kept and loads it again.
     * On macOS a reinstall issued from the runtime that the unit started ends that runtime after the old job was
     * unloaded and before the new one was loaded, so the next start finds what the reinstall kept: the bytes of the
     * unit in force were written beside the unit before the load, and they are written back and loaded here; when no
     * unit was installed, the new unit file is removed and nothing is unloaded. A start without a pending reinstall
     * does nothing and issues no command. The kept state is dropped before the load, because the load can end the
     * caller; the unit file in force is the previous one from then on, and the next login loads it.
     *
     * @return {@code true} when a reinstall was pending and its kept unit was restored or removed, {@code false} when
     *         no reinstall was pending
     * @throws ServiceUnitException if the kept unit cannot be read, written back or removed, or its marker cannot be
     *                              deleted, which leaves the pair for the next start, or if the restored unit cannot be
     *                              loaded after the pair was dropped, which no later start retries
     */
    public boolean restore() {
        if (!unit.restorePending()) {
            return false;
        }
        restoreKept();
        return true;
    }

    /**
     * Restores the unit that the reinstall kept and loads it again, or removes the new unit when no unit was
     * installed. The file work runs before the load, which can end the caller, so the kept state is dropped before
     * the load; a load that fails after it is logged and rethrown, and no later start retries it. A failed restore is
     * logged and rethrown.
     *
     * @return {@code true} when the kept unit was written back, {@code false} when the unit was removed
     */
    private boolean restoreKept() {
        var path = unit.path();
        try {
            var restored = unit.restore();
            unit.discardRestore();
            if (restored) {
                load(path);
                LOGGER.info(INFO.SERVICE_UNIT_RESTORED, path);
            } else {
                LOGGER.info(INFO.SERVICE_UNIT_RESTORE_WITHOUT_PREVIOUS, path);
            }
            return restored;
        } catch (ServiceUnitException e) {
            LOGGER.warn(e, WARN.SERVICE_UNIT_RESTORE_FAILED, path, e.getMessage());
            throw e;
        }
    }

    /**
     * Loads the unit and logs a failing command of the service manager; the failure is rethrown.
     *
     * @param path the written unit file
     */
    private void load(Path path) {
        try {
            manager.load(path);
        } catch (ServiceUnitException e) {
            LOGGER.error(e, ERROR.SERVICE_MANAGER_FAILED, "load", e.getMessage());
            throw e;
        }
    }

    /**
     * Drops the kept pair after a load that succeeded. A failure never fails the install: the warning names that the
     * pair stayed, and a marker that stayed makes the next start treat the reinstall as unfinished.
     *
     * @param path the unit file that was loaded
     */
    private void dropAfterLoad(Path path) {
        try {
            unit.discardRestore();
        } catch (ServiceUnitException e) {
            LOGGER.warn(e, WARN.SERVICE_UNIT_KEPT_NOT_DROPPED, path, e.getMessage());
        }
    }

    /**
     * Drops the pair that this install kept when the unit itself cannot be written, and answers the failure the
     * caller rethrows with the drop's own failure suppressed.
     *
     * @param failure the failure of the write
     * @return {@code failure}, with the drop's failure suppressed when it had one
     */
    private ServiceUnitException discardAfter(ServiceUnitException failure) {
        try {
            unit.discardRestore();
        } catch (ServiceUnitException discardFailure) {
            failure.addSuppressed(discardFailure);
        }
        return failure;
    }

    /**
     * Runs one step of the service manager and logs its failure.
     *
     * @return the failure, or empty if the step succeeded
     */
    private static Optional<ServiceUnitException> callManager(String operation, Runnable step) {
        try {
            step.run();
            return Optional.empty();
        } catch (ServiceUnitException e) {
            LOGGER.error(e, ERROR.SERVICE_MANAGER_FAILED, operation, e.getMessage());
            return Optional.of(e);
        }
    }

    /**
     * @return {@code first} with {@code later} suppressed, or {@code later} when there is no first failure
     */
    private static Optional<ServiceUnitException> firstOf(Optional<ServiceUnitException> first,
            Optional<ServiceUnitException> later) {
        if (first.isEmpty()) {
            return later;
        }
        later.ifPresent(first.get()::addSuppressed);
        return first;
    }
}
