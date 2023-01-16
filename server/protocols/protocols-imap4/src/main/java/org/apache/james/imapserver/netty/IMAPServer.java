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

import java.net.MalformedURLException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.ConnectionLimitUpstreamHandler;
import org.apache.james.protocols.netty.ConnectionPerIpLimitUpstreamHandler;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.netty.HandlerConstants;
import org.apache.james.util.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.stream.ChunkedWriteHandler;


/**
 * NIO IMAP Server which use Netty.
 */
public class IMAPServer extends AbstractConfigurableAsyncServer implements ImapConstants, IMAPServerMBean, NettyConstants {
    private static final Logger LOG = LoggerFactory.getLogger(IMAPServer.class);

    public static class AuthenticationConfiguration {
        private static final boolean PLAIN_AUTH_DISALLOWED_DEFAULT = true;
        private static final boolean PLAIN_AUTH_ENABLED_DEFAULT = true;
        private static final String OIDC_PATH = "auth.oidc";

        public static AuthenticationConfiguration parse(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
            boolean isRequireSSL = configuration.getBoolean("auth.requireSSL", fallback(configuration));
            boolean isPlainAuthEnabled = configuration.getBoolean("auth.plainAuthEnabled", PLAIN_AUTH_ENABLED_DEFAULT);

            if (configuration.immutableConfigurationsAt(OIDC_PATH).isEmpty()) {
                return new AuthenticationConfiguration(
                    isRequireSSL,
                    isPlainAuthEnabled);
            } else {
                try {
                    return new AuthenticationConfiguration(
                        isRequireSSL,
                        isPlainAuthEnabled,
                        OidcSASLConfiguration.parse(configuration.configurationAt(OIDC_PATH)));
                } catch (MalformedURLException | NullPointerException exception) {
                    throw new ConfigurationException("Failed to retrieve oauth component", exception);
                }
            }
        }

        private static boolean fallback(HierarchicalConfiguration<ImmutableNode> configuration) {
            return configuration.getBoolean("plainAuthDisallowed", PLAIN_AUTH_DISALLOWED_DEFAULT);
        }

        private final boolean isSSLRequired;
        private final boolean plainAuthEnabled;
        private final Optional<OidcSASLConfiguration> oidcSASLConfiguration;

        public AuthenticationConfiguration(boolean isSSLRequired, boolean plainAuthEnabled) {
            this.isSSLRequired = isSSLRequired;
            this.plainAuthEnabled = plainAuthEnabled;
            this.oidcSASLConfiguration = Optional.empty();
        }

        public AuthenticationConfiguration(boolean isSSLRequired, boolean plainAuthEnabled, OidcSASLConfiguration oidcSASLConfiguration) {
            this.isSSLRequired = isSSLRequired;
            this.plainAuthEnabled = plainAuthEnabled;
            this.oidcSASLConfiguration = Optional.of(oidcSASLConfiguration);
        }

        public boolean isSSLRequired() {
            return isSSLRequired;
        }

        public boolean isPlainAuthEnabled() {
            return plainAuthEnabled;
        }

        public Optional<OidcSASLConfiguration> getOidcSASLConfiguration() {
            return oidcSASLConfiguration;
        }
    }

    private static final String SOFTWARE_TYPE = "JAMES " + VERSION + " Server ";
    private static final String DEFAULT_TIME_UNIT = "SECONDS";
    private static final String CAPABILITY_SEPARATOR = "|";
    public static final int DEFAULT_MAX_LINE_LENGTH = 65536; // Use a big default
    public static final Size DEFAULT_IN_MEMORY_SIZE_LIMIT = Size.of(10L, Size.Unit.M); // Use 10MB as default
    public static final int DEFAULT_TIMEOUT = 30 * 60; // default timeout is 30 minutes
    public static final int DEFAULT_LITERAL_SIZE_LIMIT = 0;

    private final ImapProcessor processor;
    private final ImapEncoder encoder;
    private final ImapDecoder decoder;
    private final ImapMetrics imapMetrics;
    private final GaugeRegistry gaugeRegistry;

    private String hello;
    private boolean compress;
    private int maxLineLength;
    private int inMemorySizeLimit;
    private int timeout;
    private int literalSizeLimit;
    private AuthenticationConfiguration authenticationConfiguration;
    private Optional<ConnectionLimitUpstreamHandler> connectionLimitUpstreamHandler = Optional.empty();
    private Optional<ConnectionPerIpLimitUpstreamHandler> connectionPerIpLimitUpstreamHandler = Optional.empty();
    private boolean ignoreIDLEUponProcessing;
    private Duration heartbeatInterval;
    private ReactiveThrottler reactiveThrottler;


    public IMAPServer(ImapDecoder decoder, ImapEncoder encoder, ImapProcessor processor, ImapMetrics imapMetrics, GaugeRegistry gaugeRegistry) {
        this.processor = processor;
        this.encoder = encoder;
        this.decoder = decoder;
        this.imapMetrics = imapMetrics;
        this.gaugeRegistry = gaugeRegistry;
    }

    @Override
    public void doConfigure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        
        super.doConfigure(configuration);
        
        hello = SOFTWARE_TYPE + getHelloName() + " is ready.";
        compress = configuration.getBoolean("compress", false);
        maxLineLength = configuration.getInt("maxLineLength", DEFAULT_MAX_LINE_LENGTH);
        inMemorySizeLimit = Math.toIntExact(Optional.ofNullable(configuration.getString("inMemorySizeLimit", null))
            .map(Size::parse)
            .orElse(DEFAULT_IN_MEMORY_SIZE_LIMIT)
            .asBytes());
        literalSizeLimit = parseLiteralSizeLimit(configuration);

