/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.ning.http.util;

import java.io.IOException;
import java.net.ServerSocket;

public class FreePortFinder {

    public static int findFreePort() {
        try {
            ServerSocket dummySocket = new ServerSocket(0);
            int freePort = dummySocket.getLocalPort();
            dummySocket.setReuseAddress(true);
            dummySocket.close();
            return freePort;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
