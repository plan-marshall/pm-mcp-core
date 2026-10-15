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
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import de.planmarshall.core.queue.SessionGenerationStore;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;

/**
 * The step {@link SweepStep.Name#FENCED_SESSION_GENERATIONS} of the periodic sweep: the entries of the project's
 * session generation record {@code queue/session-generations.json} fenced before the instant the step is given are
 * removed under the queue lock (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Periodic Sweep).
 * <p>
 * The evidence is a fence and its age: a current generation is never removed, and neither is one fenced at or after
 * the instant. The removal is one write of the record ({@link SessionGenerationStore#removeFencedOlderThan(Instant)});
 * a project without a record has nothing to remove. A call that carries a removed generation finds it unknown. The
 * step reports how many entries it removed; a record that cannot be read or written is a failure of the step.
 *
 * @since 0.1
 */
public final class FencedSessionGenerationsSweep implements SweepStep {

    private static final CuiLogger LOGGER = new CuiLogger(FencedSessionGenerationsSweep.class);

    private final Path base;
    private final LockManager lockManager;
    private final Clock clock;

    /**
     * @param base        the base directory of the runtime
     * @param lockManager the lock manager the queue lock is taken on
     * @param clock       the clock of the session generation store
     */
    public FencedSessionGenerationsSweep(Path base, LockManager lockManager, Clock clock) {
        this.base = Objects.requireNonNull(base, "base");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Name name() {
        return Name.FENCED_SESSION_GENERATIONS;
    }

    /**
     * Removes the generations of the project fenced before the instant.
     *
     * @param projectId the id of the enrolled project, whose recovery has finished
     * @param olderThan the instant before which a fenced generation is old enough to be removed
     * @return how many generations were removed; a failure if the record could not be read or written
     */
    @Override
    public RecoveryStep.Outcome run(String projectId, Instant olderThan) {
        Objects.requireNonNull(olderThan, "olderThan");
        var store = new SessionGenerationStore(base, projectId, lockManager, clock);
        try {
            var removed = store.removeFencedOlderThan(olderThan).size();
            if (removed > 0) {
                LOGGER.info(INFO.FENCED_SESSION_GENERATIONS_SWEPT, removed, projectId);
            }
            return new RecoveryStep.Outcome.Done("removed " + removed + " fenced session generations");
        } catch (StoreRefusal e) {
            return new RecoveryStep.Outcome.Failed("session generation record refused (" + e.outcome().code() + "): "
                    + e.getMessage());
        } catch (UncheckedIOException | InternalFaultException e) {
            return new RecoveryStep.Outcome.Failed("fenced session generations not swept: " + e.getMessage());
        }
    }
}
