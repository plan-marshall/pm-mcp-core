/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileRefusal;
import de.planmarshall.core.role.RoleSkill;

/**
 * The navigation figures of a task, derived from its activity journal and its records (Model Work § Navigation Figures):
 * what the work cost to navigate beside what it cost in tokens. Every figure carries its population, and a figure whose
 * population cannot be read is {@code indeterminate} — never {@code 0}.
 * <p>
 * The figures are evidence for comparing a skill or a role configuration, never a limit: nothing gates on them.
 *
 * @param cacheReadShare   the cache-read tokens over the context tokens of the task's turns
 * @param revisits         the reads of a file already read in the same task over all its reads
 * @param languageServerUse the language-server reads over all code lookups
 * @param fallbackToReadRate the language-server lookups followed by a whole-file read of the file they answered with,
 *                           over all lookups
 * @param residency        the residency of each required skill, in the order the task names them
 * @since 0.1
 */
public record NavigationFigures(Figure cacheReadShare, Figure revisits, Figure languageServerUse,
Figure fallbackToReadRate, List<SkillResidency> residency) {

    /** The name of the revisits figure. */
    private static final String REVISITS_FIGURE = "revisits";

    /** The name of the cache-read share figure. */
    private static final String CACHE_READ_SHARE = "cache_read_share";

    /** Copies the residency list. */
    public NavigationFigures {
        Objects.requireNonNull(cacheReadShare, "cacheReadShare");
        Objects.requireNonNull(revisits, REVISITS_FIGURE);
        Objects.requireNonNull(languageServerUse, "languageServerUse");
        Objects.requireNonNull(fallbackToReadRate, "fallbackToReadRate");
        residency = List.copyOf(Objects.requireNonNull(residency, "residency"));
    }

    /**
     * @param entries     the task's activity journal
     * @param vocabulary  the harness's tool names
     * @param required    the skills the task requires, in delivery order
     * @param inBandBytes the bytes delivered in-band per skill URI for the generation
     * @return the figures
     */
    public static NavigationFigures derive(List<ActivityEntry> entries, NavigationVocabulary vocabulary,
            List<RoleSkill> required, Map<String, Long> inBandBytes) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(vocabulary, "vocabulary");
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(inBandBytes, "inBandBytes");
        return new NavigationFigures(cacheReadShare(entries), revisits(entries, vocabulary),
                languageServerUse(entries, vocabulary), fallbackToReadRate(entries, vocabulary),
                residency(entries, vocabulary, required, inBandBytes));
    }

    /** {@code cache_read / (input + cache_read + cache_creation)} over the turns that report all three. */
    static Figure cacheReadShare(List<ActivityEntry> entries) {
        long cacheRead = 0;
        long context = 0;
        var turns = 0;
        var readable = 0;
        var counted = 0;
        for (var entry : entries) {
            if (entry.kind() == ActivityEntry.Kind.TURN) {
                turns++;
                if (readableUsage(entry.usage())) {
                    readable++;
                }
                var share = cacheShareOf(entry);
                if (share.isPresent()) {
                    cacheRead += share.get()[0];
                    context += share.get()[1];
                    counted++;
                }
            }
        }
        if (turns == 0 || readable == 0) {
            return Figure.indeterminate(CACHE_READ_SHARE);
        }
        if (counted == 0 || context == 0) {
            return Figure.unmeasured(CACHE_READ_SHARE);
        }
        return Figure.measured(CACHE_READ_SHARE, (double) cacheRead / context, counted);
    }

    /** @return the cache reads and the context tokens of one turn, or empty when the turn is no full measurement */
    private static Optional<long[]> cacheShareOf(ActivityEntry entry) {
        if (entry.kind() != ActivityEntry.Kind.TURN || entry.usage() == null) {
            return Optional.empty();
        }
        var usage = usage(entry.usage());
        var input = measured(usage, "input_tokens");
        var read = reportedInteger(usage, "cache_read");
        var creation = measured(usage, "cache_creation");
        if (input.isEmpty() || read.isEmpty() || creation.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new long[]{read.get(), input.get() + read.get() + creation.get()});
    }

    /** @return whether a turn's usage text is a readable object; a malformed or empty one is not */
    private static boolean readableUsage(String raw) {
        return raw != null && !usage(raw).members().isEmpty();
    }

    /** The reads of a file already read earlier in the task over all its reads. */
    static Figure revisits(List<ActivityEntry> entries, NavigationVocabulary vocabulary) {
        var seen = new LinkedHashSet<String>();
        var reads = 0;
        var revisited = 0;
        for (var entry : entries) {
            if (isReadOfAFile(entry, vocabulary)) {
                reads++;
                if (!seen.add(entry.tool().target())) {
                    revisited++;
                }
            }
        }
        return reads == 0 ? Figure.indeterminate(REVISITS_FIGURE)
                : Figure.measured(REVISITS_FIGURE, (double) revisited / reads, reads);
    }

    private static boolean isReadOfAFile(ActivityEntry entry, NavigationVocabulary vocabulary) {
        return entry.kind() == ActivityEntry.Kind.TOOL_CALL && entry.tool() != null
                && entry.tool().target() != null
                && vocabulary.fileReadTools().contains(entry.tool().name());
    }

    /** {@code pm_lsp} reads over {@code pm_lsp} reads plus the harness's own search calls. */
    static Figure languageServerUse(List<ActivityEntry> entries, NavigationVocabulary vocabulary) {
        var lookups = lookups(entries, vocabulary);
        var searches = callsOf(entries, vocabulary.searchTools());
        var all = lookups.size() + searches;
        return all == 0 ? Figure.indeterminate("language_server_use")
                : Figure.measured("language_server_use", (double) lookups.size() / all, all);
    }

    /** The lookups followed by a whole-file read of the file they answered with, over all lookups. */
    static Figure fallbackToReadRate(List<ActivityEntry> entries, NavigationVocabulary vocabulary) {
        var lookups = 0;
        var followed = 0;
        for (var index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            if (isLookup(entry, vocabulary)) {
                lookups++;
                if (followedByWholeFileRead(entries, index, entry, vocabulary)) {
                    followed++;
                }
            }
        }
        return lookups == 0 ? Figure.indeterminate("fallback_to_read_rate")
                : Figure.measured("fallback_to_read_rate", (double) followed / lookups, lookups);
    }

    private static boolean followedByWholeFileRead(List<ActivityEntry> entries, int index, ActivityEntry lookup,
            NavigationVocabulary vocabulary) {
        for (var later = index + 1; later < entries.size(); later++) {
            var candidate = entries.get(later);
            if (candidate.kind() == ActivityEntry.Kind.TOOL_CALL && candidate.tool() != null
                    && candidate.tool().target() != null
                    && vocabulary.wholeFileReadTools().contains(candidate.tool().name())
                    && candidate.tool().target().equals(lookup.tool().target())) {
                return true;
            }
        }
        return false;
    }

    /** Per required skill the bytes delivered in-band and the sections read on demand. */
    static List<SkillResidency> residency(List<ActivityEntry> entries, NavigationVocabulary vocabulary,
            List<RoleSkill> required, Map<String, Long> inBandBytes) {
        var sectionsBySkill = new LinkedHashMap<String, List<String>>();
        for (var entry : entries) {
            var skillRead = skillRead(entry, vocabulary);
            if (skillRead.length > 0) {
                sectionsBySkill.computeIfAbsent(skillRead[0], ignored -> new ArrayList<>()).add(skillRead[1]);
            }
        }
        var residency = new ArrayList<SkillResidency>();
        for (var skill : required) {
            var sections = sectionsBySkill.getOrDefault(skill.uri(), List.of());
            residency.add(new SkillResidency(skill.uri(), inBandBytes.getOrDefault(skill.uri(), 0L),
                    sections));
        }
        return List.copyOf(residency);
    }

    /** @return the skill URI and the section of one on-demand read, or an empty array for any other entry */
    private static String[] skillRead(ActivityEntry entry, NavigationVocabulary vocabulary) {
        if (entry.kind() != ActivityEntry.Kind.TOOL_CALL || entry.tool() == null
                || !vocabulary.skillTool().equals(entry.tool().name()) || entry.tool().target() == null) {
            return new String[0];
        }
        var target = entry.tool().target();
        var separator = target.indexOf('#');
        var section = separator < 0 ? "" : target.substring(separator + 1);
        return section.isEmpty() ? new String[0] : new String[]{target.substring(0, separator), section};
    }

    private static List<ActivityEntry> lookups(List<ActivityEntry> entries, NavigationVocabulary vocabulary) {
        return entries.stream().filter(entry -> isLookup(entry, vocabulary)).toList();
    }

    private static boolean isLookup(ActivityEntry entry, NavigationVocabulary vocabulary) {
        return entry.kind() == ActivityEntry.Kind.TOOL_CALL && entry.tool() != null
                && vocabulary.languageServerTool().equals(entry.tool().name())
                && entry.tool().target() != null;
    }

    private static long callsOf(List<ActivityEntry> entries, Set<String> tools) {
        return entries.stream()
                .filter(entry -> entry.kind() == ActivityEntry.Kind.TOOL_CALL && entry.tool() != null
                        && tools.contains(entry.tool().name()))
                .count();
    }

    private static Optional<Long> measured(JsonNode usage, String key) {
        var value = usage.member(key).orElse(null);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (value.isInteger()) {
            var count = value.integerValue().orElse(0);
            return count < 0 ? Optional.empty() : Optional.of(count);
        }
        if (value.isString() && "not_applicable".equals(value.text().orElse(null))) {
            return Optional.of(0L);
        }
        return Optional.empty();
    }

    /**
     * Reads a cache column: {@code not_applicable} is the harness reporting no cache reads, which makes the share
     * {@code unmeasured} rather than a measured zero (Model Work § Navigation Figures). A negative count is no
     * measurement.
     */
    private static Optional<Long> reportedInteger(JsonNode usage, String key) {
        var value = usage.member(key).orElse(null);
        if (value == null || value.isNull() || !value.isInteger()) {
            return Optional.empty();
        }
        var count = value.integerValue().orElse(0);
        return count < 0 ? Optional.empty() : Optional.of(count);
    }

    private static JsonNode usage(String raw) {
        try {
            return JsonDocument.parse(raw.getBytes(StandardCharsets.UTF_8), "usage");
        } catch (ProfileRefusal _) {
            return JsonNode.object(Map.of());
        }
    }
}
