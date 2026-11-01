/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.cuioss.tools.logging.CuiLogger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.jspecify.annotations.Nullable;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;
import de.planmarshall.core.store.StrictJson;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Operation;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Phase;
import de.planmarshall.runtime.credentials.dbus.DbusConnection;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The credential store as the operator's recorded choice (PM-CRED-2, PM-CRED-4): {@code credentials.store} of
 * {@code <PM_MCP_BASE>/machine-config.json}, {@code os} or {@code file}, absent until the operator chose. The runtime
 * never chooses a store by itself and never serves from another store than the recorded one.
 * <p>
 * <b>The setting.</b> The credential-store aspect records through {@link #record(LockManager, String, Store)}, the
 * store move through {@link #record(LockTransaction, Store, Writer)}; both write the key under
 * {@code locks/machine-config.lock}: inside the lock the document is re-read, every other member kept,
 * {@code revision} incremented and the file replaced in one atomic write, so two writers of the file never interleave
 * and no check-then-act window opens outside the lock. An absent document is created with {@code format_version} 1,
 * {@code revision} 1 and the other required keys at their schema defaults ({@code max_build_slots}
 * {@value #DEFAULT_MAX_BUILD_SLOTS}, {@code lsp_idle_timeout_seconds} {@value #DEFAULT_LSP_IDLE_TIMEOUT_SECONDS}). Only
 * the credential-store aspect of the machine's onboarding and the store move write the key; every other writer is
 * refused with {@code aspect_owned}. While a move between the stores is open, while its record
 * {@code state/credential-store-move.json} exists, only the move writes the key: the aspect is refused with
 * {@code aspect_owned} as well; and the move writes it only then, so a caller that names itself the store move while
 * no record exists is refused. The aspect records a store the first time only: once a store is recorded, a change to
 * the other store is the move, so the aspect is refused for it with {@code aspect_owned}, and for the recorded store it
 * writes nothing. The record and the recorded store are checked under {@code locks/credentials.lock}, which the move
 * holds for its whole duration and which sorts before the machine configuration lock, so no other writer records a
 * store between the check and the write, nor while a move runs.
 * <p>
 * <b>The active store.</b> {@link #activeStore()} gives the store of the recorded choice, read from the file at every
 * call, so a switch by the move takes effect for the next operation. Before a store is recorded it refuses with
 * {@code onboarding_incomplete}, naming the store step. With {@code os} it gives the OS store of the platform, the
 * macOS Keychain or the Linux Secret Service; when that store is unavailable, at start or later, a read fails with
 * {@code credential_store_unavailable} and a write with {@code credential_store_locked}, and nothing is written to the
 * file store in its place. The Secret Service is probed before every operation; the Keychain store is loaded once per
 * instance, a load that fails is tried again. With {@code file} it gives the file store below {@code <PM_MCP_BASE>/credentials}.
 * <p>
 * <b>The integrity key before the choice.</b> {@link #integrityKeyStore()} gives the store the enrolment integrity key
 * lives in: the active store once a store is recorded, and before that the store the runtime finds, the OS store where
 * one is available, else the file store, for which the key is the key file below {@code state/}. That store is found
 * once per instance and kept, so a transient outage of the OS store never moves the key to another store.
 * <p>
 * <b>The first recording reconciles the two stores.</b> The key can lie in the store the instance found or in the other
 * one, a key file written while the OS store was unavailable or a keyring entry left behind by a recording whose
 * deletion failed, so the first recording reads both and keeps the key every sidecar verifies under (ADR
 * runtime-credentials/007). The store being recorded holds that key before {@code credentials.store} names it, and the
 * key of the other store is deleted after the switch. A recording that is cut by a stop is finished by the next start
 * from its record (ADR runtime-credentials/008).
 *
 * @since 0.1
 */
public final class CredentialStoreChoice {

    /** The name of the machine configuration file below the base. */
    public static final String FILE_NAME = "machine-config.json";

    /** The current format version of the machine configuration file. */
    static final int FORMAT_VERSION = 1;

    /** The schema default of {@code max_build_slots}, written when the document is created. */
    static final int DEFAULT_MAX_BUILD_SLOTS = 2;

    /** The schema default of {@code lsp_idle_timeout_seconds}, written when the document is created. */
    static final int DEFAULT_LSP_IDLE_TIMEOUT_SECONDS = 900;

    static final String REVISION = "revision";
    static final String MAX_BUILD_SLOTS = "max_build_slots";
    static final String LSP_IDLE_TIMEOUT_SECONDS = "lsp_idle_timeout_seconds";
    /** The member {@code credentials} of the document, and the directory of the file store below the base. */
    static final String CREDENTIALS = "credentials";
    /** The member {@code store} of {@code credentials}. */
    static final String STORE = "store";

    /** The step of the machine's onboarding that records the store, named by {@code onboarding_incomplete}. */
    static final String STORE_STEP = "store";

    private static final CuiLogger LOGGER = new CuiLogger(CredentialStoreChoice.class);

    /** The store the operator can record. */
    public enum Store {
        /** The OS store of the platform: the macOS Keychain or the Linux Secret Service. */
        OS("os"),
        /** The permission-guarded file store below {@code <PM_MCP_BASE>/credentials}. */
        FILE("file");

        private final String value;

        Store(String value) {
            this.value = value;
        }

        /** @return the value of {@code credentials.store} */
        public String value() {
            return value;
        }

        static Optional<Store> of(String value) {
            return Arrays.stream(values()).filter(store -> store.value.equals(value)).findFirst();
        }
    }

    /** Who writes {@code machine-config.json}. The set is closed; only the owners of the key may write it. */
    public enum Writer {
        /**
         * The credential-store aspect of the machine's onboarding, which records the store the first time only, in the
         * transaction of its own first recording; a later change of the recorded store goes through the move
         * ({@link CredentialStoreMove}).
         */
        CREDENTIAL_STORE_ASPECT(true),
        /** The move of every entry between the stores, which switches the key in one write. */
        STORE_MOVE(true),
        /** Every other writer of the machine configuration, the plain configuration endpoint among them. */
        OTHER_CONFIGURATION(false);

        private final boolean owner;

        Writer(boolean owner) {
            this.owner = owner;
        }

        /** @return whether this writer may write {@code credentials.store} */
        public boolean owner() {
            return owner;
        }
    }

    /**
     * A request about the credential store that is refused although no store failed: the store is not recorded yet,
     * or the writer does not own the key.
     */
    public static final class Refusal extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Why the request was refused. */
        public enum Reason {
            /** No store is recorded yet: no credential can be entered before the store step of the onboarding. */
            ONBOARDING_INCOMPLETE("onboarding_incomplete"),
            /**
             * The writer does not own {@code credentials.store}: it is not one of its owners, an operation on the
             * credential stores is open, which alone owns the key until it ends, or the credential-store aspect asks
             * for another store than the one recorded, which only the move changes.
             */
            ASPECT_OWNED("aspect_owned");

            private final String code;

            Reason(String code) {
                this.code = code;
            }

            /** @return the error code of the API */
            public String code() {
                return code;
            }
        }

        private final Reason reason;

        /**
         * @param reason  the reason
         * @param message the description
         */
        Refusal(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        /** @return the reason */
        public Reason reason() {
            return reason;
        }
    }

    /** The OS keyrings, separated for tests. */
    interface Keyrings {

        /**
         * @param service the service name
         * @return the Keychain store
         * @throws SecretStoreException if Security.framework cannot be loaded
         */
        SecretStore keychain(String service);

        /**
         * @param socket  the session bus socket
         * @param service the service name
         * @return the Secret Service store, or empty if it is not usable now
         */
        Optional<SecretStore> secretService(Path socket, String service);
    }

    /** The real keyrings of this machine. */
    static final Keyrings SYSTEM = new Keyrings() {

        @Override
        public SecretStore keychain(String service) {
            return new KeychainSecretStore(service);
        }

        @Override
        public Optional<SecretStore> secretService(Path socket, String service) {
            var store = new SecretServiceStore(socket, Posix.getuid(), service, SecretServiceStore.BUS_TIMEOUT);
            return store.available() ? Optional.of(store) : Optional.empty();
        }
    };

    private final Path base;
    private final AtomicStoreFile file;
    private final AtomicStoreFile moveRecord;
    private final String service;
    private final MachinePaths.Os os;
    private final Map<String, String> environment;
    private final Keyrings keyrings;
    private final FileSecretStore fileStore;
    private final Clock clock;

    /** The store of the integrity key before the choice, found at the first call and then kept; empty until then. */
    private final AtomicReference<SecretStore> keyStoreBeforeChoice = new AtomicReference<>();

    /** The Keychain store, loaded at the first probe on macOS that loads it and then kept; empty until then. */
    private final AtomicReference<SecretStore> loadedKeychain = new AtomicReference<>();

    /**
     * @param base        the base directory of the runtime
     * @param service     the service name of the instance ({@link ServiceName})
     * @param os          the operating system
     * @param environment the process environment, which names the session bus
     * @param keyrings    the OS keyrings
     */
    CredentialStoreChoice(Path base, String service, MachinePaths.Os os, Map<String, String> environment,
            Keyrings keyrings) {
        this(base, service, os, environment, keyrings, Clock.systemUTC());
    }

    /**
     * @param base        the base directory of the runtime
     * @param service     the service name of the instance ({@link ServiceName})
     * @param os          the operating system
     * @param environment the process environment, which names the session bus
     * @param keyrings    the OS keyrings
     * @param clock       the clock of the start instant and of the transaction id of a first recording
     */
    CredentialStoreChoice(Path base, String service, MachinePaths.Os os, Map<String, String> environment,
            Keyrings keyrings, Clock clock) {
        var root = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        this.base = root;
        this.file = new AtomicStoreFile(root.resolve(FILE_NAME), LockKey.machineConfiguration(root));
        this.moveRecord = new AtomicStoreFile(CredentialStoreMoveRecord.file(root), LockKey.credentials(root));
        this.service = Objects.requireNonNull(service, "service");
        this.os = Objects.requireNonNull(os, "os");
        this.environment = Map.copyOf(environment);
        this.keyrings = Objects.requireNonNull(keyrings, "keyrings");
        this.fileStore = new FileSecretStore(root.resolve(CREDENTIALS));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param paths the machine paths
     * @return the choice of the running process for the given machine root
     */
    public static CredentialStoreChoice of(MachinePaths paths) {
        return new CredentialStoreChoice(paths.base(),
                ServiceName.of(paths.base(), Path.of(System.getProperty("user.home"))), paths.os(), System.getenv(),
                SYSTEM);
    }

    /** @return the service name every keyring entry of the instance lives under */
    public String service() {
        return service;
    }

    /**
     * Reads the recorded store without a lock: the file is replaced by one atomic rename, so the read sees the
     * document before or after a write.
     *
     * @return the recorded store; empty if the file or the key is absent
     * @throws de.planmarshall.core.store.StoreRefusal if the document is in another format version, is unreadable, or
     *                                                 holds a value of {@code credentials.store} other than
     *                                                 {@code os} or {@code file}
     * @throws UncheckedIOException                    if the file exists and cannot be read
     */
    public Optional<Store> recorded() {
        return StoreSnapshot.read(file.store()).content().flatMap(document -> parse(document).store());
    }

    /**
     * Records a store in the transaction of an operation that already holds {@code locks/credentials.lock}: the switch
     * of a move, or of the aspect's first recording. Under the lock of the machine configuration, which this call
     * acquires, the document is re-read, {@code credentials.store} set, {@code revision} incremented, and the file
     * replaced in one atomic write; every other member is kept. A writer that does not own the key is refused. Each of
     * the two owners writes only while the record of its own operation exists, which is re-read under the credentials
     * lock: the store move with a record of {@code operation: move}, the credential-store aspect with a record of
     * {@code operation: first_recording} that its own carry wrote. The aspect's ordinary path is
     * {@link #record(LockManager, String, Store)}, which coordinates both stores' keys.
     *
     * @param transaction the transaction of the write; it holds no lock that sorts after the credentials lock other
     *                    than the machine configuration lock
     * @param store       the store to record
     * @param writer      who writes the key
     * @throws Refusal                                 with {@code aspect_owned} if the writer does not own the key;
     *                                                 nothing was acquired or written then
     * @throws IllegalArgumentException                if the writer is the credential-store aspect and the record of its
     *                                                 first recording is not open; nothing was acquired or written then
     * @throws IllegalStateException                   if the writer is the store move and no record of a move exists;
     *                                                 nothing was written then
     * @throws de.planmarshall.core.store.StoreRefusal if the document on disk is in another format version or
     *                                                 unreadable; nothing was written then
     * @throws UncheckedIOException                    if the record of a move or the document cannot be read, or the
     *                                                 document cannot be written
     */
    @SuppressWarnings("java:S6213") // record is the domain verb of the recorded store (ADR runtime-credentials/001, /002)
    public void record(LockTransaction transaction, Store store, Writer writer) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(store, STORE);
        if (!Objects.requireNonNull(writer, "writer").owner()) {
            throw new Refusal(Refusal.Reason.ASPECT_OWNED,
                    "credentials.store of %s is written only by the credential-store aspect and the store move"
                            .formatted(file.store()));
        }
        if (writer == Writer.CREDENTIAL_STORE_ASPECT) {
            requireFirstRecording(transaction);
            write(transaction, store, writer);
            return;
        }
        transaction.acquire(moveRecord.lockKey());
        requireMove(transaction);
        write(transaction, store, writer);
    }

    /**
     * Refuses the aspect in a transaction of another caller unless the record of its own first recording is open, which
     * only the aspect of this instance writes before this call; the transaction holds the credentials lock.
     */
    private void requireFirstRecording(LockTransaction transaction) {
        if (!transaction.holds(moveRecord.lockKey())) {
            throw new IllegalArgumentException("the credential-store aspect writes credentials.store only in the "
                    + "transaction of its own first recording, which holds " + moveRecord.lockKey().lockFile());
        }
        var firstRecording = moveRecord.read(transaction)
                .map(document -> CredentialStoreMoveRecord.read(moveRecord.store(), document))
                .filter(stored -> stored.operation() == Operation.FIRST_RECORDING);
        if (firstRecording.isEmpty()) {
            throw new IllegalArgumentException("the credential-store aspect writes credentials.store in the transaction "
                    + "of another caller only while the record of its first recording is open; it records a store "
                    + "through record(LockManager, String, Store)");
        }
    }

    /** Refuses the store move outside a move, where no record of a move exists; the transaction holds its lock. */
    private void requireMove(LockTransaction transaction) {
        var move = moveRecord.read(transaction)
                .map(document -> CredentialStoreMoveRecord.read(moveRecord.store(), document))
                .filter(stored -> stored.operation() == Operation.MOVE);
        if (move.isEmpty()) {
            throw new IllegalStateException(
                    "the store move records credentials.store of %s only while the record %s of a move exists"
                            .formatted(file.store(), moveRecord.store()));
        }
    }

    /**
     * Records the store the operator chose in the credential-store aspect of the machine's onboarding, in a
     * transaction of its own: under {@code locks/credentials.lock} it is refused while the record of an operation
     * exists, and then {@code credentials.store} is written as {@link #record(LockTransaction, Store, Writer)} writes
     * it.
     * <p>
     * <b>Only the first recording writes.</b> Where a store is recorded already, the aspect does not change it: a
     * change of the recorded store is a move ({@link CredentialStoreMove}), which carries every entry and the key, so
     * another store is refused with {@code aspect_owned}, and the recorded store again is a no-op that writes and carries
     * nothing.
     * <p>
     * <b>Both stores are read.</b> Before the choice the key lives in the store the runtime found
     * ({@link #integrityKeyStore()}), but it can also lie in the other store, a key file written while the OS store was
     * unavailable or a keyring entry left behind by a recording whose deletion failed. The first recording therefore
     * reconciles the two stores instead of looking at the found one alone ({@link IntegrityKeyCarry}, ADR
     * runtime-credentials/007): the key it keeps is the one every HMAC sidecar verifies under. Where only one store
     * holds a key, that key is the one; where both hold the same key, it is kept; where both hold different keys, the
     * sidecars decide, and where they decide nothing the recording is refused naming both stores, since keeping the
     * wrong key would make every sidecar unverifiable and deleting the right one would lose it. The store being
     * recorded then holds the kept key before {@code credentials.store} names it, no second key is created there, and
     * the key of the other store is deleted after the switch.
     * <p>
     * <b>The first recording is resumable.</b> The recording writes the record of
     * {@link CredentialStoreMoveRecord#FIRST_RECORDING} before it copies anything, switches {@code credentials.store}
     * in one write, rewrites the record to {@code switched}, and deletes the kept key from the other store, the record
     * last (ADR runtime-credentials/008). A stop before the switch leaves no store recorded and a record that the next
     * start resumes by removing the copy and recording again; a stop after the switch leaves a record that the next
     * start resumes by deleting the key from the other store. While the record exists only the operation that owns it
     * writes {@code credentials.store}, so the aspect is refused with {@code aspect_owned} until the resume ends it.
     *
     * @param locks the lock manager; the calling thread holds no lock that sorts after the credentials lock
     * @param user  the name of the running user, the owner of the key file and of every file the verification reads
     * @param store the store the operator chose
     * @throws Refusal                                 with {@code aspect_owned} while the record of an operation exists,
     *                                                 or where another store than {@code store} is recorded already;
     *                                                 nothing was written then
     * @throws SecretStoreException                    if the key cannot be carried, with {@code credential_store_failed}
     *                                                 where the two stores hold different keys the sidecars do not
     *                                                 decide between, the key does not read back, a sidecar does not
     *                                                 verify under it or cannot be read, or the machine's files the
     *                                                 verification reads are not in their format, and with the reason of
     *                                                 the store where a store fails while the key is read or written;
     *                                                 nothing was recorded then, the copy was removed from the store
     *                                                 being recorded (a removal that fails keeps the record, so the
     *                                                 next start removes it) and the other store keeps the key. With
     *                                                 the reason of the other store if the key cannot be deleted from
     *                                                 it after the switch: the recording stands, the key is in both
     *                                                 stores, and the record stays so the next start finishes it
     * @throws de.planmarshall.core.store.StoreRefusal if the machine configuration cannot be read as its format, or a
     *                                                 record it reads cannot; nothing was written then
     * @throws UncheckedIOException                    if the record of an operation or the machine configuration
     *                                                 cannot be read or written
     */
    @SuppressWarnings("java:S6213") // record is the domain verb of the recorded store (ADR runtime-credentials/001, /002)
    public void record(LockManager locks, String user, Store store) {
        Objects.requireNonNull(locks, "locks");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(store, STORE);
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(moveRecord.lockKey());
            requireNoMove(transaction);
            var recorded = recorded();
            if (recorded.isPresent()) {
                requireRecorded(recorded.get(), store);
                return;
            }
            var keyCarry = new IntegrityKeyCarry(base, locks, user);
            var firstRecording = beginFirstRecording(keyCarry, store);
            if (firstRecording.isEmpty()) {
                write(transaction, store, Writer.CREDENTIAL_STORE_ASPECT);
                return;
            }
            ensureRecordDirectory();
            moveRecord.write(transaction, firstRecording.get().write());
            completeFirstRecording(transaction, firstRecording.get(), keyCarry);
        }
    }

    /** Creates the directory of the record where the base has none yet, as the atomic store leaves it to its caller. */
    private void ensureRecordDirectory() {
        var directory = moveRecord.store().getParent();
        if (Files.isDirectory(directory)) {
            return;
        }
        try {
            Files.createDirectories(directory,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (IOException e) {
            throw new UncheckedIOException("Directory of the credential store record cannot be created: " + directory, e);
        }
    }

    /**
     * Refuses the aspect for another store than the recorded one: a change of the recorded store is a move. The
     * recorded store again is a no-op.
     */
    private void requireRecorded(Store recorded, Store store) {
        if (recorded != store) {
            throw new Refusal(Refusal.Reason.ASPECT_OWNED,
                    ("'%s' is recorded in credentials.store of %s; the aspect records a store the first time only, "
                            + "a change to '%s' is a move between the credential stores")
                            .formatted(recorded.value(), file.store(), store.value()));
        }
    }

    /**
     * Reconciles the two stores and describes the first recording in its record, which the caller writes before
     * anything is copied or switched. The decision with side effects, which key is kept and where it is, is taken
     * once here and persisted; {@link #completeFirstRecording} never re-decides it.
     * <p>
     * A store being recorded that cannot be read now is recorded as it is where no key has to be moved into it: the
     * store is written and the key of the other store stays where it is, so no record is written. Where the other store
     * holds a key, the recording is refused instead, since bringing the key into the store is the point of the carry.
     *
     * @return the first recording in phase {@code copying}; empty if there is no key to move
     * @throws SecretStoreException with {@code credential_store_failed} if both stores hold different keys and the
     *                              sidecars do not decide between them, or with the reason of the store being recorded
     *                              if it cannot be read while the other store holds a key; nothing was written then
     */
    private Optional<CredentialStoreMoveRecord> beginFirstRecording(IntegrityKeyCarry keyCarry, Store store) {
        var sourceStore = otherStore(store);
        var targetKey = readKeyOfStore(keyCarry, store);
        var sourceKey = readKeyOfStore(keyCarry, sourceStore);
        if (!targetKey.readable()) {
            if (sourceKey.key().isPresent()) {
                throw new SecretStoreException(targetKey.failure().reason(),
                        ("the first recording of '%s' cannot carry the integrity key of '%s' into it: the store cannot "
                                + "be read: %s; nothing was recorded").formatted(store.value(), sourceStore.value(),
                                targetKey.failure().getMessage()),
                        targetKey.failure());
            }
            return Optional.empty();
        }
        if (targetKey.key().isEmpty() && sourceKey.key().isEmpty()) {
            return Optional.empty();
        }
        var keptInTarget = reconciles(keyCarry, store, targetKey.key(), sourceStore, sourceKey.key());
        return Optional.of(CredentialStoreMoveRecord.copying(Operation.FIRST_RECORDING, sourceStore, store,
                clock.instant(), keptInTarget, TransactionId.generate(clock)));
    }

    /** What one of the two stores holds of the integrity key, and why it could not be read, if it could not. */
    private record OtherKey(Optional<SecretKey> key, @Nullable SecretStoreException failure) {

        static OtherKey of(Optional<SecretKey> key) {
            return new OtherKey(key, null);
        }

        boolean readable() {
            return failure == null;
        }
    }

    /**
     * Reads the key of one of the two stores, best-effort: a store that cannot be read now, an OS store in a transient
     * outage say, is taken as holding no key, since a store the recording does not record may not block it. A key that
     * it holds then stays there, and the move into that store refuses it by name later; the warning names the store, so
     * the operator sees it.
     */
    private OtherKey readKeyOfStore(IntegrityKeyCarry keyCarry, Store store) {
        try {
            return OtherKey.of(keyCarry.keyIn(backendOf(store)).read());
        } catch (SecretStoreException e) {
            LOGGER.warn(WARN.CREDENTIAL_STORE_UNREADABLE_WHILE_RECORDING, store.value(), e.getMessage());
            return new OtherKey(Optional.empty(), e);
        }
    }

    /**
     * Decides which of the two keys the first recording keeps, from the two stores and the sidecars, and refuses where
     * they decide nothing.
     *
     * @return whether the store being recorded holds the kept key already
     */
    private static boolean reconciles(IntegrityKeyCarry keyCarry, Store targetStore,
            Optional<SecretKey> targetKey, Store sourceStore, Optional<SecretKey> sourceKey) {
        if (sourceKey.isEmpty()) {
            return targetKey.isPresent();
        }
        if (targetKey.isEmpty()) {
            return false;
        }
        if (IntegrityKeyCarry.sameKey(targetKey.get(), sourceKey.get())) {
            return true;
        }
        var inTarget = keyCarry.failureUnder(targetKey.get());
        var inSource = keyCarry.failureUnder(sourceKey.get());
        if (inTarget.isEmpty() != inSource.isEmpty()) {
            return inTarget.isEmpty();
        }
        throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                ("the first recording of '%s' is refused: '%s' and '%s' hold different integrity keys and %s; remove the "
                        + "key of one of the two stores by hand: %s, %s").formatted(targetStore.value(),
                        targetStore.value(), sourceStore.value(),
                        inTarget.isEmpty() || inSource.isEmpty()
                                ? "a sidecar verifies under both, so nothing tells which key the machine is signed under"
                                : "the sidecars verify under neither, so neither key is the machine's",
                        inTarget.map(failure -> "'%s' does not verify the sidecars: %s %s"
                                .formatted(targetStore.value(), failure.file(), failure.reason()))
                                .orElse("'%s' verifies every sidecar".formatted(targetStore.value())),
                        inSource.map(failure -> "'%s' does not verify the sidecars: %s %s"
                                .formatted(sourceStore.value(), failure.file(), failure.reason()))
                                .orElse("'%s' verifies every sidecar".formatted(sourceStore.value()))));
    }

    /** @return the store that is not {@code store}: the one the key is deleted from after the first recording */
    static Store otherStore(Store store) {
        return store == Store.OS ? Store.FILE : Store.OS;
    }

    /**
     * Completes a first recording from its record: brings the kept key into the store being recorded, verifies the
     * sidecars under it, switches {@code credentials.store}, rewrites the record to {@code switched}, deletes the key
     * from the other store and deletes the record last. A failure before the switch removes the copy and deletes the
     * record, unless the copy cannot be removed, which keeps the record for the next start; a failure after the switch
     * keeps the record, so the next start deletes the key from the other store.
     */
    private void completeFirstRecording(LockTransaction transaction, CredentialStoreMoveRecord firstRecording,
            IntegrityKeyCarry keyCarry) {
        var target = backendOf(firstRecording.to());
        var source = backendOf(firstRecording.from());
        var copied = false;
        var sourceKey = readKeyOfStore(keyCarry, firstRecording.from());
        try {
            var targetKey = keyCarry.keyIn(target).read();
            if (firstRecording.targetHeldKey()) {
                if (targetKey.isEmpty()) {
                    throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                            ("the first recording of '%s' cannot be completed: it held the key it keeps when it "
                                    + "started and holds none now; nothing was recorded")
                                    .formatted(firstRecording.to().value()));
                }
                if (sourceKey.key().isPresent()) {
                    keyCarry.verifySidecars(targetKey.get());
                }
            } else if (sourceKey.key().isPresent()) {
                if (targetKey.isPresent()) {
                    keyCarry.keyIn(target).delete();
                }
                copied = true;
                keyCarry.copy(source, target).ifPresent(keyCarry::verifySidecars);
            } else if (targetKey.isPresent()) {
                throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                        ("the first recording of '%s' cannot be completed: '%s' is %s, so the key the record %s carries "
                                + "cannot be brought into '%s', which holds another key; nothing was recorded")
                                .formatted(firstRecording.to().value(), firstRecording.from().value(),
                                        sourceKey.readable() ? "empty" : "unreadable", moveRecord.store(),
                                        firstRecording.to().value()));
            }
            record(transaction, firstRecording.to(), Writer.CREDENTIAL_STORE_ASPECT);
        } catch (SecretStoreException | UncheckedIOException | StoreRefusal | InvalidCredentialNameException e) {
            throw failedFirstRecording(transaction, firstRecording, keyCarry, copied, e);
        }
        moveRecord.write(transaction, firstRecording.switched().write());
        deleteKeyOfSource(firstRecording, keyCarry, false);
        CredentialStoreMove.deleteRecord(transaction, moveRecord);
    }

    /**
     * Ends a first recording that failed before the switch: removes the copy from the store being recorded, deletes or
     * keeps the record, and says what became of both. The record stays exactly while the next start has a clean-up left
     * that it may do.
     *
     * @return the failure of the recording, for the caller to throw
     */
    private SecretStoreException failedFirstRecording(LockTransaction transaction,
            CredentialStoreMoveRecord firstRecording, IntegrityKeyCarry keyCarry, boolean copied,
            RuntimeException cause) {
        var to = firstRecording.to().value();
        var from = firstRecording.from().value();
        var recordedNow = recordedNow(cause);
        var switched = recordedNow.equals(Optional.of(firstRecording.to()));
        var removed = switched || !copied || removeCopy(firstRecording, keyCarry, cause);
        String outcome;
        if (switched) {
            outcome = ("'%s' is recorded although the recording failed; the copy stays and the record is kept, so the "
                    + "next start finishes the recording and deletes the key from '%s'").formatted(to, from);
        } else if (!removed) {
            outcome = ("nothing was recorded and '%s' keeps the integrity key; the copy could not be removed from "
                    + "'%s', so the record is kept and the next start removes it").formatted(from, to);
        } else {
            outcome = "nothing was recorded and '%s' keeps the integrity key".formatted(from);
            try {
                CredentialStoreMove.deleteRecord(transaction, moveRecord);
            } catch (UncheckedIOException e) {
                cause.addSuppressed(e);
            }
        }
        LOGGER.warn(cause, WARN.CREDENTIAL_STORE_FIRST_RECORDING_FAILED, to, from, outcome, cause.getMessage());
        return new SecretStoreException(SecretStoreException.Reason.FAILED,
                "the first recording of '%s' failed, %s: %s".formatted(to, outcome, cause.getMessage()), cause);
    }

    /** @return whether the copy was removed from the store being recorded; a failure is added to the cause */
    private boolean removeCopy(CredentialStoreMoveRecord firstRecording, IntegrityKeyCarry keyCarry,
            RuntimeException cause) {
        try {
            if (!firstRecording.targetHeldKey()) {
                keyCarry.keyIn(backendOf(firstRecording.to())).delete();
            }
            return true;
        } catch (SecretStoreException e) {
            cause.addSuppressed(e);
            return false;
        }
    }

    /**
     * @return the store {@code credentials.store} names now; empty, with the failure added to the cause, if the
     *         machine configuration cannot be read
     */
    private Optional<Store> recordedNow(RuntimeException cause) {
        try {
            return recorded();
        } catch (StoreRefusal | UncheckedIOException e) {
            cause.addSuppressed(e);
            return Optional.empty();
        }
    }

    /**
     * Deletes the kept key from the store the first recording did not record, after the switch: under the credentials
     * lock, and only once the store being recorded provably holds that same key. A store that cannot be read, or a
     * recorded store that holds no key or another one, refuses the deletion with an {@code IllegalStateException} and
     * keeps the record, so the key is never deleted from the only store that holds it; a failing deletion keeps the
     * record too, so the next start finishes it.
     */
    private void deleteKeyOfSource(CredentialStoreMoveRecord firstRecording, IntegrityKeyCarry keyCarry,
            boolean resume) {
        var targetKey = keyCarry.keyIn(backendOf(firstRecording.to())).read();
        var sourceKey = resume ? keyCarry.keyIn(backendOf(firstRecording.from())).read()
                : readKeyOfStore(keyCarry, firstRecording.from()).key();
        if (sourceKey.isEmpty()) {
            return;
        }
        if (targetKey.isEmpty()) {
            throw new IllegalStateException(("the record %s of the first recording of '%s' is in phase '%s' but '%s' "
                    + "holds no integrity key while '%s' does; nothing was deleted and the record is kept")
                    .formatted(moveRecord.store(), firstRecording.to().value(), firstRecording.phase().value(),
                            firstRecording.to().value(), firstRecording.from().value()));
        }
        if (!IntegrityKeyCarry.sameKey(sourceKey.get(), targetKey.get())
                && keyCarry.failureUnder(targetKey.get()).isPresent()) {
            throw new IllegalStateException(("the record %s of the first recording of '%s' is in phase '%s' but '%s' "
                    + "holds a key the sidecars do not verify under, beside another key in '%s'; nothing was deleted "
                    + "and the record is kept").formatted(moveRecord.store(), firstRecording.to().value(),
                    firstRecording.phase().value(), firstRecording.to().value(), firstRecording.from().value()));
        }
        try {
            keyCarry.keyIn(backendOf(firstRecording.from())).delete();
        } catch (SecretStoreException e) {
            throw new SecretStoreException(e.reason(),
                    ("the recording of '%s' stands and it holds the integrity key, but the key cannot be deleted from "
                            + "'%s', so it is in both stores; the record %s stays, so the next start finishes the "
                            + "deletion: %s").formatted(firstRecording.to().value(), firstRecording.from().value(),
                            moveRecord.store(), e.getMessage()),
                    e);
        }
    }

    /**
     * Resumes the first recording a stop cut, from its record and {@code credentials.store}: in phase {@code copying}
     * with the store being recorded already recorded, or in phase {@code switched} with it recorded, it finishes the
     * deletion of the key from the other store and ends the record; in phase {@code copying} with no store recorded it
     * removes the copy and records again; otherwise it refuses and deletes nothing.
     *
     * @param transaction        a transaction that holds the credentials lock
     * @param firstRecording     the record as the resume read it
     * @param user               the name of the running user, the owner of the key file and of the verified files
     * @param locks              the lock manager of the operation, which opens the transaction of the verification
     * @return the record as it was found
     */
    Optional<CredentialStoreMoveRecord> resumeFirstRecording(LockTransaction transaction,
            CredentialStoreMoveRecord firstRecording, String user, LockManager locks) {
        var keyCarry = new IntegrityKeyCarry(base, locks, user);
        var recorded = recorded();
        if (firstRecording.phase() == Phase.SWITCHED || recorded.equals(Optional.of(firstRecording.to()))) {
            requireTargetRecorded(firstRecording, recorded);
            if (firstRecording.phase() == Phase.COPYING) {
                moveRecord.write(transaction, firstRecording.switched().write());
            }
            deleteKeyOfSource(firstRecording, keyCarry, true);
            CredentialStoreMove.deleteRecord(transaction, moveRecord);
            return Optional.of(firstRecording);
        }
        if (recorded.isPresent()) {
            throw new IllegalStateException(("the record %s of the first recording of '%s' is in phase '%s' but "
                    + "credentials.store names '%s'; a first recording switches only where no store is recorded. "
                    + "Nothing was deleted and the record is kept").formatted(moveRecord.store(),
                    firstRecording.to().value(), firstRecording.phase().value(), recorded.get().value()));
        }
        deleteCopyOfFirstRecording(firstRecording, keyCarry);
        completeFirstRecording(transaction, firstRecording, keyCarry);
        return Optional.of(firstRecording);
    }

    /** Refuses to finish a first recording whose store is not recorded; nothing is deleted then. */
    private void requireTargetRecorded(CredentialStoreMoveRecord firstRecording, Optional<Store> recorded) {
        if (!recorded.equals(Optional.of(firstRecording.to()))) {
            throw new IllegalStateException(("the record %s of the first recording of '%s' is in phase '%s' but "
                    + "credentials.store names %s; nothing was deleted from '%s' or '%s' and the record is kept")
                    .formatted(moveRecord.store(), firstRecording.to().value(), firstRecording.phase().value(),
                            recorded.map(store -> "'" + store.value() + "'").orElse("no store"),
                            firstRecording.from().value(), firstRecording.to().value()));
        }
    }

    /**
     * Deletes the copy a first recording left in the store it records, before it starts again; and only that copy. A
     * store that cannot be read fails the resume and keeps the record, so the copy is never left behind unnoticed.
     */
    private void deleteCopyOfFirstRecording(CredentialStoreMoveRecord firstRecording, IntegrityKeyCarry keyCarry) {
        if (!firstRecording.targetHeldKey()
                && keyCarry.keyIn(backendOf(firstRecording.from())).read().isPresent()) {
            keyCarry.keyIn(backendOf(firstRecording.to())).delete();
        }
    }

    /** Refuses every writer but the operation that owns it while a record exists; the transaction holds its lock. */
    private void requireNoMove(LockTransaction transaction) {
        if (moveRecord.read(transaction).isPresent()) {
            throw new Refusal(Refusal.Reason.ASPECT_OWNED,
                    "an operation on the credential stores is open in %s; until it ends only that operation records %s"
                            .formatted(moveRecord.store(), "credentials.store of " + file.store()));
        }
    }

    /** Writes {@code credentials.store} under the machine configuration lock, which this call acquires. */
    private void write(LockTransaction transaction, Store store, Writer writer) {
        transaction.acquire(file.lockKey());
        var current = file.read(transaction);
        var revision = current.map(document -> parse(document).revision()).orElse(0L) + 1;
        file.write(transaction, current.map(document -> rewrite(document, store, revision))
                .orElseGet(() -> create(store)));
        LOGGER.info(INFO.CREDENTIAL_STORE_RECORDED, store.value(), writer, revision);
    }

    /**
     * Gives the store of the recorded choice, as it is recorded now.
     *
     * @return the active store
     * @throws Refusal                                 with {@code onboarding_incomplete} if no store is recorded yet
     * @throws de.planmarshall.core.store.StoreRefusal if the machine configuration cannot be read as its format
     */
    public SecretStore activeStore() {
        var store = recorded().orElseThrow(() -> new Refusal(Refusal.Reason.ONBOARDING_INCOMPLETE,
                "no credential store is recorded yet; record one in the '%s' step of the machine's onboarding"
                        .formatted(STORE_STEP)));
        return store == Store.FILE ? fileStore : new ProbedOsStore(true);
    }

    /**
     * Gives one of the two stores, whether it is recorded or not: what the store move copies from and into. The OS
     * store is probed before every operation, like the recorded one; an unavailable OS store fails with the same
     * reasons, and its failure names it as the store it is, never as the recorded store, since the target of a move is
     * not recorded.
     *
     * @param store the store
     * @return the file store below {@code <PM_MCP_BASE>/credentials}, or the OS store of the platform
     */
    SecretStore backendOf(Store store) {
        return Objects.requireNonNull(store, STORE) == Store.FILE ? fileStore : new ProbedOsStore(false);
    }

    /**
     * Gives the store the enrolment integrity key lives in: the active store once a store is recorded; before that
     * the OS store where one is available, else the file store, for which the key is the key file below
     * {@code state/}. The store before the choice is found once, at the first call, and kept for the lifetime of this
     * instance: a later probe never moves the key from the OS store to the key file or back, so a transient outage of
     * the OS store fails an operation on the key instead of creating a second key in the other store.
     *
     * @return the store of the integrity key
     * @throws de.planmarshall.core.store.StoreRefusal if the machine configuration cannot be read as its format
     */
    public SecretStore integrityKeyStore() {
        if (recorded().isPresent()) {
            return activeStore();
        }
        return keyStoreBeforeChoice.updateAndGet(found -> found != null ? found : osStore().orElse(fileStore));
    }

    /** @return the name of the OS store of this platform */
    String osStoreName() {
        return os == MachinePaths.Os.MACOS ? SecretStore.KEYCHAIN : SecretStore.SECRET_SERVICE;
    }

    /**
     * Probes the OS store of the platform.
     *
     * @return the OS store, or empty if it is unavailable now
     */
    Optional<SecretStore> osStore() {
        return osStoreOrReason().store();
    }

    /**
     * Probes the OS store of the platform for its check before it is recorded.
     *
     * @return the OS store
     * @throws SecretStoreException with {@code credential_store_unavailable} and the cause if it is unavailable now
     */
    SecretStore requireOsStore() {
        var probe = osStoreOrReason();
        return probe.store().orElseThrow(() -> new SecretStoreException(SecretStoreException.Reason.UNAVAILABLE,
                "the OS credential store '%s' is unavailable: %s".formatted(osStoreName(), probe.reason())));
    }

    private Probe osStoreOrReason() {
        if (os == MachinePaths.Os.MACOS) {
            try {
                return new Probe(Optional.of(keychain()), "");
            } catch (SecretStoreException e) {
                return new Probe(Optional.empty(), "Keychain: " + e.getMessage());
            }
        }
        var socket = DbusConnection.sessionBusSocket(environment);
        if (socket.isEmpty()) {
            return new Probe(Optional.empty(), "Secret Service: no unix:path session bus address");
        }
        var store = keyrings.secretService(socket.get(), service);
        return new Probe(store, store.isPresent() ? ""
                : "Secret Service: not reachable, or no unlocked default collection on " + socket.get());
    }

    /**
     * Gives the Keychain store, loaded at most once per instance: loading it looks up Security.framework, which is
     * never released, so it is not repeated for every operation. A load that fails is not kept and is tried again at
     * the next probe; a locked Keychain is reported by the store itself at every operation.
     *
     * @return the Keychain store
     * @throws SecretStoreException if Security.framework cannot be loaded
     */
    private SecretStore keychain() {
        var loaded = loadedKeychain.get();
        if (loaded != null) {
            return loaded;
        }
        var store = keyrings.keychain(service);
        return loadedKeychain.compareAndSet(null, store) ? store : loadedKeychain.get();
    }

    /** The outcome of a probe of the OS store. */
    private record Probe(Optional<SecretStore> store, String reason) {
    }

    /** What this class reads of the machine configuration. */
    private record Document(long revision, Optional<Store> store) {
    }

    private Document parse(byte[] document) {
        return StrictJson.read(file.store(), document, FORMAT_VERSION, parser -> {
            StrictJson.requireObject(parser, FILE_NAME);
            var seen = new HashSet<String>();
            long revision = 0;
            Optional<Store> store = Optional.empty();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = StrictJson.nextField(parser, seen);
                switch (field) {
                    case REVISION -> revision = revision(parser);
                    case CREDENTIALS -> store = credentialsStore(parser);
                    default -> parser.skipChildren();
                }
            }
            return new Document(revision, store);
        });
    }

    private static long revision(JsonParser parser) throws IOException {
        var revision = StrictJson.integer(parser, REVISION);
        if (revision < 0) {
            throw StrictJson.unreadable("'%s' must not be negative".formatted(REVISION));
        }
        return revision;
    }

    private static Optional<Store> credentialsStore(JsonParser parser) throws IOException {
        StrictJson.requireObject(parser, CREDENTIALS);
        var seen = new HashSet<String>();
        Optional<Store> store = Optional.empty();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            if (STORE.equals(StrictJson.nextField(parser, seen))) {
                var value = StrictJson.string(parser, CREDENTIALS + "." + STORE);
                store = Optional.of(Store.of(value).orElseThrow(() -> StrictJson.unreadable(
                        "'%s.%s' must be 'os' or 'file': %s".formatted(CREDENTIALS, STORE, value))));
            } else {
                parser.skipChildren();
            }
        }
        return store;
    }

    /** Copies the document with the new revision and store; every other member is copied as it is. */
    private byte[] rewrite(byte[] document, Store store, long revision) {
        return generate(generator -> {
            try (var parser = StrictJson.factory().createParser(document)) {
                parser.nextToken();
                generator.writeStartObject();
                var written = new HashSet<String>();
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    var field = parser.currentName();
                    parser.nextToken();
                    written.add(field);
                    switch (field) {
                        case REVISION -> generator.writeNumberField(REVISION, revision);
                        case CREDENTIALS -> writeCredentials(generator, parser, store);
                        default -> {
                            generator.writeFieldName(field);
                            generator.copyCurrentStructure(parser);
                        }
                    }
                }
                if (!written.contains(REVISION)) {
                    generator.writeNumberField(REVISION, revision);
                }
                if (!written.contains(CREDENTIALS)) {
                    writeCredentials(generator, null, store);
                }
                generator.writeEndObject();
            }
        });
    }

    private static void writeCredentials(JsonGenerator generator, @Nullable JsonParser parser, Store store)
            throws IOException {
        generator.writeObjectFieldStart(CREDENTIALS);
        generator.writeStringField(STORE, store.value());
        if (parser != null) {
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = parser.currentName();
                parser.nextToken();
                if (STORE.equals(field)) {
                    parser.skipChildren();
                } else {
                    generator.writeFieldName(field);
                    generator.copyCurrentStructure(parser);
                }
            }
        }
        generator.writeEndObject();
    }

    private static byte[] create(Store store) {
        return generate(generator -> {
            generator.writeStartObject();
            generator.writeNumberField(StrictJson.FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeNumberField(REVISION, 1);
            generator.writeNumberField(MAX_BUILD_SLOTS, DEFAULT_MAX_BUILD_SLOTS);
            generator.writeNumberField(LSP_IDLE_TIMEOUT_SECONDS, DEFAULT_LSP_IDLE_TIMEOUT_SECONDS);
            writeCredentials(generator, null, store);
            generator.writeEndObject();
        });
    }

    /** Writes one document. */
    @FunctionalInterface
    private interface Content {

        void write(JsonGenerator generator) throws IOException;
    }

    private static byte[] generate(Content content) {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out)) {
            content.write(generator);
        } catch (IOException e) {
            throw new UncheckedIOException("machine configuration cannot be written", e);
        }
        out.write('\n');
        return out.toByteArray();
    }

    /**
     * The OS store, probed before every operation, so an OS store that becomes unavailable after the start is reported
     * as such: the Secret Service is probed afresh, the Keychain is loaded once per instance and then answers for
     * itself, a locked Keychain at every operation. An unavailable store fails a read with {@code credential_store_unavailable} and a write with
     * {@code credential_store_locked}; a locked collection or item met by a read is unavailable as well. Given as the
     * recorded store, its unavailability is logged and named as that of the recorded store; given as one of the two
     * stores of a move, it is named as the OS store only.
     */
    private final class ProbedOsStore implements SecretStore {

        private final boolean recorded;

        /** @param recorded whether the store is given as the recorded store */
        ProbedOsStore(boolean recorded) {
            this.recorded = recorded;
        }

        @Override
        public String name() {
            return osStoreName();
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            forWrite().put(account, secret);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            var store = forRead();
            try {
                return store.get(account);
            } catch (SecretStoreException e) {
                throw lockedAsUnavailable(e);
            }
        }

        @Override
        public boolean delete(CredentialAccount account) {
            return forWrite().delete(account);
        }

        @Override
        public List<CredentialAccount> entries() {
            var store = forRead();
            try {
                return store.entries();
            } catch (SecretStoreException e) {
                throw lockedAsUnavailable(e);
            }
        }

        private SecretStore forRead() {
            return available(SecretStoreException.Reason.UNAVAILABLE);
        }

        private SecretStore forWrite() {
            return available(SecretStoreException.Reason.LOCKED);
        }

        private SecretStore available(SecretStoreException.Reason reason) {
            var probe = osStoreOrReason();
            if (probe.store().isPresent()) {
                return probe.store().get();
            }
            throw new SecretStoreException(reason, unavailable(probe.reason()));
        }

        private SecretStoreException lockedAsUnavailable(SecretStoreException e) {
            if (e.reason() != SecretStoreException.Reason.LOCKED) {
                return e;
            }
            return new SecretStoreException(SecretStoreException.Reason.UNAVAILABLE, unavailable(e.getMessage()), e);
        }

        /** Logs the unavailability of the recorded store, and describes it as that of the store it is. */
        private String unavailable(String cause) {
            if (!recorded) {
                return "the credential store '%s' is unavailable: %s".formatted(osStoreName(), cause);
            }
            LOGGER.warn(WARN.RECORDED_OS_STORE_UNAVAILABLE, osStoreName(), cause);
            return "the recorded credential store '%s' is unavailable: %s".formatted(osStoreName(), cause);
        }
    }
}
