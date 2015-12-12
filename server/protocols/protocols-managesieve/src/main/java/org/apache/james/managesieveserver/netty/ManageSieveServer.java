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

import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.netty.ChannelGroupHandler;
import org.apache.james.protocols.netty.ConnectionLimitUpstreamHandler;
import org.apache.james.protocols.netty.ConnectionPerIpLimitUpstreamHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

import static org.jboss.netty.channel.Channels.pipeline;

public class ManageSieveServer extends AbstractConfigurableAsyncServer implements ManageSieveServerMBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManageSieveServer.class);

    final static String SSL_HANDLER = "sslHandler";
    final static String FRAMER = "framer";
    final static String CORE_HANDLER = "coreHandler";
    final static String GROUP_HANDLER = "groupHandler";
    final static String CONNECTION_LIMIT_HANDLER = "connectionLimitHandler";
    final static String CONNECTION_LIMIT_PER_IP_HANDLER = "connectionPerIpLimitHandler";
    final static String CONNECTION_COUNT_HANDLER = "connectionCountHandler";
    final static String CHUNK_WRITE_HANDLER = "chunkWriteHandler";
    final static String EXECUTION_HANDLER = "executionHandler";

    private int maxLineLength;
    private ManageSieveProcessor manageSieveProcessor;

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
    protected ChannelUpstreamHandler createCoreHandler() {
        return new ManageSieveChannelUpstreamHandler(manageSieveProcessor,
            getEncryption() == null ? null : getEncryption().getContext(),
            getEnabledCipherSuites(),
            isSSL(),
            LOGGER);
    }

    private boolean isSSL() {
        return getEncryption() != null
            && !getEncryption().isStartTLS();
    }

    @Override
    protected ChannelPipelineFactory createPipelineFactory(final ChannelGroup group) {

        return new ChannelPipelineFactory() {

            private final ChannelGroupHandler groupHandler = new ChannelGroupHandler(group);

            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = pipeline();
                Encryption secure = getEncryption();
                if (secure != null && !secure.isStartTLS()) {
                    // We need to set clientMode to false.
                    // See https://issues.apache.org/jira/browse/JAMES-1025
                    SSLEngine engine = secure.getContext().createSSLEngine();
                    engine.setUseClientMode(false);
                    pipeline.addFirst(SSL_HANDLER, new SslHandler(engine));

                }
                pipeline.addLast(GROUP_HANDLER, groupHandler);
                pipeline.addLast(CONNECTION_LIMIT_HANDLER, new ConnectionLimitUpstreamHandler(ManageSieveServer.this.connectionLimit));
                pipeline.addLast(CONNECTION_LIMIT_PER_IP_HANDLER, new ConnectionPerIpLimitUpstreamHandler(ManageSieveServer.this.connPerIP));
                // Add the text line decoder which limit the max line length,
                // don't strip the delimiter and use CRLF as delimiter
                // Use a SwitchableDelimiterBasedFrameDecoder, see JAMES-1436
                pipeline.addLast(FRAMER, new DelimiterBasedFrameDecoder(maxLineLength, false, Delimiters.lineDelimiter()));
                pipeline.addLast(CONNECTION_COUNT_HANDLER, getConnectionCountHandler());
                pipeline.addLast(CHUNK_WRITE_HANDLER, new ChunkedWriteHandler());

                ExecutionHandler ehandler = getExecutionHandler();
                if (ehandler  != null) {
                    pipeline.addLast(EXECUTION_HANDLER, ehandler);

                }
                pipeline.addLast("stringDecoder", new StringDecoder(CharsetUtil.UTF_8));
                pipeline.addLast(CORE_HANDLER, createCoreHandler());
                pipeline.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8));
                return pipeline;
            }

        };
    }

    @Override
    public String getServiceType() {
        return "Manage Sieve Service";
    }
}
