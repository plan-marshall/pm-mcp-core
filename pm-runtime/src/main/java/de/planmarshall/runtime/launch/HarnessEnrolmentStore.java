/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import javax.crypto.SecretKey;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileRefusal;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.enrolment.HarnessesSidecarCheck;

/**
 * The machine-wide harness enrolment store {@code <PM_MCP_BASE>/harnesses.json} and its HMAC sidecar (PM-SEC-5, Harness
 * Enrolment Store): the one store API behind the onboarding's {@code harness.enrol} and the operator's
 * {@code pm-operator enrol-harness}, and the store the launch verifies before every worker spawn.
 * <p>
 * Every read verifies the file against its sidecar under the enrolment integrity key first; a file whose size, SHA-256
 * or HMAC differs is refused as {@link Refusal#TAMPERED} and never parsed, and a new enrolment then writes the named
 * client only, as the re-baseline of a tampered file prescribes. Every write holds {@code locks/harnesses.lock} and
 * writes the canonical file and its sidecar, owner-only, in that order; the store holds no environment value, no
 * variable name and no credential.
 *
 * @since 0.1
 */
public final class HarnessEnrolmentStore {

    /** The one format version of the store. */
    public static final int FORMAT_VERSION = 1;

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");
    private static final String HARNESSES = "harnesses";
    private static final String FORMAT_VERSION_MEMBER = "format_version";

    private final Path base;
    private final Path file;
    private final Path sidecarFile;
    private final Supplier<SecretKey> key;
    private final LockManager lockManager;
    private final HarnessesSidecarCheck check;

    /**
     * @param base        the base directory {@code <PM_MCP_BASE>} of the runtime
     * @param key         the enrolment integrity key, read on every access
     * @param lockManager the lock manager of the runtime
     * @param user        the name of the running user, the owner the file and its sidecar must have
     */
    public HarnessEnrolmentStore(Path base, Supplier<SecretKey> key, LockManager lockManager, String user) {
        this.base = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.file = this.base.resolve(HarnessesSidecarCheck.FILE_NAME);
        this.sidecarFile = this.file.resolveSibling(HarnessesSidecarCheck.FILE_NAME + EnrolmentSidecar.SUFFIX);
        this.key = Objects.requireNonNull(key, "key");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.check = new HarnessesSidecarCheck(this.base, user);
    }

    /**
     * @param clientId the client id of the harness
     * @return the enrolment of the harness
     * @throws StoreException if the file fails its integrity check or does not read as the store
     */
    public Optional<HarnessEnrolment> find(String clientId) {
        return all().stream().filter(enrolment -> enrolment.clientId().equals(clientId)).findFirst();
    }

    /**
     * @return every enrolment, in client-id order
     * @throws StoreException if the file fails its integrity check or does not read as the store
     */
    public List<HarnessEnrolment> all() {
        try (var transaction = lockManager.openTransaction()) {
            return read(transaction).values().stream().toList();
        }
    }

    /**
     * Writes the enrolment of one harness. A file that failed its integrity check is re-baselined: only the named
     * client is written, and every other client must be enrolled again.
     *
     * @param enrolment the enrolment to write
     * @return the enrolment as written
     * @throws StoreException if the file cannot be read or written
     */
    public HarnessEnrolment enrol(HarnessEnrolment enrolment) {
        Objects.requireNonNull(enrolment, "enrolment");
        try (var transaction = lockManager.openTransaction()) {
            var entries = read(transaction, true);
            entries.put(enrolment.clientId(), enrolment);
            write(transaction, entries);
            return enrolment;
        }
    }

    /**
     * Removes the enrolment of one harness, leaving the other entries in place.
     *
     * @param clientId the client id of the harness
     * @return whether the harness was enrolled
     * @throws StoreException if the file fails its integrity check or cannot be written
     */
    public boolean unenrol(String clientId) {
        try (var transaction = lockManager.openTransaction()) {
            var entries = read(transaction);
            if (entries.remove(clientId) == null) {
                return false;
            }
            write(transaction, entries);
            return true;
        }
    }

