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

package org.apache.james.protocols.pop3.core;

import java.util.Collection;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * Handles USER command
 */
public class UserCmdHandler extends AbstractPOP3CommandHandler implements CapaCapability {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserCmdHandler.class);
    private static final Collection<String> COMMANDS = ImmutableSet.of("USER");
    private static final Set<String> CAPS = ImmutableSet.of("USER");

    private final MetricFactory metricFactory;

    @Inject
    public UserCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    /**
     * Handler method called upon receipt of a USER command. Reads in the user
     * id.
     */
    @Override
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-user", () ->
            MDCBuilder.withMdc(
                MDCBuilder.create()
                    .addToContext(MDCBuilder.ACTION, "USER")
                    .addToContext(MDCConstants.withSession(session))
                    .addToContext(MDCConstants.forRequest(request)),
                () -> user(session, request)));
    }

    private Response user(POP3Session session, Request request) {
        LOGGER.trace("USER command received");
        String parameters = request.getArgument();
        if (session.getHandlerState() == POP3Session.AUTHENTICATION_READY && parameters != null) {
            session.setUsername(Username.of(parameters));
            session.setHandlerState(POP3Session.AUTHENTICATION_USERSET);
            return POP3Response.OK;
        } else {
            return POP3Response.ERR;
        }
    }

    @Override
    public Set<String> getImplementedCapabilities(POP3Session session) {
        return CAPS;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
