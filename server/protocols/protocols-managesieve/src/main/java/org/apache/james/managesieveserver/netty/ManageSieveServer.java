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


import javax.net.ssl.SSLEngine;

import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.AllButStartTlsLineChannelHandlerFactory;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.ConnectionLimitInboundHandler;
import org.apache.james.protocols.netty.ConnectionPerIpLimitInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;

public class ManageSieveServer extends AbstractConfigurableAsyncServer implements ManageSieveServerMBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManageSieveServer.class);

    static final String SSL_HANDLER = "sslHandler";
    static final String FRAMER = "framer";
    static final String CORE_HANDLER = "coreHandler";
    static final String CONNECTION_LIMIT_HANDLER = "connectionLimitHandler";
    static final String CONNECTION_LIMIT_PER_IP_HANDLER = "connectionPerIpLimitHandler";
    static final String CONNECTION_COUNT_HANDLER = "connectionCountHandler";
    static final String CHUNK_WRITE_HANDLER = "chunkWriteHandler";

    private final int maxLineLength;
    private final ManageSieveProcessor manageSieveProcessor;

    public ManageSieveServer(int maxLineLength, ManageSieveProcessor manageSieveProcessor) {
        this.maxLineLength = maxLineLength;
        this.manageSieveProcessor = manageSieveProcessor;
    }

    @Override
    protected int getDefaultPort() {
        return 4190;
    }

    @Override
    protected String getDefaultJMXName() {
        return "managesieveserver";
    }

    @Override
    protected ChannelInboundHandlerAdapter createCoreHandler() {
        return new ManageSieveChannelUpstreamHandler(manageSieveProcessor,
            getEncryption(),
            LOGGER);
    }

    @Override
    protected Server createServer() {
        beforeBuild();
        return new Server(this);
    }

    private final class Server extends AbstractConfigurableAsyncServer.Server {
        private Server(ManageSieveServer factory) {
            super(factory);
        }

        @Override
        protected AbstractChannelPipelineFactory createChannelInitializer() {
            return new AbstractChannelPipelineFactory(getFrameHandlerFactory(), getGroupsManager()) {

                @Override
                protected ChannelInboundHandlerAdapter createHandler() {
                    return createCoreHandler();
                }

                @Override
                protected void initPipeline(ChannelPipeline pipeline) throws Exception {
                    Encryption secure = getEncryption();
                    if (secure != null && !secure.isStartTLS()) {
                        // We need to set clientMode to false.
                        // See https://issues.apache.org/jira/browse/JAMES-1025
                        SSLEngine engine = secure.createSSLEngine();
                        engine.setUseClientMode(false);
                        pipeline.addFirst(SSL_HANDLER, new SslHandler(engine));

                    }
                    pipeline.addLast(CONNECTION_LIMIT_HANDLER, new ConnectionLimitInboundHandler(ManageSieveServer.this.connectionLimit));
                    pipeline.addLast(CONNECTION_LIMIT_PER_IP_HANDLER, new ConnectionPerIpLimitInboundHandler(ManageSieveServer.this.connPerIP));
                    // Add the text line decoder which limit the max line length,
                    // don't strip the delimiter and use CRLF as delimiter
                    // Use a SwitchableDelimiterBasedFrameDecoder, see JAMES-1436
                    pipeline.addLast(executorGroup, FRAMER, getFrameHandlerFactory().create(pipeline));
                    pipeline.addLast(executorGroup, CONNECTION_COUNT_HANDLER, getConnectionCountHandler());
                    pipeline.addLast(executorGroup, CHUNK_WRITE_HANDLER, new ChunkedWriteHandler());

                    pipeline.addLast(executorGroup, "stringDecoder", new StringDecoder(CharsetUtil.UTF_8));
                    pipeline.addLast(executorGroup, CORE_HANDLER, createHandler());
                    pipeline.addLast(executorGroup, "stringEncoder", new StringEncoder(CharsetUtil.UTF_8));
                }

            };
        }
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
