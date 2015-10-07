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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.james.protocols.api.ProtocolSession.State;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

/**
 * Handles LIST command
 */
public class ListCmdHandler implements CommandHandler<POP3Session> {
    private static final Collection<String> COMMANDS = Collections.unmodifiableCollection(Arrays.asList("LIST"));

    /**
     * Handler method called upon receipt of a LIST command. Returns the number
     * of messages in the mailbox and its aggregate size, or optionally, the
     * number and size of a single message.
     * 
     * @param session
     *            the pop3 session
     * @param request
     *            the request to process
     */

    @SuppressWarnings("unchecked")
    public Response onCommand(POP3Session session, Request request) {
        String parameters = request.getArgument();
        List<MessageMetaData> uidList = (List<MessageMetaData>) session.getAttachment(POP3Session.UID_LIST, State.Transaction);
        List<String> deletedUidList = (List<String>) session.getAttachment(POP3Session.DELETED_UID_LIST, State.Transaction);

        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            POP3Response response = null;

            if (parameters == null) {

                long size = 0;
                int count = 0;
                List<MessageMetaData> validResults = new ArrayList<MessageMetaData>();
                if (uidList.isEmpty() == false) {

                    for (int i = 0; i < uidList.size(); i++) {
                        MessageMetaData data = uidList.get(i);
                        if (deletedUidList.contains(data.getUid()) == false) {
                            size += data.getSize();
                            count++;
                            validResults.add(data);
                        }
                    }
                }
                StringBuilder responseBuffer = new StringBuilder(32).append(count).append(" ").append(size);
                response = new POP3Response(POP3Response.OK_RESPONSE, responseBuffer.toString());
                count = 0;
                for (int i = 0; i < validResults.size(); i++) {
                    responseBuffer = new StringBuilder(16).append(i + 1).append(" ").append(validResults.get(i).getSize());
                    response.appendLine(responseBuffer.toString());
                }
                response.appendLine(".");
            } else {
                int num = 0;
                try {
                    num = Integer.parseInt(parameters);
                    
                    MessageMetaData data = MessageMetaDataUtils.getMetaData(session, num);
                    if (data == null) {
                        StringBuilder responseBuffer = new StringBuilder(64).append("Message (").append(num).append(") does not exist.");
                        return  new POP3Response(POP3Response.ERR_RESPONSE, responseBuffer.toString());
                    }
                    
                    if (deletedUidList.contains(data.getUid()) == false) {
                        StringBuilder responseBuffer = new StringBuilder(64).append(num).append(" ").append(data.getSize());
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
            return response;
        } else {
            return POP3Response.ERR;
        }
    }

    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
