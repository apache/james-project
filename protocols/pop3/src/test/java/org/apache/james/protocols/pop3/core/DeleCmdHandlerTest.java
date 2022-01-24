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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;
import org.junit.jupiter.api.Test;

public class DeleCmdHandlerTest {

    @Test
    void onCommandDeletesInitialMessage() throws IOException {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn("1");

        MessageMetaData data = new MessageMetaData("1234", 567);
        when(session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(data)));
        when(session.getAttachment(POP3Session.DELETED_UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.empty());

        Mailbox mailbox = mock(Mailbox.class);
        when(session.getUserMailbox()).thenReturn(mailbox);
        when(mailbox.getMessage(data.getUid())).thenThrow(new IOException("cannot retrieve message content"));

        Response response = new DeleCmdHandler(new RecordingMetricFactory()).onCommand(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.OK_RESPONSE);
        assertThat(response.getLines().get(0)).contains("Message deleted");

        verify(session).setAttachment(eq(POP3Session.DELETED_UID_LIST), 
            argThat(uidList -> uidList.size() == 1 && uidList.contains("1234")),
            eq(ProtocolSession.State.Transaction));
    }

    @Test
    void onCommandDeletesAdditionalMessage() throws IOException {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn("1");

        MessageMetaData data = new MessageMetaData("1234", 567);
        when(session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(data)));
        List<String> uidList = new ArrayList<>();
        when(session.getAttachment(POP3Session.DELETED_UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(uidList));

        Mailbox mailbox = mock(Mailbox.class);
        when(session.getUserMailbox()).thenReturn(mailbox);
        when(mailbox.getMessage(data.getUid())).thenThrow(new IOException("cannot retrieve message content"));

        Response response = new DeleCmdHandler(new RecordingMetricFactory()).onCommand(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.OK_RESPONSE);
        assertThat(response.getLines().get(0)).contains("Message deleted");

        assertThat(uidList).containsOnly("1234");
        verify(session, never()).setAttachment(eq(POP3Session.DELETED_UID_LIST), anyList(), eq(ProtocolSession.State.Transaction));
    }

    @Test
    void onCommandHandlesDeletedMessage() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn("1");

        MessageMetaData data = new MessageMetaData("1234", 567);
        when(session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(data)));
        when(session.getAttachment(POP3Session.DELETED_UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(data.getUid())));

        Response response = new DeleCmdHandler(new RecordingMetricFactory()).onCommand(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.ERR_RESPONSE);
        assertThat(response.getLines().get(0)).contains("already deleted");
    }

}
