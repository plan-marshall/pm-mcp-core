/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

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
 * Loads the plain-file fixtures of the ported build parsers from the test class path (PM-TEST-2).
 * <p>
 * The fixtures are enumerated from their directory: every {@code *.txt} file of it is a fixture, except the roster
 * {@value #ROSTER}. A fixture is never listed by hand, so a file added to the directory runs without a code change.
 * <p>
 * A fixture has this form (lines starting with {@code #} and blank lines are ignored):
 *
 * <pre>
 * original: plan-marshall@&lt;pin&gt; &lt;pytest path&gt;::&lt;case&gt; [&lt;pytest path&gt;::&lt;case&gt; ...]
 * &lt;directive&gt; &lt;argument&gt;
 * </pre>
 *
 * or, for a deliberate change, {@code deliberate: <name>} as the first line, where {@code <name>} is the file name without
 * its {@code .txt} suffix. The directives a fixture may use are the ones in {@link #GRAMMAR}; a directive the grammar
 * does not declare, or one of the wrong arity, is refused naming the fixture and the line, so a malformed fixture fails
 * the suite instead of being skipped.
 * <p>
 * A test class evaluates the directives of its own parser and then calls {@link #assertConsumed}, which fails when a
 * directive of the fixture was never looked at — a typo in a keyword cannot silently weaken a case.
 * <p>
 * The loader refuses, as well: an {@code original:} header whose pin is not {@link #PIN}, a deliberate header whose name is
 * not the file name, a fixture with no expectation, a {@code log-file} that cannot be read, and a malformed roster.
 *
 * @since 0.1
 */
final class BuildLogFixtures {

    /**
     * The {@code plan-marshall} commit the fixtures were derived from: the head of the sibling checkout when the ported
     * sources and their pytest suites were read by hand.
     */
    static final String PIN = "7b7e08f088b6c1b333147aeb2c636325da31b0b6";

    /** The roster of the original pytest cases of a fixture directory, excluded from the fixtures. */
    static final String ROSTER = "original-cases.txt";

    /**
     * The directives a fixture may use, each with its argument count and whether its single argument takes the rest of
     * the line, spaces included. An argument taking the rest may be empty, so {@code log} alone is an empty log line.
     */
    static final Map<String, Shape> GRAMMAR = grammar();

    private static final String SUFFIX = ".txt";

    private static final String HEADER_ORIGINAL = "original: ";

    private static final String HEADER_DELIBERATE = "deliberate: ";

    private static final Pattern CASE_REFERENCE = Pattern.compile("([^\\s:]+\\.py)::([\\w:]+)");

    private BuildLogFixtures() {
    }

    /**
     * The shape of one directive of the grammar.
     *
     * @param arguments the number of arguments
     * @param rest      whether the single argument takes the rest of the line, spaces included, and may be empty
     * @since 0.1
     */
    record Shape(int arguments, boolean rest) {
    }

    /**
     * One directive line of a fixture body.
     *
     * @param line      the line number in the fixture file
     * @param keyword   the directive keyword
     * @param arguments the arguments, the last one holding the rest of the line for a rest-taking directive
     * @since 0.1
     */
    record Directive(int line, String keyword, List<String> arguments) {

        /**
         * Returns the single argument of the directive.
         *
         * @return the argument, possibly empty
         */
        String argument() {
            return arguments.getFirst();
        }
    }

    /**
     * A loaded fixture.
     *
     * @param name         the file name without its suffix
     * @param original     {@code true} for an original-derived fixture, {@code false} for a deliberate change
     * @param pytestPath   the path of the original pytest suite, empty for a deliberate change
     * @param cases        the pytest cases the fixture is derived from, empty for a deliberate change
     * @param log          the build-log text the parser receives
     * @param expectations the expectation directives, in file order
     * @since 0.1
     */
    record Case(
    String name,
    boolean original,
    String pytestPath,
    Set<String> cases,
    String log,
    List<Expectation> expectations) {

        /**
         * Returns every expectation of the given keyword, in file order.
         *
         * @param keyword the directive keyword
         * @return the expectations with that keyword
         */
        List<Expectation> of(String keyword) {
            return expectations.stream().filter(expectation -> expectation.keyword().equals(keyword)).toList();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * One evaluated expectation of a fixture.
     *
     * @param line     the line number in the fixture file
     * @param keyword  the directive keyword
     * @param argument the single argument of the directive
     * @since 0.1
     */
    record Expectation(int line, String keyword, String argument) {

        /**
         * Splits the argument on single spaces.
         *
         * @param parts the maximum number of parts; the last one then takes the rest of the argument
         * @return the parts
         */
        List<String> split(int parts) {
            return List.of(argument.split(" ", parts));
        }
    }

    /**
     * Enumerates and parses every fixture of a class-path directory.
     *
     * @param directory the class-path directory, e.g. {@code ported/build/maven}
     * @return the fixtures, sorted by name
     * @throws UncheckedIOException  if the directory cannot be listed or a file cannot be read
     * @throws IllegalStateException if the directory is not on the test class path as a directory
     */
    static List<Case> load(String directory) {
        Path root = directory(directory);
        try (Stream<Path> files = Files.list(root)) {
            List<Path> fixtures = files.filter(path -> path.getFileName().toString().endsWith(SUFFIX))
                    .filter(path -> !ROSTER.equals(path.getFileName().toString())).sorted().toList();
            var loaded = new ArrayList<Case>();
            for (Path file : fixtures) {
                String name = file.getFileName().toString();
                loaded.add(parse(name.substring(0, name.length() - SUFFIX.length()),
                        Files.readString(file, StandardCharsets.UTF_8), root));
            }
            return List.copyOf(loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the fixtures of " + directory, e);
        }
    }

    /**
     * Reads a non-fixture file of a fixture directory, such as the roster.
     *
     * @param directory the class-path directory
     * @param file      the file name
     * @return its content
     * @throws UncheckedIOException  if the file cannot be read
     * @throws IllegalStateException if the directory is not on the test class path as a directory
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
     * @param root    the fixture directory, used to resolve {@code log-file} references
     * @return the fixture
     * @throws IllegalArgumentException if the fixture is malformed, naming the fixture and the line
     */
    static Case parse(String name, String text, Path root) {
        boolean original = false;
        boolean headerSeen = false;
        String pytestPath = "";
        Set<String> cases = new TreeSet<>();
        var directives = new ArrayList<Directive>();
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            int number = index + 1;
            String line = lines[index];
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                headerSeen = true;
                if (line.startsWith(HEADER_DELIBERATE)) {
                    String deliberate = line.substring(HEADER_DELIBERATE.length()).strip();
                    if (!deliberate.equals(name)) {
                        throw refusal(name, number,
                                "names the deliberate change '" + deliberate + "', not its file name");
                    }
                    original = false;
                } else {
                    var header = header(name, number, line);
                    original = true;
                    pytestPath = header.path;
                    cases = header.cases;
                }
                continue;
            }
            directives.add(directive(name, number, line));
        }
        if (!headerSeen) {
            throw refusal(name, 0, "has no header");
        }
        if (directives.isEmpty()) {
            throw refusal(name, 0, "has no directive");
        }
        return new Case(name, original, pytestPath, Set.copyOf(cases), log(name, directives, root),
                expectations(directives));
    }

    /**
     * Fails when a directive of the fixture was never evaluated.
     *
     * @param fixture  the fixture
     * @param consumed the indices of the evaluated expectations
     * @throws AssertionError naming the fixture and the line of the first unevaluated directive
     */
    static void assertConsumed(Case fixture, Set<Integer> consumed) {
        for (int index = 0; index < fixture.expectations().size(); index++) {
            if (!consumed.contains(index)) {
                Expectation expectation = fixture.expectations().get(index);
                throw new AssertionError("fixture " + fixture.name() + " line " + expectation.line()
                        + " was not evaluated: " + expectation.keyword());
            }
        }
    }

    /**
     * Reads the roster of the original pytest cases of a fixture directory.
     * <p>
     * The roster names its suites, each {@code suite: plan-marshall@<pin> <pytest path>}, and then has one line per case:
     * {@code case <name> -> <fixture>[ <fixture>...]} or {@code case <name> excluded: <reason>}. A {@code case} line
     * belongs to the suite named last above it.
     *
     * @param directory the class-path directory
     * @return the roster, keyed by {@code <pytest path>::<case>}
     * @throws IllegalArgumentException if the roster is malformed, naming the line
     */
    static Roster roster(String directory) {
        return rosterOf(read(directory, ROSTER));
    }

    /**
     * Reads a roster from its text.
     *
     * @param text the roster content
     * @return the roster, keyed by {@code <pytest path>::<case>}
     * @throws IllegalArgumentException if the roster is malformed, naming the line
     */
    static Roster rosterOf(String text) {
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
                if (parts.length != 2 || !("plan-marshall@" + PIN).equals(parts[0])) {
                    throw refusal(ROSTER, index + 1, "needs one suite line naming the inventory pin: " + line);
                }
                suite = parts[1];
                continue;
            }
            if (!line.startsWith("case ")) {
                throw refusal(ROSTER, index + 1, "is no case line: " + line);
            }
            if (suite == null) {
                throw refusal(ROSTER, index + 1, "names a case before its suite: " + line);
            }
            String body = line.substring("case ".length());
            int arrow = body.indexOf(" -> ");
            int excluded = body.indexOf(" excluded: ");
            String caseName;
            RosterEntry entry;
            if (arrow > 0) {
                caseName = body.substring(0, arrow);
                Set<String> fixtures;
                try {
                    fixtures = Set.of(body.substring(arrow + " -> ".length()).strip().split(" "));
                } catch (IllegalArgumentException _) {
                    throw refusal(ROSTER, index + 1, "maps the case to a duplicate fixture: " + line);
                }
                entry = new RosterEntry(suite, caseName, fixtures, "");
            } else if (excluded > 0) {
                caseName = body.substring(0, excluded);
                entry = new RosterEntry(suite, caseName, Set.of(),
                        body.substring(excluded + " excluded: ".length()).strip());
            } else {
                throw refusal(ROSTER, index + 1, "maps the case to no fixture and gives no exclusion: " + line);
            }
            if (entries.put(suite + "::" + caseName, entry) != null) {
                throw refusal(ROSTER, index + 1, "lists the case twice: " + caseName);
            }
        }
        if (entries.isEmpty()) {
            throw refusal(ROSTER, 0, "names no case");
        }
        return new Roster(Map.copyOf(entries));
    }

    /**
     * The roster of the original pytest suites of a fixture directory.
     *
     * @param cases the entries, keyed by {@code <pytest path>::<case>}
     * @since 0.1
     */
    record Roster(Map<String, RosterEntry> cases) {
    }

    /**
     * One case of the roster.
     *
     * @param pytestPath the path of the suite the case belongs to
     * @param name       the case name
     * @param fixtures   the fixtures derived from the case, empty for an excluded case
     * @param reason     the reason of an exclusion, empty for a mapped case
     * @since 0.1
     */
    record RosterEntry(String pytestPath, String name, Set<String> fixtures, String reason) {
    }

    private record Header(String path, Set<String> cases) {
    }

    /**
     * Closes the fixture corpus against the roster, in both directions.
     * <p>
     * Every case an {@code original:} header names is in the roster; every mapped fixture exists and names that case; every
     * excluded case maps to no fixture and states its reason. A mapped deliberate change is exempt from the last check: a
     * {@code deliberate:} fixture carries no pytest case by convention, because it asserts the behaviour the port
     * corrects rather than a value of the original.
     *
     * @param directory     the class-path fixture directory
     * @param fixtures      the fixtures of that directory
     * @param expectedCases the number of cases the roster holds
     * @throws AssertionError if the corpus and the roster disagree
     */
    static void assertRoster(String directory, List<Case> fixtures, int expectedCases) {
        Roster roster = roster(directory);
        for (Case fixture : fixtures) {
            if (!fixture.original()) {
                continue;
            }
            for (String name : fixture.cases()) {
                if (!roster.cases().containsKey(fixture.pytestPath() + "::" + name)) {
                    throw new AssertionError("the roster does not list the case " + name + " of "
                            + fixture.pytestPath());
                }
            }
        }
        for (RosterEntry entry : roster.cases().values()) {
            if (!entry.reason().isEmpty()) {
                if (!entry.fixtures().isEmpty()) {
                    throw new AssertionError("the excluded case " + entry.name() + " maps to a fixture");
                }
                continue;
            }
            for (String name : entry.fixtures()) {
                Case fixture = fixtures.stream()
                        .filter(candidate -> candidate.name().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("the roster maps to no fixture: " + name));
                if (fixture.original() && !fixture.cases().contains(entry.name())) {
                    throw new AssertionError(
                            "the fixture " + name + " does not name the case " + entry.name());
                }
            }
        }
        if (roster.cases().size() != expectedCases) {
            throw new AssertionError(
                    "the roster holds " + roster.cases().size() + " cases, not " + expectedCases);
        }
    }

    private static Header header(String name, int number, String line) {
        if (!line.startsWith(HEADER_ORIGINAL)) {
            throw refusal(name, number, "has an unknown header: " + line);
        }
        String[] parts = line.substring(HEADER_ORIGINAL.length()).strip().split(" ");
        String pin = "plan-marshall@" + PIN;
        if (!pin.equals(parts[0])) {
            throw refusal(name, number, "names " + parts[0] + ", not the fixture pin " + pin);
        }
        if (parts.length < 2) {
            throw refusal(name, number, "names no pytest case");
        }
        String path = null;
        Set<String> cases = new TreeSet<>();
        for (int index = 1; index < parts.length; index++) {
            var matcher = CASE_REFERENCE.matcher(parts[index]);
            if (!matcher.matches()) {
                throw refusal(name, number, "has a malformed pytest case reference: " + parts[index]);
            }
            if (path != null && !path.equals(matcher.group(1))) {
                throw refusal(name, number, "names cases of two pytest suites");
            }
            path = matcher.group(1);
            cases.add(matcher.group(2));
        }
        return new Header(Objects.requireNonNull(path), Set.copyOf(cases));
    }

    private static Directive directive(String name, int number, String line) {
        int space = line.indexOf(' ');
        String keyword = space < 0 ? line : line.substring(0, space);
        Shape shape = GRAMMAR.get(keyword);
        if (shape == null) {
            throw refusal(name, number, "has a directive the grammar does not declare: " + line);
        }
        String remainder = space < 0 ? "" : line.substring(space + 1);
        if (shape.rest()) {
            if (shape.arguments() != 1) {
                throw refusal(name, number, keyword + " is declared with one rest argument");
            }
            if (remainder.isEmpty() && !"log".equals(keyword)) {
                throw refusal(name, number, keyword + " needs its argument: " + line);
            }
            return new Directive(number, keyword, List.of(remainder));
        }
        List<String> arguments = remainder.isEmpty() ? List.of() : List.of(remainder.split(" ", -1));
        if (arguments.size() != shape.arguments() || arguments.stream().anyMatch(String::isEmpty)) {
            throw refusal(name, number, keyword + " takes " + shape.arguments() + " argument(s): " + line);
        }
        return new Directive(number, keyword, arguments);
    }

    private static List<Expectation> expectations(List<Directive> directives) {
        var expectations = new ArrayList<Expectation>();
        for (Directive directive : directives) {
            if (!"log".equals(directive.keyword()) && !"log-file".equals(directive.keyword())
                    && !"filter-out".equals(directive.keyword()) && !"crlf".equals(directive.keyword())) {
                expectations.add(new Expectation(directive.line(), directive.keyword(), directive.arguments().isEmpty()
                        ? ""
                        : directive.arguments().getFirst()));
            }
        }
        return List.copyOf(expectations);
    }

    private static String log(String name, List<Directive> directives, Path root) {
        var text = new StringBuilder();
        for (Directive directive : directives) {
            switch (directive.keyword()) {
                case "log" -> text.append(directive.argument()).append('\n');
                case "log-file" -> text.append(readLogFile(name, directive, root));
                default -> {
                }
            }
        }
        String assembled = text.toString();
        for (Directive directive : directives) {
            if ("filter-out".equals(directive.keyword())) {
                assembled = assembled.lines()
                        .filter(line -> !line.contains(directive.argument()))
                        .reduce("", (left, right) -> left + right + "\n");
            }
        }
        if (directives.stream().anyMatch(directive -> "crlf".equals(directive.keyword()))) {
            assembled = assembled.replace("\r\n", "\n").replace("\n", "\r\n");
        }
        return assembled;
    }

    private static String readLogFile(String name, Directive directive, Path root) {
        Path file = root.resolve(directive.argument());
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return content.endsWith("\n") ? content : content + "\n";
        } catch (IOException e) {
            throw refusal(name, directive.line(), "names an unreadable log file: " + directive.argument());
        }
    }

    private static Map<String, Shape> grammar() {
        var grammar = new LinkedHashMap<String, Shape>();
        grammar.put("log", new Shape(1, true));
        grammar.put("log-file", new Shape(1, false));
        grammar.put("filter-out", new Shape(1, true));
        grammar.put("crlf", new Shape(0, false));
        List<String> restDirectives = List.of("status", "success-marker", "tests", "by-runner", "no-tests-ran",
                "diagnostic", "no-diagnostic", "diagnostic-count", "stack-trace-contains", "categorize", "domain",
                "verdict", "finding-count", "finding", "newly-detected", "pre-existing");
        restDirectives.forEach(keyword -> grammar.put(keyword, new Shape(1, true)));
        grammar.put("no-by-runner", new Shape(0, false));
        return Map.copyOf(grammar);
    }

    private static Path directory(String directory) {
        URL url = BuildLogFixtures.class.getClassLoader().getResource(directory);
        if (url == null || !"file".equals(url.getProtocol())) {
            throw new IllegalStateException(
                    "fixture directory " + directory + " is not on the test class path as a directory");
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
