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

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One value of a project over the machine's value (Store Schemas § Project Values over Machine Values): the machine's
 * value is the proposal, the project records its own value only when it changed, and accepting the proposal records
 * nothing, so the project keeps following the machine. The rule is the same for every key and aspect; the nearest level
 * wins, and a level that says nothing defers to the next.
 *
 * @param machineValue the machine's value, shown as the proposal
 * @param projectValue the project's own value; {@code null} while the project follows the machine
 * @param <T>          the value of the key
 * @since 0.1
 */
public record ProjectValueProposal<T>(T machineValue, @Nullable T projectValue) {

    /**
     * @throws NullPointerException if the machine's value is {@code null}
     */
    public ProjectValueProposal {
        Objects.requireNonNull(machineValue, "machineValue");
    }

    /**
     * @param machineValue the machine's value of the key, as its proposal
     * @param <T>          the value of the key
     * @return the proposal of a project that follows the machine
     */
    public static <T> ProjectValueProposal<T> following(T machineValue) {
        return new ProjectValueProposal<>(machineValue, null);
    }

    /**
     * @return the effective value: the project's own if it recorded one, the machine's otherwise
     */
    public T effective() {
        return projectValue == null ? machineValue : projectValue;
    }

    /** @return whether the project records no value of its own and follows the machine */
    public boolean followsMachine() {
        return projectValue == null;
    }

    /**
     * @param chosen the value the operator chose
     * @return whether the choice is a change the project records; {@code false} for the machine's own value, which
     *         records nothing
     */
    public boolean recordsChange(T chosen) {
        return !Objects.equals(chosen, machineValue);
    }

    /**
     * @param chosen the value the operator chose
     * @return the value to record for the project; empty when the choice is the machine's value and nothing is recorded
     */
    public Optional<T> recorded(T chosen) {
        return recordsChange(chosen) ? Optional.of(chosen) : Optional.empty();
    }
}
