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



package org.apache.james.protocols.smtp.core;

import java.util.Collection;

import jakarta.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.UnknownCommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.UnknownHook;

import com.google.common.collect.ImmutableSet;

/**
  * Default command handler for handling unknown commands
  */
public class UnknownCmdHandler extends AbstractHookableCmdHandler<UnknownHook> {

    /**
     * The name of the command handled by the command handler
     */
    private static final Collection<String> COMMANDS = ImmutableSet.of(UnknownCommandHandler.COMMAND_IDENTIFIER);
    private static final String MISSING_CURR_COMMAND = "";
    public static final ProtocolSession.AttachmentKey<String> CURR_COMMAND = ProtocolSession.AttachmentKey.of("CURR_COMMAND", String.class);

    @Inject
    public UnknownCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    protected Response doCoreCmd(SMTPSession session, String command, String parameters) {
        StringBuilder result = new StringBuilder();
        result.append(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_CMD)).append(" Command ").append(command).append(" unrecognized.");
        return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, result);
    }

    @Override
    protected Response doFilterChecks(SMTPSession session, String command, String parameters) {
        session.setAttachment(CURR_COMMAND, command, State.Transaction);
        return null;
    }

    @Override
    protected HookResult callHook(UnknownHook rawHook, SMTPSession session, String parameters) {
        return rawHook.doUnknown(session, session.getAttachment(CURR_COMMAND, State.Transaction).orElse(MISSING_CURR_COMMAND));
    }

    @Override
    protected Class<UnknownHook> getHookInterface() {
        return UnknownHook.class;
    }

    @Override
    protected TimeMetric timer(Request request) {
        return metricFactory.timer("SMTP-unknown");
    }
}