    /**
     * Records an accepted self-update of the enrolled executable: the new version, the new digest and the instant, in
     * the same transaction that verified the harness.
     *
     * @param clientId the client id of the harness
     * @param version  the version the probe reported
     * @param sha256   the digest of the changed executable
     * @param updateAt the instant of the accepted self-update
     * @return the entry as written, or empty when the harness is not enrolled any more
     * @throws StoreException if the file fails its integrity check or cannot be written
     */
    public Optional<HarnessEnrolment> acceptSelfUpdate(String clientId, String version, String sha256,
            Instant updateAt) {
        try (var transaction = lockManager.openTransaction()) {
            var entries = read(transaction);
            var current = entries.get(clientId);
            if (current == null) {
                return Optional.empty();
            }
            var updated = current.updated(version, sha256, updateAt);
            entries.put(clientId, updated);
            write(transaction, entries);
            return Optional.of(updated);
        }
    }

    /** @return the file of the store */
    public Path file() {
        return file;
    }

    private Map<String, HarnessEnrolment> read(LockTransaction transaction) {
        return read(transaction, false);
    }

    /**
     * @param transaction the transaction
     * @param rebaseline  whether a file that fails its integrity check, or whose verified bytes do not read as the
     *                    store, is read as empty for the re-baseline of a new enrolment
     */
    private Map<String, HarnessEnrolment> read(LockTransaction transaction, boolean rebaseline) {
        var result = check.verify(transaction, key.get());
        if (!result.passed()) {
            if (rebaseline) {
                return new TreeMap<>();
            }
            throw new StoreException(result.outcome() == HarnessesSidecarCheck.Outcome.INSECURE
                    ? Refusal.INSECURE : Refusal.TAMPERED, result.reason().orElse("the harness enrolment store "
                    + "does not match its sidecar"));
        }
        if (result.outcome() == HarnessesSidecarCheck.Outcome.NOTHING_TO_VERIFY) {
            return new TreeMap<>();
        }
        try {
            return decode(readBytes(file));
        } catch (StoreException unreadable) {
            if (rebaseline && unreadable.refusal() == Refusal.UNREADABLE) {
                return new TreeMap<>();
            }
            throw unreadable;
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("The harness enrolment store cannot be read: " + path, e);
        }
    }

    private Map<String, HarnessEnrolment> decode(byte[] document) {
        JsonNode root;
        try {
            root = JsonDocument.parse(document, HarnessesSidecarCheck.FILE_NAME);
        } catch (ProfileRefusal | IllegalArgumentException malformed) {
            throw new StoreException(Refusal.UNREADABLE, "not a harness enrolment store: " + malformed.getMessage());
        }
        var version = root.member(FORMAT_VERSION_MEMBER).flatMap(node -> node.integerValue().isPresent()
                ? Optional.of(node.integerValue().getAsLong()) : Optional.<Long>empty());
        if (version.isEmpty() || version.get() != FORMAT_VERSION) {
            throw new StoreException(Refusal.UNREADABLE, "the format version is not " + FORMAT_VERSION);
        }
        var entries = new TreeMap<String, HarnessEnrolment>();
        var harnesses = root.member(HARNESSES).orElse(JsonNode.nullValue());
        for (var member : harnesses.members().entrySet()) {
            try {
                entries.put(member.getKey(), entry(member.getKey(), member.getValue()));
            } catch (ProfileRefusal | DateTimeParseException | NoSuchElementException
                    | IllegalArgumentException malformed) {
                throw new StoreException(Refusal.UNREADABLE,
                        "the entry '%s' is malformed: %s".formatted(member.getKey(), malformed.getMessage()));
            }
        }
        return entries;
    }

    private static HarnessEnrolment entry(String clientId, JsonNode node) {
        return new HarnessEnrolment(clientId, Path.of(text(node, "executable_path")), text(node, "sha256"),
                text(node, "version"), Instant.parse(text(node, "enrolled_at")), Instant.parse(text(node, "updated_at")),
                usageReport(node.member("usage_report").orElseThrow()), events(node.member("events").orElse(JsonNode
                .nullValue())), weights(node.member("weights").orElse(JsonNode.nullValue())));
    }

    private static HarnessEnrolment.UsageReport usageReport(JsonNode node) {
        var figures = new LinkedHashMap<String, String>();
        node.member("figures").orElse(JsonNode.nullValue()).members()
                .forEach((figure, result) -> figures.put(figure, result.text().orElseThrow()));
        return new HarnessEnrolment.UsageReport(
                HarnessEnrolment.UsageSource.fromWireName(text(node, "source")), figures);
    }

