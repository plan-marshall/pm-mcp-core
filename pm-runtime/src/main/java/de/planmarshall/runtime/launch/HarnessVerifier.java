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

import de.cuioss.tools.logging.CuiLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.InstantSource;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.launch.HarnessEnrolmentStore.Refusal;
import de.planmarshall.runtime.log.RuntimeLogMessages;

/**
 * The launch-time verification of the enrolled harness (PM-EXEC-1, PM-CLIENT-5, Harness Enrolment Store): the store's
 * integrity, the enrolment, the digest of the executable at every spawn, and, where the digest changed, the update
 * evidence and the version probe before any changed file runs.
 * <p>
 * A changed executable is never its own witness: the evidence of the profile is checked first, without running the
 * file, and only then does the version probe run. An accepted self-update rewrites {@code harnesses.json} with the new
 * version and digest and is audited {@code harness_updated}; every other case refuses the launch before spawn with
 * {@code harness_not_enrolled}, {@code harness_update_unverified}, {@code harness_version_unsupported},
 * {@code harness_tampered} or {@code harness_store_unreadable}.
 *
 * @since 0.1
 */
public final class HarnessVerifier {

    private static final CuiLogger LOGGER = new CuiLogger(
            HarnessVerifier.class);

    private final HarnessEnrolmentStore store;
    private final Digest digest;
    private final VersionProbe versionProbe;
    private final EvidenceCheck evidenceCheck;
    private final AuditSink audit;
    private final InstantSource clock;

