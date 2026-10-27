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
 * The metrics row marks a continuation with the form the queue task record holds, and the two vocabularies are declared
 * apart because {@code core.queue} depends on {@code core.store} and not the other way round. This test keeps them
 * equal: a form added to one without the other would make the mark at the write site fail or be dropped.
 */
@DisplayName("The continuation mark")
class ContinuationMarkParityTest {

    @Test
    @DisplayName("has the same wire names in the metrics row and in the queue task record")
    void wireNames() {
        assertEquals(
                wireNames(QueueTaskRecord.ContinuationForm.values(),
                        QueueTaskRecord.ContinuationForm::wireName),
                wireNames(MetricsRow.ContinuationMark.values(), MetricsRow.ContinuationMark::wireName));
    }

    private static <T extends Enum<T>> Set<String> wireNames(T[] values, Function<T, String> name) {
        return Arrays.stream(values).map(name).collect(Collectors.toSet());
    }
}
