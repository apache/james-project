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
package org.apache.james.lmtpserver.netty;

import java.util.Optional;
import java.util.Properties;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.lmtpserver.CoreCmdHandlerLoader;
import org.apache.james.lmtpserver.jmx.JMXHandlersLoader;
import org.apache.james.protocols.api.OidcSASLConfiguration;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.lib.netty.AbstractProtocolAsyncServer;
import org.apache.james.protocols.lmtp.LMTPConfiguration;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.LineDelimiterBasedChannelHandlerFactory;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.smtpserver.ExtendedSMTPSession;
import org.apache.james.smtpserver.netty.SMTPChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelInboundHandlerAdapter;

public class LMTPServer extends AbstractProtocolAsyncServer implements LMTPServerMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(LMTPServer.class);

    /**
     * The maximum message size allowed by this SMTP server. The default value,
     * 0, means no limit.
     */
    private long maxMessageSize = 0;
    private final LMTPConfigurationImpl lmtpConfig = new LMTPConfigurationImpl();
    private final LMTPMetricsImpl lmtpMetrics;
    private String lmtpGreeting;

    public LMTPServer(LMTPMetricsImpl lmtpMetrics) {
        this.lmtpMetrics = lmtpMetrics;
    }

    @Override
    public int getDefaultPort() {
        return 24;
    }

    @Override
    public String getServiceType() {
        return "LMTP Service";
    }

    @Override
    public void doConfigure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        super.doConfigure(configuration);
        if (isEnabled()) {

            // get the message size limit from the conf file and multiply
            // by 1024, to put it in bytes
            maxMessageSize = configuration.getLong("maxmessagesize", maxMessageSize) * 1024;
            if (maxMessageSize > 0) {
                LOGGER.info("The maximum allowed message size is {} bytes.", maxMessageSize);
            } else {
                LOGGER.info("No maximum message size is enforced for this server.");
            }

            // get the lmtpGreeting
            lmtpGreeting = configuration.getString("lmtpGreeting", null);

        }
    }

    /**
     * A class to provide SMTP handler configuration to the handlers
     */
    public class LMTPConfigurationImpl extends LMTPConfiguration {

        protected LMTPConfigurationImpl() {
            super("JAMES Protocols Server");
        }
        
        @Override
        public String getHelloName() {
            return LMTPServer.this.getHelloName();
        }

        @Override
        public long getMaxMessageSize() {
            return LMTPServer.this.maxMessageSize;
        }

        public String getSMTPGreeting() {
            return LMTPServer.this.lmtpGreeting;
        }

        @Override
        public boolean isPlainAuthEnabled() {
            return false;
        }

        @Override
        public Optional<OidcSASLConfiguration> saslConfiguration() {
            return Optional.empty();
        }

        @Override
        public Properties customProperties() {
            return null;
        }
    }

    @Override
    public long getMaximalMessageSize() {
        return lmtpConfig.getMaxMessageSize();
    }

    @Override
    protected String getDefaultJMXName() {
        return "lmtpserver";
    }

    @Override
    public void setMaximalMessageSize(long maxSize) {
        maxMessageSize = maxSize;
    }

    @Override
    public String getHeloName() {
        return lmtpConfig.getHelloName();
    }

    @Override
    protected ChannelInboundHandlerAdapter createCoreHandler() {
        SMTPProtocol transport = new SMTPProtocol(getProtocolHandlerChain(), lmtpConfig) {
            @Override
            public ProtocolSession newSession(ProtocolTransport transport) {
                return new ExtendedSMTPSession(lmtpConfig, transport);
            }
        };
        return new SMTPChannelInboundHandler(transport, lmtpMetrics);
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
        return new LineDelimiterBasedChannelHandlerFactory(AbstractChannelPipelineFactory.MAX_LINE_LENGTH);
    }

}
