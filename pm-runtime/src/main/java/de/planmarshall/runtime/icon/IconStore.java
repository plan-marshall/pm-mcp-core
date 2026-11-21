/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.icon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

import de.planmarshall.core.id.HarnessId;
import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileRefusal;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.runtime.start.PosixModes;

/**
 * The machine's harness icon store {@code <PM_MCP_BASE>/icons/} and its index (PM-CLIENT-5, Harness Profiles § Harness
 * Icons; Store Schemas § Harness Icon Store).
 * <p>
 * One file per harness, {@code icons/<harness_id>.<ext>} with extension {@code svg} or {@code png} and mode
 * {@code 0600}, and the index {@code icons/index.json}. Both are written under {@code locks/icons.lock} with an atomic
 * write: the content is staged in a file in the same directory and moved onto its name, so a reader sees the old file
 * or the new one and never a partial one.
 * <p>
 * The store takes the candidate bytes from a caller and never fetches them: the outbound fetch belongs to the profile
 * that declares the {@code source} form, and no shipped profile does, so the store stays empty until then. A
 * {@code harness_id} is checked against {@link HarnessId} before it reaches a path, so no separator or dot segment can
 * leave the directory, and a target that is a symbolic link is refused rather than written through.
 *
 * @since 0.1
 */
public final class IconStore {

    /** The one format version of the index. */
    public static final int FORMAT_VERSION = 1;

    private static final String ICONS = "icons";
    private static final String INDEX = "index.json";
    private static final String FORMAT_VERSION_MEMBER = "format_version";
    private static final String ENTRIES = "entries";
    private static final String SOURCE_URL = "source_url";
    private static final String SHA256 = "sha256";
    private static final String SIZE = "size";
    private static final String MEDIA_TYPE = "media_type";
    private static final String FETCHED_AT = "fetched_at";
    private static final String STAGED = ".staged";
    /** The scheme every source URL carries. */
    private static final String HTTPS = "https";
    private static final int MAX_SOURCE_URL = 2048;

    private final Path base;
    private final Path directory;
    private final Path indexFile;
    private final LockManager lockManager;

