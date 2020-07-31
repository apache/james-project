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

package org.apache.james.mpt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.StringReader;

import org.apache.james.mpt.api.ProtocolInteractor;
import org.apache.james.mpt.protocol.ProtocolSessionBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestFileProtocolSessionBuilder {

    static final String SCRIPT_WITH_VARIABLES = "HELLO ${not} ${foo} WORLD ${bar}";
    static final String SCRIPT_WITH_FOO_REPLACED_BY_WHATEVER = "HELLO ${not} whatever WORLD ${bar}";
    static final String SCRIPT_WITH_VARIABLES_INLINED = "HELLO not foo WORLD bar";
    
    ProtocolSessionBuilder builder;
    ProtocolInteractor session;

    @BeforeEach
    void setUp() {
        builder = new ProtocolSessionBuilder();
        session = mock(ProtocolInteractor.class);
    }

    @Test
    void testShouldPreserveContentsWhenNoVariablesSet() throws Exception {
        builder.addProtocolLines("A Script", new StringReader(ProtocolSessionBuilder.CLIENT_TAG + " " + SCRIPT_WITH_VARIABLES), session);

        verify(session, times(1)).cl(-1, SCRIPT_WITH_VARIABLES);
        verifyNoMoreInteractions(session);
    }

    @Test
    void testShouldReplaceVariableWhenSet() throws Exception {
        builder.setVariable("foo", "whatever");
        builder.addProtocolLines("A Script", new StringReader(ProtocolSessionBuilder.CLIENT_TAG + " " + SCRIPT_WITH_VARIABLES), session);

        verify(session, times(1)).cl(-1, SCRIPT_WITH_FOO_REPLACED_BY_WHATEVER);
        verifyNoMoreInteractions(session);
    }

    @Test
    void testShouldReplaceAllVariablesWhenSet() throws Exception {
        builder.setVariable("bar", "bar");
        builder.setVariable("foo", "foo");
        builder.setVariable("not", "not");
        builder.addProtocolLines("A Script", new StringReader(ProtocolSessionBuilder.CLIENT_TAG + " " + SCRIPT_WITH_VARIABLES), session);

        verify(session, times(1)).cl(-1, SCRIPT_WITH_VARIABLES_INLINED);
        verifyNoMoreInteractions(session);
    }

    @Test
    void testShouldReplaceVariableAtBeginningAndEnd() throws Exception {
        builder.setVariable("foo", "whatever");
        builder.addProtocolLines("A Script", new StringReader(ProtocolSessionBuilder.CLIENT_TAG + " " + "${foo} Some Other Script${foo}${foo}"), session);

        verify(session, times(1)).cl(-1, "whatever Some Other Scriptwhateverwhatever");
        verifyNoMoreInteractions(session);
    }

    @Test
    void testShouldIgnoreNotQuiteVariables() throws Exception {
        final String NEARLY = "{foo}${}${foo Some Other Script${foo}";
        builder.setVariable("foo", "whatever");
        builder.addProtocolLines("A Script", new StringReader(ProtocolSessionBuilder.CLIENT_TAG + " " + NEARLY), session);

        verify(session, times(1)).cl(-1, NEARLY);
        verifyNoMoreInteractions(session);
    }
}
