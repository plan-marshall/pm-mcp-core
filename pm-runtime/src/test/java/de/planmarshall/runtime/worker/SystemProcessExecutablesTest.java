/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.runtime.launch.HarnessVerifier;

/**
 * The operating system's reading of a process's executable (PM-WORK-7, {@code agent_started}): the path and digest of a
 * live process, the absence of a process that does not exist, an executable that is not a regular file, and the branch
 * the reading is selected by.
 */
@DisplayName("the executable reader of the operating system")
class SystemProcessExecutablesTest {

    private static final long CURRENT_PID = ProcessHandle.current().pid();

    @TempDir
    Path base;

    @Test
    @DisplayName("the reader resolves the test JVM's own executable path and digest")
    void resolvesTheOwnExecutable() {
        var reading = new SystemProcessExecutables().read(CURRENT_PID);
        var executable = assertInstanceOf(ProcessExecutables.Found.class, reading).executable();

        assertAll(
                () -> assertEquals(CURRENT_PID, executable.pid()),
                () -> assertTrue(executable.path().isAbsolute(), "the path is absolute"),
                () -> assertTrue(Files.isRegularFile(executable.path()), "the path is a regular file"),
                () -> assertEquals(ProcessExecutable.DIGEST_PREFIX
                        + HarnessVerifier.digestExecutable(executable.path()).orElseThrow(),
                        executable.digest(), "the digest is the file's"),
                () -> assertEquals(HarnessVerifier.digestExecutable(executable.path()).orElseThrow(),
                        executable.sha256(), "the sha256 is the digest without its prefix"));
    }

    @Test
    @DisplayName("the Linux branch reads the kernel's own link of the process")
    void theLinuxBranchReadsTheProcLink() throws Exception {
        Assumptions.assumeTrue(onLinux(), "the /proc link exists on Linux only");
        var reading = new SystemProcessExecutables().read(CURRENT_PID);

        assertEquals(Path.of("/proc/self/exe").toRealPath(),
                assertInstanceOf(ProcessExecutables.Found.class, reading).executable().path());
    }

    @Test
    @DisplayName("a PID no process holds yields absent")
    void anAbsentProcessYieldsAbsent() {
        var reading = new SystemProcessExecutables().read(Integer.MAX_VALUE);

        assertInstanceOf(ProcessExecutables.Absent.class, reading);
    }

    @Test
    @DisplayName("an executable that is not a regular file yields not-regular")
    void aDeletedExecutableYieldsNotRegular() throws Exception {
        var deleted = Files.createTempFile(base, "deleted", ".bin");
        Files.delete(deleted);
        var reader = new SystemProcessExecutables(pid -> Optional.of(deleted), HarnessVerifier::digestExecutable);

        var reading = reader.read(4711);

        assertEquals(deleted.toAbsolutePath().normalize(),
                assertInstanceOf(ProcessExecutables.NotRegular.class, reading).path(),
                "the answer names the path it could not digest");
    }

    @Test
    @DisplayName("a process that answers no path yields absent, not not-regular")
    void aProcessWithoutAPathYieldsAbsent() {
        var reader = new SystemProcessExecutables(pid -> Optional.empty(), HarnessVerifier::digestExecutable);

        assertInstanceOf(ProcessExecutables.Absent.class, reader.read(4711));
    }

    @Test
    @DisplayName("the branch is selected from os.name")
    void theBranchFollowsTheOperatingSystemName() {
        var unknown = SystemProcessExecutables.branch("Plan 9");
        var linux = SystemProcessExecutables.branch("Linux");
        var mac = SystemProcessExecutables.branch("Mac OS X");

        assertAll(
                () -> assertTrue(unknown.of(CURRENT_PID).isEmpty(), "an unknown platform answers nothing"),
                () -> assertEquals(onLinux(), linux.of(CURRENT_PID).isPresent(),
                        "the Linux branch reads /proc, which exists on Linux only"),
                () -> assertTrue(!onMac() || mac.of(CURRENT_PID).isPresent(),
                        "the macOS branch answers where libproc exists"),
                () -> assertTrue(!onLinux() || mac.of(CURRENT_PID).isEmpty(),
                        "the macOS branch of another platform answers nothing"));
    }

    private static boolean onLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("linux");
    }

    private static boolean onMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }
}
