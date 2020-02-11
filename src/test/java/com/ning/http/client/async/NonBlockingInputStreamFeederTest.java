/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.ning.http.client.async;

import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;
import static java.util.Arrays.copyOfRange;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.glassfish.grizzly.Buffer;
import org.testng.annotations.Test;

import com.ning.http.client.providers.grizzly.FeedableBodyGenerator;
import com.ning.http.client.providers.grizzly.NonBlockingInputStreamFeeder;

public class NonBlockingInputStreamFeederTest {

  private static final byte[] DATA = "Hello, world!".getBytes();
  private static final byte[] EMPTY = new byte[0];

  @Test
  public void feedSmallPayload() throws IOException {
    assertPayload(DATA);
  }

  @Test
  public void feedEmptyPayload() throws IOException {
    assertPayload(EMPTY);
  }

  protected void assertPayload(byte[] data) throws IOException {
    // Set the buffer size smaller than the data to be sent to allow the creation of many buffers
    int bufferSize = data.length / 2;
    List<Buffer> buffers = new LinkedList();

    NonBlockingInputStreamFeeder nonBlockingInputStreamFeeder =
        new NonBlockingInputStreamFeeder(new FeedableBodyGenerator(), new ByteArrayInputStream(data), bufferSize);

    NonBlockingInputStreamFeeder mockedFeeder = spy(nonBlockingInputStreamFeeder);

    // mock the final method NonBlockingFeeder.feed in order to capture the buffers to test
    doAnswer(a -> {
      // Save buffer instances to join them after completion.
      // Because buffers mustn't share state, it shouldn't be any problem doing that.
      buffers.add(a.getArgument(0));
      return null;
    }).when(mockedFeeder).feed(any(Buffer.class), anyBoolean());

    while (mockedFeeder.isReady()) {
      mockedFeeder.canFeed();
    }

    byte[] received = buffers.stream()
        .reduce(new byte[0],
                (acc, buff) -> addAll(acc, copyOfRange(buff.array(), buff.position(), buff.remaining())),
                (arr1, arr2) -> addAll(arr1, arr2));

    assertEquals(received, data);
  }

  public static byte[] addAll(byte[] one, byte[] two) {
    byte[] result = copyOf(one, one.length + two.length);
    arraycopy(two, 0, result, one.length, two.length);
    return result;
  }
}
