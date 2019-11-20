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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.mpt.user.ScriptedUserAdder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestScriptedUserAdder {
    
    private DiscardProtocol protocol;
    
    private DiscardProtocol.Record record;
    
    @Before
    public void setUp() throws Exception {
        protocol = new DiscardProtocol();
        protocol.start();
        record = protocol.recordNext();
    }

    @After
    public void tearDown() throws Exception {
        protocol.stop();
    }

    @Test
    public void testShouldExecuteScriptAgainstPort() throws Exception {
        ScriptedUserAdder adder = new ScriptedUserAdder("localhost", protocol.getPort(), "C: USER='${user}' password='${password}'");
        adder.addUser(Username.of("user"), "Some Password");
        assertThat(record.complete()).isEqualTo("USER='user' password='Some Password'\r\n");
    }
}
