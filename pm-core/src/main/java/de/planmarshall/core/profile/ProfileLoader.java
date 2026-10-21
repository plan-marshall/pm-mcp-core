/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import de.planmarshall.core.store.EnrolmentSidecar;

/**
 * Loads the effective client profile set of a machine and a project (PM-CLIENT-5, Profile Sets).
 * <p>
 * The loader reads the whole documents of the three sets and validates each against the client profile schema and the
 * hardening rules. The nearest set wins as a whole document and is never merged with another: a project profile of a
 * client id the machine or the shipped set defines replaces that document, a machine profile replaces the shipped one,
 * and a machine profile of a client id the release does not ship adds a harness. The loader names no harness; the
 * client ids and every value of a profile are data.
 * <p>
 * A machine or project profile counts only with its verification record: the {@code profile_digest} must be the
 * SHA-256 of the canonical JSON of the document, and the {@code <client_id>.json.hmac} sidecar must verify with the key
 * of the enrolment store. Otherwise the profile is refused {@code profile_unverified} and the profile of the next set
 * stays in force. A record of another build of the enrolled harness, inside the {@code supported_versions} range, marks
 * the profile {@code verified_on_other_build} and launches go on; outside the range the profile is refused
 * {@code harness_version_unsupported}.
 * <p>
 * The loader reads the sets and reports the state it found; writing them is the onboarding's
 * ({@code machine-onboarding-unit}). Nothing here repairs, migrates or rewrites a profile or a record.
 *
 * @since 0.1
 */
public final class ProfileLoader {

    private static final String PROFILES = "profiles";
    private static final String VERIFICATION = "verification";
    private static final String SUFFIX = ".json";
    private static final String HMAC = ".hmac";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKey key;
    private final Map<String, EnrolledHarness> enrolled;
    private final ProfileRules rules;

