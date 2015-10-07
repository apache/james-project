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

import junit.framework.TestCase;

import org.apache.james.mpt.DiscardProtocol;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.resources.Union;

public class TestAddUser extends TestCase {

    private static final int PORT = 10001;
    
    DiscardProtocol fakeServer;
    
    DiscardProtocol.Record record;
    
    MailProtocolTestTask subject;
    
    protected void setUp() throws Exception {
        super.setUp();
        fakeServer = new DiscardProtocol(PORT);
        fakeServer.start();
        record = fakeServer.recordNext();
        
        subject = new MailProtocolTestTask();
        subject.setHost("127.0.0.1");
        subject.setPort(PORT+1);
        subject.add(new Union());
        subject.setProject(new Project());
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        fakeServer.stop();
    }

    public void testShouldExecuteScriptAgainstPort() throws Exception {
        MailProtocolTestTask.AddUser user = subject.createAddUser();
        user.setPort(PORT);
        user.setPasswd("PASSWORD");
        user.setUser("USER");
        final String script = "This script adds a user";
        user.addText("C: " + script);
        subject.execute();
        assertEquals(script + "\r\n", record.complete());
    }
}
