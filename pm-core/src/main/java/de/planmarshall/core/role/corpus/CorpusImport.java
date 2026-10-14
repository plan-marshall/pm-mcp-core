/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import de.cuioss.tools.logging.CuiLogger;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import de.planmarshall.core.log.CoreLogMessages.ERROR;
import de.planmarshall.core.log.CoreLogMessages.WARN;
import de.planmarshall.core.role.corpus.ImportResult.CoveredSource;
import de.planmarshall.core.role.corpus.ImportResult.MissedSource;
import de.planmarshall.core.role.corpus.ImportResult.Reason;
import de.planmarshall.core.role.corpus.SourceManifest.SourceFile;
import de.planmarshall.core.role.corpus.SourceRow.MalformedSource;
import de.planmarshall.core.store.StoreRefusal;

/**
 * Imports the corpus sources (PM-WORK-6, PM-TEST-6): for every source below {@code sources/} of the corpus root, the
 * files its manifest names are fetched at the pinned commit, checked against their SHA-256, and the rows its overlay
 * selects are generated into items below the cache root.
 * <p>
 * The fetch goes through the two interfaces the daemon composes: {@link SourceRepository} for the git part and
 * {@link LfsObjectSource} for the objects of files stored in Git LFS. A plain file must have the SHA-256 of its
 * manifest; a file in LFS must be a pointer whose oid is that SHA-256, and the downloaded object must have it too.
 * <p>
 * A source is missed as a whole, and none of its items is written, when a step of its fetch is
 * {@link SourceFetch.Outcome#UNREACHABLE} or {@link SourceFetch.Outcome#PIN_MISSING}, when a file fails its hash, or
 * when its manifest, overlay or file does not yield the selected items; the {@link ImportResult} names it with its
 * reason. The items of the other sources are generated all the same. The items are written below
 * {@code <cache root>/items/} at {@code <topic>/<slug>.json}, replacing those of an earlier import; nothing is written
 * below the corpus root, and the cache root must lie outside it.
 *
 * @since 0.1
 */
public final class CorpusImport {

    /** The directory of the sources below the corpus root. */
    public static final String SOURCES = "sources";

    /** The manifest of a source, below its directory. */
    public static final String MANIFEST = "manifest.json";

    /** The overlay of a source, below its directory. */
    public static final String OVERLAY = "overlay.json";

    /** The directory of the generated items below the cache root. */
    public static final String ITEMS = "items";

    private static final CuiLogger LOGGER = new CuiLogger(CorpusImport.class);

    private final SourceRepository repository;
    private final LfsObjectSource lfs;

