/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import java.util.Objects;

/**
 * What the session generation record says about the generation a call carries (PM-WORK-5): it is current, it was
 * fenced, it was superseded by a {@code pm-mcp enter} of the same session identity, or the record does not know it.
 * <p>
 * A superseded generation is fenced as well; it is told apart because a request carrying it is answered at the
 * transport with {@code generation_superseded}, and the relay then binds again, while every other call of a fenced
 * generation reaches its handler and is refused with {@code generation_fenced}.
 *
 * @since 0.1
 */
public sealed interface GenerationCheck {

    /**
     * @param id         the generation a call carries
     * @param generation its entry in the record; {@code null} if the record has none
     * @return the check of the generation
     */
    static GenerationCheck of(GenerationId id, SessionGeneration generation) {
        Objects.requireNonNull(id, "id");
        if (generation == null) {
            return new Unknown(id);
        }
        if (!generation.id().equals(id)) {
            throw new IllegalArgumentException("The entry %s is not that of %s".formatted(generation.id(), id));
        }
        if (!generation.isFenced()) {
            return new Current(generation);
        }
        return generation.fence().byEnter() ? new Superseded(generation) : new Fenced(generation);
    }

    private static void requireEntry(SessionGeneration generation) {
        Objects.requireNonNull(generation, "generation");
    }

    /**
     * The generation is the current one of its session identity.
     *
     * @param generation its entry
     * @since 0.1
     */
    record Current(SessionGeneration generation) implements GenerationCheck {

        /** @throws NullPointerException if the entry is {@code null} */
        public Current {
            requireEntry(generation);
        }
    }

    /**
     * A newer generation of the same session identity, issued by a relay, fenced the generation: its calls are
     * refused.
     *
     * @param generation its entry
     * @since 0.1
     */
    record Fenced(SessionGeneration generation) implements GenerationCheck {

        /** The code a call of a fenced generation is refused with. */
        public static final String CODE = "generation_fenced";

        /** @throws NullPointerException if the entry is {@code null} */
        public Fenced {
            requireEntry(generation);
        }
    }

    /**
     * A {@code pm-mcp enter} of the same session identity fenced the generation: a request carrying it is answered
     * at the transport.
     *
     * @param generation its entry
     * @since 0.1
     */
    record Superseded(SessionGeneration generation) implements GenerationCheck {

        /** The code a request of a generation superseded by an enter is answered with at the transport. */
        public static final String CODE = "generation_superseded";

        /** @throws NullPointerException if the entry is {@code null} */
        public Superseded {
            requireEntry(generation);
        }
    }

    /**
     * The record has no entry of the generation: it was never issued in this project, or its entry was removed.
     *
     * @param id the generation the call carries
     * @since 0.1
     */
    record Unknown(GenerationId id) implements GenerationCheck {

        /** @throws NullPointerException if the id is {@code null} */
        public Unknown {
            Objects.requireNonNull(id, "id");
        }
    }
}
