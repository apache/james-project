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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.james.util.concurrent.NamedThreadFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.PromiseCombiner;

public final class EventLoopGroupManagerDefault implements EventLoopGroupManager {

    private static final int PROCESSORS = Runtime.getRuntime().availableProcessors();

    public static final int DEFAULT_BOSS_THREADS = Math.min(2, PROCESSORS);
    public static final int DEFAULT_WORKER_THREADS = PROCESSORS * 2;
    public static final int DEFAULT_EXECUTOR_GROUP_THREADS = PROCESSORS * 2; // TODO potentially recommend setting this to 0 so reuses worker thread pool.

    private String threadNamePrefix = "apache-james";
    private int bossThreads = DEFAULT_BOSS_THREADS;
    private int workerThreads = DEFAULT_WORKER_THREADS;
    private int executorGroupThreads = DEFAULT_EXECUTOR_GROUP_THREADS;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private EventLoopGroup executorGroup;

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * Set the worker thread count to use. Default is nCores * 2
     */
    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getExecutorGroupThreads() {
        return executorGroupThreads;
    }

    public void setExecutorGroupThreads(int executorGroupThreads) {
        this.executorGroupThreads = executorGroupThreads;
    }

    @Override
    public void bind(ServerBootstrap bootstrap) {
        if (bossThreads <= 0) {
            throw new IllegalStateException("Boss threads must be 1 or greater");
        }
        bossGroup = new NioEventLoopGroup(bossThreads, getThreadFactory("-bossThreads"));
        workerGroup = workerThreads > 0 ? new NioEventLoopGroup(workerThreads, getThreadFactory("-worker")) : null;
        if (workerGroup == null) {
            bootstrap.group(bossGroup);
        } else {
            bootstrap.group(bossGroup, workerGroup);
        }

        executorGroup = executorGroupThreads > 0 ? new DefaultEventLoopGroup(executorGroupThreads, getThreadFactory("-executor")) : null;
    }

    private ThreadFactory getThreadFactory(String postfix) {
        return NamedThreadFactory.withName(threadNamePrefix + postfix);
    }

    @Override
    public EventExecutorGroup getExecutorGroup() {
        return executorGroup;
    }

    @Override
    public void unbind(PromiseCombiner combiner, long quietPeriod, long timeout, TimeUnit unit) {
        shutdownIfNotNull(combiner, bossGroup, quietPeriod, timeout, unit);
        shutdownIfNotNull(combiner, workerGroup, quietPeriod, timeout, unit);
        shutdownIfNotNull(combiner, executorGroup, quietPeriod, timeout, unit);

        this.bossGroup = null;
        this.workerGroup = null;
        this.executorGroup = null;
    }

    private static void shutdownIfNotNull(PromiseCombiner combiner, EventLoopGroup group,
            long quietPeriod, long timeout, TimeUnit unit) {
        if (group != null) {
            combiner.add(group.shutdownGracefully(quietPeriod, timeout, unit));
        }
    }

}
