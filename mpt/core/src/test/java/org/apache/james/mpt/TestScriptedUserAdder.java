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

import org.apache.james.mpt.user.ScriptedUserAdder;

import junit.framework.TestCase;

public class TestScriptedUserAdder extends TestCase {
    
    private DiscardProtocol protocol;
    
    private DiscardProtocol.Record record;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        protocol = new DiscardProtocol();
        protocol.start();
        record = protocol.recordNext();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        protocol.stop();
    }

    public void testShouldExecuteScriptAgainstPort() throws Exception {
        ScriptedUserAdder adder = new ScriptedUserAdder("localhost", protocol.getPort(), "C: USER='${user}' password='${password}'");
        adder.addUser("A User", "Some Password");
        assertEquals("USER='A User' password='Some Password'\r\n", record.complete());
    }
}