    /**
     * @param base        the base directory {@code <PM_MCP_BASE>} of the runtime
     * @param lockManager the lock manager of the runtime
     */
    public IconStore(Path base, LockManager lockManager) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.directory = this.base.resolve(ICONS);
        this.indexFile = directory.resolve(INDEX);
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
    }

    /** @return the store's directory {@code <PM_MCP_BASE>/icons} */
    public Path directory() {
        return directory;
    }

    /** @return the index file {@code <PM_MCP_BASE>/icons/index.json} */
    public Path indexFile() {
        return indexFile;
    }

    /**
     * @return the index as the store holds it; the empty index where no icon was ever written
     * @throws IconRefusal if the index exists but does not read as the store's index
     */
    public IconIndex index() {
        if (!Files.exists(indexFile, LinkOption.NOFOLLOW_LINKS)) {
            return IconIndex.empty();
        }
        return decode(readBytes(indexFile));
    }

    /**
     * @param harnessId the harness the icon belongs to
     * @return the stored icon of that harness, where one is recorded and its file is a regular file that still matches
     *         the entry: its size is the recorded one, it is within the bound, and its SHA-256 is the recorded digest. A
     *         file that differs from its entry is not served, so a crash between the icon's write and the index's, or a
     *         tampered file, yields nothing here rather than a mismatched icon.
     */
    public Optional<StoredIcon> find(String harnessId) {
        var entry = index().find(harnessId);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        var recorded = entry.get();
        var file = fileFor(harnessId, recorded.media());
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        final long size;
        try {
            size = Files.size(file);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
        if (size > IconSanitiser.MAX_BYTES || size != recorded.size()) {
            return Optional.empty();
        }
        final byte[] content;
        try (var stream = Files.newInputStream(file)) {
            content = stream.readNBytes((int) size + 1);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
        if (content.length != size || !sha256(content).equals(recorded.sha256())) {
            return Optional.empty();
        }
        return Optional.of(new StoredIcon(file, recorded));
    }

    /**
     * Accepts one candidate icon: sanitises it, writes it under its harness id and records it in the index, all under
     * the store's lock.
     *
     * @param harnessId the harness the icon belongs to
     * @param sourceUrl the URL the icon was fetched from
     * @param fetchedAt when the icon was fetched
     * @param candidate the candidate bytes, as a caller hands them over
     * @return the stored file and its index entry
     * @throws IconRefusal if the harness id is not a {@link HarnessId}, the candidate is refused, the target is a
     *                     symbolic link, or the index cannot be read
     */
    public StoredIcon accept(String harnessId, String sourceUrl, Instant fetchedAt, byte[] candidate) {
        Objects.requireNonNull(harnessId, "harnessId");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        Objects.requireNonNull(candidate, "candidate");
        if (!HarnessId.matches(harnessId)) {
            throw new IconRefusal(IconRefusal.HARNESS_ID_INVALID, "Not a harness id: " + harnessId);
        }
        if (!isHttpsUrl(sourceUrl)) {
            throw new IconRefusal(IconRefusal.SOURCE_URL_INVALID,
                    "A source URL is an https URL with a host and at most %d characters".formatted(MAX_SOURCE_URL));
        }
        var sanitised = IconSanitiser.accept(candidate);
        var file = fileFor(harnessId, sanitised.mediaType());
        var content = sanitised.content();
        var entry = IconIndexEntry.builder()
                .sourceUrl(sourceUrl)
                .sha256(sha256(content))
                .size(content.length)
                .mediaType(sanitised.mediaType().mediaType())
                .fetchedAt(fetchedAt)
                .build();
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(LockKey.icons(base));
            ensureDirectory();
            refuseLink(file);
            var entries = new TreeMap<>(index().entries());
            entries.put(harnessId, entry);
            writeAtomically(file, content);
            deleteOtherMedia(harnessId, sanitised.mediaType());
            writeAtomically(indexFile, CanonicalJson.write(document(entries)).getBytes(StandardCharsets.UTF_8));
        }
        return new StoredIcon(file, entry);
    }

    private void deleteOtherMedia(String harnessId, IconMediaType kept) {
        var other = fileFor(harnessId, kept == IconMediaType.SVG ? IconMediaType.PNG : IconMediaType.SVG);
        try {
            Files.deleteIfExists(other);
        } catch (IOException e) {
            throw new UncheckedIOException("The icon store cannot remove the replaced icon: " + other, e);
        }
    }

    /**
     * @param sourceUrl the URL the icon was fetched from
     * @return whether the value is an absolute https URL with a host and no whitespace
     */
    private static boolean isHttpsUrl(String sourceUrl) {
        if (sourceUrl.length() > MAX_SOURCE_URL || sourceUrl.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        try {
            var uri = java.net.URI.create(sourceUrl);
            return HTTPS.equals(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (IllegalArgumentException notAUri) {
            return false;
        }
    }

    private Path fileFor(String harnessId, IconMediaType mediaType) {
        return directory.resolve(harnessId + "." + mediaType.extension());
    }

    private void ensureDirectory() {
        try {
            if (Files.isSymbolicLink(directory)) {
                throw new IconRefusal(IconRefusal.TARGET_IS_LINK,
                        "The icon store directory is a symbolic link: " + directory);
            }
            if (!Files.isDirectory(directory)) {
                Files.createDirectories(directory);
                restrictDirectory(directory);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The icon store directory cannot be created: " + directory, e);
        }
    }

    private static void refuseLink(Path file) {
        if (Files.isSymbolicLink(file)) {
            throw new IconRefusal(IconRefusal.TARGET_IS_LINK, "The icon target is a symbolic link: " + file);
        }
    }

    private IconIndex decode(byte[] document) {
        JsonNode root;
        try {
            root = JsonDocument.parse(document, INDEX);
        } catch (ProfileRefusal | IllegalArgumentException malformed) {
            throw new IconRefusal(IconRefusal.INDEX_UNREADABLE, "not an icon index: " + malformed.getMessage());
        }
        var version = integer(root, FORMAT_VERSION_MEMBER);
        if (version.isEmpty() || version.getAsLong() != FORMAT_VERSION) {
            throw new IconRefusal(IconRefusal.INDEX_UNREADABLE, "the format version is not " + FORMAT_VERSION);
        }
        var entriesNode = root.member(ENTRIES).orElseThrow(
                () -> new IconRefusal(IconRefusal.INDEX_UNREADABLE, "the member 'entries' is missing"));
        if (!entriesNode.isObject()) {
            throw new IconRefusal(IconRefusal.INDEX_UNREADABLE, "the member 'entries' is not an object");
        }
        var entries = new TreeMap<String, IconIndexEntry>();
        for (var member : entriesNode.members().entrySet()) {
            try {
                entries.put(member.getKey(), entry(member.getKey(), member.getValue()));
            } catch (IllegalArgumentException | DateTimeParseException malformed) {
                throw new IconRefusal(IconRefusal.INDEX_UNREADABLE,
                        "the entry '%s' is malformed: %s".formatted(member.getKey(), malformed.getMessage()));
            }
        }
        return new IconIndex((int) version.getAsLong(), entries);
    }

    private static IconIndexEntry entry(String harnessId, JsonNode node) {
        if (!HarnessId.matches(harnessId)) {
            throw new IllegalArgumentException("Not a harness id: " + harnessId);
        }
        return new IconIndexEntry(text(node, SOURCE_URL), text(node, SHA256),
                integer(node, SIZE).orElseThrow(
                        () -> new IllegalArgumentException("the member 'size' is missing")),
                text(node, MEDIA_TYPE), Instant.parse(text(node, FETCHED_AT)));
    }

    private static OptionalLong integer(JsonNode node, String member) {
        return node.member(member).map(JsonNode::integerValue).orElseGet(OptionalLong::empty);
    }

    private static String text(JsonNode node, String member) {
        return node.member(member).flatMap(JsonNode::text).orElseThrow(
                () -> new IllegalArgumentException("the member '%s' is missing".formatted(member)));
    }

    private static JsonNode document(Map<String, IconIndexEntry> entries) {
        var members = new TreeMap<String, JsonNode>();
        entries.forEach((harnessId, entry) -> {
            var entryMembers = new LinkedHashMap<String, JsonNode>();
            entryMembers.put(SOURCE_URL, JsonNode.string(entry.sourceUrl()));
            entryMembers.put(SHA256, JsonNode.string(entry.sha256()));
            entryMembers.put(SIZE, JsonNode.integer(entry.size()));
            entryMembers.put(MEDIA_TYPE, JsonNode.string(entry.mediaType()));
            entryMembers.put(FETCHED_AT, JsonNode.string(entry.fetchedAt().toString()));
            members.put(harnessId, JsonNode.object(entryMembers));
        });
        return JsonNode.object(Map.of(FORMAT_VERSION_MEMBER, JsonNode.integer(FORMAT_VERSION), ENTRIES,
                JsonNode.object(members)));
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("The icon index cannot be read: " + path, e);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM provides SHA-256", e);
        }
    }

    private static void writeAtomically(Path target, byte[] content) {
        try {
            var directory = target.getParent();
            Files.createDirectories(directory);
            var staged = Files.createTempFile(directory, target.getFileName() + ".", STAGED,
                    PosixModes.fileAttribute());
            try {
                Files.write(staged, content);
                force(staged);
                move(staged, target);
            } finally {
                Files.deleteIfExists(staged);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The icon store cannot be written: " + target, e);
        }
    }

    /**
     * @param path the staged file, before it is moved onto its name
     * @throws IOException if the file cannot be forced to its medium
     */
    private static void force(Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void move(Path staged, Path target) throws IOException {
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restrictDirectory(Path directory) {
        try {
            if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(directory, PosixModes.DIRECTORY);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The icon store directory cannot be restricted: " + directory, e);
        }
    }
}