    /**
     * @param repository the git part of the fetch
     * @param lfs        the LFS part of the fetch
     */
    public CorpusImport(SourceRepository repository, LfsObjectSource lfs) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.lfs = Objects.requireNonNull(lfs, "lfs");
    }

    /**
     * Imports every source of the corpus.
     *
     * @param corpusRoot the corpus root, holding {@code sources/}
     * @param cacheRoot  the cache root the cache repositories and the items are written below, outside the corpus root
     * @return the sources covered and missed; a corpus root that exists without a {@code sources/} directory, or
     *         whose {@code sources/} holds no source, yields an empty, {@link ImportResult.Verdict#COMPLETE} result
     * @throws IllegalArgumentException if the cache root lies below the corpus root
     * @throws UncheckedIOException     if the corpus root does not exist, is no directory or cannot be read, if its
     *                                  {@code sources/} is no directory or cannot be listed, or if the items cannot be
     *                                  written; a corpus that cannot be read is never imported as one without
     *                                  sources, and the items of an earlier import are then left in place
     */
    public ImportResult run(Path corpusRoot, Path cacheRoot) {
        var corpus = Objects.requireNonNull(corpusRoot, "corpusRoot").toAbsolutePath().normalize();
        var cache = Objects.requireNonNull(cacheRoot, "cacheRoot").toAbsolutePath().normalize();
        if (cache.startsWith(corpus)) {
            throw new IllegalArgumentException("the cache root %s lies below the corpus root %s".formatted(cache,
                    corpus));
        }
        var directories = sourceDirectories(corpus);
        var itemsRoot = cache.resolve(ITEMS);
        deleteTree(itemsRoot);
        var covered = new ArrayList<CoveredSource>();
        var missed = new ArrayList<MissedSource>();
        var written = new HashSet<String>();
        for (var directory : directories) {
            var name = directory.getFileName().toString();
            try {
                var items = importSource(directory, name, cache);
                requireNewIds(name, items, written);
                items.forEach(item -> write(itemsRoot, item));
                covered.add(new CoveredSource(name, items.size()));
            } catch (SourceMissed e) {
                missed.add(e.missed);
            }
        }
        return new ImportResult(covered, missed);
    }

    private List<ImportedItem> importSource(Path directory, String name, Path cache) throws SourceMissed {
        var manifest = read(directory.resolve(MANIFEST), name, SourceManifestCodec::read);
        if (!manifest.name().equals(name)) {
            throw missed(name, Reason.MALFORMED, null,
                    "the manifest names the source '%s' in the directory '%s'".formatted(manifest.name(), name));
        }
        var overlay = read(directory.resolve(OVERLAY), name, SourceOverlayCodec::read);
        if (!overlay.source().equals(name) || manifest.file(overlay.file()).isEmpty()) {
            throw missed(name, Reason.MALFORMED, overlay.file(),
                    "the overlay does not name a file of the source's manifest");
        }
        content(repository.prepare(cache, manifest), name, null);
        content(repository.fetchCommit(cache, manifest), name, null);
        byte[] rowsFile = null;
        for (var file : manifest.files()) {
            var content = fetchFile(cache, manifest, file);
            if (file.path().equals(overlay.file())) {
                rowsFile = content;
            }
        }
        return generate(manifest, overlay, rows(name, overlay.file(), rowsFile));
    }

    private byte[] fetchFile(Path cache, SourceManifest manifest, SourceFile file) throws SourceMissed {
        var name = manifest.name();
        var stored = content(repository.readFile(cache, manifest, file.path()), name, file.path());
        if (!file.lfs()) {
            return verified(name, file, stored);
        }
        var pointer = LfsPointer.parse(stored);
        if (pointer.isEmpty()) {
            throw hashMismatch(name, file, "a file that is no LFS pointer, SHA-256 " + Sha256.hex(stored));
        }
        if (!pointer.get().oid().equals(file.sha256())) {
            throw hashMismatch(name, file, "the pointer oid " + pointer.get().oid());
        }
        var object = content(lfs.object(manifest.repository(), pointer.get()), name, file.path());
        return verified(name, file, object);
    }

    private static byte[] verified(String name, SourceFile file, byte[] content) throws SourceMissed {
        if (!Sha256.matches(content, file.sha256())) {
            throw hashMismatch(name, file, Sha256.hex(content));
        }
        return content;
    }

    private static byte[] content(SourceFetch fetch, String name, String file) throws SourceMissed {
        return switch (fetch.outcome()) {
            case FETCHED -> fetch.content();
            case UNREACHABLE -> throw missed(name, Reason.UNREACHABLE, file, fetch.detail());
            case PIN_MISSING -> throw missed(name, Reason.PIN_MISSING, file, fetch.detail());
        };
    }

    private static List<SourceRow> rows(String name, String file, byte[] content) throws SourceMissed {
        try {
            if (file.endsWith(".jsonl")) {
                return JsonlRowReader.read(content);
            }
            if (file.endsWith(".csv")) {
                return CsvRowReader.read(content);
            }
        } catch (MalformedSource e) {
            throw missed(name, Reason.MALFORMED, file, e.getMessage());
        }
        throw missed(name, Reason.MALFORMED, file, "the file is neither JSON Lines (.jsonl) nor CSV (.csv)");
    }

    private static List<ImportedItem> generate(SourceManifest manifest, SourceOverlay overlay, List<SourceRow> rows)
            throws SourceMissed {
        var items = new ArrayList<ImportedItem>();
        for (var row : overlay.rows()) {
            var source = select(manifest.name(), overlay, row, rows);
            try {
                items.add(ItemGenerator.generate(manifest, overlay, row, source));
            } catch (IllegalArgumentException e) {
                throw missed(manifest.name(), Reason.MALFORMED, overlay.file(),
                        "the row '%s' does not generate its item: %s".formatted(row.key(), e.getMessage()));
            }
        }
        return List.copyOf(items);
    }

    private static SourceRow select(String name, SourceOverlay overlay, SourceOverlay.Row row, List<SourceRow> rows)
            throws SourceMissed {
        if (overlay.keyField() == null) {
            if (row.key().length() > String.valueOf(rows.size()).length()
                    || Integer.parseInt(row.key()) > rows.size()) {
                throw missed(name, Reason.MALFORMED, overlay.file(),
                        "the file has no row at position " + row.key());
            }
            return rows.get(Integer.parseInt(row.key()) - 1);
        }
        var matches = rows.stream().filter(candidate -> row.key().equals(candidate.fields().get(overlay.keyField())))
                .toList();
        if (matches.size() != 1) {
            throw missed(name, Reason.MALFORMED, overlay.file(), "the file has %d rows whose field '%s' is '%s'"
                    .formatted(matches.size(), overlay.keyField(), row.key()));
        }
        return matches.getFirst();
    }

    private static void requireNewIds(String name, List<ImportedItem> items, Set<String> written)
            throws SourceMissed {
        for (var item : items) {
            if (written.contains(item.id())) {
                throw missed(name, Reason.MALFORMED, null,
                        "the item '%s' is generated by another source too".formatted(item.id()));
            }
        }
        items.forEach(item -> written.add(item.id()));
    }

    private static <T> T read(Path file, String name, DocumentReader<T> reader) throws SourceMissed {
        byte[] document;
        try {
            document = Files.readAllBytes(file);
        } catch (IOException e) {
            throw missed(name, Reason.MALFORMED, null, "%s cannot be read: %s".formatted(file.getFileName(), e));
        }
        try {
            return reader.read(file, document);
        } catch (StoreRefusal e) {
            throw missed(name, Reason.MALFORMED, null, e.getMessage());
        }
    }

    /**
     * Lists the source directories, telling a corpus without sources from one that could not be read.
     *
     * @param corpus the corpus root
     * @return the source directories in name order; empty only for a corpus root that exists and has no
     *         {@code sources/} directory, or a {@code sources/} directory without a subdirectory
     * @throws UncheckedIOException if the corpus root is no directory, its {@code sources/} entry cannot be told to be
     *                              absent or is no directory, or the sources cannot be listed
     */
    private static List<Path> sourceDirectories(Path corpus) {
        if (Files.notExists(corpus)) {
            throw new UncheckedIOException("The corpus root does not exist: " + corpus,
                    new NoSuchFileException(corpus.toString()));
        }
        if (!Files.isDirectory(corpus)) {
            throw new UncheckedIOException("The corpus root is no directory or cannot be read: " + corpus,
                    new NotDirectoryException(corpus.toString()));
        }
        var sources = corpus.resolve(SOURCES);
        if (Files.notExists(sources, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (!Files.isDirectory(sources)) {
            throw new UncheckedIOException("The corpus sources are no directory: " + sources,
                    new NotDirectoryException(sources.toString()));
        }
        try (Stream<Path> entries = Files.list(sources)) {
            return entries.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("The corpus sources cannot be listed: " + sources, e);
        }
    }

    private static void write(Path itemsRoot, ImportedItem item) {
        var file = itemsRoot.resolve(item.id() + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, ImportedItemCodec.write(item));
        } catch (IOException e) {
            throw new UncheckedIOException("The imported item cannot be written: " + file, e);
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(root)) {
            for (var entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The items of an earlier import cannot be removed: " + root, e);
        }
    }

    private static SourceMissed missed(String name, Reason reason, String file, String detail) {
        LOGGER.warn(WARN.CORPUS_SOURCE_MISSED, name, "%s: %s".formatted(reason.wireName(), detail));
        return new SourceMissed(new MissedSource(name, reason, file, detail));
    }

    private static SourceMissed hashMismatch(String name, SourceFile file, String found) {
        LOGGER.error(ERROR.CORPUS_SOURCE_HASH_MISMATCH, name, file.path(), file.sha256(), found);
        return new SourceMissed(new MissedSource(name, Reason.HASH_MISMATCH, file.path(),
                "expected %s, found %s".formatted(file.sha256(), found)));
    }

    /** Reads a document of the corpus's own part. */
    @FunctionalInterface
    private interface DocumentReader<T> {
        T read(Path file, byte[] document);
    }

    /** Ends the import of one source; the import goes on with the next. */
    private static final class SourceMissed extends Exception {

        @Serial
        private static final long serialVersionUID = 1L;

        private final transient MissedSource missed;

        private SourceMissed(MissedSource missed) {
            super(missed.detail(), null, false, false);
            this.missed = missed;
        }
    }
}
