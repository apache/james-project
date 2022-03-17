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

import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.util.concurrent.NamedThreadFactory;

import com.google.common.collect.ImmutableList;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;



/**
 * Abstract base class for Servers which want to use async io
 */
public abstract class AbstractAsyncServer implements ProtocolServer {

    public static final int DEFAULT_IO_WORKER_COUNT = Runtime.getRuntime().availableProcessors() * 2;
    private volatile int backlog = 250;
    
    private volatile int timeout = 120;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private volatile boolean started;
    
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private volatile int ioWorker = DEFAULT_IO_WORKER_COUNT;
    
    private List<InetSocketAddress> addresses = new ArrayList<>();
    protected String jmxName;
    
    public synchronized void setListenAddresses(InetSocketAddress... addresses) {
        if (started) {
            throw new IllegalStateException("Can only be set when the server is not running");
        }
        this.addresses = ImmutableList.copyOf(addresses);
    }
    
    /**
     * Set the IO-worker thread count to use. Default is nCores * 2
     */
    public void setIoWorkerCount(int ioWorker) {
        if (started) {
            throw new IllegalStateException("Can only be set when the server is not running");
        }
        this.ioWorker = ioWorker;
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
        bootstrap.channel(NioServerSocketChannel.class);

        bossGroup = new NioEventLoopGroup(NamedThreadFactory.withName(jmxName + "-boss"));
        workerGroup = new NioEventLoopGroup(ioWorker, NamedThreadFactory.withName(jmxName + "-io"));

        bootstrap.group(bossGroup, workerGroup);

        ChannelInitializer<SocketChannel> factory = createPipelineFactory(channels);

        // Configure the pipeline factory.
        bootstrap.childHandler(factory);

        for (InetSocketAddress address : addresses) {
            Channel channel = bootstrap.bind(address).sync().channel();
            channels.add(channel);
        }

        configureBootstrap(bootstrap);

        started = true;
    }

    /**
     * Configure the bootstrap before it get bound
     */
    protected void configureBootstrap(ServerBootstrap bootstrap) {
        // Bind and start to accept incoming connections.
        bootstrap.option(ChannelOption.SO_BACKLOG, backlog);
        bootstrap.option(ChannelOption.SO_REUSEADDR, true);
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
    }
    
    @Override
    public synchronized void unbind() {
        if (started == false) {
            return;
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        started = false;
    }

    @Override
    public synchronized List<InetSocketAddress> getListenAddresses() {
        ImmutableList.Builder<InetSocketAddress> builder = ImmutableList.builder();
        for (Channel channel : ImmutableList.copyOf(channels.iterator())) {
            builder.add((InetSocketAddress) channel.localAddress());
        }
        return builder.build();
    }
    
    
    /**
     * Create ChannelPipelineFactory to use by this Server implementation
     */
    protected abstract ChannelInitializer<SocketChannel> createPipelineFactory(ChannelGroup group);

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
