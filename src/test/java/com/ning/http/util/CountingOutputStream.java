/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.ning.http.util;

import java.io.IOException;
import java.io.OutputStream;

// a /dev/null but counting how many bytes it received
public class CountingOutputStream extends OutputStream {
    private int byteCount = 0;

    @Override
    public void write(int b) throws IOException {
        byteCount++;
    }

    public int getByteCount() {
        return byteCount;
    }
}
