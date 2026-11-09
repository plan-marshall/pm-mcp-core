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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.StoreRefusal;

/**
 * The shapes {@code consents.json} accepts and refuses (Store Schemas § Consent Before Acting). The refused documents are
 * built from tokens at test time, so their generated data comes from the per-test seed; the tokens themselves are the
 * contract of the shape a case breaks.
 */
@EnableGeneratorController
@DisplayName("Consent codec")
class ConsentCodecTest {

    private static final String PROJECT_TOKEN = "<project>";
    private static final String GRANTED_TOKEN = "<granted>";
    private static final String SCRIPT_TOKEN = "<script>";

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
                        + entry("scan_harnesses").replace(", \"granted_at\":\"" + GRANTED_TOKEN + "\"", "") + "]}",
                        "granted_at"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("scan_harnesses").replace("}", ", \"project_id\":\"" + PROJECT_TOKEN + "\"}") + "]}",
                        "project"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("inspect_checkout").replace("\"project_id\":\"" + PROJECT_TOKEN + "\", ", "") + "]}",
                        "project"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("run_checkout_scripts").replace("\"scripts\":[], ", "") + "]}", "scripts"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("scan_harnesses").replace("}", ", \"scripts\":[]}") + "]}", "scripts"),
                Arguments.of("{\"format_version\":1,\"revision\":0,\"consents\":["
                        + entry("run_checkout_scripts").replace("\"project_id\":\"" + PROJECT_TOKEN
                        + "\", \"scripts\":[]", "\"project_id\":\"" + PROJECT_TOKEN + "\", \"scripts\":[{\"path\":\""
                        + SCRIPT_TOKEN + "\",\"sha256\":\"nope\"}]") + "]}",
                        "digest"));
    }

    private static String entry(String kind) {
        var parsed = ConsentKind.of(kind).orElseThrow();
        var project = parsed.projectScoped() ? "\"project_id\":\"" + PROJECT_TOKEN + "\", " : "";
        var scripts = parsed.scriptScoped() ? "\"scripts\":[], " : "";
        return "{\"kind\":\"" + kind + "\", " + project + scripts
                + "\"granted_at\":\"" + GRANTED_TOKEN + "\", \"channel\":\"cli\"}";
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("refusedDocuments")
    @DisplayName("a document outside the store's one shape is refused")
    void refused(String template, String what) {
        var project = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var script = "bin/" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT) + ".sh";
        var granted = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        var document = template.replace(PROJECT_TOKEN, project)
                .replace(GRANTED_TOKEN, granted.toString())
                .replace(SCRIPT_TOKEN, script);
        var path = file();
        var bytes = document.getBytes(UTF_8);

        var refusal = assertThrows(StoreRefusal.class, () -> ConsentCodec.read(path, bytes), what);
        assertEquals(path, refusal.file());
    }

    @Test
    @DisplayName("writes and reads back every member of an entry, including the web device and the scripts")
    void roundTrip() {
        var project = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var script = "bin/" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT) + ".sh";
        var digest = "%064x".formatted(Generators.longs().next());
        var granted = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        var device = "dev-" + "%012x".formatted(Generators.longs(0L, 0xFFFF_FFFF_FFFFL).next());
        var revision = Generators.longs(0L, 1_000L).next();
        var consent = Consent.checkoutScripts(project, List.of(new ConsentScript(script, digest)),
                granted, Consent.Channel.WEB, device);

        var document = ConsentCodec.write(List.of(consent), revision);
        var read = ConsentCodec.read(file(), document);

        assertAll(
                () -> assertEquals(revision, read.revision()),
                () -> assertEquals(List.of(consent), read.consents()),
                () -> assertTrue(new String(document, UTF_8).contains("\"scripts\":[{\"path\":\"" + script + "\"")));
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
        var granted = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        assertThrows(IllegalArgumentException.class,
                () -> Consent.project(ConsentKind.INSPECT_CHECKOUT, "Upper", granted, Consent.Channel.CLI, null));
    }
}
