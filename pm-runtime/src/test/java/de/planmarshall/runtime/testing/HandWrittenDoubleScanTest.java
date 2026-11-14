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

    private static final Pattern TYPE_DECLARATION = Pattern.compile("\\b(?:class|enum|record)\\s+(\\w+)");
    private static final Pattern NEW = Pattern.compile("\\bnew\\s+");
    private static final Pattern LAMBDA = Pattern.compile("->|::");
    private static final Pattern INTERFACE = Pattern.compile("\\binterface\\s+(\\w+)");
    private static final Pattern CLASS = Pattern.compile("\\bclass\\s+(\\w+)");
    private static final Pattern GENERICS = Pattern.compile("<[^<>]*>");

    private static final Set<String> MODIFIERS = Set.of("public", "protected", "private", "static", "final",
            "abstract", "sealed", "strictfp", "non");

    /** The lexical state of {@link #stripNoise(String)}. */
    private enum State {
        CODE, LINE_COMMENT, BLOCK_COMMENT, TEXT_BLOCK, STRING, CHARACTER
    }

    @Test
    @DisplayName("the scan detects planted doubles and the test tree matches the baseline")
    void enforcesTheClosedList() throws Exception {
        assertTrue(scan("planted.java", """
                class Planted implements de.planmarshall.core.service.AuditSink {
                    public void append(de.planmarshall.core.service.AuditEvent event) {
                    }
                }
                """).contains("planted.java#Planted#1"), "a planted class over a production interface is found");
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
        assertTrue(scan("planted.java", """
                class Holder {
                    String s = \"\"\"
                        \\\"\"\"
                        class Phantom implements AuditSink {}
                        \"\"\";
                }
                """).isEmpty(), "an escaped text-block delimiter hides its content");
        assertTrue(productionNames("public abstract static class Port { }").contains("Port"),
                "an abstract class's modifier order does not hide it");
        assertTrue(productionNames("class Ports { public abstract @SuppressWarnings({\"unused\"})"
                + " static class Port { } }").contains("Port"),
                "an annotated abstract class is a production type");
        assertTrue(scan("planted.java", """
                        class Holder {
                            de.planmarshall.core.service.AuditSink unused, sink = event -> {
                            };
                        }
                        """).contains("planted.java#lambda:AuditSink#1"),
                "a multi-declarator field's lambda is found");

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
        var code = stripNoise(source);
        var found = new ArrayList<String>();
        namedDouble(path, code, production, found);
        anonymousDoubles(path, code, production, found);
        lambdaDoubles(path, code, production, found);
        return found;
    }

    private static void namedDouble(String path, String code, Set<String> production, List<String> found) {
        var names = new TreeMap<String, Integer>();
        var matcher = TYPE_DECLARATION.matcher(code);
        while (matcher.find()) {
            var brace = code.indexOf('{', matcher.end());
            var semicolon = code.indexOf(';', matcher.end());
            if (brace < 0 || semicolon >= 0 && semicolon < brace) {
                continue;
            }
            var header = GENERICS.matcher(code.substring(matcher.end(), brace)).replaceAll("");
            header = GENERICS.matcher(header).replaceAll("");
            for (var clause : clauses(header)) {
                if (production.contains(simpleName(clause))) {
                    var name = matcher.group(1);
                    found.add(path + "#" + name + "#" + names.merge(name, 1, Integer::sum));
                    break;
                }
            }
        }
    }

    /** @return the type lists after {@code implements} and {@code extends}, without generics */
    private static List<String> clauses(String header) {
        var clauses = new ArrayList<String>();
        var implementsAt = header.indexOf("implements");
        var extendsAt = header.indexOf("extends");
        if (implementsAt >= 0) {
            clauses.add(header.substring(implementsAt + "implements".length()));
        }
        if (extendsAt >= 0) {
            clauses.add(header.substring(extendsAt + "extends".length(),
                    implementsAt > extendsAt ? implementsAt : header.length()));
        }
        var types = new ArrayList<String>();
        for (var clause : clauses) {
            for (var type : clause.split(",")) {
                types.add(type.strip());
            }
        }
        return types;
    }

    private static void anonymousDoubles(String path, String code, Set<String> production, List<String> found) {
        var counts = new TreeMap<String, Integer>();
        var matcher = NEW.matcher(code);
        while (matcher.find()) {
            var index = matcher.end();
            var start = index;
            while (index < code.length() && (Character.isJavaIdentifierPart(code.charAt(index))
                    || code.charAt(index) == '.')) {
                index++;
            }
            var type = simpleName(code.substring(start, index));
            var angle = 0;
            while (index < code.length() && (code.charAt(index) == '<' || angle > 0)) {
                if (code.charAt(index) == '<') {
                    angle++;
                } else if (code.charAt(index) == '>') {
                    angle--;
                }
                index++;
            }
            while (index < code.length() && Character.isWhitespace(code.charAt(index))) {
                index++;
            }
            if (!production.contains(type) || index >= code.length() || code.charAt(index) != '(') {
                continue;
            }
            var depth = 0;
            var close = -1;
            for (var cursor = index; cursor < code.length(); cursor++) {
                if (code.charAt(cursor) == '(') {
                    depth++;
                } else if (code.charAt(cursor) == ')') {
                    depth--;
                    if (depth == 0) {
                        close = cursor;
                        break;
                    }
                }
            }
            var brace = close < 0 ? -1 : code.indexOf('{', close);
            var semicolon = close < 0 ? -1 : code.indexOf(';', close);
            if (brace >= 0 && (semicolon < 0 || brace < semicolon)) {
                found.add(path + "#anonymous:" + type + "#" + counts.merge(type, 1, Integer::sum));
            }
        }
    }

    private static void lambdaDoubles(String path, String code, Set<String> production, List<String> found) {
        var counts = new TreeMap<String, Integer>();
        var matcher = LAMBDA.matcher(code);
        while (matcher.find()) {
            var start = Math.max(Math.max(code.lastIndexOf(';', matcher.start()),
                    code.lastIndexOf('{', matcher.start())), code.lastIndexOf('}', matcher.start()));
            var prefix = code.substring(start + 1, matcher.start());
            var equals = prefix.lastIndexOf('=');
            if (equals <= 0 || "=!<>".indexOf(prefix.charAt(equals - 1)) >= 0) {
                continue;
            }
            var index = equals - 1;
            while (index >= 0 && Character.isWhitespace(prefix.charAt(index))) {
                index--;
            }
            while (index >= 0 && (Character.isJavaIdentifierPart(prefix.charAt(index))
                    || prefix.charAt(index) == '.')) {
                index--;
            }
            while (index >= 0 && Character.isWhitespace(prefix.charAt(index))) {
                index--;
            }
            while (index >= 0 && prefix.charAt(index) == ',') {
                index--;
                while (index >= 0 && Character.isWhitespace(prefix.charAt(index))) {
                    index--;
                }
                while (index >= 0 && (Character.isJavaIdentifierPart(prefix.charAt(index))
                        || prefix.charAt(index) == '.')) {
                    index--;
                }
                while (index >= 0 && Character.isWhitespace(prefix.charAt(index))) {
                    index--;
                }
            }
            var typeEnd = index + 1;
            while (index >= 0 && (Character.isJavaIdentifierPart(prefix.charAt(index))
                    || prefix.charAt(index) == '.')) {
                index--;
            }
            var type = simpleName(prefix.substring(index + 1, typeEnd));
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
                        .forEach(path -> production.addAll(productionNames(stripNoise(read(path)))));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return production;
    }

    /**
     * @param code the source text
     * @return the names of its interfaces and abstract classes, whatever their modifier and annotation order
     */
    static Set<String> productionNames(String code) {
        var names = new TreeSet<String>();
        var interfaces = INTERFACE.matcher(code);
        while (interfaces.find()) {
            names.add(interfaces.group(1));
        }
        var classes = CLASS.matcher(code);
        while (classes.find()) {
            if (abstractBefore(code, classes.start())) {
                names.add(classes.group(1));
            }
        }
        return names;
    }

    /** @return whether the declaration header before {@code class} carries {@code abstract} in any modifier order */
    private static boolean abstractBefore(String code, int classStart) {
        var index = classStart - 1;
        while (index >= 0) {
            while (index >= 0 && Character.isWhitespace(code.charAt(index))) {
                index--;
            }
            if (index < 0) {
                return false;
            }
            if (code.charAt(index) == ')') {
                var depth = 0;
                var cursor = index;
                for (; cursor >= 0; cursor--) {
                    if (code.charAt(cursor) == ')') {
                        depth++;
                    } else if (code.charAt(cursor) == '(') {
                        depth--;
                        if (depth == 0) {
                            break;
                        }
                    }
                }
                if (cursor < 0) {
                    return false;
                }
                var nameStart = cursor - 1;
                while (nameStart >= 0 && (Character.isJavaIdentifierPart(code.charAt(nameStart))
                        || code.charAt(nameStart) == '.')) {
                    nameStart--;
                }
                if (nameStart < 0 || code.charAt(nameStart) != '@') {
                    return false;
                }
                index = nameStart - 1;
                continue;
            }
            if (!Character.isJavaIdentifierPart(code.charAt(index))) {
                return false;
            }
            var end = index + 1;
            while (index >= 0 && Character.isJavaIdentifierPart(code.charAt(index))) {
                index--;
            }
            var word = code.substring(index + 1, end);
            if ("abstract".equals(word)) {
                return true;
            }
            if (index >= 0 && code.charAt(index) == '@') {
                index--;
                continue;
            }
            if (!MODIFIERS.contains(word)) {
                return false;
            }
        }
        return false;
    }

    private static String simpleName(String type) {
        var withoutGenerics = GENERICS.matcher(type).replaceAll("").strip();
        var lastDot = withoutGenerics.lastIndexOf('.');
        return lastDot < 0 ? withoutGenerics : withoutGenerics.substring(lastDot + 1);
    }

    /**
     * Blanks comments, text blocks and the content of string and character literals, so a declaration inside one is
     * not read as code and a comment cannot hide one. A single pass, so a large file cannot exhaust the stack.
     *
     * @param source the source text
     * @return the text without comments and with blanked literal content
     */
    private static String stripNoise(String source) {
        var code = new StringBuilder(source.length());
        var state = State.CODE;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (current == '/' && next == '/') {
                        state = State.LINE_COMMENT;
                        index++;
                        code.append(' ');
                    } else if (current == '/' && next == '*') {
                        state = State.BLOCK_COMMENT;
                        index++;
                        code.append(' ');
                    } else if (current == '"' && next == '"' && index + 2 < source.length()
                            && source.charAt(index + 2) == '"') {
                        state = State.TEXT_BLOCK;
                        index += 2;
                        code.append(' ');
                    } else if (current == '"') {
                        state = State.STRING;
                        code.append('"');
                    } else if (current == '\'') {
                        state = State.CHARACTER;
                        code.append('\'');
                    } else {
                        code.append(current);
                    }
                }
                case LINE_COMMENT -> {
                    if (current == '\n') {
                        state = State.CODE;
                        code.append('\n');
                    }
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        state = State.CODE;
                        index++;
                        code.append(' ');
                    }
                }
                case TEXT_BLOCK -> {
                    if (current == '\\') {
                        index++;
                    } else if (current == '"' && next == '"' && index + 2 < source.length()
                            && source.charAt(index + 2) == '"') {
                        state = State.CODE;
                        index += 2;
                        code.append(' ');
                    }
                }
                case STRING -> {
                    if (current == '\\') {
                        index++;
                    } else if (current == '"') {
                        state = State.CODE;
                        code.append('"');
                    }
                }
                case CHARACTER -> {
                    if (current == '\\') {
                        index++;
                    } else if (current == '\'') {
                        state = State.CODE;
                        code.append('\'');
                    }
                }
            }
        }
        return code.toString();
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
