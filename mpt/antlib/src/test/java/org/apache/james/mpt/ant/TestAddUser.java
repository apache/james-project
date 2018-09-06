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

package org.apache.james.mpt.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mpt.DiscardProtocol;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.resources.Union;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestAddUser {

    DiscardProtocol fakeServer;
    
    DiscardProtocol.Record record;
    
    MailProtocolTestTask subject;
    
    @Before
    public void setUp() throws Exception {
        fakeServer = new DiscardProtocol();
        fakeServer.start();
        record = fakeServer.recordNext();
        
        subject = new MailProtocolTestTask();
        subject.setHost("127.0.0.1");
        subject.setPort(fakeServer.getPort().getValue());
        subject.add(new Union());
        subject.setProject(new Project());
    }

    @After
    public void tearDown() throws Exception {
        fakeServer.stop();
    }

    @Test
    public void testShouldExecuteScriptAgainstPort() throws Exception {
        MailProtocolTestTask.AddUser user = subject.createAddUser();
        user.setPort(fakeServer.getPort().getValue());
        user.setPasswd("PASSWORD");
        user.setUser("USER");
        final String script = "This script adds a user";
        user.addText("C: " + script);
        subject.execute();
        assertThat(record.complete()).isEqualTo(script + "\r\n");
    }
}
