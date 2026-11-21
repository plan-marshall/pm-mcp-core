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
import java.util.zip.CRC32;

/**
 * Minimal PNG documents for the icon tests: the signature, an {@code IHDR}, one {@code IDAT}, optional further chunks
 * and an {@code IEND}, each chunk with its CRC. The documents are the wire format the sanitiser judges, so they are
 * built from the format's rules rather than from a fixture file.
 */
final class PngFixtures {

    private PngFixtures() {
    }

    /**
     * @param extraChunks chunks written between {@code IDAT} and {@code IEND}
     * @return a PNG document with those chunks
     */
    static byte[] png(byte[]... extraChunks) {
        var body = new ByteArrayOutputStream();
        body.writeBytes(header());
        body.writeBytes(chunk("IDAT", new byte[]{0x78, (byte) 0x9C}));
        for (var extra : extraChunks) {
            body.writeBytes(extra);
        }
        body.writeBytes(chunk("IEND", new byte[0]));
        return signed(body.toByteArray());
    }

    /**
     * @return a PNG document without its {@code IEND}
     */
    static byte[] pngWithoutEnd() {
        var body = new ByteArrayOutputStream();
        body.writeBytes(header());
        body.writeBytes(chunk("IDAT", new byte[]{0x78, (byte) 0x9C}));
        return signed(body.toByteArray());
    }

    /**
     * @param body the bytes after the signature
     * @return the PNG signature followed by those bytes
     */
    static byte[] signed(byte[] body) {
        var document = new ByteArrayOutputStream();
        document.writeBytes(PngSanitiser.SIGNATURE);
        document.writeBytes(body);
        return document.toByteArray();
    }

    /**
     * @param type the chunk type, four ASCII letters
     * @param data the chunk's data
     * @return the chunk: length, type, data and CRC
     */
    static byte[] chunk(String type, byte[] data) {
        var typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        var chunk = new ByteArrayOutputStream();
        chunk.writeBytes(integer(data.length));
        chunk.writeBytes(typeBytes);
        chunk.writeBytes(data);
        var crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        chunk.writeBytes(integer((int) crc.getValue()));
        return chunk.toByteArray();
    }

    private static byte[] header() {
        var data = new ByteArrayOutputStream();
        data.writeBytes(integer(16));
        data.writeBytes(integer(16));
        data.writeBytes(new byte[]{8, 6, 0, 0, 0});
        return chunk("IHDR", data.toByteArray());
    }

    private static byte[] integer(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }
}
