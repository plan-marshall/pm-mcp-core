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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import de.planmarshall.core.store.CapabilitySpec.Requirement;

/**
 * The project's capability specs, {@code .marshall/capabilities/*.json} (Store Schemas § Capability Spec; Phase
 * Workflows § Capability Specs): the capabilities of the tree the plan started from, as the planning roles read them
 * in {@code facts.capability_index}.
 * <p>
 * The index is also the {@link IntentAcceptance.CapabilityFacts} of an acceptance: a criterion that adds a requirement
 * is refused when the requirement exists, and one that modifies or removes it is refused when the tree's fingerprint
 * is not the {@code base} it names.
 *
 * @since 0.1
 */
public final class CapabilityIndex implements IntentAcceptance.CapabilityFacts {

    private static final String DIRECTORY = ".marshall/capabilities";

    private static final String SUFFIX = ".json";

    private final Map<String, CapabilitySpec> capabilities;

    private CapabilityIndex(Map<String, CapabilitySpec> capabilities) {
        this.capabilities = Map.copyOf(new LinkedHashMap<>(capabilities));
    }

    /**
     * @param projectRoot the root of the tree
     * @return the index of the tree's capability specs; empty when it holds none
     * @throws StoreRefusal         if a spec is in another format version or unreadable
     * @throws UncheckedIOException if a spec cannot be read
     */
    public static CapabilityIndex read(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        var directory = projectRoot.resolve(DIRECTORY);
        if (!Files.isDirectory(directory)) {
            return new CapabilityIndex(Map.of());
        }
        var capabilities = new LinkedHashMap<String, CapabilitySpec>();
        try (Stream<Path> files = Files.list(directory)) {
            for (var file : files.filter(path -> path.getFileName().toString().endsWith(SUFFIX)).sorted().toList()) {
                var spec = CapabilitySpec.read(file, Files.readAllBytes(file));
                var name = file.getFileName().toString().substring(0, file.getFileName().toString().length()
                        - SUFFIX.length());
                if (!spec.capability().equals(name)) {
                    throw StoreRefusal.unreadable(file, CapabilitySpec.FORMAT_VERSION,
                            "the spec names the capability '%s', but its file is '%s'".formatted(spec.capability(), name),
                            null);
                }
                capabilities.put(spec.capability(), spec);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The capability specs cannot be listed", e);
        }
        return new CapabilityIndex(capabilities);
    }

    /**
     * @param capability  the capability name
     * @param requirement the requirement name
     * @return the requirement's fingerprint; empty when the capability or requirement does not exist
     */
    @Override
    public Optional<String> fingerprint(String capability, String requirement) {
        return Optional.ofNullable(capabilities.get(capability)).flatMap(spec -> spec.fingerprint(requirement));
    }

    /**
     * @param capability a capability name
     * @return the spec; empty when the tree holds no such capability
     */
    public Optional<CapabilitySpec> capability(String capability) {
        return Optional.ofNullable(capabilities.get(capability));
    }

    /**
     * @return the capabilities by their names, in file order
     */
    public Map<String, CapabilitySpec> capabilities() {
        return capabilities;
    }

    /**
     * @return the index as the planning roles receive it in {@code facts.capability_index}: each capability with its
     *         purpose and its requirement names
     */
    public List<Map<String, Object>> facts() {
        return capabilities.values().stream()
                .map(spec -> Map.<String, Object>of("capability", spec.capability(), "purpose", spec.purpose(),
                        "requirements", spec.requirements().stream().map(Requirement::name).toList()))
                .toList();
    }
}
