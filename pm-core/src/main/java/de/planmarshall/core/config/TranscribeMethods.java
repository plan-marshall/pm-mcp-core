/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The speech transcription methods a project allows (Store Schemas § Project Values over Machine Values; Model Work §
 * Speech Transcription): the one value a project may only narrow. The machine's methods are the outer bound, the project
 * offers a subset of them, and a method the machine does not allow is refused as {@code invalid_arguments} rather than
 * recorded.
 *
 * @param methods the methods the project allows, a subset of the machine's
 * @since 0.1
 */
public record TranscribeMethods(List<String> methods) {

    /** The reason code of a project that widens the machine's methods. */
    public static final String INVALID_ARGUMENTS = "invalid_arguments";

    /**
     * @throws NullPointerException     if the methods are {@code null}
     * @throws IllegalArgumentException if the project allows no method
     */
    public TranscribeMethods {
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("A project allows at least one transcription method");
        }
        if (methods.stream().distinct().count() != methods.size()) {
            throw new IllegalArgumentException("A project names each transcription method once");
        }
    }

    /**
     * Narrows the machine's methods to the project's subset.
     *
     * @param chosen        the methods the operator chose for the project
     * @param machineMethods the machine's methods, the outer bound
     * @return the project's methods
     * @throws IllegalArgumentException with {@value #INVALID_ARGUMENTS} in the message if a chosen method is not one the
     *                                  machine allows
     */
    public static TranscribeMethods chosen(List<String> chosen, Collection<String> machineMethods) {
        Objects.requireNonNull(machineMethods, "machineMethods");
        var narrowed = new TranscribeMethods(chosen);
        for (var method : narrowed.methods()) {
            if (!machineMethods.contains(method)) {
                throw new IllegalArgumentException(
                        "%s: the method '%s' is not one the machine allows".formatted(INVALID_ARGUMENTS, method));
            }
        }
        return narrowed;
    }
}
