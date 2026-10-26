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

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.runtime.launch.HarnessVerifier;

/**
 * The operating system's own answer for the executable of a process (PM-WORK-7, {@code agent_started}): the
 * {@code /proc/<pid>/exe} link on Linux and the {@code libproc} {@code proc_pidpath} downcall on macOS, each digested
 * through {@link HarnessVerifier#digestExecutable(Path)}.
 * <p>
 * Nothing is read from {@code /proc/<pid>/cmdline} or from {@code ps} output, and no path is resolved through
 * {@code PATH}: a command line is the process's own claim and a {@code PATH} lookup answers the name the process was
 * asked for, while this reading must answer the file it runs.
 * <p>
 * The {@code proc_pidpath} downcall descriptor, {@code proc_pidpath(jint, void*, jint) -> jint}, is registered in the
 * reachability metadata of this module,
 * {@code META-INF/native-image/de.planmarshall/pm-runtime-credentials/reachability-metadata.json}, next to the
 * descriptor of {@code kill}. A platform that is neither Linux nor macOS answers {@link Absent}
 * for every PID: the runtime runs on the two platforms the specification names, and the reading of a process table it
 * does not know is no reading.
 * <p>
 * Specification: {@code doc/specification/model-work/03-supervision-and-recycling.adoc} (A Worker Off Track, the
 * {@code agent_started} row).
 *
 * @since 0.1
 */
public final class SystemProcessExecutables implements ProcessExecutables {

    /** {@code PROC_PIDPATHINFO_MAXSIZE} of {@code libproc}: the longest path {@code proc_pidpath} writes. */
    private static final int PATH_BUFFER = 4096;

    private static final String LIBPROC = "/usr/lib/libproc.dylib";

    private static final String PID_PATH = "proc_pidpath";

    private final PathSource paths;
    private final HarnessVerifier.Digest digest;

    /** Reads through the branch of the operating system this runtime runs on. */
    public SystemProcessExecutables() {
        this(branch(System.getProperty("os.name", "")), HarnessVerifier::digestExecutable);
    }

    /**
     * @param paths  where the executable path of a PID is read
     * @param digest the digest of an executable file
     * @throws NullPointerException if an argument is {@code null}
     */
    SystemProcessExecutables(PathSource paths, HarnessVerifier.Digest digest) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.digest = Objects.requireNonNull(digest, "digest");
    }

    @Override
    public Reading read(long pid) {
        requirePid(pid);
        var path = paths.of(pid);
        if (path.isEmpty()) {
            return new Absent();
        }
        var absolute = path.get().toAbsolutePath().normalize();
        var sha256 = digest.sha256(absolute);
        if (sha256.isEmpty()) {
            return new NotRegular(absolute);
        }
        return new Found(new ProcessExecutable(pid, absolute, ProcessExecutable.DIGEST_PREFIX + sha256.get()));
    }

    /**
     * {@inheritDoc}
     * <p>
     * A process whose executable path is read but not digested is ordinary in a detection pass: only a path that can be
     * the enrolled one is worth the hash of a harness binary.
     */
    @Override
    public Optional<Path> path(long pid) {
        requirePid(pid);
        return paths.of(pid).map(path -> path.toAbsolutePath().normalize());
    }

    private static void requirePid(long pid) {
        if (pid < 1 || pid > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Not a PID: " + pid);
        }
    }

    /**
     * @param osName {@code os.name} of the runtime
     * @return the reading of that operating system, or one that answers nothing for a platform this runtime does not
     *         run on
     */
    static PathSource branch(String osName) {
        var name = osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("linux")) {
            return SystemProcessExecutables::linuxPath;
        }
        if (name.startsWith("mac")) {
            return macPaths();
        }
        return pid -> Optional.empty();
    }

    /** The Linux link {@code /proc/<pid>/exe}, whose target is the absolute path of the running file. */
    private static Optional<Path> linuxPath(long pid) {
        try {
            return Optional.of(Files.readSymbolicLink(Path.of("/proc", Long.toString(pid), "exe")));
        } catch (IOException _) {
            // no such process, a process that ended between the two reads, or an executable this user may not read
            return Optional.empty();
        }
    }

    /**
     * @return the reading through the {@code libproc} downcall, or one that answers nothing when {@code libproc} is not
     *         present: the framework is only reachable on macOS
     */
    private static PathSource macPaths() {
        SymbolLookup libproc;
        try {
            libproc = SymbolLookup.libraryLookup(LIBPROC, Arena.global());
        } catch (IllegalArgumentException _) {
            return pid -> Optional.empty();
        }
        var symbol = libproc.find(PID_PATH);
        if (symbol.isEmpty()) {
            return pid -> Optional.empty();
        }
        var pidPath = Linker.nativeLinker().downcallHandle(symbol.get(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
        return pid -> macPath(pidPath, pid);
    }

    /** The {@code proc_pidpath} call: the number of bytes written, or 0 when the process answers no path. */
    private static Optional<Path> macPath(MethodHandle pidPath, long pid) {
        try (var arena = Arena.ofConfined()) {
            var buffer = arena.allocate(PATH_BUFFER);
            int written;
            try {
                written = (int) pidPath.invokeExact((int) pid, buffer, PATH_BUFFER);
                // cui-rewrite:disable InvalidExceptionUsageRecipe
            } catch (Throwable t) {
                if (t instanceof Error e) {
                    throw e;
                }
                throw new IllegalStateException("proc_pidpath(%d) failed".formatted(pid), t);
            }
            if (written <= 0) {
                return Optional.empty();
            }
            var path = buffer.getString(0);
            return path.isEmpty() ? Optional.empty() : Optional.of(Path.of(path));
        }
    }

    /**
     * Where the executable path of a process is read.
     *
     * @since 0.1
     */
    @FunctionalInterface
    interface PathSource {

        /**
         * @param pid the PID of the process
         * @return the path the operating system reports, or empty when it reports none
         */
        Optional<Path> of(long pid);
    }
}
