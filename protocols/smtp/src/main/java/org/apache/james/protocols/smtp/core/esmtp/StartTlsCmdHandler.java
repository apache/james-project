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

package org.apache.james.protocols.smtp.core.esmtp;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.SMTPStartTlsResponse;
import org.apache.james.protocols.smtp.dsn.DSNStatus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Handles STARTTLS command
 */
public class StartTlsCmdHandler implements CommandHandler<SMTPSession>, EhloExtension {
    /**
     * The name of the command handled by the command handler
     */
    private final static String COMMAND_NAME = "STARTTLS";
    private final static Collection<String> COMMANDS = ImmutableSet.of(COMMAND_NAME);
    private final static List<String> FEATURES = ImmutableList.of(COMMAND_NAME);

    private static final Response TLS_ALREADY_ACTIVE = new SMTPResponse("500", DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_CMD) + " TLS already active RFC2487 5.2").immutable();
    private static final Response READY_FOR_STARTTLS = new SMTPStartTlsResponse("220", DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.UNDEFINED_STATUS) + " Ready to start TLS").immutable();
    private static final Response SYNTAX_ERROR = new SMTPResponse("501 " + DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Syntax error (no parameters allowed) with STARTTLS command").immutable();
    private static final Response NOT_SUPPORTED = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_CMD) +" Command " + COMMAND_NAME +" unrecognized.").immutable();
    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    /**
     * Handler method called upon receipt of a STARTTLS command. Resets
     * message-specific, but not authenticated user, state.
     * 
     */
    public Response onCommand(SMTPSession session, Request request) {
        if (session.isStartTLSSupported()) {
            if (session.isTLSStarted()) {
                return TLS_ALREADY_ACTIVE;
            } else {
                String parameters = request.getArgument();
                if ((parameters == null) || (parameters.length() == 0)) {
                    return READY_FOR_STARTTLS;
                } else {
                    return SYNTAX_ERROR;
                } 
            }

        } else {
            return NOT_SUPPORTED;
        }
    }

    /**
     * @see org.apache.james.protocols.smtp.core.esmtp.EhloExtension#getImplementedEsmtpFeatures(org.apache.james.protocols.smtp.SMTPSession)
     */
    @SuppressWarnings("unchecked")
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        // SMTP STARTTLS
        if (!session.isTLSStarted() && session.isStartTLSSupported()) {
            return FEATURES;
        } else {
            return Collections.EMPTY_LIST;
        }

    }

}
