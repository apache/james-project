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
import java.util.List;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public abstract class POP3MessageCommandDelegate {

    private final String command;
    
    public POP3MessageCommandDelegate(Collection<String> commands) {
        Preconditions.checkArgument(commands != null && !commands.isEmpty(), "You must provide at least one command keyword");
        this.command = commands.iterator().next();
    }

    public Response handleMessageRequest(POP3Session session, Request request) {
        if (session.getHandlerState() == POP3Session.TRANSACTION) {

            return POP3MessageCommandArguments.fromRequest(request)
                .map(args -> handleValidArgs(session, args))
                .orElseGet(this::handleSyntaxError);
            
        } else {
            return POP3Response.ERR;
        }
    }

    private Response handleValidArgs(POP3Session session, POP3MessageCommandArguments args) {
        try {

            MessageMetaData data = getMetaData(session, args.getMessageNumber());

            List<String> deletedUidList = session.getAttachment(POP3Session.DELETED_UID_LIST, ProtocolSession.State.Transaction).orElse(ImmutableList.of());
            if (!deletedUidList.contains(data.getUid())) {
                return handleMessageExists(session, data, args);
            } else {
                return new POP3Response(POP3Response.ERR_RESPONSE, "Message (" + args.getMessageNumber() + ") already deleted.");
            }

        } catch (IOException ioe) {
            return new POP3Response(POP3Response.ERR_RESPONSE, "Message (" + args.getMessageNumber() + ") does not exist.");
        }
    }

    private MessageMetaData getMetaData(POP3Session session, int number) throws IOException {
        if (number <= 0) {
            throw new IOException("MessageMetaData does not exist for number " + number);
        }
        return session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)
            .filter(uidList -> number <= uidList.size())
            .map(uidList -> uidList.get(number - 1))
            .orElseThrow(() -> new IOException("MessageMetaData does not exist for number " + number));
    }

    protected Response handleSyntaxError() {
        return new POP3Response(POP3Response.ERR_RESPONSE, "Usage: " + command + " [mail number]");
    }

    protected abstract Response handleMessageExists(POP3Session session, MessageMetaData data, POP3MessageCommandArguments args) throws IOException;
    
}
