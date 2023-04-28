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

package org.apache.james.transport.mailets.remote.delivery;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.queue.api.MailQueueName;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.MailetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class RemoteDeliveryConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDeliveryConfiguration.class);

    public static final String USE_PRIORITY = "usePriority";
    public static final String MAX_DNS_PROBLEM_RETRIES = "maxDnsProblemRetries";
    public static final String HELO_NAME = "heloName";
    public static final String JAVAX_PREFIX = "mail.";
    public static final String BIND = "bind";
    public static final String GATEWAY_PASSWORD = "gatewayPassword";
    public static final String GATEWAY_USERNAME_COMPATIBILITY = "gatewayusername";
    public static final String GATEWAY_USERNAME = "gatewayUsername";
    public static final String GATEWAY_PORT = "gatewayPort";
    public static final String GATEWAY = "gateway";
    public static final String SSL_ENABLE = "sslEnable";
    public static final String START_TLS = "startTLS";
    public static final String VERIFY_SERVER_IDENTITY = "verifyServerIdentity";
    public static final String BOUNCE_PROCESSOR = "bounceProcessor";
    public static final String SENDPARTIAL = "sendpartial";
    public static final String TIMEOUT = "timeout";
    public static final String CONNECTIONTIMEOUT = "connectiontimeout";
    public static final String OUTGOING = "outgoing";
    public static final String MAX_RETRIES = "maxRetries";
    public static final String DELAY_TIME = "delayTime";
    public static final String DEBUG = "debug";
    public static final String ON_SUCCESS = "onSuccess";
    public static final String LOAD_BALANCING = "loadBalancing";
    public static final int DEFAULT_SMTP_TIMEOUT = 180000;
    public static final MailQueueName DEFAULT_OUTGOING_QUEUE_NAME = MailQueueName.of("outgoing");
    public static final int DEFAULT_CONNECTION_TIMEOUT = 60000;
    public static final int DEFAULT_DNS_RETRY_PROBLEM = 0;
    public static final int DEFAULT_MAX_RETRY = 5;
    public static final String ADDRESS_PORT_SEPARATOR = ":";

    private final boolean isDebug;
    private final boolean usePriority;
    private final boolean startTLS;
    private final boolean isSSLEnable;
    private final boolean verifyServerIdentity;
    private final boolean isBindUsed;
    private final boolean sendPartial;
    private final boolean loadBalancing;
    private final int maxRetries;
    private final long smtpTimeout;
    private final int dnsProblemRetry;
    private final int connectionTimeout;
    private final List<Duration> delayTimes;
    private final HeloNameProvider heloNameProvider;
    private final MailQueueName outGoingQueueName;
    private final String bindAddress;
    private final Optional<ProcessingState> bounceProcessor;
    private final Collection<String> gatewayServer;
    private final String authUser;
    private final String authPass;
    private final Properties javaxAdditionalProperties;
    private final Optional<ProcessingState> onSuccess;

    public RemoteDeliveryConfiguration(MailetConfig mailetConfig, DomainList domainList) {
        isDebug = MailetUtil.getInitParameter(mailetConfig, DEBUG).orElse(false);
        startTLS = MailetUtil.getInitParameter(mailetConfig, START_TLS).orElse(false);
        isSSLEnable = MailetUtil.getInitParameter(mailetConfig, SSL_ENABLE).orElse(false);
        verifyServerIdentity = MailetUtil.getInitParameter(mailetConfig, VERIFY_SERVER_IDENTITY).orElse(true);
        usePriority = MailetUtil.getInitParameter(mailetConfig, USE_PRIORITY).orElse(false);
        sendPartial = MailetUtil.getInitParameter(mailetConfig, SENDPARTIAL).orElse(false);
        loadBalancing = MailetUtil.getInitParameter(mailetConfig, LOAD_BALANCING).orElse(true);
        outGoingQueueName = Optional.ofNullable(mailetConfig.getInitParameter(OUTGOING))
            .map(MailQueueName::of)
            .orElse(DEFAULT_OUTGOING_QUEUE_NAME);
        bounceProcessor = Optional.ofNullable(mailetConfig.getInitParameter(BOUNCE_PROCESSOR))
            .map(ProcessingState::new);
        bindAddress = mailetConfig.getInitParameter(BIND);

        DelaysAndMaxRetry delaysAndMaxRetry = computeDelaysAndMaxRetry(mailetConfig);
        maxRetries = delaysAndMaxRetry.getMaxRetries();
        delayTimes = delaysAndMaxRetry.getExpandedDelays();
        smtpTimeout = computeSmtpTimeout(mailetConfig);
        connectionTimeout = computeConnectionTimeout(mailetConfig);
        dnsProblemRetry = computeDnsProblemRetry(mailetConfig);
        heloNameProvider = new HeloNameProvider(mailetConfig.getInitParameter(HELO_NAME), domainList);

        String gatewayPort = mailetConfig.getInitParameter(GATEWAY_PORT);
        String gateway = mailetConfig.getInitParameter(GATEWAY);
        gatewayServer = computeGatewayServers(gatewayPort, gateway);
        if (gateway != null) {
            authUser = computeGatewayUser(mailetConfig);
            authPass = mailetConfig.getInitParameter(GATEWAY_PASSWORD);
        } else {
            authUser = null;
            authPass = null;
        }
        isBindUsed = bindAddress != null;
        javaxAdditionalProperties = computeJavaxProperties(mailetConfig);
        onSuccess = Optional.ofNullable(mailetConfig.getInitParameter(ON_SUCCESS))
            .map(ProcessingState::new);
    }

    private Properties computeJavaxProperties(MailetConfig mailetConfig) {
        Properties result = new Properties();
        // deal with <mail.*> attributes, passing them to javamail
        result.putAll(
            ImmutableList.copyOf(mailetConfig.getInitParameterNames())
                .stream()
                .filter(propertyName -> propertyName.startsWith(JAVAX_PREFIX))
                .map(propertyName -> Pair.of(propertyName, mailetConfig.getInitParameter(propertyName)))
                .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue)));
        return result;
    }

    private int computeDnsProblemRetry(MailetConfig mailetConfig) {
        String dnsRetry = mailetConfig.getInitParameter(MAX_DNS_PROBLEM_RETRIES);
        if (!Strings.isNullOrEmpty(dnsRetry)) {
            return Integer.parseInt(dnsRetry);
        } else {
            return DEFAULT_DNS_RETRY_PROBLEM;
        }
    }

    private int computeConnectionTimeout(MailetConfig mailetConfig) {
        try {
            return Integer.parseInt(
                Optional.ofNullable(mailetConfig.getInitParameter(CONNECTIONTIMEOUT))
                    .orElse(String.valueOf(DEFAULT_CONNECTION_TIMEOUT)));
        } catch (Exception e) {
            LOGGER.warn("Invalid timeout setting: {}", mailetConfig.getInitParameter(TIMEOUT));
            return DEFAULT_CONNECTION_TIMEOUT;
        }
    }

    private long computeSmtpTimeout(MailetConfig mailetConfig) {
        try {
            if (mailetConfig.getInitParameter(TIMEOUT) != null) {
                return Integer.parseInt(mailetConfig.getInitParameter(TIMEOUT));
            } else {
                return DEFAULT_SMTP_TIMEOUT;
            }
        } catch (Exception e) {
            LOGGER.warn("Invalid timeout setting: {}", mailetConfig.getInitParameter(TIMEOUT));
            return DEFAULT_SMTP_TIMEOUT;
        }
    }

    private DelaysAndMaxRetry computeDelaysAndMaxRetry(MailetConfig mailetConfig) {
        try {
            int intendedMaxRetries = Integer.parseInt(
                Optional.ofNullable(mailetConfig.getInitParameter(MAX_RETRIES))
                    .orElse(String.valueOf(DEFAULT_MAX_RETRY)));
            return DelaysAndMaxRetry.from(intendedMaxRetries, mailetConfig.getInitParameter(DELAY_TIME));
        } catch (Exception e) {
            LOGGER.warn("Invalid maxRetries setting: {}", mailetConfig.getInitParameter(MAX_RETRIES));
            return DelaysAndMaxRetry.defaults();
        }
    }

    private String computeGatewayUser(MailetConfig mailetConfig) {
        // backward compatibility with 2.3.x
        String user = mailetConfig.getInitParameter(GATEWAY_USERNAME);
        if (user == null) {
            return mailetConfig.getInitParameter(GATEWAY_USERNAME_COMPATIBILITY);
        }
        return user;
    }

    private List<String> computeGatewayServers(String gatewayPort, String gateway) {
        if (gateway != null && !gateway.isBlank()) {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            Iterable<String> gatewayParts = Splitter.on(',').split(gateway);
            for (String gatewayPart : gatewayParts) {
                builder.add(parsePart(gatewayPort, gatewayPart));
            }
            return builder.build();
        } else {
            return ImmutableList.of();
        }
    }

    private String parsePart(String gatewayPort, String gatewayPart) {
        String address = gatewayPart.trim();
        if (!address.contains(ADDRESS_PORT_SEPARATOR) && gatewayPort != null) {
            return address + ADDRESS_PORT_SEPARATOR + gatewayPort;
        }
        return address;
    }

    public Properties createFinalJavaxProperties() {
        Properties props = createFinalJavaxProperties("smtp");
        props.put("mail.smtp.ssl.enable", "false");
        return props;
    }

    public Properties createFinalJavaxPropertiesWithSSL() {
        Properties props = createFinalJavaxProperties("smtps");
        props.put("mail.smtps.ssl.enable", "true");
        return props;
    }
    
    private Properties createFinalJavaxProperties(String protocol) {
        Properties props = new Properties();
        props.put("mail.debug", "false");
        // Reactivated: javamail 1.3.2 should no more have problems with "250 OK" messages
        // (WAS "false": Prevents problems encountered with 250 OK Messages)
        props.put("mail." + protocol + ".ehlo", "true");
        props.put("mail." + protocol + ".timeout", String.valueOf(smtpTimeout));
        props.put("mail." + protocol + ".connectiontimeout", String.valueOf(connectionTimeout));
        props.put("mail." + protocol + ".sendpartial", String.valueOf(sendPartial));
        props.put("mail." + protocol + ".localhost", heloNameProvider.getHeloName());
        props.put("mail." + protocol + ".starttls.enable", String.valueOf(startTLS));
        props.put("mail." + protocol + ".ssl.checkserveridentity", String.valueOf(verifyServerIdentity));
        if (isBindUsed()) {
            props.put("mail." + protocol + ".localaddress", bindAddress);
        }
        if (authUser != null) {
            props.put("mail." + protocol + ".auth", "true");
        }
        props.putAll(javaxAdditionalProperties);
        return props;
    }

    public boolean isDebug() {
        return isDebug;
    }

    public List<Duration> getDelayTimes() {
        return delayTimes;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getSmtpTimeout() {
        return smtpTimeout;
    }

    public boolean isSendPartial() {
        return sendPartial;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public Collection<String> getGatewayServer() {
        return gatewayServer;
    }

    public String getAuthUser() {
        return authUser;
    }

    public String getAuthPass() {
        return authPass;
    }

    public boolean isBindUsed() {
        return isBindUsed;
    }

    public Optional<ProcessingState> getBounceProcessor() {
        return bounceProcessor;
    }

    public boolean isUsePriority() {
        return usePriority;
    }

    public boolean isStartTLS() {
        return startTLS;
    }

    public boolean isSSLEnable() {
        return isSSLEnable;
    }
    
    public boolean isVerifyServerIdentity() {
        return verifyServerIdentity;
    }
    
    public boolean isConnectByHostname() {
        return (isSSLEnable() || isStartTLS()) && isVerifyServerIdentity();
    }

    public HeloNameProvider getHeloNameProvider() {
        return heloNameProvider;
    }

    public MailQueueName getOutGoingQueueName() {
        return outGoingQueueName;
    }

    public Properties getJavaxAdditionalProperties() {
        return javaxAdditionalProperties;
    }

    public int getDnsProblemRetry() {
        return dnsProblemRetry;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public Optional<ProcessingState> getOnSuccess() {
        return onSuccess;
    }

    public boolean isLoadBalancing() {
        return loadBalancing;
    }

}
