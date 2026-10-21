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
import java.util.List;
import java.util.Set;

/**
 * Loads the LaunchAgent into the user's GUI domain {@code gui/<uid>} with {@code launchctl bootstrap} and unloads it
 * with {@code launchctl bootout}.
 * <p>
 * {@code bootstrap} refuses a job that is already loaded, so {@link #load(Path)} boots the job out first; a job that
 * is not loaded makes {@code bootout} exit with {@code 3} ("No such process") or, on newer macOS, {@code 113}
 * ("Could not find specified service"), which is no failure here.
 * <p>
 * {@code launchd} reads the start at login from the LaunchAgent file alone, so removing the file disables the job
 * and {@link #disable(Path)} does nothing. When the runtime that issues an install or uninstall is the job itself,
 * the {@code bootout} ends it; on a reinstall the {@code bootstrap} after it is then never issued and the job stays
 * unloaded until the next login or the next start. The unit file on disk is the new one, and the restore step of the
 * next start writes the kept previous unit back and reloads it; on macOS that reload's {@code bootout} ends the
 * started runtime, and the next login loads the previous unit
 * ({@link ServiceUnitInstaller#restore()}).
 */
final class LaunchdServiceManager implements ServiceManager {

    /** The exit codes of {@code launchctl bootout} for a job that is not loaded. */
    static final Set<Integer> NOT_LOADED = Set.of(3, 113);

    private final ProcessCall call;
    private final String domain;

    LaunchdServiceManager(ProcessCall call, long uid) {
        this.call = call;
        this.domain = "gui/" + uid;
    }

    @Override
    public void load(Path unit) {
        bootout();
        run(List.of("launchctl", "bootstrap", domain, unit.toString()), Set.of());
    }

    @Override
    public void unload(Path unit) {
        bootout();
    }

    private void bootout() {
        run(List.of("launchctl", "bootout", domain + "/" + ServiceUnitFile.LAUNCHD_LABEL), NOT_LOADED);
    }

    private void run(List<String> command, Set<Integer> accepted) {
        var result = call.run(command);
        if (result.exitCode() != 0 && !accepted.contains(result.exitCode())) {
            throw new ServiceUnitException("Command '" + String.join(" ", command) + "' exited with "
                    + result.exitCode() + ": " + result.output().strip());
        }
    }
}
