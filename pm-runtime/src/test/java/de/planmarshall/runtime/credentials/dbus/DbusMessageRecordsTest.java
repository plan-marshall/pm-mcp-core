/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials.dbus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link DbusMessage} builds the value its canonical constructor builds. The record
 * validates no component for null, so it has no refusal to mirror; every value is generated.
 */
@EnableGeneratorController
@DisplayName("DbusMessage record builder")
class DbusMessageRecordsTest {

    @Test
    @DisplayName("the builder builds the dbus message the constructor builds")
    void dbusMessageBuilderEqualsConstructor() {
        var type = Generators.integers(1, 4).next();
        var flags = Generators.integers(0, 3).next();
        var serial = Generators.longs(1, 1_000_000).next();
        var path = Generators.letterStrings(1, 20).next();
        var iface = Generators.letterStrings(1, 20).next();
        var member = Generators.letterStrings(1, 20).next();
        var errorName = Generators.letterStrings(1, 20).next();
        var replySerial = Generators.longs(0, 1_000_000).next();
        var destination = Generators.letterStrings(1, 20).next();
        var sender = Generators.letterStrings(1, 20).next();
        var signature = Generators.letterStrings(1, 20).next();
        var body = List.<Object>of(Generators.letterStrings(1, 20).next());

        var constructed = new DbusMessage(type, flags, serial, path, iface, member, errorName, replySerial,
                destination, sender, signature, body);
        var built = DbusMessage.builder()
                .type(type)
                .flags(flags)
                .serial(serial)
                .path(path)
                .iface(iface)
                .member(member)
                .errorName(errorName)
                .replySerial(replySerial)
                .destination(destination)
                .sender(sender)
                .signature(signature)
                .body(body)
                .build();

        assertEquals(constructed, built, "the builder builds the same dbus message");
    }
}
