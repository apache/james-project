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

import static org.apache.james.protocols.api.ProtocolSession.State.Connection;

import java.io.Closeable;
import java.nio.channels.ClosedChannelException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.net.ssl.SSLEngine;

import org.apache.james.protocols.api.CommandDetectionSession;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSessionImpl;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.api.handler.DisconnectHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.ProtocolHandlerChain;
import org.apache.james.protocols.api.handler.ProtocolHandlerResultHandler;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.AttributeKey;

/**
 * {@link ChannelInboundHandlerAdapter} which is used by the SMTPServer and other line based protocols
 */
public class BasicChannelUpstreamHandler extends ChannelInboundHandlerAdapter implements LineHandlerAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicChannelUpstreamHandler.class);
    public static final ProtocolSession.AttachmentKey<MDCBuilder> MDC_ATTRIBUTE_KEY = ProtocolSession.AttachmentKey.of("bound_MDC", MDCBuilder.class);
    public static final AttributeKey<CommandDetectionSession> SESSION_ATTRIBUTE_KEY =
            AttributeKey.valueOf("session");

    private final ProtocolMDCContextFactory mdcContextFactory;
    protected final Protocol protocol;
    protected final ProtocolHandlerChain chain;
    protected final Encryption secure;
    private final Deque<ChannelInboundHandlerAdapter> behaviourOverrides = new ConcurrentLinkedDeque<>();

    public BasicChannelUpstreamHandler(ProtocolMDCContextFactory mdcContextFactory, Protocol protocol) {
        this(mdcContextFactory, protocol, null);
    }

    public BasicChannelUpstreamHandler(ProtocolMDCContextFactory mdcContextFactory, Protocol protocol, Encryption secure) {
        this.mdcContextFactory = mdcContextFactory;
        this.protocol = protocol;
        this.chain = protocol.getProtocolChain();
        this.secure = secure;
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        MDCBuilder boundMDC = mdcContextFactory.onBound(protocol, ctx);
        try (Closeable closeable = boundMDC.build()) {
            ProtocolSession session = createSession(ctx);
            session.setAttachment(MDC_ATTRIBUTE_KEY, boundMDC, Connection);
            ctx.channel().attr(SESSION_ATTRIBUTE_KEY).set(session);

            List<ConnectHandler> connectHandlers = chain.getHandlers(ConnectHandler.class);
            List<ProtocolHandlerResultHandler> resultHandlers = chain.getHandlers(ProtocolHandlerResultHandler.class);

            LOGGER.info("Connection established from {}", session.getRemoteAddress().getAddress().getHostAddress());
            if (connectHandlers != null) {
                for (ConnectHandler cHandler : connectHandlers) {
                    long start = System.currentTimeMillis();
                    Response response = cHandler.onConnect(session);
                    long executionTime = System.currentTimeMillis() - start;

                    for (ProtocolHandlerResultHandler resultHandler : resultHandlers) {
                        resultHandler.onResponse(session, response, executionTime, cHandler);
                    }
                    if (response != null) {
                        // TODO: This kind of sucks but I was able to come up with something more elegant here
                        ((ProtocolSessionImpl) session).getProtocolTransport().writeResponse(response, session);
                    }

                }
            }

            super.channelActive(ctx);
        }
    }

    private MDCBuilder mdc(ChannelHandlerContext ctx) {
        ProtocolSession session = (ProtocolSession) ctx.channel().attr(SESSION_ATTRIBUTE_KEY).get();

        return Optional.ofNullable(session)
            .flatMap(s -> s.getAttachment(MDC_ATTRIBUTE_KEY, Connection))
            .map(mdc -> mdcContextFactory.withContext(session)
                .addToContext(mdc))
            .orElseGet(MDCBuilder::create);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try (Closeable closeable = mdc(ctx).build()) {
            List<DisconnectHandler> connectHandlers = chain.getHandlers(DisconnectHandler.class);
            ProtocolSession session = (ProtocolSession) ctx.channel().attr(SESSION_ATTRIBUTE_KEY).get();
            if (connectHandlers != null) {
                for (DisconnectHandler connectHandler : connectHandlers) {
                    connectHandler.onDisconnect(session);
                }
            }
            LOGGER.info("Connection closed for {}", session.getRemoteAddress().getAddress().getHostAddress());
            cleanup(ctx);
            super.channelInactive(ctx);
        }
    }


    /**
     * Call the {@link LineHandler} 
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ChannelInboundHandlerAdapter override = Iterables.getFirst(behaviourOverrides, null);
        if (override != null) {
            override.channelRead(ctx, msg);
            return;
        }

        try (Closeable closeable = mdc(ctx).build()) {
            ProtocolSession pSession = (ProtocolSession) ctx.channel().attr(SESSION_ATTRIBUTE_KEY).get();
            LinkedList<LineHandler> lineHandlers = chain.getHandlers(LineHandler.class);
            LinkedList<ProtocolHandlerResultHandler> resultHandlers = chain.getHandlers(ProtocolHandlerResultHandler.class);


            if (lineHandlers.size() > 0) {

                ByteBuf buf = (ByteBuf) msg;
                LineHandler lHandler = (LineHandler) lineHandlers.getLast();
                long start = System.currentTimeMillis();
                Response response = lHandler.onLine(pSession, buf.nioBuffer());
                long executionTime = System.currentTimeMillis() - start;

                for (ProtocolHandlerResultHandler resultHandler : resultHandlers) {
                    response = resultHandler.onResponse(pSession, response, executionTime, lHandler);
                }
                if (response != null) {
                    // TODO: This kind of sucks but I was able to come up with something more elegant here
                    ((ProtocolSessionImpl) pSession).getProtocolTransport().writeResponse(response, pSession);
                }

            }

            ((ByteBuf) msg).release();
            super.channelReadComplete(ctx);
        }
    }


    /**
     * Cleanup the channel
     */
    protected void cleanup(ChannelHandlerContext ctx) {
        ProtocolSession session = (ProtocolSession) ctx.channel().attr(SESSION_ATTRIBUTE_KEY).getAndRemove();
        if (session != null) {
            session.resetState();
        }
        ctx.close();
    }

    
    
    protected ProtocolSession createSession(ChannelHandlerContext ctx) throws Exception {
        SSLEngine engine = null;
        if (secure != null) {
            engine = secure.createSSLEngine();
        }

        return protocol.newSession(new NettyProtocolTransport(ctx.channel(), engine));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try (Closeable closeable = mdc(ctx).build()) {
            Channel channel = ctx.channel();
            ProtocolSession session = (ProtocolSession) ctx.channel().attr(SESSION_ATTRIBUTE_KEY).get();
            if (cause instanceof TooLongFrameException && session != null) {
                Response r = session.newLineTooLongResponse();
                ProtocolTransport transport = ((ProtocolSessionImpl) session).getProtocolTransport();
                if (r != null) {
                    transport.writeResponse(r, session);
                }
            } else {
                if (channel.isActive() && session != null) {
                    ProtocolTransport transport = ((ProtocolSessionImpl) session).getProtocolTransport();

                    Response r = session.newFatalErrorResponse();
                    if (r != null) {
                        transport.writeResponse(r, session);
                    }
                    transport.writeResponse(Response.DISCONNECT, session);
                }
                if (cause instanceof ClosedChannelException) {
                    LOGGER.info("Channel closed before we could send in flight messages to the users (ClosedChannelException): {}", cause.getMessage());
                } else {
                    LOGGER.error("Unable to process request", cause);
                }
                ctx.close();
            }
        }
    }

    @Override
    public void pushLineHandler(ChannelInboundHandlerAdapter lineHandlerUpstreamHandler) {
        behaviourOverrides.addFirst(lineHandlerUpstreamHandler);
    }

    @Override
    public void popLineHandler() {
        if (!behaviourOverrides.isEmpty()) {
            behaviourOverrides.removeFirst();
        }
    }

}
