/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.ning.http.client.ws;

public class BadStatusCodeException extends IllegalStateException {

  private final int status;

  public BadStatusCodeException(int status) {
    super("Invalid Status Code " + status);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }
}
