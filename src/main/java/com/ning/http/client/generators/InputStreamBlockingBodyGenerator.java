/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.ning.http.client.generators;

import java.io.InputStream;

/**
 * Same as {@link InputStreamBodyGenerator} excepts it indicates that the writing should block because the data is large.
 */
public class InputStreamBlockingBodyGenerator extends InputStreamBodyGenerator {

  public InputStreamBlockingBodyGenerator(InputStream inputStream) {
    super(inputStream);
  }
}
