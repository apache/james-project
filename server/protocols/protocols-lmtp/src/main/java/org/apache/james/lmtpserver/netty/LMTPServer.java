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


import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lmtpserver.CoreCmdHandlerLoader;
import org.apache.james.lmtpserver.jmx.JMXHandlersLoader;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.lib.netty.AbstractProtocolAsyncServer;
import org.apache.james.protocols.lmtp.LMTPConfiguration;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.LineDelimiterBasedChannelHandlerFactory;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.smtpserver.netty.SMTPChannelUpstreamHandler;
import org.jboss.netty.channel.ChannelUpstreamHandler;

public class LMTPServer extends AbstractProtocolAsyncServer implements LMTPServerMBean {

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

    /**
     * @see
     * org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer#getDefaultPort()
     */
    public int getDefaultPort() {
        return 24;
    }

    /**
     * @see org.apache.james.protocols.lib.jmx.ServerMBean#getServiceType()
     */
    public String getServiceType() {
        return "LMTP Service";
    }

    public void doConfigure(HierarchicalConfiguration configuration) throws ConfigurationException {
        super.doConfigure(configuration);
        if (isEnabled()) {

            // get the message size limit from the conf file and multiply
            // by 1024, to put it in bytes
            maxMessageSize = configuration.getLong("maxmessagesize", maxMessageSize) * 1024;
            if (maxMessageSize > 0) {
                getLogger().info("The maximum allowed message size is " + maxMessageSize + " bytes.");
            } else {
                getLogger().info("No maximum message size is enforced for this server.");
            }

            // get the lmtpGreeting
            lmtpGreeting = configuration.getString("lmtpGreeting", null);

        }
    }

    /**
     * A class to provide SMTP handler configuration to the handlers
     */
    public class LMTPConfigurationImpl extends LMTPConfiguration {

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#getHelloName()
         */
        public String getHelloName() {
            return LMTPServer.this.getHelloName();
        }

      

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#getMaxMessageSize()
         */
        public long getMaxMessageSize() {
            return LMTPServer.this.maxMessageSize;
        }

        /**
         */
        public String getSMTPGreeting() {
            return LMTPServer.this.lmtpGreeting;
        }
       
    }

    /**
     * @see LMTPServerMBean#getMaximalMessageSize()
     */
    public long getMaximalMessageSize() {
        return lmtpConfig.getMaxMessageSize();
    }

    /**
     * @see
     * org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer#getDefaultJMXName()
     */
    protected String getDefaultJMXName() {
        return "lmtpserver";
    }

    /**
     * @see
     * org.apache.james.smtpserver.netty.SMTPServerMBean#setMaximalMessageSize(long)
     */
    public void setMaximalMessageSize(long maxSize) {
        maxMessageSize = maxSize;
    }

    /**
     * @see org.apache.james.lmtpserver.netty.LMTPServerMBean#getHeloName()
     */
    public String getHeloName() {
        return lmtpConfig.getHelloName();
    }

    @Override
    protected ChannelUpstreamHandler createCoreHandler() {
        SMTPProtocol protocol = new SMTPProtocol(getProtocolHandlerChain(), lmtpConfig, getLogger());
        return new SMTPChannelUpstreamHandler(protocol, getLogger(), lmtpMetrics);
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
