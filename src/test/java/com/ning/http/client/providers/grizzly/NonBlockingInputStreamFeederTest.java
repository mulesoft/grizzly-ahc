/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.ning.http.client.providers.grizzly;

import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;
import static java.util.Arrays.copyOfRange;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.impl.FutureImpl;
import org.testng.annotations.Test;

public class NonBlockingInputStreamFeederTest {

  private static final byte[] DATA = "Hello, world!".getBytes();
  private static final byte[] EMPTY = new byte[0];

  @Test
  public void feedSmallPayload() throws IOException {
    assertFeeding(DATA);
  }

  @Test
  public void feedEmptyPayload() throws IOException {
    assertFeeding(EMPTY);
  }

  @Test
  public void whenWriteQueuIsFullTheOnQueueWriteStrategyIsAppliedAndAllTheDataIsConsumed() throws IOException {
    byte[] data = DATA;
    int bufferSize = data.length / 2;
    FeedableBodyGenerator feedableBodyGenerator = new FeedableBodyGenerator();
    FeedableBodyGenerator spiedFeedableBodyGenerator = spy(feedableBodyGenerator);
    NonBlockingInputStreamFeeder nonBlockingInputStreamFeeder =
            new NonBlockingInputStreamFeeder(spiedFeedableBodyGenerator, new ByteArrayInputStream(data), bufferSize);
    NonBlockingInputStreamFeeder spiedNonBlockingInputStreamFeeder = spy(nonBlockingInputStreamFeeder);
    spiedFeedableBodyGenerator.setFeeder(spiedNonBlockingInputStreamFeeder);
    FilterChainContext filterChainContext = mock(FilterChainContext.class);
    HttpRequestPacket requestPacket = mock(HttpRequestPacket.class);
    Connection connection = mock(Connection.class);
    when(filterChainContext.getConnection()).thenReturn(connection);
    when(connection.getMaxAsyncWriteQueueSize()).thenReturn(100);
    when(spiedNonBlockingInputStreamFeeder.isReady()).thenReturn(true).thenReturn(false);
    spiedFeedableBodyGenerator.initializeAsynchronousTransfer(filterChainContext, requestPacket);
    verify(spiedNonBlockingInputStreamFeeder).onFullWriteQueue(connection);
    verify(connection).notifyCanWrite(any(WriteHandler.class));
    assertFeeding(DATA);
  }

  @Test
  public void whenExecutionExceptionIsThrownWhileQueueIsBlocking_thenDoNotWaitForQueueToBeFree() throws Exception {
    byte[] data = DATA;
    int bufferSize = data.length / 2;
    FeedableBodyGenerator feedableBodyGenerator = new FeedableBodyGenerator();
    FeedableBodyGenerator spiedFeedableBodyGenerator = spy(feedableBodyGenerator);
    NonBlockingInputStreamFeeder nonBlockingInputStreamFeeder =
      new NonBlockingInputStreamFeeder(spiedFeedableBodyGenerator, new ByteArrayInputStream(data), bufferSize);
    NonBlockingInputStreamFeeder spiedNonBlockingInputStreamFeeder = spy(nonBlockingInputStreamFeeder);
    spiedFeedableBodyGenerator.setFeeder(spiedNonBlockingInputStreamFeeder);
    FilterChainContext filterChainContext = mock(FilterChainContext.class);
    HttpRequestPacket requestPacket = mock(HttpRequestPacket.class);
    Connection connection = mock(Connection.class);
    when(filterChainContext.getConnection()).thenReturn(connection);
    when(connection.getMaxAsyncWriteQueueSize()).thenReturn(100);

    FutureImpl<Boolean> future = mock(FutureImpl.class);
    when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new ExecutionException(new IOException("Mocked exception")));
    when(future.get()).thenThrow(new ExecutionException(new IOException("Mocked exception")));
    spiedFeedableBodyGenerator.initializeAsynchronousTransfer(filterChainContext, requestPacket);
    verify(spiedNonBlockingInputStreamFeeder).onFullWriteQueue(connection);
    verify(connection).notifyCanWrite(any(WriteHandler.class));
    assertFalse(spiedNonBlockingInputStreamFeeder.onFullWriteQueue(connection));
  }

  private void assertFeeding(byte[] data) throws IOException {
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

  private static byte[] addAll(byte[] one, byte[] two) {
    byte[] result = copyOf(one, one.length + two.length);
    arraycopy(two, 0, result, one.length, two.length);
    return result;
  }
}