    /**
     * @param key      the key of the enrolment store, which the HMAC sidecar of a machine or project profile is
     *                 verified with
     * @param enrolled the enrolled harness build by client id
     * @param rules    the bundle-managed paths and harness homes of this installation
     */
    public ProfileLoader(SecretKey key, Map<String, EnrolledHarness> enrolled, ProfileRules rules) {
        this.key = Objects.requireNonNull(key, "key");
        this.enrolled = Map.copyOf(enrolled);
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * @param sets the three sets
     * @return the effective profile set of a runtime without a project
     */
    public ProfileSet load(ProfileSets sets) {
        return load(sets, null);
    }

    /**
     * @param sets      the three sets
     * @param projectId the project whose project set is read; {@code null} for no project set
     * @return the effective profile set
     */
    public ProfileSet load(ProfileSets sets, String projectId) {
        Objects.requireNonNull(sets, "sets");
        var effective = new TreeMap<String, LoadedProfile>();
        var refused = new ArrayList<ProfileSet.RefusedProfile>();
        readShipped(sets, effective, refused);
        readDirectory(sets.machineDirectory(), ProfileSetKind.MACHINE, effective, refused);
        if (projectId != null && sets.projectDirectory() != null) {
            readProject(sets.projectDirectory(), effective, refused);
        }
        return new ProfileSet(effective, refused);
    }

    private void readShipped(ProfileSets sets, Map<String, LoadedProfile> effective,
            List<ProfileSet.RefusedProfile> refused) {
        for (var entry : sets.shipped().entrySet()) {
            var clientId = entry.getKey();
            try {
                var profile = ProfileValidator.validate(entry.getValue().document(),
                        "%s/%s%s".formatted(PROFILES, clientId, SUFFIX), rules);
                requireClientId(profile, clientId, clientId + SUFFIX);
                place(new LoadedProfile(clientId, ProfileSetKind.SHIPPED, profile, entry.getValue().document(),
                        LoadedProfile.State.VERIFIED), effective, refused);
            } catch (ProfileRefusal refusal) {
                refused.add(new ProfileSet.RefusedProfile(clientId, ProfileSetKind.SHIPPED, refusal));
            }
        }
    }

    private void readDirectory(Path directory, ProfileSetKind kind, Map<String, LoadedProfile> effective,
            List<ProfileSet.RefusedProfile> refused) {
        for (var clientId : clientIds(directory)) {
            try {
                var profile = readVerified(directory, clientId, kind);
                place(profile, effective, refused);
            } catch (ProfileRefusal refusal) {
                refused.add(new ProfileSet.RefusedProfile(clientId, kind, refusal));
            }
        }
    }

    private void readProject(Path directory, Map<String, LoadedProfile> effective,
            List<ProfileSet.RefusedProfile> refused) {
        for (var clientId : clientIds(directory)) {
            try {
                var base = effective.get(clientId);
                if (base == null) {
                    throw ProfileRefusal.invalid("%s/%s%s".formatted(PROFILES, clientId, SUFFIX), "/client_id",
                            "a project profile exists only for a client id the machine or shipped set defines");
                }
                var profile = readVerified(directory, clientId, ProfileSetKind.PROJECT);
                sameAsBase(profile, base);
                place(profile, effective, refused);
            } catch (ProfileRefusal refusal) {
                refused.add(new ProfileSet.RefusedProfile(clientId, ProfileSetKind.PROJECT, refusal));
            }
        }
    }

    /** Reads one profile of a machine or project set and binds it to its record, its sidecar and the enrolled build. */
    private LoadedProfile readVerified(Path directory, String clientId, ProfileSetKind kind) {
        var document = read(directory.resolve(clientId + SUFFIX));
        var name = "%s/%s%s".formatted(kind.wireName(), clientId, SUFFIX);
        var profile = ProfileValidator.validate(document, name, rules);
        requireClientId(profile, clientId, name);
        var record = record(directory, clientId, name);
        if (!clientId.equals(record.clientId())) {
            throw ProfileRefusal.unverified(name, "the record names the client '%s'".formatted(record.clientId()));
        }
        if (!CanonicalJson.digest(profile).equals(record.profileDigest())) {
            throw ProfileRefusal.unverified(name,
                    "the record's profile_digest is not the digest of the document");
        }
        verifySidecar(directory.resolve(clientId + SUFFIX + HMAC), name, document);
        var build = enrolled.get(clientId);
        if (build == null) {
            throw ProfileRefusal.unverified(name, "the harness '%s' is not enrolled".formatted(clientId));
        }
        if (!record.harness().executable().equals(build.executable())) {
            throw ProfileRefusal.unverified(name,
                    "the record names the executable '%s', the enrolment '%s'".formatted(
                            record.harness().executable(), build.executable()));
        }
        var state = LoadedProfile.State.VERIFIED;
        if (!build.version().equals(record.harness().version())
                || !build.executableSha256().equals(record.harness().executableSha256())) {
            var supported = profile.member("headless")
                    .filter(headless -> !headless.isNull())
                    .flatMap(headless -> headless.member("supported_versions"))
                    .flatMap(JsonNode::text)
                    .map(range -> SupportedVersions.parse(range, name, "/headless/supported_versions"))
                    .orElseThrow(() -> ProfileRefusal.versionUnsupported(name, "/headless",
                            "a machine profile has a headless block with a supported_versions range"));
            if (!supported.contains(build.version())) {
                throw ProfileRefusal.versionUnsupported(name, "/headless/supported_versions",
                        "the enrolled harness reports version %s, outside the supported range".formatted(build.version()));
            }
            state = LoadedProfile.State.VERIFIED_ON_OTHER_BUILD;
        }
        return new LoadedProfile(clientId, kind, profile, document, state);
    }

    private VerificationRecord record(Path directory, String clientId, String name) {
        var file = directory.resolve(VERIFICATION).resolve(clientId + SUFFIX);
        if (!Files.isRegularFile(file)) {
            throw ProfileRefusal.unverified(name, "no verification record at '%s'".formatted(file));
        }
        try {
            return VerificationRecord.read(Files.readAllBytes(file), file.toString());
        } catch (ProfileRefusal refusal) {
            throw ProfileRefusal.unverified(name,
                    "the verification record is refused at '%s': %s".formatted(refusal.pointer(), refusal.detail()));
        } catch (IOException e) {
            throw ProfileRefusal.unverified(name, "the verification record cannot be read: %s".formatted(e.getMessage()));
        }
    }

    private void verifySidecar(Path file, String name, byte[] document) {
        if (!Files.isRegularFile(file)) {
            throw ProfileRefusal.unverified(name, "no HMAC sidecar at '%s'".formatted(file));
        }
        try {
            var sidecar = EnrolmentSidecar.read(file, Files.readAllBytes(file));
            if (!sidecar.matches(document) || !validHmac(document, sidecar.hmac())) {
                throw ProfileRefusal.unverified(name, "the HMAC sidecar does not verify");
            }
        } catch (ProfileRefusal refusal) {
            throw refusal;
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException | IOException e) {
            throw ProfileRefusal.unverified(name, "the HMAC sidecar cannot be read: %s".formatted(e.getMessage()));
        }
    }

    private boolean validHmac(byte[] document, String recorded) {
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            var computed = HexFormat.of().formatHex(mac.doFinal(document));
            return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
                    recorded.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The JDK provides no " + HMAC_ALGORITHM, e);
        }
    }

