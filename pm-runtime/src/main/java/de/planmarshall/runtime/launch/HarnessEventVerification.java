/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityEventMapping;
import de.planmarshall.runtime.worker.ActivityEventMapper;

/**
 * The {@code events} stage of the profile verification (Client Profiles § Profile Verification): it maps a recorded
 * output of the harness through the profile's {@code headless.events} and records per activity kind whether it was
 * {@code reported} or {@code missing}. A mapping that does not resolve on the recorded output is {@code missing}, and
 * the stage fails; a harness whose profile declares no event stream is not measured by this stage.
 * <p>
 * The recorded output is a fixture the verification writes for the test, never the content of the product. The stage
 * reads only the mapping's own pointers, so a value it does not declare cannot make a kind appear.
 *
 * @since 0.1
 */
public final class HarnessEventVerification {

    /** The result of one kind. */
    public static final String REPORTED = "reported";

    /** A kind the recorded output never carried. */
    public static final String MISSING = "missing";

    private HarnessEventVerification() {
    }

    /**
     * @param mapping       the profile's events mapping; {@code null} for a harness without an event stream
     * @param usagePointers the profile's usage pointers, by usage column
     * @param outputLines   the recorded output of the harness
     * @return the stage result, one entry per declared kind
     */
    public static Result verify(ActivityEventMapping mapping, Map<String, String> usagePointers,
            List<String> outputLines) {
        Objects.requireNonNull(usagePointers, "usagePointers");
        Objects.requireNonNull(outputLines, "outputLines");
        if (mapping == null) {
            return new Result(true, Map.of());
        }
        var mapper = new ActivityEventMapper(mapping, usagePointers);
        var seen = outputLines.stream().map(mapper::map).flatMap(Optional::stream)
                .map(ActivityEventMapper.Mapped::kind).toList();
        var results = new LinkedHashMap<String, String>();
        for (var kind : ActivityEntry.Kind.values()) {
            if (kind.source() == ActivityEntry.Source.HARNESS && mapping.kinds().containsKey(kind)) {
                results.put(kind.wireName(), seen.contains(kind) ? REPORTED : MISSING);
            }
        }
        var passed = results.values().stream().noneMatch(MISSING::equals);
        return new Result(passed, results);
    }

    /**
     * @param passed whether every declared kind was reported
     * @param kinds  the result per declared kind, {@code reported} or {@code missing}
     * @since 0.1
     */
    public record Result(boolean passed, Map<String, String> kinds) {

        /** Copies the results. */
        public Result {
            kinds = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(kinds, "kinds")));
        }

        /** @return the kinds that were not reported */
        public Set<String> missing() {
            return kinds.entrySet().stream().filter(entry -> MISSING.equals(entry.getValue()))
                    .map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
