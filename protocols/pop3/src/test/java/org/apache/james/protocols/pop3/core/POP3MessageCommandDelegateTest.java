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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;
import org.junit.jupiter.api.Test;

public class POP3MessageCommandDelegateTest {

    private static final Response SUCCESS = new POP3Response(POP3Response.OK_RESPONSE, "test succeeded");
    private static final MessageMetaData MESSAGE_META_DATA = new MessageMetaData("1234", 567);
    
    private POP3MessageCommandDelegate commandDelegate = new POP3MessageCommandDelegate(Set.of("TEST")) {
        @Override
        protected Response handleMessageExists(POP3Session session, MessageMetaData data, POP3MessageCommandArguments args) throws IOException {
            return SUCCESS;
        }
    };

    @Test
    void onCommandIncludesKeywordInSyntaxError() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn(null);

        Response response = commandDelegate.handleMessageRequest(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.ERR_RESPONSE);
        assertThat(response.getLines().get(0)).isEqualTo("-ERR Usage: TEST [mail number]");
    }

    @Test
    void onCommandFailsInAuthReadyState() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.AUTHENTICATION_READY);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn("1");

        assertThat(commandDelegate.handleMessageRequest(session, request).getRetCode())
            .isEqualTo(POP3Response.ERR_RESPONSE);
    }

    @Test
    void onCommandFailsInUserSetState() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.AUTHENTICATION_USERSET);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn("1");

        assertThat(commandDelegate.handleMessageRequest(session, request).getRetCode())
            .isEqualTo(POP3Response.ERR_RESPONSE);
    }

    @Test
    void onCommandHandlesNonExistingMessage() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn("1");

        Response response = commandDelegate.handleMessageRequest(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.ERR_RESPONSE);
        assertThat(response.getLines().get(0)).contains("does not exist");
    }

    @Test
    void onCommandHandlesDeletedMessage() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn("1");

        MessageMetaData data = MESSAGE_META_DATA;
        when(session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(data)));

        when(session.getAttachment(POP3Session.DELETED_UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(data.getUid())));

        Response response = commandDelegate.handleMessageRequest(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.ERR_RESPONSE);
        assertThat(response.getLines().get(0)).contains("already deleted");
    }

    @Test
    void onCommandHandlesExistingMessage() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn("1");

        when(session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(MESSAGE_META_DATA)));

        Response response = commandDelegate.handleMessageRequest(session, request);
        assertThat(response).isEqualTo(SUCCESS);
    }
}
