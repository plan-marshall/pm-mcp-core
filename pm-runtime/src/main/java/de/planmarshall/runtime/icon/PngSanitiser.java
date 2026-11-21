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
 * signature, the chunk structure and the stripping of the chunks that carry free text, so a stored image carries none.
 * <p>
 * The bytes are walked as the format defines them: eight signature bytes, then chunks of a length, a four-letter type,
 * the data and a CRC-32 over the type and the data. The first chunk must be {@code IHDR} with a positive width and
 * height, at least one {@code IDAT} must carry the image, the last chunk must be {@code IEND}, and no byte may follow
 * it. A critical chunk the format does not define, a broken checksum or a truncated chunk is refused. The chunks that
 * carry free text ({@code tEXt}, {@code zTXt}, {@code iTXt}) and the {@code eXIf} chunk, whose description and comment
 * fields carry free text, are dropped; every other chunk is copied with its own checksum, so the image is unchanged.
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
    private static final Set<String> STRIPPED = Set.of("tEXt", "zTXt", "iTXt", "eXIf");

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
     * @return the image with the chunks that carry free text stripped
     * @throws IconRefusal if the signature, the chunk structure, a checksum or the boundaries are not those of a PNG
     */
    static byte[] sanitise(byte[] candidate) {
        var stripped = new ByteArrayOutputStream(candidate.length);
        stripped.writeBytes(SIGNATURE);
        new Walk(candidate, stripped).run();
        return stripped.toByteArray();
    }

    /** One walk over a PNG document: the bytes, the stripped output and the chunks seen so far. */
    private static final class Walk {

        private final byte[] candidate;
        private final ByteArrayOutputStream stripped;
        private int position = SIGNATURE.length;
        private boolean header;
        private boolean data;
        private boolean end;

        private Walk(byte[] candidate, ByteArrayOutputStream stripped) {
            this.candidate = candidate;
            this.stripped = stripped;
        }

        private void run() {
            while (position < candidate.length) {
                step();
            }
            if (!header || !data || !end) {
                throw malformed("the header, the image data or the end chunk is missing");
            }
        }

        private void step() {
            if (end) {
                throw malformed("bytes follow the end chunk");
            }
            var length = requireChunk();
            var type = requireType();
            requireKnown(type);
            requireChecksum(length);
            observe(type, length);
            if (!STRIPPED.contains(type)) {
                stripped.write(candidate, position, CHUNK_OVERHEAD + length);
            }
            position += CHUNK_OVERHEAD + length;
        }

        private int requireChunk() {
            if (candidate.length - position < CHUNK_OVERHEAD) {
                throw malformed("a chunk header is truncated");
            }
            var length = readInt(candidate, position);
            if (length < 0 || length > candidate.length - position - CHUNK_OVERHEAD) {
                throw malformed("a chunk length exceeds the document");
            }
            return length;
        }

        private String requireType() {
            var type = new String(candidate, position + 4, 4, StandardCharsets.US_ASCII);
            if (!isTypeName(type)) {
                throw malformed("a chunk type is not four ASCII letters");
            }
            return type;
        }

        private void requireKnown(String type) {
            if (!CRITICAL.contains(type) && Character.isUpperCase(type.charAt(0))) {
                throw malformed("an unknown critical chunk");
            }
        }

        private void requireChecksum(int length) {
            if (readInt(candidate, position + 8 + length) != crc32(candidate, position + 4, 4 + length)) {
                throw malformed("a chunk checksum differs");
            }
        }

        private void observe(String type, int length) {
            if (!header) {
                requireHeader(type, length);
                header = true;
            }
            if ("IDAT".equals(type)) {
                data = true;
            }
            if ("IEND".equals(type)) {
                requireEnd(length);
                end = true;
            }
        }

        private void requireHeader(String type, int length) {
            if (!"IHDR".equals(type) || length != HEADER_LENGTH) {
                throw malformed("the first chunk is not the header");
            }
            if (readInt(candidate, position + 8) < 1 || readInt(candidate, position + 12) < 1) {
                throw malformed("the image size is not positive");
            }
        }

        private void requireEnd(int length) {
            if (length != 0) {
                throw malformed("the end chunk carries data");
            }
        }

        private static IconRefusal malformed(String reason) {
            return new IconRefusal(IconRefusal.PNG_MALFORMED, "Not a PNG image: " + reason);
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

        private static int readInt(byte[] bytes, int offset) {
            return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16
                    | (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
        }

        private static int crc32(byte[] bytes, int offset, int length) {
            var crc = new CRC32();
            crc.update(bytes, offset, length);
            return (int) crc.getValue();
        }
    }
}
