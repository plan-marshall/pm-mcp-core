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

import java.util.Locale;
import java.util.Objects;

import de.planmarshall.core.profile.VerificationRecord;

/**
 * The platform the runtime itself runs on (PM-CLIENT-5, Runtime check on load): the operating system and the
 * architecture in the vocabulary a verification record writes, so the verification-record gate can compare a record's
 * {@code platform} with the machine it would be honoured on.
 * <p>
 * The values are read from {@link System#getProperty(String)} and normalised into the record's spelling
 * ({@code linux}, {@code darwin} and {@code x86_64}, {@code aarch64}), because the JVM reports {@code Linux} and
 * {@code amd64} where the record writes {@code linux} and {@code x86_64}. The record's {@code platform.confinement} is
 * not part of this value: the confinement is decided per job by the launcher, not by the machine the runtime runs on.
 * A test supplies its own value instead of reading the properties, so the comparison is exercised on every host.
 *
 * @param os   the operating system, in the record's spelling
 * @param arch the architecture, in the record's spelling
 * @since 0.1
 */
public record RuntimePlatform(String os, String arch) {

    private static final String OS_NAME = "os.name";
    private static final String OS_ARCH = "os.arch";
    private static final String LINUX = "linux";
    private static final String DARWIN = "darwin";
    private static final String WINDOWS = "windows";
    private static final String X86_64 = "x86_64";
    private static final String AARCH64 = "aarch64";

    /**
     * @throws NullPointerException if a value is {@code null}
     */
    public RuntimePlatform {
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(arch, "arch");
    }

    /**
     * @return the platform this runtime runs on, read from {@code os.name} and {@code os.arch}
     */
    public static RuntimePlatform current() {
        return new RuntimePlatform(normaliseOs(System.getProperty(OS_NAME)), normaliseArch(System.getProperty(OS_ARCH)));
    }

    /**
     * @param other a record's platform
     * @return whether the record was written on this operating system and architecture
     * @throws NullPointerException if the platform is {@code null}
     */
    public boolean matches(VerificationRecord.Platform other) {
        Objects.requireNonNull(other, "other");
        return os.equals(other.os()) && arch.equals(other.arch());
    }

    private static String normaliseOs(String name) {
        Objects.requireNonNull(name, OS_NAME);
        var lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith(LINUX)) {
            return LINUX;
        }
        if (lower.startsWith("mac") || lower.startsWith(DARWIN)) {
            return DARWIN;
        }
        if (lower.startsWith(WINDOWS)) {
            return WINDOWS;
        }
        return lower;
    }

    private static String normaliseArch(String arch) {
        Objects.requireNonNull(arch, OS_ARCH);
        var lower = arch.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "amd64", X86_64 -> X86_64;
            case "arm64", AARCH64 -> AARCH64;
            default -> lower;
        };
    }
}
