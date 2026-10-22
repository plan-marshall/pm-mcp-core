/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Reads the shipped role set from {@code classpath:/roles/}, which the assembly supplies (PM-WORK-1, Role Sets): one
 * document {@code <name>.json} per role, in a directory of the build or in the JAR of the assembly.
 * <p>
 * The directory also holds the release's default assignment ({@code default-assignment.json}, the onboarding's) and the
 * release's qualification records ({@code qualification/<role>.json}), neither of which is a role: the reader skips the
 * reserved file name and every subdirectory. A file whose name is not a role name refuses the read, so no shipped
 * document escapes the build test that validates the set.
 * <p>
 * The reader is a build test's entry and a runtime entry alike: the release compiles the documents into the binary.
 *
 * @since 0.1
 */
public final class ShippedRoles {

    private static final String DIRECTORY = "roles";
    private static final String RESERVED = "default-assignment";
    private static final Pattern ROLE_NAME = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");

    private ShippedRoles() {
    }

    /**
     * @param loader the loader of the resources
     * @return the shipped role set
     * @throws UncheckedIOException if a resource cannot be listed or read
     */
    public static RoleSets read(ClassLoader loader) {
        var documents = new TreeMap<String, byte[]>();
        try {
            var resources = loader.getResources(DIRECTORY);
            while (resources.hasMoreElements()) {
                readResource(resources.nextElement(), documents);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The shipped roles cannot be listed", e);
        }
        var shipped = new LinkedHashMap<String, RoleSets.ShippedRole>();
        for (var entry : documents.entrySet()) {
            shipped.put(entry.getKey(), new RoleSets.ShippedRole(entry.getValue()));
        }
        return RoleSets.of(shipped);
    }

    /** @return the shipped role set of this classpath */
    public static RoleSets read() {
        return read(ShippedRoles.class.getClassLoader());
    }

    private static void readResource(URL directory, Map<String, byte[]> documents) throws IOException {
        if ("file".equals(directory.getProtocol())) {
            try {
                readDirectory(Path.of(directory.toURI()), documents);
            } catch (URISyntaxException e) {
                throw new IOException("Not a file URL: " + directory, e);
            }
        } else if ("jar".equals(directory.getProtocol())) {
            readJar((JarURLConnection) directory.openConnection(), documents);
        }
    }

    private static void readDirectory(Path directory, Map<String, byte[]> documents) throws IOException {
        try (var entries = Files.list(directory)) {
            for (var entry : entries.sorted().toList()) {
                var name = entry.getFileName().toString();
                if (Files.isRegularFile(entry) && name.endsWith(".json")) {
                    add(name, Files.readAllBytes(entry), documents);
                }
            }
        }
    }

    private static void readJar(JarURLConnection connection, Map<String, byte[]> documents) throws IOException {
        var prefix = connection.getEntryName() == null ? DIRECTORY : connection.getEntryName();
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        var jar = connection.getJarFile();
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            var name = entry.getName();
            if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".json")) {
                continue;
            }
            var relative = name.substring(prefix.length());
            if (!relative.contains("/")) {
                add(relative, read(jar, name), documents);
            }
        }
    }

    private static byte[] read(JarFile jar, String name) throws IOException {
        try (InputStream stream = jar.getInputStream(jar.getEntry(name))) {
            return stream.readAllBytes();
        }
    }

    private static void add(String fileName, byte[] document, Map<String, byte[]> documents) {
        var name = fileName.substring(0, fileName.length() - ".json".length());
        if (RESERVED.equals(name)) {
            return;
        }
        if (!ROLE_NAME.matcher(name).matches()) {
            throw new IllegalStateException("The shipped role file '%s' is not named by a role name".formatted(fileName));
        }
        documents.put(name, document);
    }
}
