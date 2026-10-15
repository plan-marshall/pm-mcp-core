/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.log;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.tools.logging.LogRecord;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The holder numbers every message of {@code pm-core} once (PM-IMPL-1): two messages with one identifier cannot be
 * told apart in a log, and an identifier outside the range of its level reads as another level. Its prefix is the
 * prefix of no other holder of the repository.
 */
@DisplayName("CoreLogMessages")
class CoreLogMessagesTest {

    /** The prefixes of the log-message holders of the repository; a new holder adds its prefix here. */
    private static final List<String> KNOWN_PREFIXES = List.of("PM_CORE", "PM_RUNTIME", "PM_WORKFLOW");

    private static final Pattern PREFIX_DECLARATION =
            Pattern.compile("public static final String PREFIX = \"([^\"]+)\";");

    @ParameterizedTest(name = "{0} holds identifiers {1} to {2}")
    @CsvSource({"INFO, 1, 99", "WARN, 100, 199", "ERROR, 200, 299"})
    @DisplayName("every message lies in the range of its level and carries the prefix")
    void ranges(String level, int first, int last) throws Exception {
        var records = records(level);

        assertFalse(records.isEmpty(), level);
        for (LogRecord logRecord : records) {
            assertEquals(CoreLogMessages.PREFIX, logRecord.getPrefix());
            assertTrue(logRecord.getIdentifier() >= first && logRecord.getIdentifier() <= last,
                    logRecord.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("no identifier is used twice")
    void unique() throws Exception {
        var all = new ArrayList<LogRecord>();
        for (String level : List.of("INFO", "WARN", "ERROR")) {
            all.addAll(records(level));
        }
        var seen = new HashSet<Integer>();

        for (LogRecord logRecord : all) {
            assertTrue(seen.add(logRecord.getIdentifier()), logRecord.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("every holder of the repository has a prefix of its own, and every prefix is known")
    void prefixUniqueAcrossHolders() throws Exception {
        var declared = new ArrayList<String>();
        for (Path sources : moduleSources(repositoryRoot())) {
            try (Stream<Path> files = Files.walk(sources)) {
                var holders = files.filter(file -> file.getFileName().toString().endsWith("LogMessages.java")).toList();
                for (Path holder : holders) {
                    var matcher = PREFIX_DECLARATION.matcher(Files.readString(holder));
                    if (matcher.find()) {
                        declared.add(matcher.group(1));
                    }
                }
            }
        }

        assertAll(
                () -> assertEquals(declared.size(), new HashSet<>(declared).size(), declared.toString()),
                () -> assertEquals(new HashSet<>(KNOWN_PREFIXES), new HashSet<>(declared), declared.toString()),
                () -> assertEquals(KNOWN_PREFIXES.size(), new HashSet<>(KNOWN_PREFIXES).size(),
                        KNOWN_PREFIXES.toString()),
                () -> assertTrue(KNOWN_PREFIXES.contains(CoreLogMessages.PREFIX), CoreLogMessages.PREFIX));
    }

    static Stream<Arguments> namedRecords() {
        return Stream.of(
                Arguments.of(CoreLogMessages.INFO.ENROLMENT_PAIR_COMPLETED, "PM_CORE-1",
                        "Stopped write of the enrolment of project '%s' completed by the recovery: transaction '%s'"),
                Arguments.of(CoreLogMessages.WARN.ENROLMENT_PAIR_DISCARDED, "PM_CORE-101",
                        "Incomplete staged write of the enrolment of project '%s' discarded, "
                                + "the enrolment is as it was before the write: %s"),
                Arguments.of(CoreLogMessages.WARN.CORPUS_SOURCE_MISSED, "PM_CORE-102",
                        "Corpus source '%s' missed, its items are left out: %s"),
                Arguments.of(CoreLogMessages.ERROR.CORPUS_SOURCE_HASH_MISMATCH, "PM_CORE-200",
                        "Corpus source '%s' fails its hash: file '%s' expected %s, found %s"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("namedRecords")
    @DisplayName("the records of the enrolment pair and of the corpus import carry their identifier and template")
    void namedRecord(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString()),
                () -> assertEquals(template, logRecord.getTemplate()));
    }

    /** The directory above the working directory that holds {@code pm-core} and the root POM. */
    private static Path repositoryRoot() {
        var dir = Path.of("").toAbsolutePath();
        while (dir != null
                && !(Files.isDirectory(dir.resolve("pm-core")) && Files.isRegularFile(dir.resolve("pom.xml")))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "repository root not found above the working directory");
        return dir;
    }

    /** The production source directories of the modules, the provider modules below {@code pm-providers} included. */
    private static List<Path> moduleSources(Path root) throws IOException {
        var modules = new ArrayList<Path>();
        try (Stream<Path> children = Files.list(root)) {
            modules.addAll(children.filter(child -> child.getFileName().toString().startsWith("pm-")).toList());
        }
        try (Stream<Path> providers = Files.list(root.resolve("pm-providers"))) {
            modules.addAll(providers.toList());
        }
        var sources = modules.stream().map(module -> module.resolve("src/main/java")).filter(Files::isDirectory)
                .toList();
        assertFalse(sources.isEmpty(), root.toString());
        return sources;
    }

    private static List<LogRecord> records(String level) throws ReflectiveOperationException {
        var holder = Class.forName(CoreLogMessages.class.getName() + "$" + level);
        var records = new ArrayList<LogRecord>();
        for (Field field : holder.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && LogRecord.class.isAssignableFrom(field.getType())) {
                records.add((LogRecord) field.get(null));
            }
        }
        return records;
    }
}
