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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
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

    /** The members of a serving program's entry. */
    private static final Set<String> PROGRAM_MEMBERS = Set.of("executable_path", "sha256", "version", "enrolled_at",
            "updated_at", "weights", "usage_report", "events");

    /** The members of a remote endpoint's entry. */
    private static final Set<String> ENDPOINT_MEMBERS = Set.of("endpoint", "enrolled_at", "updated_at", "weights",
            "usage_report", "events");

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
        var binding = node.member("endpoint").orElse(null);
        if (binding != null) {
            if (!binding.isObject()) {
                throw new IllegalArgumentException("the member 'endpoint' is not an object");
            }
            onlyMembers(node, clientId, ENDPOINT_MEMBERS);
            return new HarnessEnrolment(clientId, null, null, null, Instant.parse(text(node, "enrolled_at")),
                    Instant.parse(text(node, "updated_at")), usageReport(node), events(node), weights(node),
                    new HarnessEnrolment.Binding(text(binding, "origin"), text(binding, "credential_key")));
        }
        onlyMembers(node, clientId, PROGRAM_MEMBERS);
        return new HarnessEnrolment(clientId, Path.of(text(node, "executable_path")), text(node, "sha256"),
                text(node, "version"), Instant.parse(text(node, "enrolled_at")), Instant.parse(text(node,
                        "updated_at")), usageReport(node), events(node), weights(node));
    }

    /**
     * @param node     the entry
     * @param clientId the client id, for the refusal
     * @param allowed  the members of the form the entry takes
     * @throws IllegalArgumentException if the entry carries a member of the other form or an undeclared one, so a mixed
     *                                  entry is refused instead of being read as one of its halves
     */
    private static void onlyMembers(JsonNode node, String clientId, Set<String> allowed) {
        for (var member : node.members().keySet()) {
            if (!allowed.contains(member)) {
                throw new IllegalArgumentException(
                        "the entry '%s' carries the member '%s', which is not a member of the entry's form"
                                .formatted(clientId, member));
            }
        }
    }

    private static HarnessEnrolment.UsageReport usageReport(JsonNode node) {
        var report = requiredObject(node, "usage_report");
        var figures = new LinkedHashMap<String, String>();
        requiredObject(report, "figures").members()
                .forEach((figure, result) -> figures.put(figure, textValue(result, "the figure '%s'".formatted(figure))));
        return new HarnessEnrolment.UsageReport(
                HarnessEnrolment.UsageSource.fromWireName(text(report, "source")), figures,
                startContext(report.member("start_context").orElse(null)));
    }

    private static List<HarnessEnrolment.UsageReport.StartContext> startContext(JsonNode startContext) {
        if (startContext == null) {
            return List.of();
        }
        if (!startContext.isArray()) {
            throw new IllegalArgumentException("the member 'start_context' is not an array");
        }
        var contexts = new ArrayList<HarnessEnrolment.UsageReport.StartContext>();
        var elements = startContext.elements();
        for (var index = 0; index < elements.size(); index++) {
            var entry = elements.get(index);
            if (!entry.isObject()) {
                throw new IllegalArgumentException("the start_context entry %d is not an object".formatted(index));
            }
            var workerClass = text(entry, "class");
            var model = text(entry, "model");
            var tokens = entry.member("tokens").orElseThrow(
                    () -> new IllegalArgumentException("the member 'tokens' is missing"));
            final OptionalLong value;
            if (tokens.isInteger()) {
                if (tokens.integerValue().orElseThrow() < 0) {
                    throw new IllegalArgumentException("'tokens' must not be negative");
                }
                value = OptionalLong.of(tokens.integerValue().orElseThrow());
            } else if (tokens.isString() && "unmeasured".equals(tokens.text().orElseThrow())) {
                value = OptionalLong.empty();
            } else {
                throw new IllegalArgumentException("'tokens' must be an integer or 'unmeasured'");
            }
            contexts.add(new HarnessEnrolment.UsageReport.StartContext(workerClass, model, value));
        }
        return List.copyOf(contexts);
    }

    private static Map<String, String> events(JsonNode node) {
        var results = new LinkedHashMap<String, String>();
        requiredObject(node, "events").members().forEach((kind, result) -> results.put(kind,
                textValue(result, "the event '%s'".formatted(kind))));
        return results;
    }

    private static Map<String, HarnessEnrolment.Weights> weights(JsonNode node) {
        var weights = new LinkedHashMap<String, HarnessEnrolment.Weights>();
        node.member("weights").ifPresent(value -> {
            if (!value.isObject()) {
                throw new IllegalArgumentException("the member 'weights' is not an object");
            }
            value.members().forEach((model, entry) -> weights.put(model, new HarnessEnrolment.Weights(text(entry,
                    "weights_id"), Instant.parse(text(entry, "read_at")))));
        });
        return weights;
    }

    /**
     * @param node   the object that carries the member
     * @param member the name of a required object member
     * @return the member's value
     * @throws IllegalArgumentException if the member is absent, {@code null} or not an object, so a malformed document
     *                                  is refused instead of read as an empty map
     */
    private static JsonNode requiredObject(JsonNode node, String member) {
        var value = node.member(member).orElseThrow(
                () -> new IllegalArgumentException("the member '%s' is missing".formatted(member)));
        if (!value.isObject()) {
            throw new IllegalArgumentException("the member '%s' is not an object".formatted(member));
        }
        return value;
    }

    private static String textValue(JsonNode node, String what) {
        return node.text().orElseThrow(() -> new IllegalArgumentException("%s is not a string".formatted(what)));
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
        if (enrolment.binding() != null) {
            members.put("endpoint", JsonNode.object(Map.of("origin", JsonNode.string(enrolment.binding().origin()),
                    "credential_key", JsonNode.string(enrolment.binding().credentialKey()))));
        } else {
            members.put("executable_path", JsonNode.string(enrolment.executablePath().toString()));
            members.put("sha256", JsonNode.string(enrolment.sha256()));
            members.put("version", JsonNode.string(enrolment.version()));
        }
        members.put("enrolled_at", JsonNode.string(enrolment.enrolledAt().toString()));
        members.put("updated_at", JsonNode.string(enrolment.updatedAt().toString()));
        var figures = new TreeMap<String, JsonNode>();
        enrolment.usageReport().figures().forEach((figure, result) -> figures.put(figure, JsonNode.string(result)));
        var report = new LinkedHashMap<String, JsonNode>();
        report.put("source", JsonNode.string(enrolment.usageReport().source().wireName()));
        report.put("figures", JsonNode.object(figures));
        if (!enrolment.usageReport().startContext().isEmpty()) {
            var contexts = new ArrayList<JsonNode>();
            for (var context : enrolment.usageReport().startContext()) {
                var tokens = context.tokens().isPresent() ? JsonNode.integer(context.tokens().getAsLong())
                        : JsonNode.string("unmeasured");
                contexts.add(JsonNode.object(Map.of("class", JsonNode.string(context.workerClass()), "model",
                        JsonNode.string(context.model()), "tokens", tokens)));
            }
            report.put("start_context", JsonNode.array(contexts));
        }
        members.put("usage_report", JsonNode.object(report));
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
