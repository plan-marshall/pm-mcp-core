/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The guard of ADR 026: a new hand-written double in this module's test tree fails this test.
 * <p>
 * The scan finds the three shapes the ADR defines — a class, record or enum that declares {@code implements} or
 * {@code extends} over a production interface or abstract class, an anonymous class over one, and a lambda or method
 * reference assigned to one — and compares them with {@code src/test/resources/hand-written-doubles.txt}. That file
 * holds the ADR's closed list plus the population that is outside the old name-based scan and is kept until a
 * conversion plan takes it up (PLAN-V2-125 for `pm-core` and `pm-workflow`, PLAN-V2-126 for `pm-runtime`). A new
 * double adds a line; the failure names the file to compare.
 * <p>
 * A lambda passed straight to a constructor or a method (for example {@code new FileLockManager(event -> {})}) has no
 * declared type at the call site and is not found by the source scan; a reviewer keeps that shape, as ADR 026 says.
 */
@DisplayName("Hand-written double scan")
class HandWrittenDoubleScanTest {

    private static final Path BASELINE = Path.of("src/test/resources/hand-written-doubles.txt");
    private static final Path FOUND = Path.of("target/hand-written-doubles-found.txt");

    @Test
    @DisplayName("the scan detects planted doubles and the test tree matches the baseline")
    void enforcesTheClosedList() throws Exception {
        assertTrue(scan("planted.java", """
                class Planted implements de.planmarshall.core.service.AuditSink {
                    public void append(de.planmarshall.core.service.AuditEvent event) {
                    }
                }
                """).contains("planted.java#Planted"), "a planted class over a production interface is found");
        assertTrue(scan("planted.java", "class Plain { }").isEmpty(),
                "a class without a production type is no double");
        assertTrue(scan("planted.java", """
                class Holder {
                    de.planmarshall.core.service.AuditSink sink = new de.planmarshall.core.service.AuditSink() {
                        public void append(de.planmarshall.core.service.AuditEvent event) {
                        }
                    };
                }
                """).contains("planted.java#anonymous:AuditSink#1"), "a planted anonymous class is found");
        assertTrue(scan("planted.java", """
                        class Holder {
                            de.planmarshall.core.service.AuditSink sink = event -> {
                            };
                        }
                        """).contains("planted.java#lambda:AuditSink#1"),
                "a planted lambda of a production type is found");

        Set<String> found = new TreeSet<>(scanModule());
        Set<String> baseline = baseline();

        if (!baseline.equals(found)) {
            Files.write(FOUND, found);
            fail("the hand-written double scan differs from its baseline; compare " + FOUND + " with " + BASELINE);
        }
    }

    private static Set<String> baseline() throws IOException {
        var entries = new TreeSet<String>();
        for (var line : Files.readAllLines(BASELINE)) {
            var entry = line.strip();
            if (!entry.isEmpty() && !entry.startsWith("#")) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static List<String> scanModule() {
        var production = productionTypes(Path.of("src/main/java"), Path.of("../pm-core/src/main/java"));
        var found = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/testing/generators/"))
                    .sorted()
                    .forEach(path -> found.addAll(scan(relative(path), read(path), production)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    private static List<String> scan(String path, String source) {
        return scan(path, source, productionTypes(Path.of("src/main/java"), Path.of("../pm-core/src/main/java")));
    }

    private static List<String> scan(String path, String source, Set<String> production) {
        var code = stripComments(source);
        var found = new ArrayList<String>();
        namedDouble(path, code, production, found);
        anonymousDoubles(path, code, production, found);
        lambdaDoubles(path, code, production, found);
        return found;
    }

    private static void namedDouble(String path, String code, Set<String> production, List<String> found) {
        var declaration = Pattern.compile(
                "(?s)\\b(?:class|enum|record)\\s+(\\w+)[^{;]*?\\b(?:implements|extends)\\s+([^{]+)\\{");
        var matcher = declaration.matcher(code);
        while (matcher.find()) {
            for (var type : matcher.group(2).split(",")) {
                if (production.contains(simpleName(type))) {
                    found.add(path + "#" + matcher.group(1));
                    break;
                }
            }
        }
    }

    private static void anonymousDoubles(String path, String code, Set<String> production, List<String> found) {
        var counts = new TreeMap<String, Integer>();
        var anonymous = Pattern.compile("\\bnew\\s+([\\w.]+)\\s*\\(\\s*\\)\\s*\\{");
        var matcher = anonymous.matcher(code);
        while (matcher.find()) {
            var type = simpleName(matcher.group(1));
            if (production.contains(type)) {
                found.add(path + "#anonymous:" + type + "#" + counts.merge(type, 1, Integer::sum));
            }
        }
    }

    private static void lambdaDoubles(String path, String code, Set<String> production, List<String> found) {
        var counts = new TreeMap<String, Integer>();
        var lambda = Pattern.compile("(?m)^\\s*(?:@\\w+\\s+)?(?:(?:private|static|final|protected|public)\\s+)*"
                + "([\\w.]+)\\s+\\w+\\s*=\\s*[^;\\n]*(?:->|::)");
        var matcher = lambda.matcher(code);
        while (matcher.find()) {
            var type = simpleName(matcher.group(1));
            if (production.contains(type)) {
                found.add(path + "#lambda:" + type + "#" + counts.merge(type, 1, Integer::sum));
            }
        }
    }

    private static Set<String> productionTypes(Path... roots) {
        var production = new TreeSet<String>();
        for (var root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java")).sorted()
                        .forEach(path -> collect(production, Pattern.compile(
                                "\\b(?:interface|abstract\\s+class)\\s+(\\w+)").matcher(stripComments(read(path)))));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return production;
    }

    private static void collect(Set<String> production, Matcher matcher) {
        while (matcher.find()) {
            production.add(matcher.group(1));
        }
    }

    private static String simpleName(String type) {
        var withoutGenerics = type.replaceAll("(?s)<.*", "").trim();
        var lastDot = withoutGenerics.lastIndexOf('.');
        return lastDot < 0 ? withoutGenerics : withoutGenerics.substring(lastDot + 1);
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)\"\"\".*?\"\"\"", "").replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\\n]*", "");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relative(Path path) {
        return path.toString().replace('\\', '/').replaceFirst("^src/test/java/", "");
    }
}
