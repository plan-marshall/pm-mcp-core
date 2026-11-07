/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.gitlab;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.planmarshall.provider.http.Json;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code glab api} invocations. The command words, the flags, the HTTP methods, the GitLab API paths with their
 * query parameters and the JSON member names are the contract of the {@code glab} CLI and the GitLab REST API, kept as
 * literals; the host, the project, the numbers, the ids, the branches and the texts are generated.
 */
@EnableGeneratorController
@DisplayName("GlabCommands")
class GlabCommandsTest {

    /** The flags of a request that carries a JSON body on standard input (contract: glab api). */
    private static final List<String> BODY_FLAGS = List.of("--header", "Content-Type: application/json", "--input", "-");

    private String host;
    private String group;
    private String project;
    private String encodedProject;
    private int mergeRequest;
    private GlabCommands glab;

    @BeforeEach
    void generate() {
        host = word() + "." + word();
        group = word();
        project = word();
        // the URL encoding of the separator of a project path (contract: GitLab API project ids)
        encodedProject = group + "%2F" + project;
        mergeRequest = Generators.integers(1, 100_000).next();
        glab = new GlabCommands(host, group + "/" + project);
    }

    private static String word() {
        return Generators.strings("abcdefghijklmnopqrstuvwxyz", 3, 12).next();
    }

    private static String sha() {
        return Generators.strings("0123456789abcdef", 40, 40).next();
    }

    private List<String> argv(List<String> head, List<String> tail) {
        // the command words and the host flag of every invocation (contract: glab api)
        var all = new ArrayList<>(List.of("glab", "api", "--hostname", host));
        all.addAll(head);
        all.addAll(tail);
        return all;
    }

    @Test
    @DisplayName("merges and adds to the merge train with the sha on standard input")
    void mergePath() {
        var sha = sha();
        var merge = glab.merge(mergeRequest, sha);
        var train = glab.mergeTrainAdd(mergeRequest, sha);

        assertAll(
                () -> assertEquals(argv(List.of("--method", "PUT"), concat(BODY_FLAGS,
                        "projects/" + encodedProject + "/merge_requests/" + mergeRequest + "/merge")), merge.argv(),
                        "the merge is a PUT on the merge endpoint"),
                () -> assertEquals(Optional.of("{\"sha\":\"" + sha + "\"}"), merge.stdin(),
                        "the merge pins the sha on standard input"),
                () -> assertEquals(argv(List.of("--method", "POST"), concat(BODY_FLAGS,
                        "projects/" + encodedProject + "/merge_trains/merge_requests/" + mergeRequest)), train.argv(),
                        "the train add is a POST on the train endpoint"));
    }

    @Test
    @DisplayName("streams multi-line bodies on standard input, never in the argument vector")
    void bodiesOnStdin() {
        // an adversarial reply: a flag, a markdown heading, quotes and a command substitution
        String reply = "--force\n# heading\n\"quoted\" $(rm -rf /)";
        var discussion = sha();
        var sourceBranch = word() + "/" + word();
        // long enough that no other generated value of the invocation contains it
        var secondLine = Generators.letterStrings(24, 32).next();
        var description = word() + "\n\n" + secondLine;

        var invocation = glab.reply(mergeRequest, discussion, reply);
        var create = glab.createMergeRequest(sourceBranch, word(), word(), description);

        assertAll(
                () -> assertTrue(invocation.argv().stream().noneMatch(a -> a.contains("rm -rf") || a.contains("heading")),
                        "no line of the reply reaches the argv"),
                () -> assertEquals(Optional.of(reply),
                        Json.string(Json.parse(invocation.stdin().orElseThrow()).orElseThrow(), "body"),
                        "the reply travels as the body on stdin"),
                () -> assertEquals("projects/" + encodedProject + "/merge_requests/" + mergeRequest + "/discussions/"
                        + discussion + "/notes", invocation.argv().getLast(),
                        "the reply is posted to the discussion notes"),
                () -> assertTrue(create.argv().stream().noneMatch(a -> a.contains(secondLine)),
                        "no line of the description reaches argv"),
                () -> assertEquals(Optional.of(description),
                        Json.string(Json.parse(create.stdin().orElseThrow()).orElseThrow(), "description"),
                        "the description travels on stdin"),
                () -> assertEquals(Optional.of(sourceBranch),
                        Json.string(Json.parse(create.stdin().orElseThrow()).orElseThrow(), "source_branch"),
                        "the source branch travels on stdin"));
    }

    @Test
    @DisplayName("builds the read invocations without standard input")
    void reads() {
        var discussion = word();
        var job = Generators.integers(1, 100_000).next();

        assertAll(
                () -> assertEquals(argv(List.of("--paginate"), List.of("projects/" + encodedProject
                        + "/merge_requests/" + mergeRequest + "/discussions?per_page=100")),
                        glab.discussions(mergeRequest).argv(), "the discussions are read page by page"),
                () -> assertEquals(argv(List.of("--method", "PUT"), List.of("projects/" + encodedProject
                        + "/merge_requests/" + mergeRequest + "/discussions/" + discussion + "?resolved=true")),
                        glab.resolve(mergeRequest, discussion).argv(), "a resolve is a PUT with resolved=true"),
                () -> assertEquals(argv(List.of(), List.of("projects/" + encodedProject + "/jobs/" + job + "/trace")),
                        glab.jobTrace(job).argv(), "a job trace is a plain read"),
                () -> assertEquals(argv(List.of(), List.of("personal_access_tokens/self")),
                        glab.tokenIdentity().argv(), "the token identity is a plain read"),
                () -> assertFalse(glab.tokenIdentity().stdin().isPresent(), "a read carries no standard input"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-d1", "d1 --paginate", "../x", ""})
    @DisplayName("refuses identifiers that could be read as flags or paths (adversarial contract values)")
    void refusesDiscussionIds(String id) {
        assertThrows(IllegalArgumentException.class, () -> glab.resolve(mergeRequest, id),
                "a flag-like or path-like id is refused");
    }

    @Test
    @DisplayName("refuses invalid hosts, branches and ids (a flag-like host and branch, a zero and a negative number)")
    void refusesOtherValues() {
        var path = group + "/" + project;
        var target = word();
        var title = word();
        var body = word();
        var sha = sha();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new GlabCommands("--hostname", path),
                        "a flag-like host is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> glab.createMergeRequest("-x", target, title, body), "a flag-like branch is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> glab.jobTrace(0),
                        "a job id of zero is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> glab.merge(-1, sha),
                        "a negative merge request number is refused"));
    }

    private static List<String> concat(List<String> list, String last) {
        var all = new ArrayList<>(list);
        all.add(last);
        return all;
    }
}
