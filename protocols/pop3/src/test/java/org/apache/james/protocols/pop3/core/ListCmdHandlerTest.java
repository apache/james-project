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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;
import org.junit.jupiter.api.Test;

public class ListCmdHandlerTest {

    @Test
    void onCommandHandlesEmptyMailbox() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn(null);

        when(session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(Collections.emptyList()));

        Response response = new ListCmdHandler(new RecordingMetricFactory()).onCommand(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.OK_RESPONSE);
        assertThat(response).isInstanceOf(POP3Response.class);

        List<CharSequence> result = response.getLines();
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("+OK 0 0");
        assertThat(result.get(1)).isEqualTo(".");
    }
    
    @Test
    void onCommandRetrievesAllMessages() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn(null);

        when(session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(
            new MessageMetaData("123", 123), new MessageMetaData("456", 456))));

        Response response = new ListCmdHandler(new RecordingMetricFactory()).onCommand(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.OK_RESPONSE);
        assertThat(response).isInstanceOf(POP3Response.class);

        List<CharSequence> result = response.getLines();
        assertThat(result).hasSize(4);
        assertThat(result.get(0)).isEqualTo("+OK 2 579");
        assertThat(result.get(1)).isEqualTo("1 123");
        assertThat(result.get(2)).isEqualTo("2 456");
        assertThat(result.get(3)).isEqualTo(".");
    }

    @Test
    void onCommandExcludesDeletedMessages() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn(null);

        when(session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(
            new MessageMetaData("123", 123), new MessageMetaData("456", 456), new MessageMetaData("789", 789))));
        when(session.getAttachment(POP3Session.DELETED_UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of("456")));

        Response response = new ListCmdHandler(new RecordingMetricFactory()).onCommand(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.OK_RESPONSE);
        assertThat(response).isInstanceOf(POP3Response.class);

        List<CharSequence> result = response.getLines();
        assertThat(result).hasSize(4);
        assertThat(result.get(0)).isEqualTo("+OK 2 912");
        assertThat(result.get(1)).isEqualTo("1 123");
        assertThat(result.get(2)).isEqualTo("3 789");
        assertThat(result.get(3)).isEqualTo(".");
    }

    @Test
    void onCommandListsSingleMessage() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        when(request.getArgument()).thenReturn("1");

        MessageMetaData data = new MessageMetaData("1234", 567);
        when(session.getAttachment(POP3Session.UID_LIST, ProtocolSession.State.Transaction)).thenReturn(Optional.of(List.of(data)));

        Response response = new ListCmdHandler(new RecordingMetricFactory()).onCommand(session, request);
        assertThat(response.getRetCode()).isEqualTo(POP3Response.OK_RESPONSE);
        assertThat(response.getLines()).containsOnly("+OK 1 567");
    }

}
