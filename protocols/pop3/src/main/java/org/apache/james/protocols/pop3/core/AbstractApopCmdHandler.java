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

import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.Mailbox;

import com.google.common.collect.ImmutableSet;

/**
 * Handles the APOP command
 * 
 * @author Maurer
 *
 */
public abstract class AbstractApopCmdHandler extends AbstractPassCmdHandler {

    private static final Collection<String> COMMANDS = ImmutableSet.of("APOP");
    
    @Override
    public Response onCommand(POP3Session session, Request request) {
        if (session.getAttachment(POP3Session.APOP_TIMESTAMP, State.Connection) == null) {
            // APOP timestamp was not found in the session so APOP is not supported
            return POP3Response.ERR;
        }
        
        String parameters = request.getArgument();
        String[] parts = null;
        boolean syntaxError = false;
        if (parameters != null) {
            parts = parameters.split(" ");
            if (parts.length != 2) {
                syntaxError = true;
            }
        } else {
            syntaxError = true;
        }
        if (!syntaxError && session.getHandlerState() == POP3Session.AUTHENTICATION_READY) {

            Response response = doAuth(session, parts[0], parts[1]);
            
            if (POP3Response.OK_RESPONSE.equals(response.getRetCode())) {
                // the auth was successful so set the user
                session.setUser(parts[0]);
            }
            return response;
        } else {
            session.setHandlerState(POP3Session.AUTHENTICATION_READY);
            return AUTH_FAILED;
        }

    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.pop3.core.AbstractPassCmdHandler#auth(org.apache.james.protocols.pop3.POP3Session, java.lang.String, java.lang.String)
     */
    protected final Mailbox auth(POP3Session session, String username, String password) throws Exception {
        return auth(session, (String)session.getAttachment(POP3Session.APOP_TIMESTAMP, State.Connection), username, password);
    }


    /**
     * Authenticate a {@link POP3Session} and returns the {@link Mailbox} for it. If it can not get authenticated it will return <code>null</code>.
     * 
     * @param session
     * @param apopTimestamp
     * @param user
     * @param digest
     * @return mailbox
     * @throws Exception
     */
    protected abstract Mailbox auth(POP3Session session, String apopTimestamp, String user, String digest) throws Exception;
}
