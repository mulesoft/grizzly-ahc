/*
 * Copyright (c) 2014 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.ning.http.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public final class StringUtils {

    // lookup tables needed for toHexString(byte[], boolean)
    private static final String HEX_CHARACTERS = "0123456789abcdef";
    private static final String HEX_CHARACTERS_UC = HEX_CHARACTERS.toUpperCase();

    private static final ThreadLocal<StringBuilder> STRING_BUILDERS = new ThreadLocal<StringBuilder>() {
        protected StringBuilder initialValue() {
            return new StringBuilder(512);
        };
    };

    /**
     * BEWARE: MUSN'T APPEND TO ITSELF!
     * @return a pooled StringBuilder
     */
    public static StringBuilder stringBuilder() {
        StringBuilder sb = STRING_BUILDERS.get();
        sb.setLength(0);
        return sb;
    }

    private StringUtils() {
        // unused
    }

    public static ByteBuffer charSequence2ByteBuffer(CharSequence cs, Charset charset) {
        return charset.encode(CharBuffer.wrap(cs));
    }

    public static byte[] byteBuffer2ByteArray(ByteBuffer bb) {
        byte[] rawBase = new byte[bb.remaining()];
        bb.get(rawBase);
        return rawBase;
    }

    public static byte[] charSequence2Bytes(CharSequence sb, Charset charset) {
        ByteBuffer bb = charSequence2ByteBuffer(sb, charset);
        return byteBuffer2ByteArray(bb);
    }

    /**
     * @see #toHexString(byte[])
     */
    public static String toHexString(byte[] bytes) {
        return StringUtils.toHexString(bytes, false);
    }

    /**
     * Convert a byte array to a hexadecimal string.
     *
     * @param bytes     The bytes to format.
     * @param uppercase When <code>true</code> creates uppercase hex characters instead of lowercase (the default).
     * @return A hexadecimal representation of the specified bytes.
     */
    public static String toHexString(byte[] bytes, boolean uppercase) {
        if (bytes == null) {
            return null;
        }

        int numBytes = bytes.length;
        StringBuilder str = new StringBuilder(numBytes * 2);

        String table = (uppercase ? HEX_CHARACTERS_UC : HEX_CHARACTERS);

        for (int i = 0; i < numBytes; i++) {
            str.append(table.charAt(bytes[i] >>> 4 & 0x0f));
            str.append(table.charAt(bytes[i] & 0x0f));
        }

        return str.toString();
    }
}
