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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.StoreRefusal;

/**
 * The shapes {@code consents.json} accepts and refuses (Store Schemas § Consent Before Acting).
 */
@EnableGeneratorController
@DisplayName("Consent codec")
class ConsentCodecTest {

    private static final Instant GRANTED = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    private static final String SHA = "%064x".formatted(Generators.longs().next());
    private static final String PROJECT = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String SCRIPT = "bin/" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT)
            + ".sh";
    private static final String DEVICE = "dev-"
            + "%012x".formatted(Generators.longs(0L, 0xFFFF_FFFF_FFFFL).next());

    @TempDir
    Path base;

    private Path file() {
        return base.resolve("consents.json");
    }

    static Stream<Arguments> refusedDocuments() {
        return Stream.of(
                Arguments.of("{\"format_version\":2,\"revision\":0,\"consents\":[]}", "format version"),
                Arguments.of("[]", "not an object"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":[],\"extra\":1}", "undeclared"),
                Arguments.of("{\"format_version\":1,\"revision\":0}", "missing consents"),
                Arguments.of("{\"format_version\":1,\"revision\":-1,\"consents\":[]}", "negative revision"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("scan_harnesses") + "," + entry("scan_harnesses") + "]}", "twice"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("scan_harnesses").replace("scan_harnesses", "mystery") + "]}", "kind"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("scan_harnesses").replace("\"cli\"", "\"telepathy\"") + "]}", "channel"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("scan_harnesses").replace(", \"granted_at\":\"" + GRANTED + "\"", "") + "]}",
                        "granted_at"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("scan_harnesses").replace("}", ", \"project_id\":\"" + PROJECT + "\"}") + "]}",
                        "project"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("inspect_checkout").replace("\"project_id\":\"" + PROJECT + "\", ", "") + "]}",
                        "project"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("run_checkout_scripts").replace("\"scripts\":[], ", "") + "]}", "scripts"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("scan_harnesses").replace("}", ", \"scripts\":[]}") + "]}", "scripts"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("run_checkout_scripts").replace("\"project_id\":\"" + PROJECT + "\", \"scripts\":[]",
                        "\"project_id\":\"" + PROJECT + "\", \"scripts\":[{\"path\":\"" + SCRIPT
                                + "\",\"sha256\":\"nope\"}]") + "]}",
                        "digest"));
    }

    private static String entry(String kind) {
        var parsed = ConsentKind.of(kind).orElseThrow();
        var project = parsed.projectScoped() ? "\"project_id\":\"" + PROJECT + "\", " : "";
        var scripts = parsed.scriptScoped() ? "\"scripts\":[], " : "";
        return "{\"kind\":\"" + kind + "\", " + project + scripts
                + "\"granted_at\":\"" + GRANTED + "\", \"channel\":\"cli\"}";
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("refusedDocuments")
    @DisplayName("a document outside the store's one shape is refused")
    void refused(String document, String what) {
        var path = file();
        var bytes = document.getBytes(UTF_8);

        var refusal = assertThrows(StoreRefusal.class, () -> ConsentCodec.read(path, bytes), what);
        assertEquals(path, refusal.file());
    }

    @Test
    @DisplayName("writes and reads back every member of an entry, including the web device and the scripts")
    void roundTrip() {
        var revision = Generators.longs(0L, 1_000L).next();
        var consent = Consent.checkoutScripts(PROJECT, List.of(new ConsentScript(SCRIPT, SHA)),
                GRANTED, Consent.Channel.WEB, Optional.of(DEVICE));

        var document = ConsentCodec.write(List.of(consent), revision);
        var read = ConsentCodec.read(file(), document);

        assertAll(
                () -> assertEquals(revision, read.revision()),
                () -> assertEquals(List.of(consent), read.consents()),
                () -> assertTrue(new String(document, UTF_8).contains("\"scripts\":[{\"path\":\"" + SCRIPT + "\"")));
    }

    @Test
    @DisplayName("an empty entry list round-trips")
    void empty() {
        var read = ConsentCodec.read(file(), ConsentCodec.write(List.of(), 0));
        assertAll(
                () -> assertTrue(read.consents().isEmpty()),
                () -> assertEquals(0L, read.revision()));
    }

    @Test
    @DisplayName("a project id outside its grammar is refused by the entry")
    void invalidProjectId() {
        assertThrows(IllegalArgumentException.class,
                () -> Consent.project(ConsentKind.INSPECT_CHECKOUT, "Upper", GRANTED, Consent.Channel.CLI,
                        Optional.empty()));
    }
}
