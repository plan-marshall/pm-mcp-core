/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The token-based clone detection the server runs before the {@code finalize.simplify} task, in-process and without a
 * model (Phase Workflows § Finalize Step Ordering Bands): the lines the plan added, tokenized with identifiers and
 * literals normalized, against the rest of the tree. A run of at least {@value CloneCandidate#MIN_TOKENS} tokens that
 * repeats an existing run is a candidate; at most {@value CloneCandidate#MAX_CANDIDATES} candidates are named, the
 * longest first.
 * <p>
 * Normalizing an identifier to {@code i} and a numeric or string literal to {@code l} and {@code s} makes a copy
 * detectable across renamed variables and changed constants, which is exactly the duplication the restraint ladder
 * asks about. Comments and white space are not tokens.
 *
 * @since 0.1
 */
public final class CloneDetector {

    private static final String IDENTIFIER = "i";

    private static final String LITERAL = "l";

    private static final String TEXT = "s";

    private CloneDetector() {
    }

    /**
     * One source file with the line ranges the plan added.
     *
     * @param path        the repository-relative path
     * @param text        the content of the file
     * @param addedRanges the lines the plan added; empty for a whole file the plan created
     * @since 0.1
     */
    public record SourceFile(String path, String text, List<LineRange> addedRanges) {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public SourceFile {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(text, "text");
            addedRanges = List.copyOf(Objects.requireNonNull(addedRanges, "addedRanges"));
        }
    }

    /**
     * Finds the runs of the added lines that repeat the tree.
     *
     * @param changed the files the plan changed, each with its added ranges
     * @param tree    the rest of the tree; it must not hold a changed file
     * @return the candidates, longest first, at most {@value CloneCandidate#MAX_CANDIDATES}
     */
    public static List<CloneCandidate> detect(List<SourceFile> changed, List<SourceFile> tree) {
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(tree, "tree");
        var index = new HashMap<String, Occurrence>();
        for (var file : tree) {
            var tokens = Tokenizer.tokenize(file.text());
            for (var start = 0; start + CloneCandidate.MIN_TOKENS <= tokens.size(); start++) {
                index.putIfAbsent(key(tokens, start, CloneCandidate.MIN_TOKENS),
                        new Occurrence(file.path(), tokens, start));
            }
        }
        var candidates = new ArrayList<CloneCandidate>();
        for (var file : changed) {
            candidates.addAll(detectIn(file, index));
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt(CloneCandidate::tokens).reversed()
                        .thenComparing(CloneCandidate::addedPath)
                        .thenComparingInt(candidate -> candidate.addedRange().first()))
                .limit(CloneCandidate.MAX_CANDIDATES)
                .toList();
    }

    private static List<CloneCandidate> detectIn(SourceFile file, Map<String, Occurrence> index) {
        var candidates = new ArrayList<CloneCandidate>();
        for (var added : addedRuns(file)) {
            candidates.addAll(detectRuns(file, added, index));
        }
        return candidates;
    }

    private static List<CloneCandidate> detectRuns(SourceFile file, List<Token> added,
            Map<String, Occurrence> index) {
        var candidates = new ArrayList<CloneCandidate>();
        var start = 0;
        while (start + CloneCandidate.MIN_TOKENS <= added.size()) {
            var occurrence = index.get(key(added, start, CloneCandidate.MIN_TOKENS));
            if (occurrence == null) {
                start++;
                continue;
            }
            var length = CloneCandidate.MIN_TOKENS;
            while (start + length < added.size() && occurrence.start() + length < occurrence.tokens().size()
                    && added.get(start + length).normalized().equals(occurrence.tokens().get(occurrence.start() + length)
                    .normalized())) {
                length++;
            }
            candidates.add(new CloneCandidate(file.path(), range(added, start, length), occurrence.path(),
                    range(occurrence.tokens(), occurrence.start(), length), length));
            start += length;
        }
        return candidates;
    }

    /**
     * The added tokens, one run per added range: two hunks separated by unchanged code are never joined into one run,
     * so a candidate's range and its token run never span a gap of unchanged code.
     */
    private static List<List<Token>> addedRuns(SourceFile file) {
        var tokens = Tokenizer.tokenize(file.text());
        if (file.addedRanges().isEmpty()) {
            return List.of(tokens);
        }
        return file.addedRanges().stream()
                .map(range -> tokens.stream()
                        .filter(token -> token.line() >= range.first() && token.line() <= range.last()).toList())
                .toList();
    }

    private static LineRange range(List<Token> tokens, int start, int length) {
        return new LineRange(tokens.get(start).line(), tokens.get(start + length - 1).line());
    }

    private static String key(List<Token> tokens, int start, int length) {
        var key = new StringBuilder();
        for (var index = start; index < start + length; index++) {
            key.append(tokens.get(index).normalized()).append('\u0000');
        }
        return key.toString();
    }

    /** One token of the normalized stream and the line it stands on. */
    private record Token(String normalized, int line) {
    }

    /** Where a normalized run repeats in the tree. */
    private record Occurrence(String path, List<Token> tokens, int start) {
    }

    /** The lexer of the normalized token stream. */
    private static final class Tokenizer {

        private Tokenizer() {
        }

        static List<Token> tokenize(String text) {
            var tokens = new ArrayList<Token>();
            var line = 1;
            var index = 0;
            while (index < text.length()) {
                var character = text.charAt(index);
                if (character == '\n') {
                    line++;
                    index++;
                } else if (Character.isWhitespace(character)) {
                    index++;
                } else if (startsComment(text, index)) {
                    var cursor = skipComment(text, index, line);
                    index = cursor.index();
                    line = cursor.line();
                } else if (character == '"' || character == '\'') {
                    var startLine = line;
                    var cursor = skipLiteral(text, index, character, line);
                    index = cursor.index();
                    line = cursor.line();
                    tokens.add(new Token(TEXT, startLine));
                } else if (Character.isDigit(character)) {
                    index = skipNumber(text, index);
                    tokens.add(new Token(LITERAL, line));
                } else if (Character.isJavaIdentifierStart(character)) {
                    index = skipIdentifier(text, index);
                    tokens.add(new Token(IDENTIFIER, line));
                } else {
                    tokens.add(new Token(String.valueOf(character), line));
                    index++;
                }
            }
            return List.copyOf(tokens);
        }

        private static boolean startsComment(String text, int index) {
            return text.charAt(index) == '/' && index + 1 < text.length()
                    && (text.charAt(index + 1) == '/' || text.charAt(index + 1) == '*');
        }

        private static Cursor skipComment(String text, int start, int line) {
            var index = start + 2;
            if (text.charAt(start + 1) == '/') {
                while (index < text.length() && text.charAt(index) != '\n') {
                    index++;
                }
                return new Cursor(index, line);
            }
            while (index + 1 < text.length() && !(text.charAt(index) == '*' && text.charAt(index + 1) == '/')) {
                if (text.charAt(index) == '\n') {
                    line++;
                }
                index++;
            }
            return new Cursor(Math.min(text.length(), index + 2), line);
        }

        private static int skipNumber(String text, int start) {
            var index = start;
            while (index < text.length() && (Character.isLetterOrDigit(text.charAt(index))
                    || text.charAt(index) == '.' || text.charAt(index) == '_')) {
                index++;
            }
            return index;
        }

        private static int skipIdentifier(String text, int start) {
            var index = start;
            while (index < text.length() && Character.isJavaIdentifierPart(text.charAt(index))) {
                index++;
            }
            return index;
        }

        private record Cursor(int index, int line) {
        }

        private static Cursor skipLiteral(String text, int start, char quote, int startLine) {
            var textBlock = quote == '"' && text.startsWith("\"\"\"", start);
            var index = start + (textBlock ? 3 : 1);
            var line = startLine;
            while (index < text.length()) {
                var character = text.charAt(index);
                if (character == '\\') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                        line++;
                    }
                    index += 2;
                } else if (textBlock ? text.startsWith("\"\"\"", index) : character == quote) {
                    return new Cursor(index + (textBlock ? 3 : 1), line);
                } else if (character == '\n') {
                    if (!textBlock) {
                        return new Cursor(index, line);
                    }
                    line++;
                    index++;
                } else {
                    index++;
                }
            }
            return new Cursor(text.length(), line);
        }
    }
}
