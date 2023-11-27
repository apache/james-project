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

package org.apache.james.managesieveserver.netty;


import static org.apache.james.protocols.netty.HandlerConstants.CONNECTION_LIMIT_HANDLER;
import static org.apache.james.protocols.netty.HandlerConstants.CONNECTION_LIMIT_PER_IP_HANDLER;

import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.AllButStartTlsLineChannelHandlerFactory;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.ConnectionLimitUpstreamHandler;
import org.apache.james.protocols.netty.ConnectionPerIpLimitUpstreamHandler;
import org.apache.james.protocols.netty.Encryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;

public class ManageSieveServer extends AbstractConfigurableAsyncServer implements ManageSieveServerMBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManageSieveServer.class);

    static final String SSL_HANDLER = "sslHandler";
    static final String FRAMER = "framer";
    static final String CORE_HANDLER = "coreHandler";
    static final String CHUNK_WRITE_HANDLER = "chunkWriteHandler";

    private final int maxLineLength;
    private final ManageSieveProcessor manageSieveProcessor;
    private Optional<ConnectionLimitUpstreamHandler> connectionLimitUpstreamHandler = Optional.empty();
    private Optional<ConnectionPerIpLimitUpstreamHandler> connectionPerIpLimitUpstreamHandler = Optional.empty();

    public ManageSieveServer(int maxLineLength, ManageSieveProcessor manageSieveProcessor, FileSystem fileSystem) {
        super(fileSystem);
        this.maxLineLength = maxLineLength;
        this.manageSieveProcessor = manageSieveProcessor;
    }

    @Override
    protected int getDefaultPort() {
        return 4190;
    }

    @Override
    protected void doConfigure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        super.doConfigure(config);

        connectionLimitUpstreamHandler = ConnectionLimitUpstreamHandler.forCount(connectionLimit);
        connectionPerIpLimitUpstreamHandler = ConnectionPerIpLimitUpstreamHandler.forCount(connPerIP);
    }

    @Override
    protected String getDefaultJMXName() {
        return "managesieveserver";
    }

    @Override
    protected ChannelInboundHandlerAdapter createCoreHandler() {
        return new ManageSieveChannelUpstreamHandler(manageSieveProcessor, getEncryption(), maxLineLength);
    }

    @Override
    protected AbstractChannelPipelineFactory createPipelineFactory() {

        return new AbstractChannelPipelineFactory(createFrameHandlerFactory(), getExecutorGroup()) {

            @Override
            protected ChannelInboundHandlerAdapter createHandler() {
                return createCoreHandler();
            }

            @Override
            public void initChannel(Channel channel) {
                ChannelPipeline pipeline = channel.pipeline();
                Encryption secure = getEncryption();
                if (secure != null && !secure.isStartTLS()) {
                    pipeline.addFirst(SSL_HANDLER, secure.sslHandler());
                }

                connectionLimitUpstreamHandler.ifPresent(handler -> pipeline.addLast(CONNECTION_LIMIT_HANDLER, handler));
                connectionPerIpLimitUpstreamHandler.ifPresent(handler -> pipeline.addLast(CONNECTION_LIMIT_PER_IP_HANDLER, handler));

                // Add the text line decoder which limit the max line length,
                // don't strip the delimiter and use CRLF as delimiter
                // Use a SwitchableDelimiterBasedFrameDecoder, see JAMES-1436
                pipeline.addLast(getExecutorGroup(), FRAMER, getFrameHandlerFactory().create(pipeline));
                pipeline.addLast(getExecutorGroup(), CHUNK_WRITE_HANDLER, new ChunkedWriteHandler());

                pipeline.addLast(getExecutorGroup(), "stringDecoder", new StringDecoder(CharsetUtil.UTF_8));
                pipeline.addLast(getExecutorGroup(), CORE_HANDLER, createHandler());
                pipeline.addLast(getExecutorGroup(), "stringEncoder", new StringEncoder(CharsetUtil.UTF_8));
            }

        };
    }

    @Override
    public String getServiceType() {
        return "Manage Sieve Service";
    }

    @Override
    protected ChannelHandlerFactory createFrameHandlerFactory() {
        return new AllButStartTlsLineChannelHandlerFactory("starttls", AbstractChannelPipelineFactory.MAX_LINE_LENGTH);
    }
}
