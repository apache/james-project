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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

/**
 * Handles RSET command
 */
public class RsetCmdHandler implements CommandHandler<POP3Session> {
    private static final Collection<String> COMMANDS = Collections.unmodifiableCollection(Arrays.asList("RSET"));

    /**
     * Handler method called upon receipt of a RSET command. Calls stat() to
     * reset the mailbox.
     */
    public Response onCommand(POP3Session session, Request request) {
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            stat(session);
            return POP3Response.OK;
        } else {
            return POP3Response.ERR;
        }
        
    }

    /**
     * Implements a "stat". If the handler is currently in a transaction state,
     * this amounts to a rollback of the mailbox contents to the beginning of
     * the transaction. This method is also called when first entering the
     * transaction state to initialize the handler copies of the user inbox.
     */
    protected void stat(POP3Session session) {
        try {
            List<MessageMetaData> messages = session.getUserMailbox().getMessages();

            session.setAttachment(POP3Session.UID_LIST, messages, State.Transaction);
            session.setAttachment(POP3Session.DELETED_UID_LIST, new ArrayList<String>(), State.Transaction);
        } catch (IOException e) {
            // In the event of an exception being thrown there may or may not be
            // anything in userMailbox
            session.getLogger().error("Unable to STAT mail box ", e);
        }

    }

    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
