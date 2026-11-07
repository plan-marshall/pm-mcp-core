/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;

import lombok.experimental.UtilityClass;

/**
 * Reads a numeric limit from a properties resource of this module (PM-IMPL-5).
 * <p>
 * A limit is read when the constant that names it is initialized. A resource that is absent, a key that is absent and
 * a value that is not a non-negative integer are faults of the build and are never defaulted: the load fails with an
 * {@link IllegalStateException} that names the resource and the key.
 *
 * @since 0.1
 */
@UtilityClass
public class LimitProperties {

    /** The classpath location of the limits of a project unit file and of a unit frontmatter. */
    public static final String RESOURCE = "/de/planmarshall/workflow/dsl/unit-limits.properties";

    /** The key of the most bytes a project unit file may hold. */
    public static final String MAX_UNIT_FILE_BYTES = "pm.workflow.unit.max-file-bytes";

    /** The key of the most lines a frontmatter may hold, its two delimiters included. */
    public static final String MAX_FRONTMATTER_LINES = "pm.workflow.unit.max-frontmatter-lines";

    /** The key of the most characters a refusal echoes from the source. */
    public static final String MAX_ECHO_LENGTH = "pm.workflow.unit.max-echo-length";

    /**
     * Reads a non-negative limit from a classpath resource.
     *
     * @param resource the absolute classpath resource that holds the limit
     * @param key      the key of the limit in the resource
     * @return the value
     * @throws IllegalStateException if the resource is absent, the key is absent or the value is not a non-negative
     *                               integer
     */
    public static long read(String resource, String key) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(key, "key");
        var properties = new Properties();
        try (InputStream in = LimitProperties.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("the limit resource '" + resource + "' is absent");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("the limit resource '" + resource + "' cannot be read", e);
        }
        return value(properties, key, resource);
    }

    /**
     * Reads a non-negative limit from properties that are already loaded.
     *
     * @param properties the properties that hold the limit
     * @param key        the key of the limit
     * @param resource   the resource the properties were read from, named in a diagnostic
     * @return the value
     * @throws IllegalStateException if the key is absent or the value is not a non-negative integer
     */
    public static long value(Properties properties, String key, String resource) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resource, "resource");
        var raw = properties.getProperty(key);
        if (raw == null) {
            throw new IllegalStateException("the limit key '" + key + "' is absent from '" + resource + "'");
        }
        long value;
        try {
            value = Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("the limit '" + key + "' of '" + resource + "' is not a number: '"
                    + raw + "'", e);
        }
        if (value < 0) {
            throw new IllegalStateException("the limit '" + key + "' of '" + resource + "' is negative: " + value);
        }
        return value;
    }
}
