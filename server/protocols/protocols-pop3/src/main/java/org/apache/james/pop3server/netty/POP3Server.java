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
package org.apache.james.pop3server.netty;

import static org.apache.james.protocols.sasl.plain.PlainSaslMechanism.ENABLED;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.pop3server.core.AuthCmdHandler;
import org.apache.james.pop3server.core.CoreCmdHandlerLoader;
import org.apache.james.pop3server.core.PassCmdHandler;
import org.apache.james.pop3server.jmx.JMXHandlersLoader;
import org.apache.james.protocols.api.ProtocolConfiguration;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.lib.netty.AbstractProtocolAsyncServer;
import org.apache.james.protocols.netty.AllButStartTlsLineChannelHandlerFactory;
import org.apache.james.protocols.netty.BasicChannelInboundHandler;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.ProtocolMDCContextFactory;
import org.apache.james.protocols.pop3.POP3Protocol;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;

import com.google.common.collect.ImmutableList;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.TooLongFrameException;

/**
 * NIO POP3 Server which use Netty
 */
public class POP3Server extends AbstractProtocolAsyncServer implements POP3ServerMBean {
    // RFC 5034 section 4 exempts SASL continuation responses from regular POP3 command limits.
    private static final int MAXIMUM_SASL_CONTINUATION_LINE_LENGTH = 65536;
    private static final boolean REQUIRE_SSL_DEFAULT = true;

    /**
     * The configuration data to be passed to the handler
     */
    private final ProtocolConfiguration theConfigData = new POP3Configuration();
    private POP3Protocol protocol;
    private ImmutableList<SaslMechanism> saslMechanisms = ImmutableList.of();
    private PlainSaslMechanism passSaslMechanism = new PlainSaslMechanism(true, REQUIRE_SSL_DEFAULT);

    private class Pop3ChannelInboundHandler extends BasicChannelInboundHandler {
        private Pop3ChannelInboundHandler() {
            super(new ProtocolMDCContextFactory.Standard(), POP3Server.this.protocol, POP3Server.this.getEncryption(), false);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
            try {
                super.exceptionCaught(context, cause);
            } finally {
                if (cause instanceof TooLongFrameException) {
                    // Flush the line-length error before closing. Channel teardown then releases
                    // any active SASL exchange instead of leaving its continuation installed.
                    context.channel().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
            }
        }
    }

    public void setSaslMechanisms(ImmutableList<SaslMechanism> saslMechanisms) {
        this.saslMechanisms = saslMechanisms;
    }

    @Override
    protected int getDefaultPort() {
        return 110;
    }

    @Override
    public String getServiceType() {
        return "POP3 Service";
    }

    /**
     * A class to provide POP3 handler configuration to the handlers
     */
    private class POP3Configuration implements ProtocolConfiguration {
        @Override
        public String getHelloName() {
            return POP3Server.this.getHelloName();
        }

        @Override
        public String getGreeting() {
            return null;
        }

        @Override
        public String getSoftwareName() {
            return "JAMES POP3 Server ";
        }
    }

    @Override
    protected void doConfigure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        super.doConfigure(config);
        passSaslMechanism = new PlainSaslMechanism(ENABLED, config.getBoolean("auth.requireSSL", REQUIRE_SSL_DEFAULT));
    }

    @Override
    protected void preInit() throws Exception {
        super.preInit();
        getProtocolHandlerChain()
            .getHandlers(AuthCmdHandler.class)
            .forEach(handler -> handler.configureSaslMechanisms(saslMechanisms));
        getProtocolHandlerChain()
            .getHandlers(PassCmdHandler.class)
            .forEach(handler -> handler.configurePlainSaslMechanism(passSaslMechanism));
        protocol = new POP3Protocol(getProtocolHandlerChain(), theConfigData);
    }

    @Override
    protected String getDefaultJMXName() {
        return "pop3server";
    }

    @Override
    protected ChannelInboundHandlerAdapter createCoreHandler() {
        // Unlike the basic handler, close after reporting an oversized SASL continuation so
        // channel teardown releases the active SASL exchange.
        return new Pop3ChannelInboundHandler();
    }

    @Override
    protected Class<? extends HandlersPackage> getCoreHandlersPackage() {
        return CoreCmdHandlerLoader.class;
    }

    @Override
    protected Class<? extends HandlersPackage> getJMXHandlersPackage() {
        return JMXHandlersLoader.class;
    }

    @Override
    protected ChannelHandlerFactory createFrameHandlerFactory() {
        // RFC 5034 section 4 excludes SASL response lines from regular POP3 line-length limits because
        // they can carry larger encoded mechanism payloads. Pop3CommandDispatcher still enforces the
        // regular POP3 command limit and the RFC 5034 limit for the initial AUTH command.
        return new AllButStartTlsLineChannelHandlerFactory("stls", MAXIMUM_SASL_CONTINUATION_LINE_LENGTH);
    }

}
