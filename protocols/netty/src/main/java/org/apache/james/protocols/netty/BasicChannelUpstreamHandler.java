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

import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLEngine;

import org.apache.james.protocols.api.ProtocolSessionImpl;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.future.FutureResponse;
import org.apache.james.protocols.api.handler.ConnectHandler;
import org.apache.james.protocols.api.handler.DisconnectHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.ProtocolHandlerChain;
import org.apache.james.protocols.api.handler.ProtocolHandlerResultHandler;
import org.apache.james.protocols.netty.NettyProtocolTransport;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;

/**
 * {@link ChannelUpstreamHandler} which is used by the SMTPServer and other line based protocols
 */
@Sharable
public class BasicChannelUpstreamHandler extends SimpleChannelUpstreamHandler {
    protected final Protocol protocol;
    protected final ProtocolHandlerChain chain;
    protected final Encryption secure;

    public BasicChannelUpstreamHandler(Protocol protocol) {
        this(protocol, null);
    }

    public BasicChannelUpstreamHandler(Protocol protocol, Encryption secure) {
        this.protocol = protocol;
        this.chain = protocol.getProtocolChain();
        this.secure = secure;
    }


    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.setAttachment(createSession(ctx));
        super.channelBound(ctx, e);
    }



    /**
     * Call the {@link ConnectHandler} instances which are stored in the {@link ProtocolHandlerChain}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        List<ConnectHandler> connectHandlers = chain.getHandlers(ConnectHandler.class);
        List<ProtocolHandlerResultHandler> resultHandlers = chain.getHandlers(ProtocolHandlerResultHandler.class);
        ProtocolSession session = (ProtocolSession) ctx.getAttachment();
        session.getLogger().info("Connection established from " + session.getRemoteAddress().getAddress().getHostAddress());
        if (connectHandlers != null) {
            for (int i = 0; i < connectHandlers.size(); i++) {
                ConnectHandler cHandler = connectHandlers.get(i);
                
                long start = System.currentTimeMillis();
                Response response = connectHandlers.get(i).onConnect(session);
                long executionTime = System.currentTimeMillis() - start;
                
                for (int a = 0; a < resultHandlers.size(); a++) {
                    // Disable till PROTOCOLS-37 is implemented
                    if (response instanceof FutureResponse) {
                        session.getLogger().debug("ProtocolHandlerResultHandler are not supported for FutureResponse yet");
                        break;
                    } 
                    resultHandlers.get(a).onResponse(session, response, executionTime, cHandler);
                }
                if (response != null) {
                    // TODO: This kind of sucks but I was able to come up with something more elegant here
                    ((ProtocolSessionImpl)session).getProtocolTransport().writeResponse(response, session);
                }
               
            }
        }
        super.channelConnected(ctx, e);
    }



    @SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        List<DisconnectHandler> connectHandlers = chain.getHandlers(DisconnectHandler.class);
        ProtocolSession session = (ProtocolSession) ctx.getAttachment();
        if (connectHandlers != null) {
            for (int i = 0; i < connectHandlers.size(); i++) {
                connectHandlers.get(i).onDisconnect(session);
            }
        }
        super.channelDisconnected(ctx, e);
    }


    /**
     * Call the {@link LineHandler} 
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ProtocolSession pSession = (ProtocolSession) ctx.getAttachment();
        LinkedList<LineHandler> lineHandlers = chain.getHandlers(LineHandler.class);
        LinkedList<ProtocolHandlerResultHandler> resultHandlers = chain.getHandlers(ProtocolHandlerResultHandler.class);

        
        if (lineHandlers.size() > 0) {
        
            ChannelBuffer buf = (ChannelBuffer) e.getMessage();      
            
            LineHandler lHandler=  (LineHandler) lineHandlers.getLast();
            long start = System.currentTimeMillis();            
            Response response = lHandler.onLine(pSession,buf.toByteBuffer());
            long executionTime = System.currentTimeMillis() - start;

            for (int i = 0; i < resultHandlers.size(); i++) {
                // Disable till PROTOCOLS-37 is implemented
                if (response instanceof FutureResponse) {
                    pSession.getLogger().debug("ProtocolHandlerResultHandler are not supported for FutureResponse yet");
                    break;
                } 
                response = resultHandlers.get(i).onResponse(pSession, response, executionTime, lHandler);
            }
            if (response != null) {
                // TODO: This kind of sucks but I was able to come up with something more elegant here
                ((ProtocolSessionImpl)pSession).getProtocolTransport().writeResponse(response, pSession);
            }

        }
        
        super.messageReceived(ctx, e);
    }


    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ProtocolSession session = (ProtocolSession) ctx.getAttachment();
        if (session != null) {
            session.getLogger().info("Connection closed for " + session.getRemoteAddress().getAddress().getHostAddress());
        }
        cleanup(ctx);

        super.channelClosed(ctx, e);
    }

    /**
     * Cleanup the channel
     * 
     * @param ctx
     */
    protected void cleanup(ChannelHandlerContext ctx) {
        ProtocolSession session = (ProtocolSession) ctx.getAttachment();
        if (session != null) {
            session.resetState();
            session = null;
        }
    }

    
    
    protected ProtocolSession createSession(ChannelHandlerContext ctx) throws Exception {
        SSLEngine engine = null;
        if (secure != null) {
            engine = secure.getContext().createSSLEngine();
            String[] enabledCipherSuites = secure.getEnabledCipherSuites();
            if (enabledCipherSuites != null && enabledCipherSuites.length > 0) {
                engine.setEnabledCipherSuites(enabledCipherSuites);
            }
        }
        
        return protocol.newSession(new NettyProtocolTransport(ctx.getChannel(), engine));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Channel channel = ctx.getChannel();
        ProtocolSession session = (ProtocolSession) ctx.getAttachment();
        if (e.getCause() instanceof TooLongFrameException && session != null) {
            Response r = session.newLineTooLongResponse();
            ProtocolTransport transport = ((ProtocolSessionImpl)session).getProtocolTransport();
            if (r != null)  {
                transport.writeResponse(r, session);
            }
        } else {
            if (channel.isConnected() && session != null) {
                ProtocolTransport transport = ((ProtocolSessionImpl)session).getProtocolTransport();

                Response r = session.newFatalErrorResponse();
                if (r != null) {
                    transport.writeResponse(r, session);
                } 
                transport.writeResponse(Response.DISCONNECT, session);
            }
            if (session != null) {
                session.getLogger().debug("Unable to process request", e.getCause());
            }
            cleanup(ctx);            
        }
    }

}
