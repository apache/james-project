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

import jakarta.inject.Inject;

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
 * Handles NOOP command
 */
public class NoopCmdHandler extends AbstractPOP3CommandHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoopCmdHandler.class);
    private static final Collection<String> COMMANDS = ImmutableSet.of("NOOP");

    private final MetricFactory metricFactory;

    @Inject
    public NoopCmdHandler(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    /**
     * Handler method called upon receipt of a NOOP command. Like all good
     * NOOPs, does nothing much.
     */
    @Override
    public Response onCommand(POP3Session session, Request request) {
        return metricFactory.decorateSupplierWithTimerMetric("pop3-noop", () ->
            MDCBuilder.withMdc(
                MDCBuilder.create()
                    .addToContext(MDCBuilder.ACTION, "NOOP")
                    .addToContext(MDCConstants.withSession(session)),
                () -> noop(session)));
    }

    private Response noop(POP3Session session) {
        LOGGER.trace("NOOP command received");
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            return POP3Response.OK;
        } else {
            return POP3Response.ERR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
