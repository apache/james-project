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

import static org.apache.james.smtpserver.netty.SMTPServer.AuthenticationAnnounceMode.ALWAYS;
import static org.apache.james.smtpserver.netty.SMTPServer.AuthenticationAnnounceMode.NEVER;

import java.net.MalformedURLException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.james.protocols.api.OidcSASLConfiguration;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * NIO SMTPServer which use Netty
 */
public class SMTPServer extends AbstractProtocolAsyncServer implements SMTPServerMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(SMTPServer.class);
    private SMTPProtocol transport;

    public enum AuthenticationAnnounceMode {
        NEVER,
        FOR_UNAUTHORIZED_ADDRESSES,
        ALWAYS;

        public static AuthenticationAnnounceMode parseFallback(String authRequiredString) {
            String sanitized = authRequiredString.trim().toLowerCase(Locale.US);
            if (sanitized.equals("true")) {
                return FOR_UNAUTHORIZED_ADDRESSES;
            } else if (sanitized.equals("announce")) {
                return ALWAYS;
            } else {
                return NEVER;
            }
        }

        public static AuthenticationAnnounceMode parse(String authRequiredString) {
            String sanitized = authRequiredString.trim().toLowerCase(Locale.US);
            switch (sanitized) {
                case "forunauthorizedaddresses":
                    return FOR_UNAUTHORIZED_ADDRESSES;
                case "always":
                    return ALWAYS;
                case "never":
                    return NEVER;
                default:
                    throw new RuntimeException("Unknown value for 'auth.announce': " + authRequiredString + ". Should be one of always, never, forUnauthorizedAddresses");
            }
        }
    }

    public static class AuthenticationConfiguration {
        private static final String OIDC_PATH = "auth.oidc";

        public static AuthenticationConfiguration parse(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
            return new AuthenticationConfiguration(
                Optional.ofNullable(configuration.getString("auth.announce", null))
                    .map(AuthenticationAnnounceMode::parse)
                    .orElseGet(() -> fallbackAuthenticationAnnounceMode(configuration)),
                configuration.getBoolean("auth.requireSSL", false),
                configuration.getBoolean("auth.plainAuthEnabled", true),
                parseSASLConfiguration(configuration));
        }

        private static Optional<OidcSASLConfiguration> parseSASLConfiguration(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
            boolean haveOidcProperties = configuration.getKeys(OIDC_PATH).hasNext();
            if (haveOidcProperties) {
                try {
                    return Optional.of(OidcSASLConfiguration.parse(configuration.configurationAt(OIDC_PATH)));
                } catch (MalformedURLException exception) {
                   throw new ConfigurationException("Failed to retrieve oauth component", exception);
                }
            } else {
                return Optional.empty();
            }
        }

        private static AuthenticationAnnounceMode fallbackAuthenticationAnnounceMode(HierarchicalConfiguration<ImmutableNode> configuration) {
            return AuthenticationAnnounceMode.parseFallback(configuration.getString("authRequired", "false"));
        }

        private final AuthenticationAnnounceMode authenticationAnnounceMode;
        private final boolean requireSSL;
        private final boolean plainAuthEnabled;
        private final Optional<OidcSASLConfiguration> saslConfiguration;

        public AuthenticationConfiguration(AuthenticationAnnounceMode authenticationAnnounceMode, boolean requireSSL, boolean plainAuthEnabled, Optional<OidcSASLConfiguration> saslConfiguration) {
            this.authenticationAnnounceMode = authenticationAnnounceMode;
            this.requireSSL = requireSSL;
            this.plainAuthEnabled = plainAuthEnabled;
            this.saslConfiguration = saslConfiguration;
        }

        public AuthenticationAnnounceMode getAuthenticationAnnounceMode() {
            return authenticationAnnounceMode;
        }

        public boolean isRequireSSL() {
            return requireSSL;
        }

        public boolean isPlainAuthEnabled() {
            return plainAuthEnabled;
        }

        public Optional<OidcSASLConfiguration> getSaslConfiguration() {
            return saslConfiguration;
        }
    }

    /**
     * Whether authentication is required to use this SMTP server.
     */
    private AuthenticationConfiguration authenticationConfiguration;
    
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
    private Set<String> disabledFeatures = ImmutableSet.of();

    private boolean addressBracketsEnforcement = true;

    private boolean verifyIdentity;

    private DNSService dns;
    private String authorizedAddresses;

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
            LOGGER.info("Authorized addresses: {}", authorizedNetworks);
        }
        transport = new SMTPProtocol(getProtocolHandlerChain(), theConfigData) {
            @Override
            public ProtocolSession newSession(ProtocolTransport transport) {
                return new ExtendedSMTPSession(theConfigData, transport);
            }
        };
    }

    @Override
    public void doConfigure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        super.doConfigure(configuration);
        if (isEnabled()) {
            authenticationConfiguration = AuthenticationConfiguration.parse(configuration);

            authorizedAddresses = configuration.getString("authorizedAddresses", null);

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

            verifyIdentity = configuration.getBoolean("verifyIdentity", true);

            disabledFeatures = ImmutableSet.copyOf(configuration.getStringArray("disabledFeatures"));
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
            if (authorizedNetworks != null) {
                return SMTPServer.this.authorizedNetworks.matchInetNetwork(remoteIP);
            }
            return false;
        }

        @Override
        public boolean useHeloEhloEnforcement() {
            return SMTPServer.this.heloEhloEnforcement;
        }

        @Override
        public boolean useAddressBracketsEnforcement() {
            return SMTPServer.this.addressBracketsEnforcement;
        }

        public boolean isPlainAuthEnabled() {
            return authenticationConfiguration.isPlainAuthEnabled();
        }

        @Override
        public boolean isAuthAnnounced(String remoteIP, boolean tlsStarted) {
            if (authenticationConfiguration.requireSSL && !tlsStarted) {
                return false;
            }
            if (authenticationConfiguration.getAuthenticationAnnounceMode() == ALWAYS) {
                return true;
            }
            if (authenticationConfiguration.getAuthenticationAnnounceMode() == NEVER) {
                return false;
            }
            return Optional.ofNullable(authorizedNetworks)
                .map(nets -> !nets.matchInetNetwork(remoteIP))
                .orElse(true);
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

        @Override
        public Optional<OidcSASLConfiguration> saslConfiguration() {
            return authenticationConfiguration.getSaslConfiguration();
        }

        @Override
        public Set<String> disabledFeatures() {
            return disabledFeatures;
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
    protected ChannelInboundHandlerAdapter createCoreHandler() {
        return new SMTPChannelInboundHandler(transport, getEncryption(), proxyRequired, smtpMetrics);
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

    public AuthenticationAnnounceMode getAuthRequired() {
        return authenticationConfiguration.getAuthenticationAnnounceMode();
    }
}
