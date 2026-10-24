/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The protocol values the developer guide lists are the values the protocol has: the refusals of {@link TaskRefusal} in
 * their order, and the reasons of {@link WaitAnswer.WaitAgain} and {@link WaitAnswer.End}. The guide names a list that
 * a change to the protocol would otherwise leave stale.
 */
@DisplayName("Protocol vocabulary parity")
class ProtocolVocabularyParityTest {

    private static final String GUIDE = "pm-workflow/doc/developer/workflow-task.adoc";
    private static final String PROTOCOL = "pm-workflow/src/main/java/de/planmarshall/workflow/task/TaskProtocol.java";
    private static final Pattern LOWER_CASE_NAME = Pattern.compile("`([a-z_]+)`");
    private static final String REFUSAL_MARKER = "`TaskRefusal`: ";
    private static final String REFUSAL_END = ", in that order";
    private static final String WAIT_AGAIN = "`WaitAgain`\\s*\\(([^)]*)\\)";
    private static final String END = "`End`\\s*\\(([^)]*)\\)";

    @Test
    @DisplayName("the refusals of the guide are the refusals of the protocol, in their order")
    void refusals() {
        var text = guide();
        var start = text.indexOf(REFUSAL_MARKER);
        if (start < 0) {
            fail("the guide does not list the TaskRefusal values");
        }
        var end = text.indexOf(REFUSAL_END, start);
        if (end < 0) {
            fail("the TaskRefusal list of the guide does not end with '" + REFUSAL_END + "'");
        }

        var documented = names(text.substring(start, end));
        var implemented = List.of(TaskRefusal.values()).stream().map(TaskRefusal::wireName).toList();

        assertEquals(implemented, documented, "the guide lists the refusals in the order of the protocol");
    }

    @Test
    @DisplayName("the wait-answer reasons of the guide are the reasons the protocol answers")
    void waitAnswerReasons() {
        var text = guide();
        var source = source();
        var waitAgain = names(group(text, WAIT_AGAIN, "WaitAgain"));
        var end = names(group(text, END, "End"));

        assertAll(
                () -> assertEquals(reasonsOf(source, "WaitAgain"), waitAgain,
                        "the guide lists the reasons of WaitAgain the protocol answers"),
                // the end reasons are the runtime's too: the guide names drained and stopped, which this protocol
                // never answers, so it states at least every reason the protocol answers
                () -> assertTrue(end.containsAll(reasonsOf(source, "End")),
                        () -> "the guide does not list the end reasons the protocol answers: " + end));
    }

    private static String group(String text, String regex, String name) {
        var matcher = Pattern.compile(regex).matcher(text);
        if (!matcher.find()) {
            fail("the guide does not list the reasons of " + name);
        }
        return matcher.group(1);
    }

    private static List<String> reasonsOf(String source, String kind) {
        var matcher = Pattern.compile("new WaitAnswer\\." + kind + "\\(\\s*\"([a-z_]+)\"\\s*\\)").matcher(source);
        var reasons = new LinkedHashSet<String>();
        while (matcher.find()) {
            reasons.add(matcher.group(1));
        }
        if (reasons.isEmpty()) {
            fail("the protocol answers no " + kind + " reason");
        }
        return List.copyOf(reasons);
    }

    private static List<String> names(String text) {
        var matcher = LOWER_CASE_NAME.matcher(text);
        var names = new ArrayList<String>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String guide() {
        return read(GUIDE);
    }

    private static String source() {
        return read(PROTOCOL);
    }

    private static String read(String relative) {
        try {
            return Files.readString(file(relative), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + relative, e);
        }
    }

    /** The file below the module or, when the test runs from the root of the build, below the repository root. */
    private static Path file(String relative) {
        var candidates = new ArrayList<Path>();
        var module = Path.of("").toAbsolutePath().normalize();
        candidates.add(module.resolve(relative));
        if (module.getParent() != null) {
            candidates.add(module.getParent().resolve(relative));
        }
        return candidates.stream().filter(Files::isRegularFile).findFirst()
                .orElseGet(() -> fail("not found below the module or the repository root: "
                        + candidates.stream().map(Path::toString).collect(Collectors.joining(", "))));
    }
}
