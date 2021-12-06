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
package org.apache.james.smtpserver.netty;

import static org.apache.james.smtpserver.netty.SMTPServer.AuthenticationRequired.ANNOUNCE;
import static org.apache.james.smtpserver.netty.SMTPServer.AuthenticationRequired.DISABLED;

import java.util.Locale;

import javax.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.lib.netty.AbstractProtocolAsyncServer;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.AllButStartTlsLineChannelHandlerFactory;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.smtpserver.CoreCmdHandlerLoader;
import org.apache.james.smtpserver.ExtendedSMTPSession;
import org.apache.james.smtpserver.jmx.JMXHandlersLoader;
import org.apache.james.util.Size;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NIO SMTPServer which use Netty
 */
public class SMTPServer extends AbstractProtocolAsyncServer implements SMTPServerMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProtocolAsyncServer.class);

    public enum AuthenticationRequired {
        DISABLED,
        REQUIRED,
        ANNOUNCE;

        public static AuthenticationRequired parse(String authRequiredString) {
            if (authRequiredString.equals("true")) {
                return REQUIRED;
            } else if (authRequiredString.equals("announce")) {
                return ANNOUNCE;
            } else {
                return DISABLED;
            }
        }
    }

    /**
     * Whether authentication is required to use this SMTP server.
     */
    private AuthenticationRequired authRequired = DISABLED;
    
    /**
     * Whether the server needs helo to be send first
     */
    private boolean heloEhloEnforcement = false;

    /**
     * SMTPGreeting to use
     */
    private String smtpGreeting = null;

    /**
     * This is a Network Matcher that should be configured to contain authorized
     * networks that bypass SMTP AUTH requirements.
     */
    private NetMatcher authorizedNetworks = null;

    /**
     * The maximum message size allowed by this SMTP server. The default value,
     * 0, means no limit.
     */
    private long maxMessageSize = 0;

    /**
     * The configuration data to be passed to the handler
     */
    private final SMTPConfiguration theConfigData = new SMTPHandlerConfigurationDataImpl();
    private final SmtpMetrics smtpMetrics;

    private boolean addressBracketsEnforcement = true;

    private boolean verifyIdentity;

    private DNSService dns;
    private String authorizedAddresses;
    
    private SMTPChannelUpstreamHandler coreHandler;

    public SMTPServer(SmtpMetrics smtpMetrics) {
        this.smtpMetrics = smtpMetrics;
    }

    @Inject
    public void setDnsService(DNSService dns) {
        this.dns = dns;
    }
    
    @Override
    protected void preInit() throws Exception {
        super.preInit();
        if (authorizedAddresses != null) {
            java.util.StringTokenizer st = new java.util.StringTokenizer(authorizedAddresses, ", ", false);
            java.util.Collection<String> networks = new java.util.ArrayList<>();
            while (st.hasMoreTokens()) {
                String addr = st.nextToken();
                networks.add(addr);
            }
            authorizedNetworks = new NetMatcher(networks, dns);
        }
        SMTPProtocol transport = new SMTPProtocol(getProtocolHandlerChain(), theConfigData) {

            @Override
            public ProtocolSession newSession(ProtocolTransport transport) {
                return new ExtendedSMTPSession(theConfigData, transport);
            }
            
        };
        coreHandler = new SMTPChannelUpstreamHandler(transport, getEncryption(), smtpMetrics);
    }

    @Override
    public void doConfigure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        super.doConfigure(configuration);
        if (isEnabled()) {
            String authRequiredString = configuration.getString("authRequired", "false").trim().toLowerCase(Locale.US);
            authRequired = AuthenticationRequired.parse(authRequiredString);
            if (authRequired != DISABLED) {
                LOGGER.info("This SMTP server requires authentication.");
            } else {
                LOGGER.info("This SMTP server does not require authentication.");
            }

            authorizedAddresses = configuration.getString("authorizedAddresses", null);
            if (authRequired == DISABLED && authorizedAddresses == null) {
                /*
                 * if SMTP AUTH is not required then we will use
                 * authorizedAddresses to determine whether or not to relay
                 * e-mail. Therefore if SMTP AUTH is not required, we will not
                 * relay e-mail unless the sending IP address is authorized.
                 * 
                 * Since this is a change in behavior for James v2, create a
                 * default authorizedAddresses network of 0.0.0.0/0, which
                 * matches all possible addresses, thus preserving the current
                 * behavior.
                 * 
                 * James v3 should require the <authorizedAddresses> element.
                 */
                authorizedAddresses = "0.0.0.0/0.0.0.0";
            }

          
            if (authorizedNetworks != null) {
                LOGGER.info("Authorized addresses: {}", authorizedNetworks);
            }

            // get the message size limit from the conf file and multiply
            // by 1024, to put it in bytes
            maxMessageSize = Size.parse(configuration.getString("maxmessagesize", "0"), Size.Unit.K)
                .asBytes();
            if (maxMessageSize > 0) {
                LOGGER.info("The maximum allowed message size is {} bytes.", maxMessageSize);
            } else {
                LOGGER.info("No maximum message size is enforced for this server.");
            }

            heloEhloEnforcement = configuration.getBoolean("heloEhloEnforcement", true);

            // get the smtpGreeting
            smtpGreeting = configuration.getString("smtpGreeting", null);

            addressBracketsEnforcement = configuration.getBoolean("addressBracketsEnforcement", true);

            verifyIdentity = configuration.getBoolean("verifyIdentity", false);

            if (authRequired == DISABLED && verifyIdentity) {
                throw new ConfigurationException(
                    "SMTP configuration: 'verifyIdentity' can't be set to true if 'authRequired' is set to false.");
            }
        }
    }

    /**
     * Return the default port which will get used for this server if non is
     * specify in the configuration
     */
    @Override
    protected int getDefaultPort() {
        return 25;
    }

    @Override
    public String getServiceType() {
        return "SMTP Service";
    }

    /**
     * A class to provide SMTP handler configuration to the handlers
     */
    public class SMTPHandlerConfigurationDataImpl implements SMTPConfiguration {

        @Override
        public String getHelloName() {
            return SMTPServer.this.getHelloName();
        }

        @Override
        public long getMaxMessageSize() {
            return SMTPServer.this.maxMessageSize;
        }

        @Override
        public boolean isRelayingAllowed(String remoteIP) {
            boolean relayingAllowed = false;
            if (authorizedNetworks != null) {
                relayingAllowed = SMTPServer.this.authorizedNetworks.matchInetNetwork(remoteIP);
            }
            return relayingAllowed;
        }

        @Override
        public boolean useHeloEhloEnforcement() {
            return SMTPServer.this.heloEhloEnforcement;
        }

        @Override
        public boolean useAddressBracketsEnforcement() {
            return SMTPServer.this.addressBracketsEnforcement;
        }

        @Override
        public boolean isAuthRequired(String remoteIP) {
            if (SMTPServer.this.authRequired == ANNOUNCE) {
                return true;
            }
            if (SMTPServer.this.authRequired == DISABLED) {
                return false;
            }
            return !SMTPServer.this.authorizedNetworks.matchInetNetwork(remoteIP);
        }

        /**
         * Return true if the username and mail from must match for a authorized
         * user
         */
        public boolean verifyIdentity() {
            return SMTPServer.this.verifyIdentity;
        }

        @Override
        public String getGreeting() {
            return SMTPServer.this.smtpGreeting;
        }

        @Override
        public String getSoftwareName() {
            return "JAMES SMTP Server ";
        }

    }

    @Override
    public long getMaximalMessageSize() {
        return maxMessageSize;
    }

    @Override
    public boolean getAddressBracketsEnforcement() {
        return addressBracketsEnforcement;
    }

    @Override
    public boolean getHeloEhloEnforcement() {
        return heloEhloEnforcement;
    }
    
    @Override
    protected String getDefaultJMXName() {
        return "smtpserver";
    }

    @Override
    public void setMaximalMessageSize(long maxSize) {
        this.maxMessageSize = maxSize;
    }

    @Override
    public void setAddressBracketsEnforcement(boolean enforceAddressBrackets) {
        this.addressBracketsEnforcement = enforceAddressBrackets;
    }

    @Override
    public void setHeloEhloEnforcement(boolean enforceHeloEHlo) {
        this.heloEhloEnforcement = enforceHeloEHlo;
    }

    @Override
    public String getHeloName() {
        return theConfigData.getHelloName();
    }

    @Override
    protected ChannelUpstreamHandler createCoreHandler() {
        return coreHandler;
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
        return new AllButStartTlsLineChannelHandlerFactory("starttls", AbstractChannelPipelineFactory.MAX_LINE_LENGTH);
    }

    public AuthenticationRequired getAuthRequired() {
        return authRequired;
    }
}
