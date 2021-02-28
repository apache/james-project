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

import javax.net.ssl.SSLEngine;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.netty.ChannelGroupHandler;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.ConnectionLimitUpstreamHandler;
import org.apache.james.protocols.netty.ConnectionPerIpLimitUpstreamHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

/**
 * NIO IMAP Server which use Netty.
 */
public class IMAPServer extends AbstractConfigurableAsyncServer implements ImapConstants, IMAPServerMBean, NettyConstants {
    private static final Logger LOG = LoggerFactory.getLogger(IMAPServer.class);

    private static final String softwaretype = "JAMES " + VERSION + " Server ";
    private static final String DEFAULT_TIME_UNIT = "SECONDS";
    private static final String CAPABILITY_SEPARATOR = "|";

    private final ImapProcessor processor;
    private final ImapEncoder encoder;
    private final ImapDecoder decoder;
    private final ImapMetrics imapMetrics;

    private String hello;
    private boolean compress;
    private int maxLineLength;
    private int inMemorySizeLimit;
    private boolean plainAuthDisallowed;
    private int timeout;
    private int literalSizeLimit;

    public static final int DEFAULT_MAX_LINE_LENGTH = 65536; // Use a big default
    public static final int DEFAULT_IN_MEMORY_SIZE_LIMIT = 10485760; // Use 10MB as default
    public static final int DEFAULT_TIMEOUT = 30 * 60; // default timeout is 30 seconds
    public static final int DEFAULT_LITERAL_SIZE_LIMIT = 0;

    public IMAPServer(ImapDecoder decoder, ImapEncoder encoder, ImapProcessor processor, ImapMetrics imapMetrics) {
        this.processor = processor;
        this.encoder = encoder;
        this.decoder = decoder;
        this.imapMetrics = imapMetrics;
    }

    @Override
    public void doConfigure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        
        super.doConfigure(configuration);
        
        hello = softwaretype + getHelloName() + " is ready.";
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

        processor.configure(getImapConfiguration(configuration));
    }

    @VisibleForTesting static ImapConfiguration getImapConfiguration(HierarchicalConfiguration<ImmutableNode> configuration) {
        ImmutableSet<String> disabledCaps = ImmutableSet.copyOf(Splitter.on(CAPABILITY_SEPARATOR).split(configuration.getString("disabledCaps", "")));

        return ImapConfiguration.builder()
                .enableIdle(configuration.getBoolean("enableIdle", ImapConfiguration.DEFAULT_ENABLE_IDLE))
                .idleTimeInterval(configuration.getLong("idleTimeInterval", ImapConfiguration.DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS))
                .idleTimeIntervalUnit(getTimeIntervalUnit(configuration.getString("idleTimeIntervalUnit", DEFAULT_TIME_UNIT)))
                .disabledCaps(disabledCaps)
                .build();
    }

    private static TimeUnit getTimeIntervalUnit(String timeIntervalUnit) {
        try {
            return TimeUnit.valueOf(timeIntervalUnit);
        } catch (IllegalArgumentException e) {
            LOG.info("Time interval unit is not valid {}, the default {} value should be used", timeIntervalUnit, ImapConfiguration.DEFAULT_HEARTBEAT_INTERVAL_UNIT);
            return ImapConfiguration.DEFAULT_HEARTBEAT_INTERVAL_UNIT;
        }
    }

    @Override
    public int getDefaultPort() {
        return 143;
    }

    @Override
    public String getServiceType() {
        return "IMAP Service";
    }

    @Override
    protected ChannelPipelineFactory createPipelineFactory(final ChannelGroup group) {
        
        return new ChannelPipelineFactory() {
            
            private final ChannelGroupHandler groupHandler = new ChannelGroupHandler(group);
            private final HashedWheelTimer timer = new HashedWheelTimer();
            
            private final TimeUnit timeoutUnit = TimeUnit.SECONDS;

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = pipeline();
                pipeline.addLast(GROUP_HANDLER, groupHandler);
                pipeline.addLast("idleHandler", new IdleStateHandler(timer, 0, 0, timeout, timeoutUnit));
                pipeline.addLast(TIMEOUT_HANDLER, new ImapIdleStateHandler());
                pipeline.addLast(CONNECTION_LIMIT_HANDLER, new ConnectionLimitUpstreamHandler(IMAPServer.this.connectionLimit));

                pipeline.addLast(CONNECTION_LIMIT_PER_IP_HANDLER, new ConnectionPerIpLimitUpstreamHandler(IMAPServer.this.connPerIP));

                // Add the text line decoder which limit the max line length,
                // don't strip the delimiter and use CRLF as delimiter
                // Use a SwitchableDelimiterBasedFrameDecoder, see JAMES-1436
                pipeline.addLast(FRAMER, getFrameHandlerFactory().create(pipeline));
               
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
        if (secure != null && secure.isStartTLS()) {
           coreHandler = new ImapChannelUpstreamHandler(hello, processor, encoder, compress, plainAuthDisallowed, secure.getContext(), getEnabledCipherSuites(), imapMetrics);
        } else {
           coreHandler = new ImapChannelUpstreamHandler(hello, processor, encoder, compress, plainAuthDisallowed, imapMetrics);
        }
        return coreHandler;
    }

    @Override
    protected ChannelHandlerFactory createFrameHandlerFactory() {
        return new SwitchableLineBasedFrameDecoderFactory(maxLineLength);
    }

}