        timeout = configuration.getInt("timeout", DEFAULT_TIMEOUT);
        if (timeout < DEFAULT_TIMEOUT) {
            throw new ConfigurationException("Minimum timeout of 30 minutes required. See rfc2060 5.4 for details");
        }
        authenticationConfiguration = AuthenticationConfiguration.parse(configuration);
        connectionLimitUpstreamHandler = ConnectionLimitUpstreamHandler.forCount(connectionLimit);
        connectionPerIpLimitUpstreamHandler = ConnectionPerIpLimitUpstreamHandler.forCount(connPerIP);
        ignoreIDLEUponProcessing = configuration.getBoolean("ignoreIDLEUponProcessing", true);
        ImapConfiguration imapConfiguration = getImapConfiguration(configuration);
        heartbeatInterval = imapConfiguration.idleTimeIntervalAsDuration();
        reactiveThrottler = new ReactiveThrottler(gaugeRegistry, imapConfiguration.getConcurrentRequests(), imapConfiguration.getMaxQueueSize());
        processor.configure(imapConfiguration);
    }

    private static Integer parseLiteralSizeLimit(HierarchicalConfiguration<ImmutableNode> configuration) {
        return Optional.ofNullable(configuration.getString("literalSizeLimit", null))
            .map(Size::parse)
            .map(Size::asBytes)
            .map(Math::toIntExact)
            .orElse(DEFAULT_LITERAL_SIZE_LIMIT);
    }

    @VisibleForTesting static ImapConfiguration getImapConfiguration(HierarchicalConfiguration<ImmutableNode> configuration) {
        ImmutableSet<String> disabledCaps = ImmutableSet.copyOf(Splitter.on(CAPABILITY_SEPARATOR).split(configuration.getString("disabledCaps", "")));

        return ImapConfiguration.builder()
                .enableIdle(configuration.getBoolean("enableIdle", ImapConfiguration.DEFAULT_ENABLE_IDLE))
                .idleTimeInterval(configuration.getLong("idleTimeInterval", ImapConfiguration.DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS))
                .idleTimeIntervalUnit(getTimeIntervalUnit(configuration.getString("idleTimeIntervalUnit", DEFAULT_TIME_UNIT)))
                .disabledCaps(disabledCaps)
                .appendLimit(Optional.of(parseLiteralSizeLimit(configuration)).filter(i -> i > 0))
                .maxQueueSize(configuration.getInteger("maxQueueSize", ImapConfiguration.DEFAULT_QUEUE_SIZE))
                .concurrentRequests(configuration.getInteger("concurrentRequests", ImapConfiguration.DEFAULT_CONCURRENT_REQUESTS))
                .withCustomProperties(configuration.getProperties("customProperties"))
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
    protected AbstractChannelPipelineFactory createPipelineFactory() {
        
        return new AbstractChannelPipelineFactory(getFrameHandlerFactory(), getExecutorGroup()) {

            @Override
            protected ChannelInboundHandlerAdapter createHandler() {
                return createCoreHandler();
            }

            @Override
            public void initChannel(Channel channel) {
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast(TIMEOUT_HANDLER, new ImapIdleStateHandler(timeout));

                connectionLimitUpstreamHandler.ifPresent(handler -> pipeline.addLast(HandlerConstants.CONNECTION_LIMIT_HANDLER, handler));
                connectionPerIpLimitUpstreamHandler.ifPresent(handler -> pipeline.addLast(HandlerConstants.CONNECTION_LIMIT_PER_IP_HANDLER, handler));

                if (proxyRequired) {
                    pipeline.addLast(HandlerConstants.PROXY_HANDLER, new HAProxyMessageDecoder());
                    pipeline.addLast("proxyInformationHandler", new HAProxyMessageHandler());
                }

                // Add the text line decoder which limit the max line length,
                // don't strip the delimiter and use CRLF as delimiter
                // Use a SwitchableDelimiterBasedFrameDecoder, see JAMES-1436
                pipeline.addLast(FRAMER, getFrameHandlerFactory().create(pipeline));
               
                Encryption secure = getEncryption();
                if (secure != null && !secure.isStartTLS()) {
                    if (proxyRequired) {
                        channel.pipeline().addAfter("proxyInformationHandler", SSL_HANDLER, secure.sslHandler());
                    } else {
                        channel.pipeline().addFirst(SSL_HANDLER, secure.sslHandler());
                    }
                }

                pipeline.addLast(CHUNK_WRITE_HANDLER, new ChunkedWriteHandler());

                pipeline.addLast(REQUEST_DECODER, new ImapRequestFrameDecoder(decoder, inMemorySizeLimit,
                    literalSizeLimit, maxLineLength));

                pipeline.addLast(CORE_HANDLER, createCoreHandler());
            }

        };
    }

    @Override
    protected String getDefaultJMXName() {
        return "imapserver";
    }

    @Override
    protected ChannelInboundHandlerAdapter createCoreHandler() {
        Encryption secure = getEncryption();
        return ImapChannelUpstreamHandler.builder()
            .reactiveThrottler(reactiveThrottler)
            .hello(hello)
            .processor(processor)
            .encoder(encoder)
            .compress(compress)
            .authenticationConfiguration(authenticationConfiguration)
            .secure(secure)
            .imapMetrics(imapMetrics)
            .heartbeatInterval(heartbeatInterval)
            .ignoreIDLEUponProcessing(ignoreIDLEUponProcessing)
            .build();
    }

    @Override
    protected ChannelHandlerFactory createFrameHandlerFactory() {
        return new SwitchableLineBasedFrameDecoderFactory(maxLineLength);
    }

}
