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
package org.apache.james.protocols.netty;

import java.util.concurrent.TimeUnit;

import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.PromiseCombiner;

/**
 * Abstracts out the creation and management of groups to provide as much flexibility and configurability as possible.
 *
 * Common use cases for implementing this interface;
 *  (a) If you have other netty4 servers, even non-james one, it's a good idea to share at least the same parent/acceptor/boss group - which you can do by implementing this interface.
 *  (b) For SMTP, commonly you'll have a server listening on port 587 (and maybe 25 and 2525 too) configured with STARTTLS
 *      and a separate server listening on port 465 configured with SMTPS/Implicit TLS (i.e. like HTTPS)
 *      - in this case you should always use the same {@link EventLoopGroupManager} to achieve the best performance with the least overhead from context switching.
 *  (c) You want to provide your own implementation, implementing things like Thread Affinity - https://netty.io/wiki/thread-affinity.html
 * 
 * This interface was created to allow cleaner and better management of {@link EventLoopGroup}.
 * It's important that all {@link EventLoopGroup} are created and maintained via this interface.
 * This interfaces allows a cleaner separation of responsibilities and ensures that important tasks like shutting down {@link EventLoopGroup} aren't forgotton.
 */
public interface EventLoopGroupManager {

    /**
     * Implementation should call either;
     *   - {@link ServerBootstrap#group(EventLoopGroup)} to single group; or
     *   - {@link ServerBootstrap#group(EventLoopGroup, EventLoopGroup)} to set a separate parent/acceptor/boss and child/client/worker groups.
     * 
     * No other methods should be called.
     */
    void bind(ServerBootstrap bootstrap);

    /**
     * Return null to have your core handler run as part of the child/client/worker group.
     * This is fine assuming the core handler is entirely async (i.e. non-blocking).
     * However most core handlers - i.e. {@link BasicChannelInboundHandler} which wraps {@link Protocol} - include blocking code.
     * 
     * If your core handler includes blocking code and your child/client/worker group has a small number of threads,
     * then you should definately return a seperate group. For example, return 'new ${@link DefaultEventLoopGroup)()'.
     */
    EventExecutorGroup getExecutorGroup();

    /**
     * This method is called when {@link AbstractAsyncServer.unbind()} is called.
     * 
     * If the groups should shutdown when {@link AbstractAsyncServer} shutdowns, then should add one or more {@link Future<?>} to the combiner.
     * 
     * Alternatively, if the lifecycle of the groups is being externally managed, then this implementation simply does nothing.
     * 
     * @param quietPeriod Allows new tasks to be submitted during the <i>'the quiet period'</i> - if a task is submitted during the quiet period, the quiet period will start over.
     *                    Defaults to {@link ProtocolServer#DEFAULT_SHUTDOWN_QUIET_PERIOD}.
     * @param timeout     The maximum amount of time to wait until the executor is {@linkplain #shutdown()} regardless if a task was submitted during the quiet period
     *                    Defaults to {@link ProtocolServer#DEFAULT_SHUTDOWN_TIMEOUT}.
     * @param unit        The unit of {@code quietPeriod} and {@code timeout}.
     *                    Defaults to {@link TimeUnit#SECONDS}.
     */
    void unbind(PromiseCombiner combiner, long quietPeriod, long timeout, TimeUnit unit);

}