    private void requireClientId(JsonNode profile, String clientId, String name) {
        var declared = ProfileValidator.clientId(profile);
        if (!declared.equals(clientId)) {
            throw ProfileRefusal.invalid("%s/%s%s".formatted(PROFILES, clientId, SUFFIX), "/client_id",
                    "the document declares '%s', its file name is '%s'".formatted(declared, clientId));
        }
    }

    /** A project profile changes how a harness works, not which harness it is. */
    private void sameAsBase(LoadedProfile project, LoadedProfile base) {
        for (var pointer : List.of("/detection", "/identity", "/headless/executable")) {
            var projectValue = value(project.document(), pointer);
            var baseValue = value(base.document(), pointer);
            if (!CanonicalJson.write(projectValue).equals(CanonicalJson.write(baseValue))) {
                throw ProfileRefusal.invalid(project.clientId() + SUFFIX, pointer,
                        "a project profile has the %s of the machine or shipped profile".formatted(pointer));
            }
        }
    }

    /**
     * Reads a value below a profile whose parent may be JSON {@code null}: a project profile of a harness without a
     * headless block is equal to its base when both are {@code null} at the same pointer.
     */
    private static JsonNode value(JsonNode profile, String pointer) {
        var current = profile;
        for (var token : pointer.substring(1).split("/")) {
            if (current.isNull()) {
                return JsonNode.nullValue();
            }
            var next = current.member(token);
            if (next.isEmpty()) {
                return JsonNode.nullValue();
            }
            current = next.get();
        }
        return current;
    }

    /** Places a profile if no effective profile already uses its detection precedence. */
    private void place(LoadedProfile profile, Map<String, LoadedProfile> effective,
            List<ProfileSet.RefusedProfile> refused) {
        var precedence = precedence(profile);
        for (var current : effective.values()) {
            if (current.clientId().equals(profile.clientId())) {
                continue;
            }
            if (precedence.isPresent() && precedence(current).filter(precedence.get()::equals).isPresent()) {
                refused.add(new ProfileSet.RefusedProfile(profile.clientId(), profile.kind(),
                        ProfileRefusal.invalid(profile.clientId() + SUFFIX, "/detection/precedence",
                                "the precedence %d is used by '%s'".formatted(precedence.get(), current.clientId()))));
                return;
            }
        }
        effective.put(profile.clientId(), profile);
    }

    private static Optional<Long> precedence(LoadedProfile profile) {
        var precedence = profile.document().member("detection")
                .filter(detection -> !detection.isNull())
                .flatMap(detection -> detection.member("precedence"));
        if (precedence.isEmpty()) {
            return Optional.empty();
        }
        var value = precedence.get().integerValue();
        return value.isPresent() ? Optional.of(value.getAsLong()) : Optional.empty();
    }

    private static List<String> clientIds(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (var entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(SUFFIX))
                    .map(name -> name.substring(0, name.length() - SUFFIX.length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("The profile set '%s' cannot be listed".formatted(directory), e);
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw ProfileRefusal.unreadable(file.toString(), "the profile cannot be read: " + e.getMessage());
        }
    }

}