    private static Map<String, String> events(JsonNode node) {
        var results = new LinkedHashMap<String, String>();
        node.members().forEach((kind, result) -> results.put(kind, result.text().orElseThrow()));
        return results;
    }

    private static Map<String, HarnessEnrolment.Weights> weights(JsonNode node) {
        var weights = new LinkedHashMap<String, HarnessEnrolment.Weights>();
        node.members().forEach((model, entry) -> weights.put(model, new HarnessEnrolment.Weights(text(entry,
                "weights_id"), Instant.parse(text(entry, "read_at")))));
        return weights;
    }

    private static String text(JsonNode node, String member) {
        return node.member(member).flatMap(JsonNode::text).orElseThrow(
                () -> new IllegalArgumentException("the member '%s' is missing".formatted(member)));
    }

    private void write(LockTransaction transaction, Map<String, HarnessEnrolment> entries) {
        transaction.acquire(LockKey.harnesses(base));
        var document = CanonicalJson.write(document(entries)).getBytes(StandardCharsets.UTF_8);
        var sidecar = EnrolmentHmac.sidecar(key.get(), document);
        writeAtomically(file, document);
        writeAtomically(sidecarFile, sidecar.write());
    }

    private static JsonNode document(Map<String, HarnessEnrolment> entries) {
        var harnesses = new TreeMap<String, JsonNode>();
        entries.forEach((clientId, enrolment) -> harnesses.put(clientId, entry(enrolment)));
        return JsonNode.object(Map.of(FORMAT_VERSION_MEMBER, JsonNode.integer(FORMAT_VERSION), HARNESSES,
                JsonNode.object(harnesses)));
    }

    private static JsonNode entry(HarnessEnrolment enrolment) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put("executable_path", JsonNode.string(enrolment.executablePath().toString()));
        members.put("sha256", JsonNode.string(enrolment.sha256()));
        members.put("version", JsonNode.string(enrolment.version()));
        members.put("enrolled_at", JsonNode.string(enrolment.enrolledAt().toString()));
        members.put("updated_at", JsonNode.string(enrolment.updatedAt().toString()));
        var figures = new TreeMap<String, JsonNode>();
        enrolment.usageReport().figures().forEach((figure, result) -> figures.put(figure, JsonNode.string(result)));
        members.put("usage_report", JsonNode.object(Map.of("source", JsonNode.string(enrolment.usageReport().source()
                .wireName()), "figures", JsonNode.object(figures))));
        var events = new TreeMap<String, JsonNode>();
        enrolment.events().forEach((kind, result) -> events.put(kind, JsonNode.string(result)));
        members.put("events", JsonNode.object(events));
        if (!enrolment.weights().isEmpty()) {
            var weights = new TreeMap<String, JsonNode>();
            enrolment.weights().forEach((model, value) -> weights.put(model, JsonNode.object(Map.of("weights_id",
                    JsonNode.string(value.weightsId()), "read_at", JsonNode.string(value.readAt().toString())))));
            members.put("weights", JsonNode.object(weights));
        }
        return JsonNode.object(members);
    }

    private static void writeAtomically(Path target, byte[] content) {
        try {
            var directory = target.getParent();
            Files.createDirectories(directory);
            var staged = Files.createTempFile(directory, target.getFileName() + ".", ".staged");
            try {
                Files.write(staged, content);
                restrict(staged);
                move(staged, target);
            } finally {
                Files.deleteIfExists(staged);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The harness enrolment store cannot be written: " + target, e);
        }
    }

    private static void move(Path staged, Path target) throws IOException {
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restrict(Path path) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        }
    }

    /** Why the harness enrolment store was refused. */
    public enum Refusal {
        /** The file or its sidecar fails a per-use check. */
        INSECURE("harness_tampered"),
        /** One half of the pair is missing, or the size, SHA-256 or HMAC differs. */
        TAMPERED("harness_tampered"),
        /** The verified bytes do not read as the store. */
        UNREADABLE("harness_store_unreadable");

        private final String code;

        Refusal(String code) {
            this.code = code;
        }

        /** @return the reason code of a launch record */
        public String code() {
            return code;
        }
    }

    /** A refused read or write of the harness enrolment store. */
    public static final class StoreException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Refusal refusal;

        StoreException(Refusal refusal, String detail) {
            super("%s: %s".formatted(refusal.code(), detail));
            this.refusal = refusal;
        }

        /** @return why the store was refused */
        public Refusal refusal() {
            return refusal;
        }
    }
}
