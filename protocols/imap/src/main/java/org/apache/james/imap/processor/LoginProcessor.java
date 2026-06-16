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

package org.apache.james.imap.processor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.LoginRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * Processes a <code>LOGIN</code> command.
 */
public class LoginProcessor extends AbstractAuthProcessor<LoginRequest> implements CapabilityImplementingProcessor {
    private static final List<Capability> LOGINDISABLED_CAPS = ImmutableList.of(Capability.of("LOGINDISABLED"));
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginProcessor.class);

    private final JamesSaslAuthenticator jamesSaslAuthenticator;
    private Optional<PlainSaslMechanism> plainSaslMechanism;

    @Inject
    public LoginProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory, PathConverter.Factory pathConverterFactory,
                          JamesSaslAuthenticator jamesSaslAuthenticator) {
        super(LoginRequest.class, mailboxManager, factory, metricFactory, pathConverterFactory);
        this.jamesSaslAuthenticator = jamesSaslAuthenticator;
        this.plainSaslMechanism = Optional.empty();
    }

    /**
     * Start password authentication if enabled.
     */
    @Override
    protected void processRequest(LoginRequest request, ImapSession session, Responder responder) {
        Optional<PlainSaslMechanism> plainSaslMechanism = availablePlainSaslMechanism(session);

        // check if the login is allowed with LOGIN command. See IMAP-304
        if (plainSaslMechanism.isEmpty()) {
            LOGGER.warn("Login rejected because PLAIN SASL mechanism is disabled");
            no(request, responder, HumanReadableText.DISABLED_LOGIN);
        } else {
            SaslAuthenticator authenticator = jamesSaslAuthenticator.withExtraAuthorizator(withAdminUsers());
            handleSaslStep(plainSaslMechanism.orElseThrow().authenticate(request.getUserid(), request.getPassword(), authenticator),
                session, request, responder, "Password authentication succeeded.");
        }
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        // Announce LOGINDISABLED if plain auth / login is deactivated and the session is not using
        // TLS. See IMAP-304
        if (availablePlainSaslMechanism(session).isEmpty()) {
            return LOGINDISABLED_CAPS;
        }
        return Collections.emptyList();
    }

    public void configureSaslMechanisms(ImmutableList<SaslMechanism> saslMechanisms) {
        this.plainSaslMechanism = saslMechanisms.stream()
            .filter(mechanism -> SaslMechanismNames.PLAIN.equalsIgnoreCase(mechanism.name()))
            .filter(PlainSaslMechanism.class::isInstance)
            .map(PlainSaslMechanism.class::cast)
            .findFirst();
    }

    private Optional<PlainSaslMechanism> availablePlainSaslMechanism(ImapSession session) {
        return plainSaslMechanism
            .filter(mechanism -> mechanism.isAvailableOnTransport(session.isTLSActive()));
    }

    @Override
    protected MDCBuilder mdc(LoginRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "LOGIN")
            .addToContext("login-user", request.getUserid().asString());
    }
}
