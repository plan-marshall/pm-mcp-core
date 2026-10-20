/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.ported;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads the plain-file fixtures of one ported primitive from the class path (PM-TEST-2).
 * <p>
 * The fixtures are enumerated from their directory: every {@code *.txt} file of it is a fixture, except the roster
 * {@value #ROSTER}, which is excluded because it lists the pytest cases of the original rather than a scenario. A
 * fixture is never listed by hand, so a fixture file added to the directory runs without any code change.
 * <p>
 * A fixture file has this form (lines starting with {@code #} and blank lines are ignored):
 *
 * <pre>
 * original: plan-marshall@&lt;pin&gt; &lt;pytest path&gt;::&lt;case&gt; [&lt;pytest path&gt;::&lt;case&gt; ...]
 * requires: &lt;capability&gt;            (optional, repeatable)
 * &lt;directive&gt; &lt;argument&gt; ...
 * </pre>
 *
 * or, for a deliberate change, {@code deliberate: <name>} as the first line, where {@code <name>} is the file name
 * without its {@code .txt} suffix. The loader refuses, naming the fixture and the line: an unknown or missing header;
 * an {@code original:} header whose pin is not {@link #INVENTORY_PIN}; a deliberate header whose name is not the file
 * name; a directive the primitive's grammar does not declare, or one with the wrong number of arguments.
 *
 * @since 0.1
 */
final class PortedFixtures {

    /** The {@code plan-marshall} commit the migration inventory pins; every original-derived fixture names it. */
    static final String INVENTORY_PIN = "297ae30661ab0001c92ab4d9b008cdec1c514758";

    /** The roster of the original's pytest cases, kept beside the fixtures and excluded from them. */
    static final String ROSTER = "original-cases.txt";

    private static final String SUFFIX = ".txt";
    private static final Pattern CASE_REFERENCE = Pattern.compile("([^\\s:]+\\.py)::(test_\\w+)");

    private PortedFixtures() {
    }

    /**
     * Where a fixture comes from.
     *
     * @param original    {@code true} for an original-derived fixture, {@code false} for a deliberate change
     * @param pytestPath  the path of the original pytest suite, empty for a deliberate change
     * @param cases       the pytest cases the fixture is derived from, empty for a deliberate change
     */
    record Header(boolean original, String pytestPath, Set<String> cases) {
    }

    /**
     * One directive line of a fixture body.
     *
     * @param line      the line number in the fixture file
     * @param keyword   the directive keyword
     * @param arguments the arguments, the last one holding the rest of the line for a rest-taking directive
     */
    record Directive(int line, String keyword, List<String> arguments) {
    }

    /**
     * The shape of one directive of a primitive's grammar.
     *
     * @param arguments the number of arguments
     * @param rest      whether the last argument takes the rest of the line, spaces included (it may be empty)
     */
    record Shape(int arguments, boolean rest) {
    }

    /**
     * A loaded fixture.
     *
     * @param name       the file name without its suffix
     * @param header     where it comes from
     * @param requires   the capabilities it needs to run
     * @param directives its body in file order
     */
    record Fixture(String name, Header header, Set<String> requires, List<Directive> directives) {

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Enumerates and parses every fixture of a class-path directory.
     *
     * @param directory the class-path directory, e.g. {@code ported/worktree-sha}
     * @param grammar   the directives the primitive declares
     * @return the fixtures, sorted by name
     * @throws UncheckedIOException if the fixture directory cannot be listed or a fixture cannot be read
     * @throws IllegalStateException if the fixture directory is not on the test class path as a directory
     */
    static List<Fixture> load(String directory, Map<String, Shape> grammar) {
        Path root = directory(directory);
        try (Stream<Path> files = Files.list(root)) {
            List<Path> fixtures = files.filter(p -> p.getFileName().toString().endsWith(SUFFIX))
                    .filter(p -> !ROSTER.equals(p.getFileName().toString())).sorted().toList();
            var loaded = new ArrayList<Fixture>();
            for (Path file : fixtures) {
                String name = file.getFileName().toString();
                loaded.add(parse(name.substring(0, name.length() - SUFFIX.length()),
                        Files.readString(file, StandardCharsets.UTF_8), grammar));
            }
            return List.copyOf(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the fixtures of " + directory, e);
        }
    }

    /**
     * Reads a non-fixture file of the fixture directory, such as the roster.
     *
     * @param directory the class-path directory
     * @param file      the file name
     * @return its content
     * @throws UncheckedIOException if the file cannot be read
     * @throws IllegalStateException if the fixture directory is not on the test class path as a directory
     */
    static String read(String directory, String file) {
        try {
            return Files.readString(directory(directory).resolve(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + directory + "/" + file, e);
        }
    }

    /**
     * Parses one fixture.
     *
     * @param name    the fixture name (the file name without its suffix)
     * @param text    the file content
     * @param grammar the directives the primitive declares
     * @return the fixture
     * @throws IllegalArgumentException if the fixture is malformed, naming the fixture and the line
     */
    static Fixture parse(String name, String text, Map<String, Shape> grammar) {
        Header header = null;
        Set<String> requires = new TreeSet<>();
        var directives = new ArrayList<Directive>();
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            int number = index + 1;
            String line = lines[index];
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (header == null) {
                header = header(name, number, line);
            } else if (line.startsWith("requires: ")) {
                requires.add(line.substring("requires: ".length()).strip());
            } else {
                directives.add(directive(name, number, line, grammar));
            }
        }
        if (header == null) {
            throw refusal(name, 0, "has no header");
        }
        if (directives.isEmpty()) {
            throw refusal(name, 0, "has no directive");
        }
        return new Fixture(name, header, Set.copyOf(requires), List.copyOf(directives));
    }

    private static Header header(String name, int number, String line) {
        if (line.startsWith("deliberate: ")) {
            String deliberate = line.substring("deliberate: ".length()).strip();
            if (!deliberate.equals(name)) {
                throw refusal(name, number, "names the deliberate change '" + deliberate + "', not its file name");
            }
            return new Header(false, "", Set.of());
        }
        if (!line.startsWith("original: ")) {
            throw refusal(name, number, "has an unknown header: " + line);
        }
        String[] parts = line.substring("original: ".length()).strip().split(" ");
        String pin = "plan-marshall@" + INVENTORY_PIN;
        if (!pin.equals(parts[0])) {
            throw refusal(name, number, "names " + parts[0] + ", not the inventory pin " + pin);
        }
        if (parts.length < 2) {
            throw refusal(name, number, "names no pytest case");
        }
        String path = null;
        Set<String> cases = new TreeSet<>();
        for (int i = 1; i < parts.length; i++) {
            var matcher = CASE_REFERENCE.matcher(parts[i]);
            if (!matcher.matches()) {
                throw refusal(name, number, "has a malformed pytest case reference: " + parts[i]);
            }
            if (path != null && !path.equals(matcher.group(1))) {
                throw refusal(name, number, "names cases of two pytest suites");
            }
            path = matcher.group(1);
            cases.add(matcher.group(2));
        }
        return new Header(true, Objects.requireNonNull(path), Set.copyOf(cases));
    }

    private static Directive directive(String name, int number, String line, Map<String, Shape> grammar) {
        int space = line.indexOf(' ');
        String keyword = space < 0 ? line : line.substring(0, space);
        Shape shape = grammar.get(keyword);
        if (shape == null) {
            throw refusal(name, number, "has a directive the grammar does not declare: " + line);
        }
        String remainder = space < 0 ? "" : line.substring(space + 1);
        List<String> arguments;
        if (shape.rest()) {
            String[] split = remainder.split(" ", shape.arguments());
            arguments = List.of(split);
            if (space < 0 || split.length != shape.arguments()) {
                throw refusal(name, number, keyword + " takes " + shape.arguments() + " argument(s): " + line);
            }
        } else {
            arguments = remainder.isEmpty() ? List.of() : List.of(remainder.split(" ", -1));
            if (arguments.size() != shape.arguments() || arguments.stream().anyMatch(String::isEmpty)) {
                throw refusal(name, number, keyword + " takes " + shape.arguments() + " argument(s): " + line);
            }
        }
        return new Directive(number, keyword, arguments);
    }

    /**
     * Reads the roster of the original's pytest cases.
     * <p>
     * The roster names its suite once, {@code suite: plan-marshall@<pin> <pytest path>}, with the pin of the
     * inventory, and then has one line per case: {@code case <name> -> <fixture>[ <fixture>...]} or
     * {@code case <name> excluded: <reason>}; {@code #} lines and blank lines are ignored.
     *
     * @param text the roster content
     * @return the suite and, per case, its fixtures or the reason of its exclusion, in file order
     * @throws IllegalArgumentException if the roster is malformed, naming the roster and the line where one applies
     */
    static Roster roster(String text) {
        String suite = null;
        var entries = new LinkedHashMap<String, RosterEntry>();
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("suite: ")) {
                String[] parts = line.substring("suite: ".length()).strip().split(" ");
                if (parts.length != 2 || !("plan-marshall@" + INVENTORY_PIN).equals(parts[0]) || suite != null) {
                    throw refusal(ROSTER, index + 1, "needs one suite line naming the inventory pin: " + line);
                }
                suite = parts[1];
                continue;
            }
            if (!line.startsWith("case ")) {
                throw refusal(ROSTER, index + 1, "is no case line: " + line);
            }
            String body = line.substring("case ".length());
            RosterEntry entry;
            String caseName;
            int arrow = body.indexOf(" -> ");
            int excluded = body.indexOf(" excluded: ");
            if (arrow > 0) {
                caseName = body.substring(0, arrow);
                entry = new RosterEntry(Set.of(body.substring(arrow + " -> ".length()).strip().split(" ")), "");
            } else if (excluded > 0) {
                caseName = body.substring(0, excluded);
                entry = new RosterEntry(Set.of(), body.substring(excluded + " excluded: ".length()).strip());
            } else {
                throw refusal(ROSTER, index + 1, "maps the case to no fixture and gives no exclusion: " + line);
            }
            if (entries.put(caseName, entry) != null) {
                throw refusal(ROSTER, index + 1, "lists the case twice: " + caseName);
            }
        }
        if (suite == null) {
            throw refusal(ROSTER, 0, "names no suite");
        }
        return new Roster(suite, entries);
    }

    /**
     * The roster of the original's pytest suite.
     *
     * @param pytestPath the path of the suite in the original repository
     * @param cases      per case its entry, in file order
     */
    record Roster(String pytestPath, Map<String, RosterEntry> cases) {
    }

    /**
     * One case of the roster.
     *
     * @param fixtures the fixtures derived from the case, empty for an excluded case
     * @param reason   the reason of an exclusion, empty for a mapped case
     */
    record RosterEntry(Set<String> fixtures, String reason) {
    }

    private static Path directory(String directory) {
        URL url = PortedFixtures.class.getClassLoader().getResource(directory);
        if (url == null || !"file".equals(url.getProtocol())) {
            throw new IllegalStateException("fixture directory " + directory + " is not on the test class path as a directory");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("fixture directory " + directory + " has no file path", e);
        }
    }

    private static IllegalArgumentException refusal(String fixture, int line, String reason) {
        return new IllegalArgumentException("fixture " + fixture + (line > 0 ? " line " + line : "") + " " + reason);
    }
}
