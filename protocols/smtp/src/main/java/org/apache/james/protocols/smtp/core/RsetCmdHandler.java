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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;

import com.google.common.collect.ImmutableSet;

/**
  * Handles RSET command
  */
public class RsetCmdHandler implements CommandHandler<SMTPSession> {
    /**
     * The name of the command handled by the command handler
     */
    private static final Collection<String> COMMANDS = ImmutableSet.of("RSET");
    private static final Response OK = new SMTPResponse(SMTPRetCode.MAIL_OK, DSNStatus.getStatus(DSNStatus.SUCCESS,DSNStatus.UNDEFINED_STATUS)+" OK").immutable();
    private static final Response SYNTAX_ERROR = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_INVALID_ARG)+" Unexpected argument provided with RSET command").immutable();
    /**
     * handles RSET command
     *
    **/
    public Response onCommand(SMTPSession session, Request request) {
        return doRSET(session, request.getArgument());
    }


    /**
     * Handler method called upon receipt of a RSET command.
     * Resets message-specific, but not authenticated user, state.
     *
     * @param session SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     * @return 
     */
    private Response doRSET(SMTPSession session, String argument) {
        if ((argument == null) || (argument.length() == 0)) {
            session.resetState();
            return OK;
        } else {
            return SYNTAX_ERROR;
        }
    }

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
}
