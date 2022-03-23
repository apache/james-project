/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.protocols.api;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;

/**
 * A {@link ProtocolServer} accept inbound traffic and handle it. Basically the protocols API can be used to handle every "line based" protocol in an easy fashion
 */
public interface ProtocolServer {

    /**
     * Same value as {@link AbstractEventExecutor#DEFAULT_SHUTDOWN_QUIET_PERIOD}.
     */
    final long DEFAULT_SHUTDOWN_QUIET_PERIOD = 2; // 2 seconds.

    /**
     * Same value as {@link AbstractEventExecutor#DEFAULT_SHUTDOWN_TIMEOUT}.
     */
    final long DEFAULT_SHUTDOWN_TIMEOUT = 15; // 15 seconds.

    /**
     * Start the server - blocks until server has started.
     */
    default void bind() {
        sync_bind(bindAsync());
    }

    /**
     * Start the server - non-blocking/asynchronous.
     */
    Future<Void> bindAsync();

    /**
     * Stop the server - blocks until server is stopped.
     */
    default void unbind() {
        sync_unbind(unbindAsync());
    }

    /**
     * Stops the server immediately - only appropriate for using in tests.
     */
    default void unbindNow() {
        sync_unbind(unbindAsync(0, 0, TimeUnit.SECONDS));
    }

    /**
     * Stop the server - blocks until server is stopped.
     * 
     * See {@link #unbindAsync(long, long, TimeUnit)} for documentation on these parameters.
     */
    default void unbind(long quietPeriod, long timeout, TimeUnit unit) {
        sync_unbind(unbindAsync(quietPeriod, timeout, unit));
    }

    /**
     * Stop the server - non-blocking/asynchronous - with sensible default values.
     */
    default Future<Void> unbindAsync() {
        return unbindAsync(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * Stop the server - non-blocking/asynchronous.
     * 
     * Returns a future which completes after {@linkplain EventExecutorGroup#shutdownGracefully(long, long, TimeUnit)} is called on each managed {@link EventLoopGroup}.
     * 
     * @param quietPeriod Allows new tasks to be submitted during the <i>'the quiet period'</i> - if a task is submitted during the quiet period, the quiet period will start over.
     * @param timeout     The maximum amount of time to wait until the executor is {@linkplain EventExecutorGroup#shutdown()} regardless if a task was submitted during the quiet period.
     * @param unit        The unit of {@code quietPeriod} and {@code timeout}.
     */
    Future<Void> unbindAsync(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * return true if the server is bound
     */
    boolean isBound();

    /**
     * Return the read/write timeout in seconds for the socket.
     * @return the timeout
     */
    int getTimeout();

    /**
     * Return the backlog for the socket
     * 
     * @return backlog
     */
    int getBacklog();

    /**
     * Return the ips on which the server listen for connections
     * 
     * @return ips
     */
    List<InetSocketAddress> getListenAddresses();

    private static void sync_bind(Future<Void> future) {
        try {
            future.sync();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Unexpected interrupt while waiting for bind to complete", e);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to bind", e); // Wrap exception from different call stack - important to do always do this on sync() so current call stack is included. 
        }
    }

    private static void sync_unbind(Future<Void> future) {
        try {
            future.sync();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Unexpected interrupt while waiting for unbind to complete", e);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to unbind", e); // Wrap exception from different call stack - important to do always do this on sync() so current call stack is included.
        }
    }

}
