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

import static org.apache.james.imapserver.netty.HAProxyMessageHandler.PROXY_INFO;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.ConnectionDescription;
import org.apache.james.core.ConnectionDescriptionSupplier;
import org.apache.james.core.Disconnector;
import org.apache.james.core.Username;
import org.apache.james.imap.api.ConnectionCheck;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.traffic.AbstractTrafficShapingHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;


/**
 * NIO IMAP Server which use Netty.
 */
public class IMAPServer extends AbstractConfigurableAsyncServer implements ImapConstants, IMAPServerMBean, NettyConstants,
    Disconnector, ConnectionDescriptionSupplier {
    private static final Logger LOG = LoggerFactory.getLogger(IMAPServer.class);
    public static final AttributeKey<Instant> CONNECTION_DATE = AttributeKey.newInstance("connectionDate");

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
                } catch (MalformedURLException | NullPointerException | URISyntaxException exception) {
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
    private final Set<ConnectionCheck> connectionChecks;
    private final DefaultChannelGroup imapChannelGroup;

    private String hello;
    private boolean compress;
    private int maxLineLength;
    private int inMemorySizeLimit;
    private int timeout;
    private int literalSizeLimit;
    private AuthenticationConfiguration authenticationConfiguration;
    private Optional<TrafficShapingConfiguration> trafficShaping = Optional.empty();
    private Optional<ConnectionLimitUpstreamHandler> connectionLimitUpstreamHandler = Optional.empty();
    private Optional<ConnectionPerIpLimitUpstreamHandler> connectionPerIpLimitUpstreamHandler = Optional.empty();
    private Optional<IMAPCommandsThrottler.ThrottlerConfiguration> throttlerConfiguration = Optional.empty();
    private boolean ignoreIDLEUponProcessing;
    private Duration heartbeatInterval;
    private ReactiveThrottler reactiveThrottler;


    public IMAPServer(ImapDecoder decoder, ImapEncoder encoder, ImapProcessor processor, ImapMetrics imapMetrics, GaugeRegistry gaugeRegistry, Set<ConnectionCheck> connectionChecks) {
        this.processor = processor;
        this.encoder = encoder;
        this.decoder = decoder;
        this.imapMetrics = imapMetrics;
        this.gaugeRegistry = gaugeRegistry;
        this.connectionChecks = connectionChecks;
        this.imapChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
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
        if (configuration.getKeys("trafficShaping").hasNext()) {
            trafficShaping = Optional.ofNullable(configuration.configurationAt("trafficShaping"))
                .map(TrafficShapingConfiguration::from);
        }
        if (configuration.getKeys("perSessionCommandThrottling").hasNext()) {
            throttlerConfiguration = Optional.ofNullable(configuration.configurationAt("perSessionCommandThrottling"))
                .map(IMAPCommandsThrottler.ThrottlerConfiguration::from);
        }
    }

    @Override
    public void disconnect(Predicate<Username> user) {
        imapChannelGroup.stream()
            .filter(channel -> Optional.ofNullable(channel.attr(IMAP_SESSION_ATTRIBUTE_KEY).get())
                .flatMap(session -> Optional.ofNullable(session.getUserName()))
                .map(user::test)
                .orElse(false))
            .forEach(channel -> channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE));
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
                .isProvisionDefaultMailboxes(configuration.getBoolean("provisionDefaultMailboxes", ImapConfiguration.DEFAULT_PROVISION_DEFAULT_MAILBOXES))
                .withCustomProperties(configuration.getProperties("customProperties"))
                .idFieldsResponse(getIdCommandResponseFields(configuration))
                .build();
    }

    private static ImmutableMap<String, String> getIdCommandResponseFields(HierarchicalConfiguration<ImmutableNode> configuration) {
        LinkedHashMap<String, String> fieldsMap = new LinkedHashMap<>();
        configuration.configurationsAt("idCommandResponse.field")
            .forEach(field -> {
                String name = field.getString("[@name]");
                String value = field.getString("[@value]");
                fieldsMap.put(name, value);
            });
        return ImmutableMap.copyOf(fieldsMap);
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
                channel.attr(CONNECTION_DATE).set(Clock.systemUTC().instant());

                ChannelPipeline pipeline = channel.pipeline();
                channel.config().setWriteBufferWaterMark(writeBufferWaterMark);
                pipeline.addLast(TIMEOUT_HANDLER, new ImapIdleStateHandler(timeout));

                connectionLimitUpstreamHandler.ifPresent(handler -> pipeline.addLast(HandlerConstants.CONNECTION_LIMIT_HANDLER, handler));
                connectionPerIpLimitUpstreamHandler.ifPresent(handler -> pipeline.addLast(HandlerConstants.CONNECTION_LIMIT_PER_IP_HANDLER, handler));

                if (proxyRequired) {
                    pipeline.addLast(HandlerConstants.PROXY_HANDLER, new HAProxyMessageDecoder());
                    pipeline.addLast("proxyInformationHandler", new HAProxyMessageHandler(connectionChecks));
                }

                // Add the text line decoder which limit the max line length,
                // don't strip the delimiter and use CRLF as delimiter
                // Use a SwitchableDelimiterBasedFrameDecoder, see JAMES-1436
                pipeline.addLast(FRAMER, getFrameHandlerFactory().create(pipeline));
               
                Encryption secure = getEncryption();
                if (secure != null && !secure.isStartTLS()) {
                    if (proxyRequired && proxyFirst) {
                        channel.pipeline().addAfter("proxyInformationHandler", SSL_HANDLER, secure.sslHandler());
                    } else {
                        channel.pipeline().addFirst(SSL_HANDLER, secure.sslHandler());
                    }
                }
                trafficShaping.map(TrafficShapingConfiguration::newHandler)
                    .ifPresent(handler -> pipeline.addLast("trafficShaping",handler));

                pipeline.addLast(CHUNK_WRITE_HANDLER, new ChunkedWriteHandler());

                pipeline.addLast(REQUEST_DECODER, new ImapRequestFrameDecoder(decoder, inMemorySizeLimit,
                    literalSizeLimit, maxLineLength));

                throttlerConfiguration.map(IMAPCommandsThrottler::new)
                    .ifPresent(handler -> pipeline.addLast("commandThrottler", handler));

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
            .connectionChecks(connectionChecks)
            .secure(secure)
            .imapMetrics(imapMetrics)
            .heartbeatInterval(heartbeatInterval)
            .ignoreIDLEUponProcessing(ignoreIDLEUponProcessing)
            .proxyRequired(proxyRequired)
            .imapChannelGroup(imapChannelGroup)
            .build();
    }

    @Override
    protected ChannelHandlerFactory createFrameHandlerFactory() {
        return new SwitchableLineBasedFrameDecoderFactory(maxLineLength);
    }

    public Set<ConnectionCheck> getConnectionChecks() {
        return this.connectionChecks;
    }

    public boolean isReactiveThrottlerQueueFull() {
        return reactiveThrottler.isQueueFull();
    }

    @VisibleForTesting
    ReactiveThrottler getReactiveThrottler() {
        return reactiveThrottler;
    }

    @Override
    public Stream<ConnectionDescription> describeConnections() {
        return imapChannelGroup.stream()
            .map(channel -> {
                Optional<ImapSession> imapSession = Optional.ofNullable(channel.attr(IMAP_SESSION_ATTRIBUTE_KEY).get());
                Optional<TrafficCounter> trafficCounter = Optional.ofNullable(channel.pipeline().get(ChannelTrafficShapingHandler.class))
                    .map(AbstractTrafficShapingHandler::trafficCounter);
                return new ConnectionDescription(
                    "IMAP",
                    jmxName,
                    Optional.ofNullable(channel.attr(PROXY_INFO)).flatMap(attr -> Optional.ofNullable(attr.get()))
                        .map(proxyInfo -> (SocketAddress) proxyInfo.getSource())
                        .or(() -> Optional.ofNullable(channel.remoteAddress()))
                        .map(this::addressAsString),
                    Optional.ofNullable(channel.attr(CONNECTION_DATE)).flatMap(attribute -> Optional.ofNullable(attribute.get())),
                    channel.isActive(),
                    channel.isOpen(),
                    channel.isWritable(),
                    imapSession.map(ImapSession::isTLSActive).orElse(false),
                    imapSession.flatMap(session -> Optional.ofNullable(session.getUserName())),
                    ImmutableMap.<String, String>builder()
                        .put("loggedInUser", imapSession.flatMap(s -> Optional.ofNullable(s.getMailboxSession()))
                            .flatMap(MailboxSession::getLoggedInUser)
                            .map(Username::asString)
                            .orElse(""))
                        .put("isCompressed", Boolean.toString(imapSession.map(ImapSession::isCompressionActive).orElse(false)))
                        .put("selectedMailbox", imapSession.flatMap(session -> Optional.ofNullable(session.getSelected()))
                            .map(SelectedMailbox::getMailboxId)
                            .map(MailboxId::serialize)
                            .orElse(""))
                        .put("isIdling", Boolean.toString(imapSession.flatMap(session -> Optional.ofNullable(session.getSelected()))
                            .map(SelectedMailbox::isIdling)
                            .orElse(false)))
                        .put("requestCount", Long.toString(Optional.ofNullable(channel.attr(REQUEST_COUNTER))
                            .flatMap(attribute -> Optional.ofNullable(attribute.get()))
                            .map(AtomicLong::get)
                            .orElse(0L)))
                        .put("userAgent", imapSession.flatMap(s -> Optional.ofNullable(s.getAttribute("userAgent")))
                            .map(Object::toString)
                            .orElse(""))
                        .put("cumulativeWrittenBytes", Long.toString(trafficCounter.map(TrafficCounter::cumulativeWrittenBytes).orElse(0L)))
                        .put("cumulativeReadBytes", Long.toString(trafficCounter.map(TrafficCounter::cumulativeReadBytes).orElse(0L)))
                        .put("liveReadThroughputBytePerSecond", Long.toString(trafficCounter.map(TrafficCounter::lastReadThroughput).orElse(0L)))
                        .put("liveWriteThroughputBytePerSecond", Long.toString(trafficCounter.map(TrafficCounter::lastWriteThroughput).orElse(0L)))
                        .build());
            });
    }

    private String addressAsString(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress address) {
            return address.getAddress().getHostAddress();
        }
        return socketAddress.toString();
    }
}
