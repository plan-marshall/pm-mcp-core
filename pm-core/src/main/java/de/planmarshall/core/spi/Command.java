/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

import de.planmarshall.core.id.CommandId;
import de.planmarshall.core.id.CorrelationId;

/**
 * One run of a primitive as a value (PM-IMPL-5): which primitive runs, with which bound input, for which cause and
 * when. The engine builds the command from the record its parameter binder returns and runs the primitive with
 * {@link #input()}; the primitive stays the handler. A command lives in memory only: the engine returns it inside the
 * {@link CommandSettlement} of the run and never persists it.
 * <p>
 * The input may hold free text a session submitted, so the command never prints it: {@link #toString()} names the
 * class of the input, never its values, and no exception message of the constructor quotes it.
 * <p>
 * The record has six components, so it carries a {@code @Builder} beside its validating canonical constructor.
 *
 * @param commandId     the id of the run, drawn by the engine for every primitive it runs
 * @param correlationId the id of the advance the run belongs to, shared by every command of the advance
 * @param primitiveId   the id of the primitive that runs, never blank
 * @param input         the parameter record the primitive runs with, exactly the instance the binder returned
 * @param provenance    the cause of the advance
 * @param issuedAt      the instant the engine issued the command, read from its clock
 * @since 0.1
 */
@Builder
public record Command(
CommandId commandId,
CorrelationId correlationId,
String primitiveId,
Record input,
Provenance provenance,
Instant issuedAt) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the primitive id is blank
     */
    public Command {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(primitiveId, "primitiveId");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (primitiveId.isBlank()) {
            throw new IllegalArgumentException("a primitive id must not be blank");
        }
    }

    /**
     * @return the two ids, the primitive id, the provenance, the name of the class of the input and the instant;
     *         never a value of the input
     */
    @Override
    public String toString() {
        return "Command[commandId=%s, correlationId=%s, primitiveId=%s, input=%s, provenance=%s, issuedAt=%s]"
                .formatted(commandId, correlationId, primitiveId, input.getClass().getName(), provenance,
                        issuedAt);
    }
}
