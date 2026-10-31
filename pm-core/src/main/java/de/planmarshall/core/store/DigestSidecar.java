/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The digest sidecar of a scope (PM-IMPL-7, PM-IMPL-2): the recorded size and SHA-256 of every store of the scope,
 * kept beside the stores and never inside one.
 * <p>
 * The document is {@code {"format_version": 1, "stores": {"<relative path>": {"size", "sha256"}}}}. A key is the path
 * of a store relative to the path base of its scope ({@link JournalScope}). The entries are written in the sorted
 * order of their keys, so the same digests always give the same bytes. A digest is recorded only through the
 * transaction journal of the scope; this type reads and writes the document and decides nothing.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}; every refusal is a {@link StoreRefusal} naming the
 * sidecar file. A key that is not a relative, normalized store path (empty, absolute, with a {@code ..} segment) is
 * part of no such shape, so a loaded sidecar never names a file outside its scope. A sidecar that does not exist holds
 * no digest.
 *
 * @param stores the digest of each store by its relative path, in sorted order; unmodifiable
 * @since 0.1
 */
public record DigestSidecar(SortedMap<String, StoreDigest> stores) {

    /** The format version of a digest sidecar that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_STORES = "stores";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_SHA256 = "sha256";

    /**
     * Copies the digests into their sorted order.
     *
     * @throws NullPointerException     if a key or a digest is {@code null}
     * @throws IllegalArgumentException if a key is not a store path: empty, absolute, not normalized, or with a
     *                                  {@code ..} segment
     */
    public DigestSidecar {
        var sorted = new TreeMap<String, StoreDigest>();
        for (var entry : Objects.requireNonNull(stores, FIELD_STORES).entrySet()) {
            var path = Objects.requireNonNull(entry.getKey(), "path");
            if (!JournalScope.isStorePath(path)) {
                throw new IllegalArgumentException(
                        "A store of a digest sidecar has a normalized path relative to the path base: '%s'"
                                .formatted(path));
            }
            sorted.put(path, Objects.requireNonNull(entry.getValue(), "digest"));
        }
        stores = Collections.unmodifiableSortedMap(sorted);
    }

    /**
     * @param stores the digest of each store by its relative path
     * @return the sidecar of these digests
     */
    public static DigestSidecar of(Map<String, StoreDigest> stores) {
        return new DigestSidecar(new TreeMap<>(stores));
    }

    /** @return a sidecar without a digest */
    public static DigestSidecar empty() {
        return new DigestSidecar(new TreeMap<>());
    }

    /**
     * @param path the path of a store, relative to the path base of the scope
     * @return the recorded digest of the store; empty if none is recorded
     */
    public Optional<StoreDigest> digest(String path) {
        return Optional.ofNullable(stores.get(Objects.requireNonNull(path, "path")));
    }

    /**
     * @param path   the path of a store, relative to the path base of the scope
     * @param digest the digest to record for it
     * @return a sidecar with the digest recorded for the store, and every other digest unchanged
     */
    public DigestSidecar with(String path, StoreDigest digest) {
        var changed = new TreeMap<>(stores);
        changed.put(path, digest);
        return new DigestSidecar(changed);
    }

    /**
     * @param path the path of a store that a transaction deleted, relative to the path base of the scope
     * @return a sidecar without an entry for the store, and every other digest unchanged; equal to this one if it
     *         has no entry for the store
     */
    public DigestSidecar without(String path) {
        var changed = new TreeMap<>(stores);
        changed.remove(Objects.requireNonNull(path, "path"));
        return new DigestSidecar(changed);
    }

    /** @return the document, UTF-8 encoded, with the entries in the sorted order of their paths */
    public byte[] write() {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeObjectFieldStart(FIELD_STORES);
            for (var entry : stores.entrySet()) {
                generator.writeObjectFieldStart(entry.getKey());
                generator.writeNumberField(FIELD_SIZE, entry.getValue().size());
                generator.writeStringField(FIELD_SHA256, entry.getValue().sha256());
                generator.writeEndObject();
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Digest sidecar cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the sidecar file the document was read from, named by a refusal
     * @param document a sidecar document, UTF-8 encoded
     * @return the sidecar
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static DigestSidecar read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, DigestSidecar::readDocument);
    }

    /**
     * Reads the sidecar file as it is at the moment of the read. The caller holds the lock of the scope where it
     * decides on the result.
     *
     * @param file the sidecar file
     * @return the sidecar; empty if the file does not exist
     * @throws StoreRefusal         if the file declares another format version or is unreadable
     * @throws UncheckedIOException if the file exists and cannot be read
     */
    public static DigestSidecar load(Path file) {
        return StoreSnapshot.read(file).content().map(document -> read(file, document))
                .orElseGet(DigestSidecar::empty);
    }

    private static DigestSidecar readDocument(JsonParser parser) throws IOException {
        @Nullable SortedMap<String, StoreDigest> stores = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_STORES -> stores = readStores(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "sidecar", FIELD_STORES);
        try {
            return new DigestSidecar(stores);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static SortedMap<String, StoreDigest> readStores(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_STORES);
        var stores = new TreeMap<String, StoreDigest>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var path = nextField(parser, seen);
            stores.put(path, readDigest(parser, path));
        }
        return stores;
    }

    private static StoreDigest readDigest(JsonParser parser, String path) throws IOException {
        requireObject(parser, path);
        long size = 0;
        @Nullable String sha256 = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_SIZE -> size = integer(parser, field);
                case FIELD_SHA256 -> sha256 = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "digest of '%s'".formatted(path), FIELD_SIZE, FIELD_SHA256);
        try {
            return new StoreDigest(size, sha256);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }
}
