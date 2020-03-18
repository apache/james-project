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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Handles UIDL command
 */
public class UidlCmdHandler implements CommandHandler<POP3Session>, CapaCapability {
    private static final Collection<String> COMMANDS = ImmutableSet.of("UIDL");
    private static final Set<String> CAPS = ImmutableSet.of("UIDL");

    /**
     * Handler method called upon receipt of a UIDL command. Returns a listing
     * of message ids to the client.
     */
    @Override
    public Response onCommand(POP3Session session, Request request) {
        POP3Response response = null;
        String parameters = request.getArgument();
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            List<MessageMetaData> uidList = session.getAttachment(POP3Session.UID_LIST, State.Transaction).orElse(ImmutableList.of());
            List<String> deletedUidList = session.getAttachment(POP3Session.DELETED_UID_LIST, State.Transaction).orElse(ImmutableList.of());
            try {
                String identifier = session.getUserMailbox().getIdentifier();
                if (parameters == null) {
                    response = new POP3Response(POP3Response.OK_RESPONSE, "unique-id listing follows");

                    for (int i = 0; i < uidList.size(); i++) {
                        MessageMetaData metadata = uidList.get(i);
                        if (!deletedUidList.contains(metadata.getUid())) {
                            StringBuilder responseBuffer = new StringBuilder().append(i + 1).append(" ").append(metadata.getUid(identifier));
                            response.appendLine(responseBuffer.toString());
                        }
                    }

                    response.appendLine(".");
                } else {
                    int num = 0;
                    try {
                        num = Integer.parseInt(parameters);
                        
                        MessageMetaData metadata = MessageMetaDataUtils.getMetaData(session, num);

                        if (metadata == null) {
                            StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                            return  new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                        }

                        if (deletedUidList.contains(metadata.getUid()) == false) {
                            StringBuilder responseBuffer = new StringBuilder(64).append(num).append(" ").append(metadata.getUid(identifier));
                            response = new POP3Response(POP3Response.OK_RESPONSE, responseBuffer.toString());
                        } else {
                            StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") already deleted.");
                            response = new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                        }
                    } catch (IndexOutOfBoundsException npe) {
                        StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                        response = new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                    } catch (NumberFormatException nfe) {
                        StringBuilder responseBuffer = new StringBuilder(64).append(parameters).append(" is not a valid number");
                        response = new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                    }
                }
            } catch (IOException e) {
                return POP3Response.ERR;
            }
            
        } else {
            return POP3Response.ERR;
        }
        return response;
    }

    @Override
    public Set<String> getImplementedCapabilities(POP3Session session) {
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            return CAPS;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
