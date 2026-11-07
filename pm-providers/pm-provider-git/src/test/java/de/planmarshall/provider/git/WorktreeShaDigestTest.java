/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The version-1 framing of the working-tree digest. The entry tags {@code F}, {@code L} and {@code D}, the NUL
 * separators and the 8-byte big-endian length are the framing of the specification; the head, the paths and the
 * contents are generated.
 */
@EnableGeneratorController
@DisplayName("WorktreeShaDigest framing")
class WorktreeShaDigestTest {

    /** One expected entry: path, tag and payload. */
    record Entry(String path, char tag, byte[] payload) {
    }

    static Entry file(String path, String content) {
        return new Entry(path, 'F', content.getBytes(StandardCharsets.UTF_8));
    }

    static Entry link(String path, String target) {
        return new Entry(path, 'L', target.getBytes(StandardCharsets.UTF_8));
    }

    static Entry deleted(String path) {
        return new Entry(path, 'D', null);
    }

    /** Independent computation of the version-1 framing, written from the specification text. */
    static String independentDigest(String head, List<Entry> entries) {
        var bytes = new ByteArrayOutputStream();
        bytes.writeBytes(head.getBytes(StandardCharsets.US_ASCII));
        bytes.write(0);
        entries.stream().sorted(Comparator.comparing(Entry::path)).forEach(e -> {
            bytes.writeBytes(e.path().getBytes(StandardCharsets.UTF_8));
            bytes.write(0);
            bytes.write(e.tag());
            if (e.payload() != null) {
                long length = e.payload().length;
                for (int shift = 56; shift >= 0; shift -= 8) {
                    bytes.write((int) (length >>> shift) & 0xFF);
                }
                bytes.writeBytes(e.payload());
            }
        });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String head;
    private TypedGenerator<String> names;

    @BeforeEach
    void generate() {
        head = JGitCacheRepositoryTest.commitId();
        // lower-case ASCII names, distinct, so the byte order of the paths is the order of the strings
        names = Generators.uniqueValues(Generators.strings("abcdefghijklmnopqrstuvwxyz", 1, 10));
    }

    private static String content() {
        return Generators.letterStrings(1, 40).next();
    }

    @Test
    @DisplayName("frames files, links and deletions sorted by path bytes")
    void frames(@TempDir Path root) throws Exception {
        var top = names.next() + ".txt";
        var nested = names.next() + "/" + names.next() + ".txt";
        var link = names.next();
        var gone = names.next() + ".txt";
        var topContent = content();
        var nestedContent = content();
        Files.writeString(root.resolve(top), topContent);
        Files.createDirectories(root.resolve(nested).getParent());
        Files.writeString(root.resolve(nested), nestedContent);
        Files.createSymbolicLink(root.resolve(link), Path.of(nested));

        String digest = WorktreeShaDigest.digest(head, root, List.of(top, link, gone, nested));

        assertEquals(independentDigest(head, List.of(file(top, topContent), link(link, nested), deleted(gone),
                file(nested, nestedContent))), digest, "the digest matches the independent framing");
    }

    @Test
    @DisplayName("has a fixed digest for an empty path set")
    void emptySet(@TempDir Path root) throws Exception {
        assertEquals(independentDigest(head, List.of()), WorktreeShaDigest.digest(head, root, List.of()),
                "an empty path set frames the head alone");
    }
}
