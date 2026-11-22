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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.PlanOverrides.Axis;
import de.planmarshall.core.store.PlanOverrides.Channel;
import de.planmarshall.core.store.PlanOverrides.Integration;
import de.planmarshall.core.store.PlanOverrides.Posture;
import de.planmarshall.core.store.PlanOverrides.Providers;
import de.planmarshall.core.store.PlanOverrides.Role;
import de.planmarshall.core.store.PlanOverrides.UpdatedBy;

/**
 * {@link PlanOverridesCodec} reads and writes a plan's {@code overrides.json} (Store Schemas § Plan Overrides): every
 * member round-trips, a document that names an undeclared member or a value the schema does not admit is refused, and
 * the written document is the closed document of the schema.
 * <p>
 * The member names and the axis, posture, integration and channel values are the specified document format. The
 * role, model, effort and provider values are the literals the document's closed grammar admits; the base-branch name
 * and the write instant are generated.
 */
@EnableGeneratorController
@DisplayName("Plan overrides codec")
class PlanOverridesCodecTest {

    @TempDir
    Path directory;

    private final String role = "implementer";
    private final String harness = "claude";
    private final String model = "deepseek-v4.1-flash";
    private final String effort = "high";
    private final String baseBranch = scripted("release/");
    private final Instant updatedAt = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    private static String scripted(String prefix) {
        return prefix + Generators.letterStrings(3, 12).next();
    }

    private Path file() {
        return directory.resolve("overrides.json");
    }

    private PlanOverrides overrides() {
        var roles = new LinkedHashMap<String, Role>();
        roles.put(role, new Role(harness, model, effort));
        roles.put("reviewer", new Role(null, model, null));
        var axes = new LinkedHashMap<Axis, String>();
        axes.put(Axis.LANDING, "landing.main");
        axes.put(Axis.METHOD, "method.test-first");
        axes.put(Axis.ASKING, "ask.questions-only");
        var integrations = new LinkedHashMap<String, Integration>();
        integrations.put("github", Integration.CLI);
        return PlanOverrides.builder()
                .revision(3)
                .roles(roles)
                .axes(axes)
                .posture(Posture.STANDARD)
                .baseBranch(baseBranch)
                .integrations(integrations)
                .providers(new Providers(List.of("sonar"), List.of("coderabbit")))
                .updatedAt(updatedAt)
                .updatedBy(new UpdatedBy(Channel.WEB, "dev-0123456789ab"))
                .build();
    }

    @Test
    @DisplayName("every member of the document round-trips")
    void roundTrip() {
        var written = PlanOverridesCodec.write(overrides());

        var read = PlanOverridesCodec.read(file(), written);

        assertAll(
                () -> assertEquals(overrides(), read, "The document must read as it was written"),
                () -> assertTrue(new String(written, UTF_8).startsWith(
                        "{\"format_version\":1,\"revision\":3,\"roles\":{"), "The document starts in schema order"),
                () -> assertTrue(new String(written, UTF_8).contains("\"posture\":\"standard\""),
                        "The posture is its wire name"),
                () -> assertTrue(new String(written, UTF_8).contains("\"landing\":\"landing.main\""),
                        "The axis is written under its key"));
    }

    @Test
    @DisplayName("a document without posture, base branch and providers round-trips")
    void minimalDocument() {
        var minimal = PlanOverrides.builder()
                .revision(1)
                .roles(Map.of())
                .axes(Map.of())
                .integrations(Map.of())
                .providers(Providers.none())
                .updatedAt(updatedAt)
                .updatedBy(new UpdatedBy(Channel.CLI, null))
                .build();

        var read = PlanOverridesCodec.read(file(), PlanOverridesCodec.write(minimal));

        assertAll(
                () -> assertEquals(minimal, read, "The minimal document must round-trip"),
                () -> assertNull(read.posture(), "An absent posture reads as none"),
                () -> assertNull(read.updatedBy().deviceId(), "A null device id reads as none"));
    }

