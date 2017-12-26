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

import java.io.StringReader;

import org.apache.james.mpt.api.ProtocolInteractor;
import org.apache.james.mpt.protocol.ProtocolSessionBuilder;
import org.jmock.Mock;
import org.jmock.MockObjectTestCase;

public class TestFileProtocolSessionBuilder extends MockObjectTestCase {

    private static final String SCRIPT_WITH_VARIABLES = "HELLO ${not} ${foo} WORLD ${bar}";
    private static final String SCRIPT_WITH_FOO_REPLACED_BY_WHATEVER = "HELLO ${not} whatever WORLD ${bar}";
    private static final String SCRIPT_WITH_VARIABLES_INLINED = "HELLO not foo WORLD bar";
    
    ProtocolSessionBuilder builder;
    ProtocolInteractor session;

    private Mock mockSession;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        builder = new ProtocolSessionBuilder();
        mockSession = mock(ProtocolInteractor.class);
        session = (ProtocolInteractor) mockSession.proxy();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void addLines() throws Exception {
        builder.addProtocolLines("A Script", new StringReader(ProtocolSessionBuilder.CLIENT_TAG + " " + SCRIPT_WITH_VARIABLES), session);
    }
    
    public void testShouldPreserveContentsWhenNoVariablesSet() throws Exception {
        mockSession.expects(once()).method("cl").with(eq(-1), eq(SCRIPT_WITH_VARIABLES));
        addLines();
    }

    public void testShouldReplaceVariableWhenSet() throws Exception {
        mockSession.expects(once()).method("cl").with(eq(-1), eq(SCRIPT_WITH_FOO_REPLACED_BY_WHATEVER));
        builder.setVariable("foo", "whatever");
        addLines();
    }
    
    public void testShouldReplaceAllVariablesWhenSet() throws Exception {
        mockSession.expects(once()).method("cl").with(eq(-1), eq(SCRIPT_WITH_VARIABLES_INLINED));
        builder.setVariable("bar", "bar");
        builder.setVariable("foo", "foo");
        builder.setVariable("not", "not");
        addLines();
    }
    
    public void testShouldReplaceVariableAtBeginningAndEnd() throws Exception {
        mockSession.expects(once()).method("cl").with(eq(-1), eq("whatever Some Other Scriptwhateverwhatever"));
        builder.setVariable("foo", "whatever");
        builder.addProtocolLines("A Script", new StringReader(ProtocolSessionBuilder.CLIENT_TAG + " " + "${foo} Some Other Script${foo}${foo}"), session);
    }
    
    public void testShouldIgnoreNotQuiteVariables() throws Exception {
        final String NEARLY = "{foo}${}${foo Some Other Script${foo}";
        mockSession.expects(once()).method("cl").with(eq(-1), eq(NEARLY));
        builder.setVariable("foo", "whatever");
        builder.addProtocolLines("A Script", new StringReader(ProtocolSessionBuilder.CLIENT_TAG + " " + NEARLY), session);
    }
}
