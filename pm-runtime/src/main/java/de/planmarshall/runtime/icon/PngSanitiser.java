/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.icon;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.CRC32;
import lombok.experimental.UtilityClass;

/**
 * The PNG half of the icon accept step (Harness Profiles § Harness Icons; Store Schemas § Harness Icon Store): the
 * signature, the chunk structure and the stripping of the text chunks, so a stored image carries no free text.
 * <p>
 * The bytes are walked as the format defines them: eight signature bytes, then chunks of a length, a four-letter type,
 * the data and a CRC-32 over the type and the data. The first chunk must be {@code IHDR} with a positive width and
 * height, the last must be {@code IEND}, and no byte may follow it. A critical chunk the format does not define, a
 * broken checksum or a truncated chunk is refused. The chunks that carry text ({@code tEXt}, {@code zTXt}, {@code
 * iTXt}) are dropped; every other chunk is copied with its own checksum, so the image is unchanged.
 *
 * @since 0.1
 */
@UtilityClass
public class PngSanitiser {

    /** The PNG signature, the wire format's contract. */
    static final byte[] SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final int CHUNK_OVERHEAD = 12;
    private static final int HEADER_LENGTH = 13;
    private static final Set<String> CRITICAL = Set.of("IHDR", "PLTE", "IDAT", "IEND");
    private static final Set<String> TEXT = Set.of("tEXt", "zTXt", "iTXt");

    /**
     * @param candidate arbitrary bytes
     * @return whether the bytes begin with the PNG signature
     */
    static boolean looksLikePng(byte[] candidate) {
        if (candidate.length < SIGNATURE.length) {
            return false;
        }
        for (var index = 0; index < SIGNATURE.length; index++) {
            if (candidate[index] != SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param candidate a PNG image, led by its signature
     * @return the image with its text chunks stripped
     * @throws IconRefusal if the signature, the chunk structure, a checksum or the boundaries are not those of a PNG
     */
    static byte[] sanitise(byte[] candidate) {
        var stripped = new ByteArrayOutputStream(candidate.length);
        stripped.writeBytes(SIGNATURE);
        var position = SIGNATURE.length;
        var sawHeader = false;
        var sawEnd = false;
        while (position < candidate.length) {
            if (sawEnd) {
                throw malformed("bytes follow the end chunk");
            }
            if (candidate.length - position < CHUNK_OVERHEAD) {
                throw malformed("a chunk header is truncated");
            }
            var length = readInt(candidate, position);
            if (length < 0 || length > candidate.length - position - CHUNK_OVERHEAD) {
                throw malformed("a chunk length exceeds the document");
            }
            var type = new String(candidate, position + 4, 4, StandardCharsets.US_ASCII);
            if (!isTypeName(type)) {
                throw malformed("a chunk type is not four ASCII letters");
            }
            if (!CRITICAL.contains(type) && Character.isUpperCase(type.charAt(0))) {
                throw malformed("an unknown critical chunk");
            }
            var storedCrc = readInt(candidate, position + 8 + length);
            if (storedCrc != crc32(candidate, position + 4, 4 + length)) {
                throw malformed("a chunk checksum differs");
            }
            if (!sawHeader) {
                if (!"IHDR".equals(type) || length != HEADER_LENGTH) {
                    throw malformed("the first chunk is not the header");
                }
                requirePositiveSize(candidate, position + 8);
                sawHeader = true;
            }
            if ("IEND".equals(type)) {
                if (length != 0) {
                    throw malformed("the end chunk carries data");
                }
                sawEnd = true;
            }
            if (!TEXT.contains(type)) {
                stripped.write(candidate, position, CHUNK_OVERHEAD + length);
            }
            position += CHUNK_OVERHEAD + length;
        }
        if (!sawHeader || !sawEnd) {
            throw malformed("the header or the end chunk is missing");
        }
        return stripped.toByteArray();
    }

    private static void requirePositiveSize(byte[] candidate, int headerData) {
        if (readInt(candidate, headerData) < 1 || readInt(candidate, headerData + 4) < 1) {
            throw malformed("the image size is not positive");
        }
    }

    private static boolean isTypeName(String type) {
        for (var index = 0; index < type.length(); index++) {
            var character = type.charAt(index);
            if (!((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z'))) {
                return false;
            }
        }
        return type.length() == 4;
    }

    private static int readInt(byte[] bytes, int position) {
        return (bytes[position] & 0xFF) << 24 | (bytes[position + 1] & 0xFF) << 16
                | (bytes[position + 2] & 0xFF) << 8 | bytes[position + 3] & 0xFF;
    }

    private static int crc32(byte[] bytes, int offset, int length) {
        var crc = new CRC32();
        crc.update(bytes, offset, length);
        return (int) crc.getValue();
    }

    private static IconRefusal malformed(String reason) {
        return new IconRefusal(IconRefusal.PNG_MALFORMED, "Not a PNG image: " + reason);
    }
}
