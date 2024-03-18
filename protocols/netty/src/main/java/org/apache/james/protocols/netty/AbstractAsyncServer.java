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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.util.concurrent.NamedThreadFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;



/**
 * Abstract base class for Servers which want to use async io
 */
public abstract class AbstractAsyncServer implements ProtocolServer {

    public static final int DEFAULT_IO_WORKER_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    public static final int DEFAULT_BOSS_WORKER_COUNT = 2;
    private volatile int backlog = 250;
    
    private volatile int timeout = 120;

    private Optional<EventLoopGroup> bossGroup;
    private EventLoopGroup workerGroup;

    private volatile boolean started;
    
    private final ChannelGroup channels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

    private volatile int ioWorker = DEFAULT_IO_WORKER_COUNT;
    private volatile Optional<Integer> bossWorker = Optional.of(DEFAULT_BOSS_WORKER_COUNT);

    private List<InetSocketAddress> addresses = new ArrayList<>();

    protected String jmxName;

    private boolean gracefulShutdown = true;
    private boolean useEpoll = false;

    public synchronized void setListenAddresses(InetSocketAddress... addresses) {
        if (started) {
            throw new IllegalStateException("Can only be set when the server is not running");
        }
        this.addresses = ImmutableList.copyOf(addresses);
    }

    public void setGracefulShutdown(boolean gracefulShutdown) {
        this.gracefulShutdown = gracefulShutdown;
    }

    public void setUseEpoll(boolean useEpoll) {
        this.useEpoll = useEpoll;
    }

    /**
     * Set the IO-worker thread count to use.
     *
     * IO threads are used for receiving and framing IMAP messages.
     *
     * Default is nCores * 2
     */
    public void setIoWorkerCount(int ioWorker) {
        if (started) {
            throw new IllegalStateException("Can only be set when the server is not running");
        }
        this.ioWorker = ioWorker;
    }

    /**
     * Set the Boss-worker thread count to use.
     *
     * Boss threads are responsible of accepting new connections.
     *
     * Default is to not use boss threads and let the IO threads hand over this responsibility.
     */
    public void setBossWorkerCount(Optional<Integer> bossWorker) {
        if (started) {
            throw new IllegalStateException("Can only be set when the server is not running");
        }
        this.bossWorker = bossWorker;
    }

    @Override
    public synchronized void bind() throws Exception {
        if (started) {
            throw new IllegalStateException("Server running already");
        }

        if (addresses.isEmpty()) {
            throw new RuntimeException("Please specify at least on socketaddress to which the server should get bound!");
        }

        ServerBootstrap bootstrap = new ServerBootstrap();
        if (useEpoll) {
            bootstrap.channel(EpollServerSocketChannel.class);
            bossGroup = bossWorker.map(count -> new EpollEventLoopGroup(count, NamedThreadFactory.withName(jmxName + "-boss")));
            workerGroup = new EpollEventLoopGroup(ioWorker, NamedThreadFactory.withName(jmxName + "-io"));
        } else {
            bootstrap.channel(NioServerSocketChannel.class);
            bossGroup = bossWorker.map(count -> new NioEventLoopGroup(count, NamedThreadFactory.withName(jmxName + "-boss")));
            workerGroup = new NioEventLoopGroup(ioWorker, NamedThreadFactory.withName(jmxName + "-io"));
        }

        bossGroup.<Runnable>map(boss -> () -> bootstrap.group(boss, workerGroup))
            .orElse(() -> bootstrap.group(workerGroup))
            .run();

        ChannelInitializer<SocketChannel> factory = createPipelineFactory();

        // Configure the pipeline factory.
        bootstrap.childHandler(factory);

        configureBootstrap(bootstrap);

        for (InetSocketAddress address : addresses) {
            Channel channel = bootstrap.bind(address).sync().channel();
            channels.add(channel);
        }

        started = true;
    }

    /**
     * Configure the bootstrap before it get bound
     */
    protected void configureBootstrap(ServerBootstrap bootstrap) {
        // Bind and start to accept incoming connections.
        bootstrap.option(ChannelOption.SO_BACKLOG, backlog);
        bootstrap.option(ChannelOption.SO_REUSEADDR, true);
        bootstrap.childOption(ChannelOption.TCP_NODELAY, true);
    }
    
    @Override
    public synchronized void unbind() {
        if (!started) {
            return;
        }

        List<Future<?>> futures = new ArrayList<>();

        bossGroup.ifPresent(boss -> futures.add(boss.shutdownGracefully()));

        if (workerGroup != null) {
            futures.add(workerGroup.shutdownGracefully());
        }

        futures.add(channels.close());

        if (gracefulShutdown) {
            futures.forEach(Throwing.<Future<?>>consumer(Future::await).sneakyThrow());
        }

        started = false;
    }

    @Override
    public synchronized List<InetSocketAddress> getListenAddresses() {
        return channels.stream()
            .map(channel -> (InetSocketAddress) channel.localAddress())
            .collect(ImmutableList.toImmutableList());
    }
    
    /**
     * Create ChannelPipelineFactory to use by this Server implementation
     */
    protected abstract ChannelInitializer<SocketChannel> createPipelineFactory();

    /**
     * Set the read/write timeout for the server. This will throw a {@link IllegalStateException} if the
     * server is running.
     */
    public void setTimeout(int timeout) {
        if (started) {
            throw new IllegalStateException("Can only be set when the server is not running");
        }
        this.timeout = timeout;
    }
    
    
    /**
     * Set the Backlog for the socket. This will throw a {@link IllegalStateException} if the server is running.
     */
    public void setBacklog(int backlog) {
        if (started) {
            throw new IllegalStateException("Can only be set when the server is not running");
        }
        this.backlog = backlog;
    }
    

    @Override
    public int getBacklog() {
        return backlog;
    }
    

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public boolean isBound() {
        return started;
    }
}
