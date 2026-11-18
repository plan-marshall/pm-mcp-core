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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The strictness of the {@code explorer.json} reader (Store Schemas § The Operator's Projects; Format Versioning): the
 * document must be the one shape of its kind, of the current format version, and nothing may follow it. The readable
 * document is covered by {@link ExplorerStoreTest}; this test states one refusal per rule.
 */
@DisplayName("Operator's projects codec")
class ExplorerCodecTest {

    /** A valid document of the current format version, as the reader accepts it. */
    private static final String VALID = """
            {"format_version": 1, "revision": 1, "favourites": ["pm-core"],
             "activity": {"pm-core": {"last_at": "2026-11-18T01:00:00Z", "days": {"2026-11-18": 1}}}}
            """;

    @TempDir
    Path directory;

    @Test
    @DisplayName("Should read the one shape of the document")
    void readsTheValidDocument() {
        var snapshot = ExplorerCodec.read(file(), VALID.getBytes(UTF_8));

        assertAll(
                () -> assertEquals(1L, snapshot.revision(), "The document's revision is read"),
                () -> assertEquals(List.of("pm-core"), snapshot.favourites(),
                        "The favourites are read in order"),
                () -> assertEquals(1, snapshot.activity().get("pm-core").actsInWindow(),
                        "The activity's day is read"));
    }

    @Test
    @DisplayName("Should refuse content after the document")
    void refusesContentAfterTheDocument() {
        assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal(VALID + "{}").outcome(),
                "Content after the document is refused as unreadable");
    }

    @Test
    @DisplayName("Should refuse another format version as outdated or newer")
    void refusesAnotherFormatVersion() {
        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.FORMAT_NEWER,
                        refusal(VALID.replace("\"format_version\": 1", "\"format_version\": 2")).outcome(),
                        "A newer version is refused as newer"),
                () -> assertEquals(StoreRefusal.Outcome.FORMAT_OUTDATED,
                        refusal(VALID.replace("\"format_version\": 1", "\"format_version\": 0")).outcome(),
                        "An older version is refused as outdated"));
    }

    @Test
    @DisplayName("Should refuse a root that is no object, a missing member and a negative revision")
    void refusesTheDocumentShape() {
        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal("[]").outcome(),
                        "A root that is no object is refused"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE,
                        refusal("{\"format_version\": 1, \"revision\": 1, \"favourites\": []}").outcome(),
                        "A missing member is refused"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE,
                        refusal(VALID.replace("\"revision\": 1", "\"revision\": -1")).outcome(),
                        "A negative revision is refused"));
    }

    @Test
    @DisplayName("Should refuse a favourite that repeats or lies outside the grammar")
    void refusesAnInvalidFavourite() {
        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE,
                        refusal(VALID.replace("[\"pm-core\"]", "[\"pm-core\", \"pm-core\"]")).outcome(),
                        "A repeated favourite is refused"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE,
                        refusal(VALID.replace("[\"pm-core\"]", "[\"Not-Project\"]")).outcome(),
                        "A favourite outside the grammar is refused"));
    }

    @Test
    @DisplayName("Should refuse a day that is no date and a count below one")
    void refusesAnInvalidDay() {
        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE,
                        refusal(VALID.replace("\"2026-11-18\": 1", "\"yesterday\": 1")).outcome(),
                        "A day that is no date is refused"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE,
                        refusal(VALID.replace("\"2026-11-18\": 1", "\"2026-11-18\": 0")).outcome(),
                        "A count below one is refused"));
    }

    @Test
    @DisplayName("Should refuse a member the schema does not name")
    void refusesAnUndeclaredMember() {
        assertEquals(StoreRefusal.Outcome.UNREADABLE,
                refusal(VALID.replaceFirst("\\{", "{\"extra\": true, ")).outcome(),
                "An undeclared member is refused");
    }

    private Path file() {
        return directory.resolve("explorer.json");
    }

    private StoreRefusal refusal(String json) {
        var document = json.getBytes(UTF_8);
        var path = file();
        return assertThrows(StoreRefusal.class, () -> ExplorerCodec.read(path, document),
                "The document is refused as a store refusal");
    }
}