    /**
     * @param store         the harness enrolment store
     * @param digest        the digest of an executable
     * @param versionProbe  the profile's version probe
     * @param evidenceCheck the evidence a changed executable must pass
     * @param audit         where a tampered store and an accepted self-update are recorded
     * @param clock         the clock of the accepted self-update
     */
    public HarnessVerifier(HarnessEnrolmentStore store, Digest digest, VersionProbe versionProbe,
            EvidenceCheck evidenceCheck, AuditSink audit, InstantSource clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.digest = Objects.requireNonNull(digest, "digest");
        this.versionProbe = Objects.requireNonNull(versionProbe, "versionProbe");
        this.evidenceCheck = Objects.requireNonNull(evidenceCheck, "evidenceCheck");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param clientId the client id of the harness the role's effective configuration names
     * @param block    the launch contract of the harness's profile
     * @return the enrolment of the verified build, or the refusal of the launch
     */
    public HarnessVerification verify(String clientId, HeadlessBlock block) {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(block, "block");
        Optional<HarnessEnrolment> found;
        try {
            found = store.find(clientId);
        } catch (HarnessEnrolmentStore.StoreException refusal) {
            if (refusal.refusal() == Refusal.TAMPERED || refusal.refusal() == Refusal.INSECURE) {
                audit.append(AuditEvent.enrolmentTampered(clientId, refusal.getMessage()));
            }
            return refused(clientId, refusal.refusal() == Refusal.UNREADABLE ? LaunchRefusal.HARNESS_STORE_UNREADABLE
                    : LaunchRefusal.HARNESS_TAMPERED, refusal.getMessage());
        }
        if (found.isEmpty()) {
            return refused(clientId, LaunchRefusal.HARNESS_NOT_ENROLLED,
                    "the harness '%s' is not enrolled".formatted(clientId));
        }
        var enrolment = found.get();
        var current = digest.sha256(enrolment.executablePath());
        if (current.isEmpty()) {
            return refused(clientId, LaunchRefusal.HARNESS_UPDATE_UNVERIFIED,
                    "the enrolled executable '%s' is absent or not a regular file"
                            .formatted(enrolment.executablePath()));
        }
        if (current.get().equals(enrolment.sha256())) {
            return new HarnessVerification.Accepted(enrolment);
        }
        if (block.updateEvidence().method() == HeadlessBlock.EvidenceMethod.NONE
                || !evidenceCheck.passes(block.updateEvidence(), enrolment.executablePath())) {
            return refused(clientId, LaunchRefusal.HARNESS_UPDATE_UNVERIFIED,
                    "the executable changed without the update evidence of the profile '%s'".formatted(clientId));
        }
        var probed = versionProbe.version(enrolment.executablePath(), block.versionProbeArgv(),
                block.versionPattern());
        if (probed.isEmpty()) {
            return refused(clientId, LaunchRefusal.HARNESS_UPDATE_UNVERIFIED,
                    "the changed executable reported no version inside its probe");
        }
        if (!block.supports(probed.get())) {
            return refused(clientId, LaunchRefusal.HARNESS_VERSION_UNSUPPORTED,
                    "the version '%s' lies outside the supported range of '%s'".formatted(probed.get(), clientId));
        }
        var recheck = digest.sha256(enrolment.executablePath());
        if (recheck.isEmpty() || !recheck.get().equals(current.get())) {
            return refused(clientId, LaunchRefusal.HARNESS_UPDATE_UNVERIFIED,
                    "the executable changed again while its update was verified");
        }
        Optional<HarnessEnrolment> accepted;
        try {
            accepted = store.acceptSelfUpdate(clientId, probed.get(), current.get(), clock.instant());
        } catch (HarnessEnrolmentStore.StoreException failure) {
            return refused(clientId, failure.refusal() == Refusal.UNREADABLE ? LaunchRefusal.HARNESS_STORE_UNREADABLE
                    : LaunchRefusal.HARNESS_TAMPERED, failure.getMessage());
        }
        if (accepted.isEmpty()) {
            return refused(clientId, LaunchRefusal.HARNESS_NOT_ENROLLED,
                    "the harness '%s' was removed while its self-update was accepted".formatted(clientId));
        }
        audit.append(AuditEvent.harnessUpdated(clientId, enrolment.version(), enrolment.sha256(), probed.get(),
                current.get()));
        LOGGER.info(RuntimeLogMessages.INFO.HARNESS_SELF_UPDATE_ACCEPTED, clientId, enrolment
                .version(), probed.get(), current.get());
        return new HarnessVerification.Accepted(accepted.get());
    }

    private HarnessVerification refused(String clientId, LaunchRefusal reason, String detail) {
        LOGGER.warn(RuntimeLogMessages.WARN.WORKER_LAUNCH_REFUSED, clientId, reason.code(),
                detail, reason.resolvingAct(clientId));
        return new HarnessVerification.Refused(reason, detail);
    }

    /**
     * @param file an executable
     * @return its SHA-256 as lowercase hex; empty when the file is absent or not a regular file
     */
    public static Optional<String> digestExecutable(Path file) {
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                return Optional.empty();
            }
            try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ,
                         LinkOption.NOFOLLOW_LINKS)) {
                return Optional.of(sha256(channel));
            }
        } catch (NoSuchFileException | NotDirectoryException _) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("The executable cannot be read: " + file, e);
        }
    }

    private static String sha256(SeekableByteChannel channel) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is available on every Java platform", e);
        }
        var buffer = ByteBuffer.allocate(64 * 1024);
        while (channel.read(buffer) >= 0) {
            buffer.flip();
            digest.update(buffer);
            buffer.clear();
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Reads the version out of the probe's output through the profile's pattern.
     *
     * @param output  the probe's standard output
     * @param pattern the profile's version pattern; {@code null} when the profile declares no probe
     * @return the first captured group, or the whole match when the pattern captures nothing
     */
    public static Optional<String> matchVersion(String output, String pattern) {
        if (pattern == null) {
            return Optional.empty();
        }
        try {
            var matcher = Pattern.compile(pattern).matcher(output);
            if (!matcher.find()) {
                return Optional.empty();
            }
            return Optional.of(matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group());
        } catch (PatternSyntaxException _) {
            return Optional.empty();
        }
    }

    /** The digest of an executable. */
    @FunctionalInterface
    public interface Digest {

        /**
         * @param file the executable
         * @return its lowercase-hex SHA-256; empty when the file is absent or not a regular file
         */
        Optional<String> sha256(Path file);
    }

    /** The version probe of a harness profile. */
    @FunctionalInterface
    public interface VersionProbe {

        /**
         * @param executable the enrolled executable
         * @param argv       the profile's version probe argument vector
         * @param pattern    the profile's version pattern
         * @return the version the probe reported
         */
        Optional<String> version(Path executable, List<String> argv, String pattern);
    }

    /** The evidence a changed harness executable must pass. */
    @FunctionalInterface
    public interface EvidenceCheck {

        /**
         * @param evidence   the evidence the profile declares
         * @param executable the changed executable
         * @return whether the evidence passed
         */
        boolean passes(HeadlessBlock.UpdateEvidence evidence, Path executable);
    }

    /** The default digest, which never follows a symbolic link. */
    public static Digest executableDigest() {
        return HarnessVerifier::digestExecutable;
    }

    /** The default evidence check of a platform whose evidence is not available: every change waits for the operator. */
    public static EvidenceCheck noUpdateEvidence() {
        return (evidence, executable) -> false;
    }
}
