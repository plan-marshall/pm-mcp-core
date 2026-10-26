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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.profile.JsonNode;

/**
 * The mapping of a line of a worker's output stream onto the closed activity kinds (Client Profiles § What a Worker
 * Does): the {@code headless.events} block of the harness's client profile, projected so that the runtime never reads
 * the profile document itself. A {@code null} mapping is a harness whose output gives no event stream, whose tasks show
 * only the server's own entries.
 * <p>
 * The profile admits an object per kind, each with the values of {@code kind_pointer} that select it ({@code match})
 * and the pointers of the fields that kind carries. The pointers are RFC 6901 JSON pointers into the line.
 * <p>
 * The projection is total for a profile the validator admitted: the loader has already checked the shape, so a missing
 * member means the profile was not loaded through {@link de.planmarshall.core.profile.ProfileValidator}. The projection
 * refuses such a document rather than inventing a mapping.
 *
 * @param kindPointer the pointer to the value that tells a line's kind
 * @param kinds       the mapping of each activity kind; a kind absent has no mapping
 * @since 0.1
 */
public record ActivityEventMapping(String kindPointer, Map<ActivityEntry.Kind, KindMapping> kinds) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the kind pointer is not a JSON pointer or a kind occurs twice
     */
    public ActivityEventMapping {
        Objects.requireNonNull(kindPointer, "kindPointer");
        if (!kindPointer.startsWith("/")) {
            throw new IllegalArgumentException("The kind pointer is not a JSON pointer: " + kindPointer);
        }
        kinds = Collections.unmodifiableMap(new EnumMap<>(Objects.requireNonNull(kinds, "kinds")));
    }

    /**
     * @param events the {@code headless.events} value of a profile
     * @return the mapping, or {@code null} when the value is {@code null}
     * @throws IllegalArgumentException if the value is not the object {@code ProfileValidator} admitted
     */
    public static ActivityEventMapping of(JsonNode events) {
        Objects.requireNonNull(events, "events");
        if (events.isNull()) {
            return null;
        }
        var kindPointer = text(events, "kind_pointer");
        var kinds = events.member("kinds").orElseThrow(
                () -> new IllegalArgumentException("The events block has no kinds"));
        var mapped = new LinkedHashMap<ActivityEntry.Kind, KindMapping>();
        for (var kind : ActivityEntry.Kind.values()) {
            if (kind.source() != ActivityEntry.Source.HARNESS) {
                continue;
            }
            var declaration = kinds.member(kind.wireName()).orElse(null);
            if (declaration == null || declaration.isNull()) {
                continue;
            }
            mapped.put(kind, kindMapping(kind, declaration));
        }
        return new ActivityEventMapping(kindPointer, mapped);
    }

    private static KindMapping kindMapping(ActivityEntry.Kind kind, JsonNode declaration) {
        var match = new ArrayList<String>();
        for (var element : declaration.member("match").orElseThrow().elements()) {
            match.add(element.text().orElseThrow(
                    () -> new IllegalArgumentException("A match value is not a string")));
        }
        return switch (kind) {
            case TEXT -> new KindMapping(match, text(declaration, "text"), null, List.of(), null, null, null, null);
            case TOOL_CALL -> new KindMapping(match, null, text(declaration, "tool"),
                    array(declaration, "target"), null, null, null, null);
            case TOOL_RESULT -> new KindMapping(match, null, null, List.of(), text(declaration, "size"),
                    optionalText(declaration, "is_error"), null, null);
            case FILE_CHANGED -> new KindMapping(match, null, null, List.of(), null, null,
                    text(declaration, "path"), null);
            case TURN -> new KindMapping(match, null, null, List.of(), null, null, null,
                    optionalText(declaration, "stop_reason"));
            default -> throw new IllegalArgumentException("Not a harness kind: " + kind);
        };
    }

    /**
     * @param value the value of the kind pointer of a line
     * @return the kind that value selects, if any
     */
    public Optional<ActivityEntry.Kind> kindOf(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return kinds.entrySet().stream()
                .filter(entry -> entry.getValue().match().contains(value))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static String text(JsonNode node, String member) {
        return node.member(member).flatMap(JsonNode::text)
                .orElseThrow(() -> new IllegalArgumentException("The events block has no '" + member + "'"));
    }

    private static String optionalText(JsonNode node, String member) {
        var value = node.member(member).orElse(null);
        return value == null || value.isNull() ? null : value.text().orElse(null);
    }

    private static List<String> array(JsonNode node, String member) {
        var values = new ArrayList<String>();
        for (var element : node.member(member).orElseThrow().elements()) {
            values.add(element.text().orElseThrow(
                    () -> new IllegalArgumentException("A '" + member + "' value is not a string")));
        }
        return List.copyOf(values);
    }

    /**
     * The mapping of one activity kind.
     *
     * @param match             the values of the kind pointer that select this kind
     * @param textPointer       the pointer to the text; {@code text} only
     * @param toolPointer       the pointer to the tool's name; {@code tool_call} only
     * @param targetPointers    the pointers tried in order for the target; {@code tool_call} only
     * @param sizePointer       the pointer to the result's size; {@code tool_result} only
     * @param isErrorPointer    the pointer to whether the call failed; {@code tool_result} only, may be {@code null}
     * @param pathPointer       the pointer to the changed path; {@code file_changed} only
     * @param stopReasonPointer the pointer to the turn's stop reason; {@code turn} only, may be {@code null}
     * @since 0.1
     */
    public record KindMapping(List<String> match, String textPointer, String toolPointer,
    List<String> targetPointers, String sizePointer, String isErrorPointer, String pathPointer,
    String stopReasonPointer) {

        /** Copies the lists. */
        public KindMapping {
            match = List.copyOf(Objects.requireNonNull(match, "match"));
            targetPointers = List.copyOf(Objects.requireNonNull(targetPointers, "targetPointers"));
        }
    }
}
