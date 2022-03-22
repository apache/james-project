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

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.james.protocols.api.ProtocolServer;

import com.google.common.collect.ImmutableList;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;

/**
 * Abstract base class for Servers which want to use async io
 */
public abstract class AbstractAsyncServer implements ProtocolServer {

    private static final ImmediateEventExecutor EXECUTOR = ImmediateEventExecutor.INSTANCE;

    protected final int backlog;
    protected final boolean keepalive;
    protected final List<InetSocketAddress> addresses;
    protected final EventLoopGroupManager groupsManager;

    private ChannelGroup channels;

    protected AbstractAsyncServer(Factory<?> factory) {
        backlog = factory.getBacklog();
        keepalive = factory.isKeepAlive();
        addresses = factory.getListenAddresses();
        groupsManager = factory.getGroupsManager();
    }

    @Override
    public boolean isBound() {
        return channels != null;
    }

    @Override
    public synchronized Future<?> bindAsync() {
        if (channels != null) {
            throw new IllegalStateException("Server running already");
        }

        // Create bootstrap
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.channelFactory(() -> new NioServerSocketChannel()); // Identical to calling 'bootstrap.channel(NioServerSocketChannel.class)' but without reflection.
        configureBootstrap(bootstrap);
        groupsManager.bind(bootstrap); // Calls 'bootstrap.group(EventLoopGroup)' or 'bootstrap.group(EventLoopGroup, EventLoopGroup)' and nothing else.
        bootstrap.childHandler(createChannelInitializer());

        // Bind to addresses
        PromiseCombiner combiner = createCombiner();
        channels = new DefaultChannelGroup(EXECUTOR);
        for (InetSocketAddress address : addresses) {
            ChannelFuture bind = bootstrap.bind(address);
            combiner.add(bind.addListener((Future<Void> future) -> {
                if (future.isSuccess()) {
                    channels.add(bind.channel());
                }
            }));
        }
        return finish(combiner);
    }

    /**
     * Configure the bootstrap before it get bound
     */
    protected void configureBootstrap(ServerBootstrap bootstrap) {
        // Bind and start to accept incoming connections.
        bootstrap.option(ChannelOption.SO_BACKLOG, backlog);
        bootstrap.option(ChannelOption.SO_REUSEADDR, true);
        bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, keepalive);
    }

    @Override
    public synchronized Future<?> unbindAsync(long quietPeriod, long timeout, TimeUnit unit) {
        PromiseCombiner combiner = createCombiner();

        // Close channels
        ChannelGroup channels = this.channels;
        if (channels != null) {
            this.channels = null;
            combiner.add(channels.close());
        }

        // Will either close groups, or do nothing if lifecycle of these groups is being managed separately to this server.
        groupsManager.unbind(combiner, quietPeriod, timeout, unit);

        return finish(combiner);
    }

    @Override
    public int getBacklog() {
        return backlog;
    }

    @Override
    public List<InetSocketAddress> getListenAddresses() {
        return channels.stream()
                .map(channel -> (InetSocketAddress) channel.localAddress())
                .collect(ImmutableList.toImmutableList());
    }

    /**
     * Create {@link ChannelInitializer} to use by this Server implementation
     */
    protected abstract ChannelInitializer<SocketChannel> createChannelInitializer();

    public abstract static class Factory<T extends Factory<T>> {
        private int backlog = 250;
        private boolean keepalive;
        private List<InetSocketAddress> listenAddresses;
        private EventLoopGroupManager groupsManager;

        protected void beforeBuild() {
            if (listenAddresses == null || listenAddresses.isEmpty()) {
                throw new IllegalStateException("Please specify one or more at least on addresses to which the server should get bound");
            }
            if (groupsManager == null) {
                groupsManager = new EventLoopGroupManagerDefault();
            }
        }

        /**
         * Set the backlog for the socket.
         */
        public final T backlog(int backlog) {
            if (backlog <= 0) {
                throw new IllegalStateException("Backlog must be postive integer");
            }
            this.backlog = backlog;
            return this_();
        }

        public final T keepalive(boolean keepalive) {
            this.keepalive = keepalive;
            return this_();
        }

        /**
         * Set one or more addresses this server should listen on.
         */
        public final T listenAddresses(InetSocketAddress... addresses) {
            this.listenAddresses = ImmutableList.copyOf(addresses);
            return this_();
        }

        public T groupsManager(EventLoopGroupManager groupsManager) {
            this.groupsManager = groupsManager;
            return this_();
        }

        public final int getBacklog() {
            return backlog;
        }

        public final boolean isKeepAlive() {
            return keepalive;
        }

        public final List<InetSocketAddress> getListenAddresses() {
            return listenAddresses;
        }

        public EventLoopGroupManager getGroupsManager() {
            return groupsManager;
        }

        protected abstract T this_();
    }

    private static PromiseCombiner createCombiner() {
        return new PromiseCombiner(EXECUTOR);
    }

    private static Future<Void> finish(PromiseCombiner combiner) {
        Promise<Void> promise = EXECUTOR.newPromise();
        combiner.finish(promise);
        return promise;
    }

}