    @Test
    @DisplayName("a role that sets no value, a variant off its axis and an unknown member are refused")
    void refusals() {
        var text = new String(PlanOverridesCodec.write(overrides()), UTF_8);
        var offAxis = overridesWithAxes(Map.of(Axis.LANDING, "method.test-first"));
        var unknownMember = text.replace("\"revision\":3", "\"revision\":3,\"unknown\":true").getBytes(UTF_8);
        var unknownAxis = text.replace("\"method\":", "\"unknown\":").getBytes(UTF_8);
        var documentFile = file();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Role(null, null, null), "A role override sets at least one value"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanOverridesCodec.write(offAxis), "A variant off its axis is refused"),
                () -> assertThrows(StoreRefusal.class,
                        () -> PlanOverridesCodec.read(documentFile, unknownMember),
                        "An undeclared member is refused"),
                () -> assertThrows(StoreRefusal.class,
                        () -> PlanOverridesCodec.read(documentFile, unknownAxis),
                        "An axis the schema does not admit is refused"));
    }

    @Test
    @DisplayName("a role length that breaks the schema and a malformed axis name are refused")
    void grammarRefusals() {
        var text = new String(PlanOverridesCodec.write(overrides()), UTF_8);
        var tooLongModel = text.replace("\"deepseek-v4.1-flash\"",
                "\"" + "m".repeat(PlanOverridesCodec.MODEL_MAX_LENGTH + 1) + "\"").getBytes(UTF_8);
        var emptyVariant = text.replace("\"landing.main\"", "\"landing.\"").getBytes(UTF_8);
        var upperVariant = text.replace("\"landing.main\"", "\"landing.Main\"").getBytes(UTF_8);
        var documentFile = file();

        assertAll(
                () -> assertThrows(StoreRefusal.class,
                        () -> PlanOverridesCodec.read(documentFile, tooLongModel),
                        "A model over its length is an unreadable document"),
                () -> assertThrows(StoreRefusal.class,
                        () -> PlanOverridesCodec.read(documentFile, emptyVariant),
                        "A variant with an empty name is refused"),
                () -> assertThrows(StoreRefusal.class,
                        () -> PlanOverridesCodec.read(documentFile, upperVariant),
                        "A variant with an upper-case name is refused"));
    }

    @Test
    @DisplayName("a length bound counts code points, so an astral model at the bound is admitted")
    void lengthCountsCodePoints() {
        // U+1F600 is one code point and two UTF-16 units: the schema's maxLength counts it once.
        var astral = "😀";
        var atBound = astral.repeat(PlanOverridesCodec.MODEL_MAX_LENGTH);
        var overBound = astral.repeat(PlanOverridesCodec.MODEL_MAX_LENGTH + 1);
        var admitted = overridesWithRole(new Role(null, atBound, null));
        var refused = overridesWithRole(new Role(null, overBound, null));

        var read = PlanOverridesCodec.read(file(), PlanOverridesCodec.write(admitted));

        assertAll(
                () -> assertEquals(atBound, read.roles().get(role).model(), "a model at the bound round-trips"),
                () -> {
                    var refusal = assertThrows(IllegalArgumentException.class,
                            () -> PlanOverridesCodec.write(refused), "a model over the bound is refused");
                    assertTrue(refusal.getMessage().endsWith(": " + (PlanOverridesCodec.MODEL_MAX_LENGTH + 1)),
                            "the refusal counts the code points");
                });
    }

    @Test
    @DisplayName("a harness is checked as a harness id and an integration as an integration name")
    void namesCheckedByTheirOwnGrammar() {
        var text = new String(PlanOverridesCodec.write(overrides()), UTF_8);
        var upperHarness = overridesWithRole(new Role("Claude", model, effort));
        var upperIntegration = text.replace("\"github\":\"cli\"", "\"Github\":\"cli\"").getBytes(UTF_8);
        var documentFile = file();

        assertAll(
                () -> {
                    var refusal = assertThrows(IllegalArgumentException.class,
                            () -> PlanOverridesCodec.write(upperHarness), "a harness outside its grammar is refused");
                    assertEquals("Not a harness id of the document: Claude", refusal.getMessage(),
                            "the refusal names the harness grammar");
                },
                () -> {
                    var refusal = assertThrows(StoreRefusal.class,
                            () -> PlanOverridesCodec.read(documentFile, upperIntegration),
                            "an integration outside its grammar is refused");
                    assertTrue(refusal.getMessage().contains("not an integration name of the document: Github"),
                            "the refusal names the integration grammar");
                });
    }

    @Test
    @DisplayName("a written document is at revision 1 or more and carries its stamp")
    void stampRequired() {
        var unstamped = overrides().toBuilder().updatedAt(null).build();
        var absentRevision = overrides().toBuilder().revision(0).build();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> PlanOverridesCodec.write(unstamped),
                        "A document without a write instant is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PlanOverridesCodec.write(absentRevision),
                        "A written document is at revision 1 or more"));
    }

    private PlanOverrides overridesWithAxes(Map<Axis, String> axes) {
        return overrides().toBuilder().axes(axes).build();
    }

    private PlanOverrides overridesWithRole(Role value) {
        return overrides().toBuilder().roles(Map.of(role, value)).build();
    }
}
