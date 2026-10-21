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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The supported version range of a harness CLI, {@code headless.supported_versions} (PM-CLIENT-5, Update Evidence).
 * <p>
 * A range is one or more comparators separated by whitespace, each a version or one of {@code >=}, {@code >},
 * {@code <=}, {@code <} and {@code =} followed by a version, for example {@code >=2.1.0 <3.0.0}. A version is three
 * dot-separated numbers with an optional {@code -suffix}. A range is valid when every comparator parses and the
 * comparators leave at least one version in the range, which rejects {@code >2.0.0 <1.0.0} as well as a misspelled
 * comparator.
 *
 * @since 0.1
 */
public final class SupportedVersions {

    private static final Pattern VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9.-]+)?$");
    private static final Pattern COMPARATOR = Pattern.compile("^(>=|<=|>|<|=)?(.*)$");

    private final List<Bound> bounds;

    private SupportedVersions(List<Bound> bounds) {
        this.bounds = List.copyOf(bounds);
    }

    /**
     * @param range    the range, as {@code headless.supported_versions} spells it
     * @param document the profile, as a refusal names it
     * @param pointer  the JSON Pointer of the range
     * @return the range
     * @throws ProfileRefusal if the range is empty, a comparator does not parse, or no version lies in it
     */
    public static SupportedVersions parse(String range, String document, String pointer) {
        Objects.requireNonNull(range, "range");
        var text = range.strip();
        if (text.isEmpty()) {
            throw ProfileRefusal.invalid(document, pointer, "a version range is not empty");
        }
        var bounds = new ArrayList<Bound>();
        for (var token : text.split("\\s+")) {
            var comparator = COMPARATOR.matcher(token);
            if (!comparator.matches()) {
                throw ProfileRefusal.invalid(document, pointer, "not a version comparator: '%s'".formatted(token));
            }
            var operator = comparator.group(1) == null ? "=" : comparator.group(1);
            var version = version(comparator.group(2), document, pointer, token);
            bounds.add(new Bound(operator, version));
        }
        var rangeValue = new SupportedVersions(bounds);
        if (!rangeValue.hasAnyVersion()) {
            throw ProfileRefusal.invalid(document, pointer, "the range '%s' contains no version".formatted(range));
        }
        return rangeValue;
    }

    /**
     * @param version the version the enrolled harness reports
     * @return whether the version lies in this range
     * @throws IllegalArgumentException if the version is not a version
     */
    public boolean contains(String version) {
        var candidate = version(version, "", "", version);
        return bounds.stream().allMatch(bound -> bound.accepts(candidate));
    }

    /** @return whether at least one version of the lowest and highest bounds lies in the range */
    private boolean hasAnyVersion() {
        Version low = new Version(0, 0, 0, null);
        Version high = new Version(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, null);
        for (var bound : bounds) {
            low = bound.raise(low);
            high = bound.lower(high);
            if (low == null || high == null) {
                return false;
            }
        }
        return low.compareTo(high) <= 0;
    }

    private static Version version(String text, String document, String pointer, String token) {
        if (!VERSION.matcher(text).matches()) {
            throw ProfileRefusal.invalid(document, pointer, "not a version: '%s' in '%s'".formatted(text, token));
        }
        var main = text.split("-", 2);
        var numbers = main[0].split("\\.");
        return new Version(Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]), Integer.parseInt(numbers[2]),
                main.length == 2 ? main[1] : null);
    }

    private record Bound(String operator, Version version) {

        private boolean accepts(Version candidate) {
            var comparison = candidate.compareTo(version);
            return switch (operator) {
                case ">=" -> comparison >= 0;
                case ">" -> comparison > 0;
                case "<=" -> comparison <= 0;
                case "<" -> comparison < 0;
                default -> comparison == 0;
            };
        }

        private Version raise(Version low) {
            return switch (operator) {
                case ">=", "=" -> version.compareTo(low) > 0 ? version : low;
                case ">" -> {
                    var next = version.next();
                    yield next == null ? null : (next.compareTo(low) > 0 ? next : low);
                }
                default -> low;
            };
        }

        private Version lower(Version high) {
            return switch (operator) {
                case "<=", "=" -> version.compareTo(high) < 0 ? version : high;
                case "<" -> {
                    var previous = version.previous();
                    yield previous == null ? null : (previous.compareTo(high) < 0 ? previous : high);
                }
                default -> high;
            };
        }
    }

    private record Version(int major, int minor, int patch, String suffix) implements Comparable<Version> {

        private Version next() {
            return suffix == null ? new Version(major, minor, patch + 1, null)
                    : new Version(major, minor, patch, null);
        }

        private Version previous() {
            if (suffix != null) {
                return new Version(major, minor, patch, null);
            }
            if (patch > 0) {
                return new Version(major, minor, patch - 1, null);
            }
            if (minor > 0) {
                return new Version(major, minor - 1, Integer.MAX_VALUE, null);
            }
            if (major > 0) {
                return new Version(major - 1, Integer.MAX_VALUE, Integer.MAX_VALUE, null);
            }
            return null;
        }

        @Override
        public int compareTo(Version other) {
            var comparison = Integer.compare(major, other.major);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(minor, other.minor);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(patch, other.patch);
            if (comparison != 0) {
                return comparison;
            }
            if (suffix == null || other.suffix == null) {
                return suffix == null ? (other.suffix == null ? 0 : 1) : -1;
            }
            return suffix.compareTo(other.suffix);
        }
    }
}
