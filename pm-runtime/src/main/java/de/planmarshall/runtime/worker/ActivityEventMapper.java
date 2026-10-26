/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileRefusal;
import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityEventMapping;
import de.planmarshall.core.queue.ActivityEventMapping.KindMapping;

/**
 * Maps one line of a worker's output stream onto the closed activity kinds through the harness's
 * {@code headless.events} mapping (Client Profiles § What a Worker Does, Model Work § Output Read While the Worker
 * Runs).
 * <p>
 * A line that is not a JSON object, whose kind pointer names no mapped kind, or whose kind's required field is absent
 * or has the wrong JSON type, matches no mapping and is counted, never shown. A line whose kind matches is mapped
 * whole or not at all, so a half-mapped entry never reaches the journal.
 * <p>
 * The mapper reads only the pointers the profile declares: the tool's name and its target, never the whole argument
 * list, and the size and failure flag of a tool result, never its content. The journal masks what it writes; the mapper
 * never copies an undeclared value.
 *
 * @since 0.1
 */
public final class ActivityEventMapper {

    /** The keys of the usage pointers, in the order the profile declares them. */
    private static final List<String> USAGE_KEYS = List.of("input_tokens", "output_tokens", "cache_read",
            "cache_creation", "reasoning");

    private final ActivityEventMapping mapping;
    private final Map<String, String> usagePointers;

    /**
     * @param mapping       the harness's events mapping; {@code null} for a harness without an event stream
     * @param usagePointers the usage pointers of the profile, by usage column
     */
    public ActivityEventMapper(ActivityEventMapping mapping, Map<String, String> usagePointers) {
        this.mapping = mapping;
        this.usagePointers = Map.copyOf(usagePointers);
    }

    /** @return whether this harness maps any event */
    public boolean mapsEvents() {
        return mapping != null;
    }

    /**
     * @param line the line of the output stream
     * @return the mapped activity, or empty when the line matches no mapping
     */
    public Optional<Mapped> map(String line) {
        if (mapping == null || line == null || line.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = JsonDocument.parse(line.getBytes(StandardCharsets.UTF_8), "activity line");
        } catch (ProfileRefusal _) {
            return Optional.empty();
        }
        if (!root.isObject()) {
            return Optional.empty();
        }
        var kindValue = resolve(root, mapping.kindPointer()).flatMap(JsonNode::text).orElse(null);
        var kind = mapping.kindOf(kindValue).orElse(null);
        if (kind == null) {
            return Optional.empty();
        }
        var declaration = mapping.kinds().get(kind);
        return switch (kind) {
            case TEXT -> text(root, declaration);
            case TOOL_CALL -> toolCall(root, declaration);
            case TOOL_RESULT -> toolResult(root, declaration);
            case FILE_CHANGED -> fileChanged(root, declaration);
            case TURN -> turn(root, declaration);
            default -> Optional.empty();
        };
    }

    private Optional<Mapped> text(JsonNode root, KindMapping declaration) {
        var text = resolve(root, declaration.textPointer()).flatMap(JsonNode::text).orElse(null);
        if (text == null) {
            return Optional.empty();
        }
        var codePoints = text.codePointCount(0, text.length());
        var truncated = codePoints > ActivityEntry.TEXT_BOUND;
        var cut = truncated
                ? text.substring(0, text.offsetByCodePoints(0, ActivityEntry.TEXT_BOUND))
                : text;
        return Optional.of(new Mapped(ActivityEntry.Kind.TEXT, cut, truncated, null, null, null, null, null));
    }

    private Optional<Mapped> toolCall(JsonNode root, KindMapping declaration) {
        var tool = resolve(root, declaration.toolPointer()).flatMap(JsonNode::text).orElse(null);
        if (tool == null || tool.isEmpty() || tool.length() > 128) {
            return Optional.empty();
        }
        String target = null;
        for (var pointer : declaration.targetPointers()) {
            var found = resolve(root, pointer).flatMap(JsonNode::text).orElse(null);
            if (found != null) {
                target = found.length() > 1000 ? found.substring(0, 1000) : found;
                break;
            }
        }
        return Optional.of(new Mapped(ActivityEntry.Kind.TOOL_CALL, null, false,
                new ActivityEntry.Tool(tool, target), null, null, null, null));
    }

