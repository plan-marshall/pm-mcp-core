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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Enables and starts the user unit with {@code systemctl --user}, and disables and stops it again.
 * <p>
 * {@code daemon-reload} before {@code enable --now} makes the user manager read the unit as it was just written, so
 * a reinstall takes effect; {@code enable --now} on a running unit leaves it running with the old process, which a
 * restart of the runtime replaces.
 * <p>
 * {@code disable} needs the unit file, so {@link #disable(Path)} runs it before the file is removed; it removes the
 * link that starts the unit at login and leaves the service running. {@link #unload(Path)} then stops the unit,
 * which the user manager still holds after its file is gone, and reloads; a unit that is not loaded makes
 * {@code stop} exit with {@code 5}, which is no failure here. When the runtime that issues the uninstall is the
 * service itself, the stop ends it before the reload, and the user manager drops the unit at its next reload.
 */
final class SystemdServiceManager implements ServiceManager {

    /** The exit code of {@code systemctl stop} for a unit that is not loaded. */
    static final Set<Integer> NOT_LOADED = Set.of(5);

    private static final String SYSTEMCTL = "systemctl";

    private static final String USER = "--user";

    private final ProcessCall call;

    SystemdServiceManager(ProcessCall call) {
        this.call = call;
    }

    @Override
    public void load(Path unit) {
        run(systemctl("daemon-reload"), Set.of());
        run(systemctl("enable", "--now", name(unit)), Set.of());
    }

    @Override
    public void disable(Path unit) {
        run(systemctl("disable", name(unit)), Set.of());
    }

    @Override
    public void unload(Path unit) {
        run(systemctl("stop", name(unit)), NOT_LOADED);
        run(systemctl("daemon-reload"), Set.of());
    }

    private static List<String> systemctl(String... arguments) {
        var command = new ArrayList<String>(arguments.length + 2);
        command.add(SYSTEMCTL);
        command.add(USER);
        command.addAll(Arrays.asList(arguments));
        return List.copyOf(command);
    }

    private static String name(Path unit) {
        return unit.getFileName().toString();
    }

    private void run(List<String> command, Set<Integer> accepted) {
        var result = call.run(command);
        if (result.exitCode() != 0 && !accepted.contains(result.exitCode())) {
            throw new ServiceUnitException("Command '" + String.join(" ", command) + "' exited with "
                    + result.exitCode() + ": " + result.output().strip());
        }
    }
}
