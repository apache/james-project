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
package org.apache.james.smtpserver.fastfail;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * MailHook that restricts which sender addresses can be used by unauthenticated
 * connections originating from the IP whitelist (authorizedAddresses).
 *
 * Useful for printers and 3rd-party apps that rely on IP-based relay whitelisting
 * rather than SMTP authentication.
 *
 * Configuration example:
 * <pre>{@code
 * <handler class="org.apache.james.smtpserver.fastfail.AllowedUnauthenticatedSender">
 *   <allowNullSender>false</allowNullSender>
 *   <allowedSenders>
 *     <allowedSender>printer@example.com</allowedSender>
 *     <allowedSender fromIps="192.168.1.0/24,10.0.0.1/32">scanner@example.com</allowedSender>
 *   </allowedSenders>
 * </handler>
 * }</pre>
 *
 * Entries without {@code fromIps} are allowed from any whitelisted IP.
 * Entries with {@code fromIps} are only allowed from the specified subnets.
 *
 * This hook only activates when relaying is allowed and the session has no
 * authenticated user. Authenticated sessions and non-relay connections are
 * not affected.
 */
public class AllowedUnauthenticatedSender implements MailHook, ProtocolHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AllowedUnauthenticatedSender.class);

    private record AllowedSenderEntry(MailAddress sender, Optional<NetMatcher> ipMatcher) {
        boolean matches(MailAddress remoteSender, String remoteIp) {
            if (!sender.equals(remoteSender)) {
                return false;
            }
            return ipMatcher.map(matcher -> matcher.matchInetNetwork(remoteIp))
                .orElse(true);
        }
    }

    private final DNSService dnsService;
    private List<AllowedSenderEntry> allowedSenders = ImmutableList.of();
    private boolean allowNullSender = false;

    @Inject
    public AllowedUnauthenticatedSender(DNSService dnsService) {
        this.dnsService = dnsService;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfig = (HierarchicalConfiguration<ImmutableNode>) config;

        allowedSenders = hierarchicalConfig.configurationsAt("allowedSenders.allowedSender")
            .stream()
            .map(Throwing.function(this::parseAllowedSenderEntry))
            .collect(Collectors.toList());

        allowNullSender = config.getBoolean("allowNullSender", false);

        if (allowedSenders.isEmpty()) {
            throw new ConfigurationException("AllowedUnauthenticatedSender requires at least one <allowedSender> entry");
        }
    }

    private AllowedSenderEntry parseAllowedSenderEntry(HierarchicalConfiguration<ImmutableNode> senderNode) throws ConfigurationException {
        return new AllowedSenderEntry(parseMailAddress(senderNode.getString("").trim()),
            Optional.ofNullable(senderNode.getString("[@fromIps]", null))
                .map(ips -> Splitter.on(',')
                    .omitEmptyStrings()
                    .trimResults()
                    .splitToList(ips))
                .map(ips -> new NetMatcher(ips, dnsService)));
    }

    private static MailAddress parseMailAddress(String emailStr) throws ConfigurationException {
        try {
            return new MailAddress(emailStr);
        } catch (AddressException e) {
            throw new ConfigurationException("Invalid email address in allowedSender: " + emailStr, e);
        }
    }

    @Override
    public HookResult doMail(SMTPSession session, MaybeSender sender) {
        if (!session.isRelayingAllowed() || session.getUsername() != null) {
            return HookResult.DECLINED;
        }
        String remoteIp = session.getRemoteAddress().getAddress().getHostAddress();

        if (sender.isNullSender()) {
            return validateNullSender(remoteIp);
        }

        MailAddress mailSender = sender.asOptional().get();

        return validateSender(mailSender, remoteIp);
    }

    private HookResult validateSender(MailAddress mailSender, String remoteIp) {
        boolean isAllowed = allowedSenders.stream()
            .anyMatch(entry -> entry.matches(mailSender, remoteIp));

        if (isAllowed) {
            LOGGER.debug("Unauthenticated sender {} from {} is allowed", mailSender.asString(), remoteIp);
            return HookResult.DECLINED;
        }

        LOGGER.info("Unauthenticated sender {} from {} rejected: not in allowed senders list", mailSender.asString(), remoteIp);
        return HookResult.builder()
            .hookReturnCode(HookReturnCode.deny())
            .smtpReturnCode(SMTPRetCode.MAILBOX_PERM_UNAVAILABLE)
            .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH)
                + " Sender <" + mailSender.asString() + "> is not allowed for unauthenticated connection from " + remoteIp)
            .build();
    }

    private HookResult validateNullSender(String remoteIp) {
        if (allowNullSender) {
            LOGGER.debug("Unauthenticated sender <> from {} is allowed", remoteIp);
            return HookResult.DECLINED;
        } else {
            LOGGER.info("Unauthenticated sender <> from {} rejected: not in allowed senders list", remoteIp);
            return HookResult.builder()
                .hookReturnCode(HookReturnCode.deny())
                .smtpReturnCode(SMTPRetCode.MAILBOX_PERM_UNAVAILABLE)
                .smtpDescription(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH)
                    + " Sender <> is not allowed for unauthenticated connection from " + remoteIp)
                .build();
        }
    }
}
