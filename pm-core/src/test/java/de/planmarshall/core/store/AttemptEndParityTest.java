/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.QueueTaskRecord;

/**
 * The metrics row copies {@code attempt_end} from the queue task record, and the two vocabularies are declared apart
 * because {@code core.queue} depends on {@code core.store} and not the other way round. This test keeps them equal: a
 * value added to one without the other would make the copy at the write site fail or be dropped.
 */
@DisplayName("The end of an attempt")
class AttemptEndParityTest {

    private static Set<String> wireNames(Enum<?>[] values, Function<Enum<?>, String> name) {
        return Arrays.stream(values).map(name).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("has the same wire names in the metrics row and in the queue task record")
    void wireNames() {
        assertEquals(wireNames(QueueTaskRecord.AttemptEnd.values(), value -> wireName(value)),
                wireNames(AttemptEnd.values(), value -> wireName(value)));
    }

    private static String wireName(Enum<?> value) {
        if (value instanceof AttemptEnd end) {
            return end.wireName();
        }
        return ((QueueTaskRecord.AttemptEnd) value).wireName();
    }
}