    private Optional<Mapped> toolResult(JsonNode root, KindMapping declaration) {
        var sizeNode = resolve(root, declaration.sizePointer()).orElse(null);
        if (sizeNode == null || !sizeNode.isInteger()) {
            return Optional.empty();
        }
        var size = sizeNode.integerValue().orElse(-1);
        if (size < 0 || size > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        var failed = declaration.isErrorPointer() == null
                ? false
                : resolve(root, declaration.isErrorPointer()).flatMap(JsonNode::booleanValue).orElse(false);
        return Optional.of(new Mapped(ActivityEntry.Kind.TOOL_RESULT, null, false, null,
                new ActivityEntry.Result((int) size, failed), null, null, null));
    }

    private Optional<Mapped> fileChanged(JsonNode root, KindMapping declaration) {
        var path = resolve(root, declaration.pathPointer()).flatMap(JsonNode::text).orElse(null);
        if (path == null) {
            return Optional.empty();
        }
        return Optional.of(new Mapped(ActivityEntry.Kind.FILE_CHANGED, null, false, null, null,
                path.length() > 1000 ? path.substring(0, 1000) : path, null, null));
    }

    private Optional<Mapped> turn(JsonNode root, KindMapping declaration) {
        var stopReason = declaration.stopReasonPointer() == null
                ? null
                : resolve(root, declaration.stopReasonPointer()).flatMap(JsonNode::text)
                .map(ActivityEventMapper::stopReason).orElse(null);
        return Optional.of(new Mapped(ActivityEntry.Kind.TURN, null, false, null, null, null,
                stopReason, usage(root)));
    }

    /**
     * Reads the five token components through the profile's usage pointers. A pointer that is absent or names no value
     * is {@code unmeasured}; the profile's own {@code not_applicable} mark is kept.
     */
    private String usage(JsonNode root) {
        var members = new LinkedHashMap<String, JsonNode>();
        for (var key : USAGE_KEYS) {
            var pointer = usagePointers.get(key);
            if (pointer == null) {
                members.put(key, JsonNode.string("unmeasured"));
            } else if ("not_applicable".equals(pointer)) {
                members.put(key, JsonNode.string("not_applicable"));
            } else {
                var value = resolve(root, pointer).orElse(null);
                members.put(key, value == null || value.isNull()
                        ? JsonNode.string("unmeasured")
                        : value.isInteger() ? value : JsonNode.string("unmeasured"));
            }
        }
        return CanonicalJson.write(JsonNode.object(members));
    }

    /**
     * @param value the stop reason the harness reported
     * @return the closed value; {@code other} for any other text
     */
    public static ActivityEntry.StopReason stopReason(String value) {
        return ActivityEntry.StopReason.fromWireName(value).orElse(ActivityEntry.StopReason.OTHER);
    }

    /**
     * Resolves an RFC 6901 JSON pointer against a value.
     *
     * @param root    the value
     * @param pointer the pointer, or {@code null}
     * @return the value the pointer names, or empty
     */
    public static Optional<JsonNode> resolve(JsonNode root, String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return Optional.of(root);
        }
        if (!pointer.startsWith("/")) {
            return Optional.empty();
        }
        var current = root;
        for (var raw : pointer.substring(1).split("/", -1)) {
            var token = raw.replace("~1", "/").replace("~0", "~");
            if (current.isObject()) {
                var next = current.member(token).orElse(null);
                if (next == null) {
                    return Optional.empty();
                }
                current = next;
            } else if (current.isArray()) {
                int index;
                try {
                    index = Integer.parseInt(token);
                } catch (NumberFormatException _) {
                    return Optional.empty();
                }
                var elements = current.elements();
                if (index < 0 || index >= elements.size()) {
                    return Optional.empty();
                }
                current = elements.get(index);
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    /**
     * One line mapped onto the activity kinds, before the journal stamps its sequence, attempt and instant.
     *
     * @param kind      the kind
     * @param text      the cut text; {@code text} only
     * @param truncated whether the text was cut
     * @param tool      the tool call; {@code tool_call} only
     * @param result    the tool result; {@code tool_result} only
     * @param path      the changed path; {@code file_changed} only
     * @param stopReason the stop reason; {@code turn} only
     * @param usage     the usage object as raw JSON text; {@code turn} only
     * @since 0.1
     */
    public record Mapped(ActivityEntry.Kind kind, String text, boolean truncated, ActivityEntry.Tool tool,
    ActivityEntry.Result result, String path, ActivityEntry.StopReason stopReason, String usage) {
    }
}
