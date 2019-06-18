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
import org.apache.mailet.MailetConfig;
import org.apache.mailet.base.MailetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

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
    public static final String BOUNCE_PROCESSOR = "bounceProcessor";
    public static final String SENDPARTIAL = "sendpartial";
    public static final String TIMEOUT = "timeout";
    public static final String CONNECTIONTIMEOUT = "connectiontimeout";
    public static final String OUTGOING = "outgoing";
    public static final String MAX_RETRIES = "maxRetries";
    public static final String DELAY_TIME = "delayTime";
    public static final String DEBUG = "debug";
    public static final int DEFAULT_SMTP_TIMEOUT = 180000;
    public static final String DEFAULT_OUTGOING_QUEUE_NAME = "outgoing";
    public static final int DEFAULT_CONNECTION_TIMEOUT = 60000;
    public static final int DEFAULT_DNS_RETRY_PROBLEM = 0;
    public static final int DEFAULT_MAX_RETRY = 5;
    public static final String ADDRESS_PORT_SEPARATOR = ":";

    private final boolean isDebug;
    private final boolean usePriority;
    private final boolean startTLS;
    private final boolean isSSLEnable;
    private final boolean isBindUsed;
    private final boolean sendPartial;
    private final int maxRetries;
    private final long smtpTimeout;
    private final int dnsProblemRetry;
    private final int connectionTimeout;
    private final List<Duration> delayTimes;
    private final HeloNameProvider heloNameProvider;
    private final String outGoingQueueName;
    private final String bindAddress;
    private final String bounceProcessor;
    private final Collection<String> gatewayServer;
    private final String authUser;
    private final String authPass;
    private final Properties javaxAdditionalProperties;

    public RemoteDeliveryConfiguration(MailetConfig mailetConfig, DomainList domainList) {
        isDebug = MailetUtil.getInitParameter(mailetConfig, DEBUG).orElse(false);
        startTLS = MailetUtil.getInitParameter(mailetConfig, START_TLS).orElse(false);
        isSSLEnable = MailetUtil.getInitParameter(mailetConfig, SSL_ENABLE).orElse(false);
        usePriority = MailetUtil.getInitParameter(mailetConfig, USE_PRIORITY).orElse(false);
        sendPartial = MailetUtil.getInitParameter(mailetConfig, SENDPARTIAL).orElse(false);
        outGoingQueueName = Optional.ofNullable(mailetConfig.getInitParameter(OUTGOING)).orElse(DEFAULT_OUTGOING_QUEUE_NAME);
        bounceProcessor = mailetConfig.getInitParameter(BOUNCE_PROCESSOR);
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
    }

    private Properties computeJavaxProperties(MailetConfig mailetConfig) {
        Properties result = new Properties();
        // deal with <mail.*> attributes, passing them to javamail
        result.putAll(
            ImmutableList.copyOf(mailetConfig.getInitParameterNames())
                .stream()
                .filter(propertyName -> propertyName.startsWith(JAVAX_PREFIX))
                .map(propertyName -> Pair.of(propertyName, mailetConfig.getInitParameter(propertyName)))
                .collect(Guavate.toImmutableMap(Pair::getKey, Pair::getValue)));
        return result;
    }

    private int computeDnsProblemRetry(MailetConfig mailetConfig) {
        String dnsRetry = mailetConfig.getInitParameter(MAX_DNS_PROBLEM_RETRIES);
        if (!Strings.isNullOrEmpty(dnsRetry)) {
            return Integer.valueOf(dnsRetry);
        } else {
            return DEFAULT_DNS_RETRY_PROBLEM;
        }
    }

    private int computeConnectionTimeout(MailetConfig mailetConfig) {
        try {
            return Integer.valueOf(
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
                return Integer.valueOf(mailetConfig.getInitParameter(TIMEOUT));
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
            int intendedMaxRetries = Integer.valueOf(
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
        if (gateway != null) {
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
        Properties props = new Properties();
        props.put("mail.debug", "false");
        // Reactivated: javamail 1.3.2 should no more have problems with "250 OK" messages
        // (WAS "false": Prevents problems encountered with 250 OK Messages)
        props.put("mail.smtp.ehlo", "true");
        // By setting this property to true the transport is allowed to send 8 bit data to the server (if it supports
        // the 8bitmime extension).
        props.setProperty("mail.smtp.allow8bitmime", "true");
        props.put("mail.smtp.timeout", String.valueOf(smtpTimeout));
        props.put("mail.smtp.connectiontimeout", String.valueOf(connectionTimeout));
        props.put("mail.smtp.sendpartial", String.valueOf(sendPartial));
        props.put("mail.smtp.localhost", heloNameProvider.getHeloName());
        props.put("mail.smtp.starttls.enable", String.valueOf(startTLS));
        props.put("mail.smtp.ssl.enable", String.valueOf(isSSLEnable));
        if (isBindUsed()) {
            // undocumented JavaMail 1.2 feature, smtp transport will use
            // our socket factory, which will also set the local address
            props.put("mail.smtp.socketFactory.class", RemoteDeliverySocketFactory.class.getClass());
            // Don't fallback to the standard socket factory on error, do throw an exception
            props.put("mail.smtp.socketFactory.fallback", "false");
        }
        if (authUser != null) {
            props.put("mail.smtp.auth", "true");
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

    public String getBounceProcessor() {
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

    public HeloNameProvider getHeloNameProvider() {
        return heloNameProvider;
    }

    public String getOutGoingQueueName() {
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
}
