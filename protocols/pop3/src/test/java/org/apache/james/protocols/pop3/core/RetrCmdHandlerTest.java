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
import java.util.stream.Collectors;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.pop3.POP3Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RetrCmdHandlerTest {

    @ParameterizedTest
    @ValueSource(ints = {8, 16, 32, 64, 128, 256})
    void onCommandShouldNotThrowOnMessageHexNumberOverflow(int pad) {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        String overflowedNumber = Collections.nCopies(pad, "\\xff").stream().collect(Collectors.joining());
        when(request.getArgument()).thenReturn(overflowedNumber);

        assertThat(new RetrCmdHandler(new RecordingMetricFactory()).onCommand(session, request))
            .isEqualTo(RetrCmdHandler.SYNTAX_ERROR);
    }

    @Test
    void onCommandShouldNotThrowOnMessageDecNumberOverflow() {
        POP3Session session = mock(POP3Session.class);
        when(session.getHandlerState()).thenReturn(POP3Session.TRANSACTION);

        Request request = mock(Request.class);
        String overflowedNumber = Long.toString(Long.MAX_VALUE);
        when(request.getArgument()).thenReturn(overflowedNumber);

        assertThat(new RetrCmdHandler(new RecordingMetricFactory()).onCommand(session, request))
            .isEqualTo(RetrCmdHandler.SYNTAX_ERROR);
    }
}
