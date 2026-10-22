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

/**
 * A JSON Pointer of RFC 6901, resolved against a {@link JsonNode} (PM-CLIENT-5): the spelling a profile uses for a
 * pointer into a line of the harness output and for the failing value in a refusal.
 * <p>
 * A pointer is either the empty string (the whole document) or a sequence of reference tokens, each introduced by
 * {@code /}, with {@code ~1} standing for {@code /} and {@code ~0} for {@code ~}. An object token selects a member; an
 * array token is its index in decimal without a leading zero. A pointer that escapes the document, names a member of
 * something that is not an object, or has an index that is not a number is a refusal.
 *
 * @since 0.1
 */
public final class JsonPointer {

    private JsonPointer() {
    }

    /**
     * @param root     the document
     * @param pointer  the pointer, RFC 6901
     * @param document the document, as a refusal names it
     * @return the value the pointer selects
     * @throws ProfileRefusal if the pointer has no such value
     */
    public static JsonNode resolve(JsonNode root, String pointer, String document) {
        if (pointer == null) {
            throw ProfileRefusal.invalid(document, "", "a JSON Pointer is not null");
        }
        var current = root;
        if (pointer.isEmpty()) {
            return current;
        }
        if (!pointer.startsWith("/")) {
            throw ProfileRefusal.invalid(document, "", "not a JSON Pointer: it must be empty or start with '/'");
        }
        var tokens = pointer.substring(1).split("/", -1);
        var path = new StringBuilder();
        for (var token : tokens) {
            var name = token.replace("~1", "/").replace("~0", "~");
            path.append('/').append(name);
            var shown = path.toString();
            if (current.isObject()) {
                current = current.member(name).orElseThrow(() -> ProfileRefusal.invalid(document, pointer,
                        "the pointer does not resolve at '%s'".formatted(shown)));
            } else if (current.isArray()) {
                int index;
                try {
                    index = Integer.parseInt(name);
                } catch (NumberFormatException _) {
                    throw ProfileRefusal.invalid(document, pointer,
                            "the pointer has an array index that is not a number at '%s'".formatted(shown));
                }
                var elements = current.elements();
                if (index < 0 || index >= elements.size()) {
                    throw ProfileRefusal.invalid(document, pointer,
                            "the pointer does not resolve at '%s': index %d of %d".formatted(shown, index,
                                    elements.size()));
                }
                current = elements.get(index);
            } else {
                throw ProfileRefusal.invalid(document, pointer,
                        "the pointer descends into a value that is not an object or array at '%s'".formatted(shown));
            }
        }
        return current;
    }

    /**
     * @param parent the pointer of an object or array
     * @param token  the member name or index
     * @return the pointer of the member or element
     */
    static String child(String parent, String token) {
        return parent + "/" + token.replace("~", "~0").replace("/", "~1");
    }

}
