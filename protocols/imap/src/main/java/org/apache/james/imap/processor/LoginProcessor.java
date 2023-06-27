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

import javax.inject.Inject;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.LoginRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
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

    @Inject
    public LoginProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory) {
        super(LoginRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(LoginRequest request, ImapSession session, Responder responder) {
        // check if the login is allowed with LOGIN command. See IMAP-304
        if (session.isPlainAuthDisallowed()) {
            LOGGER.warn("Login attempt over clear channel rejected");
            no(request, responder, HumanReadableText.DISABLED_LOGIN);
        } else {
            doAuth(noDelegation(request.getUserid(), request.getPassword()),
                session, request, responder, HumanReadableText.INVALID_LOGIN);
        }
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        // Announce LOGINDISABLED if plain auth / login is deactivated and the session is not using
        // TLS. See IMAP-304
        if (session.isPlainAuthDisallowed()) {
            return LOGINDISABLED_CAPS;
        }
        return Collections.emptyList();
    }

    @Override
    protected MDCBuilder mdc(LoginRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "LOGIN")
            .addToContext("login-user", request.getUserid().asString());
    }
}
