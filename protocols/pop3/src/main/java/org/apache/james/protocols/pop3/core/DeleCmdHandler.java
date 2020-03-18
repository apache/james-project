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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

import com.google.common.collect.ImmutableSet;

/**
 * Handles DELE command
 */
public class DeleCmdHandler implements CommandHandler<POP3Session> {
    private static final Collection<String> COMMANDS = ImmutableSet.of("DELE");

    private static final Response SYNTAX_ERROR = new POP3Response(POP3Response.ERR_RESPONSE, "Usage: DELE [mail number]").immutable();
    private static final Response DELETED = new POP3Response(POP3Response.OK_RESPONSE, "Message deleted").immutable();

    /**
     * Handler method called upon receipt of a DELE command. This command
     * deletes a particular mail message from the mailbox.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Response onCommand(POP3Session session, Request request) {
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            int num = 0;
            try {
                num = Integer.parseInt(request.getArgument());
            } catch (Exception e) {
                return SYNTAX_ERROR;
            }
            try {
                MessageMetaData meta = MessageMetaDataUtils.getMetaData(session, num);
                if (meta == null) {
                    StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                    return  new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                }
                List<String> deletedUidList = session.getAttachment(POP3Session.DELETED_UID_LIST, State.Transaction)
                    .orElseGet(() -> {
                        ArrayList<String> uidList = new ArrayList<>();
                        session.setAttachment(POP3Session.DELETED_UID_LIST, uidList, State.Transaction);
                        return uidList;
                    });

                String uid = meta.getUid();

                if (deletedUidList.contains(uid)) {
                    StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") already deleted.");
                    return new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                } else {
                    deletedUidList.add(uid);
                    // we are replacing our reference with "DELETED", so we have
                    // to dispose the no-more-referenced mail object.
                    return DELETED;
                }
            } catch (IndexOutOfBoundsException iob) {
                StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                return  new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
            }
        } else {
            return POP3Response.ERR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
