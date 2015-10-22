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
package org.apache.james.imapserver.netty;

import static org.jboss.netty.channel.Channels.pipeline;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.net.ssl.SSLEngine;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.netty.ChannelGroupHandler;
import org.apache.james.protocols.netty.ConnectionLimitUpstreamHandler;
import org.apache.james.protocols.netty.ConnectionPerIpLimitUpstreamHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.HashedWheelTimer;

/**
 * NIO IMAP Server which use Netty.
 */
public class IMAPServer extends AbstractConfigurableAsyncServer implements ImapConstants, IMAPServerMBean, NettyConstants {

    private static final String softwaretype = "JAMES " + VERSION + " Server ";

    private ImapProcessor processor;
    private ImapEncoder encoder;
    private ImapDecoder decoder;

    private String hello;
    private boolean compress;
    private int maxLineLength;
    private int inMemorySizeLimit;
    private boolean plainAuthDisallowed;
    private int timeout;
    private int literalSizeLimit;

    public final static int DEFAULT_MAX_LINE_LENGTH = 65536; // Use a big default
    public final static int DEFAULT_IN_MEMORY_SIZE_LIMIT = 10485760; // Use 10MB as default
    public final static int DEFAULT_TIMEOUT = 30 * 60; // default timeout is 30 seconds
    public final static int DEFAULT_LITERAL_SIZE_LIMIT = 0;

    @Inject
    public void setImapProcessor(ImapProcessor processor) {
        this.processor = processor;
    }

    @Inject
    public void setImapDecoder(ImapDecoder decoder) {
        this.decoder = decoder;
    }

    @Inject
    public void setImapEncoder(ImapEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public void doConfigure(final HierarchicalConfiguration configuration) throws ConfigurationException {
        
        super.doConfigure(configuration);
        
        hello = softwaretype + " Server " + getHelloName() + " is ready.";
        compress = configuration.getBoolean("compress", false);
        maxLineLength = configuration.getInt("maxLineLength", DEFAULT_MAX_LINE_LENGTH);
        inMemorySizeLimit = configuration.getInt("inMemorySizeLimit", DEFAULT_IN_MEMORY_SIZE_LIMIT);
        literalSizeLimit = configuration.getInt("literalSizeLimit", DEFAULT_LITERAL_SIZE_LIMIT);

        plainAuthDisallowed = configuration.getBoolean("plainAuthDisallowed", false);
        timeout = configuration.getInt("timeout", DEFAULT_TIMEOUT);
        if (timeout < DEFAULT_TIMEOUT) {
            throw new ConfigurationException("Minimum timeout of 30 minutes required. See rfc2060 5.4 for details");
        }
        
        if (timeout < 0) {
            timeout = 0;
        }
        
    }

    /**
     * @see AbstractConfigurableAsyncServer#getDefaultPort()
     */
    @Override
    public int getDefaultPort() {
        return 143;
    }

    /**
     * @see AbstractConfigurableAsyncServer#getServiceType()
     */
    public String getServiceType() {
        return "IMAP Service";
    }

    @Override
    protected ChannelPipelineFactory createPipelineFactory(final ChannelGroup group) {
        
        return new ChannelPipelineFactory() {
            
            private final ChannelGroupHandler groupHandler = new ChannelGroupHandler(group);
            private final HashedWheelTimer timer = new HashedWheelTimer();
            
            private final TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;

            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = pipeline();
                pipeline.addLast(GROUP_HANDLER, groupHandler);
                pipeline.addLast("idleHandler", new IdleStateHandler(timer, 0, 0, timeout, TIMEOUT_UNIT));
                pipeline.addLast(TIMEOUT_HANDLER, new ImapIdleStateHandler());
                pipeline.addLast(CONNECTION_LIMIT_HANDLER, new ConnectionLimitUpstreamHandler(IMAPServer.this.connectionLimit));

                pipeline.addLast(CONNECTION_LIMIT_PER_IP_HANDLER, new ConnectionPerIpLimitUpstreamHandler(IMAPServer.this.connPerIP));

                // Add the text line decoder which limit the max line length,
                // don't strip the delimiter and use CRLF as delimiter
                // Use a SwitchableDelimiterBasedFrameDecoder, see JAMES-1436
                pipeline.addLast(FRAMER, new SwitchableDelimiterBasedFrameDecoder(maxLineLength, false, Delimiters.lineDelimiter()));
               
                Encryption secure = getEncryption();
                if (secure != null && !secure.isStartTLS()) {
                    // We need to set clientMode to false.
                    // See https://issues.apache.org/jira/browse/JAMES-1025
                    SSLEngine engine = secure.getContext().createSSLEngine();
                    engine.setUseClientMode(false);
                    pipeline.addFirst(SSL_HANDLER, new SslHandler(engine));

                }
                pipeline.addLast(CONNECTION_COUNT_HANDLER, getConnectionCountHandler());

                pipeline.addLast(CHUNK_WRITE_HANDLER, new ChunkedWriteHandler());

                ExecutionHandler ehandler = getExecutionHandler();
                if (ehandler  != null) {
                    pipeline.addLast(EXECUTION_HANDLER, ehandler);

                }
                pipeline.addLast(REQUEST_DECODER, new ImapRequestFrameDecoder(decoder, inMemorySizeLimit, literalSizeLimit));

                pipeline.addLast(CORE_HANDLER, createCoreHandler());
                return pipeline;
            }

        };
    }

    @Override
    protected String getDefaultJMXName() {
        return "imapserver";
    }

    @Override
    protected ChannelUpstreamHandler createCoreHandler() {
        ImapChannelUpstreamHandler coreHandler;
        Encryption secure = getEncryption();
        if (secure!= null && secure.isStartTLS()) {
           coreHandler = new ImapChannelUpstreamHandler(hello, processor, encoder, getLogger(), compress, plainAuthDisallowed, secure.getContext(), getEnabledCipherSuites());
        } else {
           coreHandler = new ImapChannelUpstreamHandler(hello, processor, encoder, getLogger(), compress, plainAuthDisallowed);
        }
        return coreHandler;
    }

    /**
     * Return null as we don't need this
     */
    protected OneToOneEncoder createEncoder() {
        return null;
    }

}
