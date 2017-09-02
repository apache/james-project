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

import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.api.UserAdder;
import org.apache.james.mpt.host.ExternalHostSystem;
import org.apache.james.mpt.monitor.NullMonitor;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.jmock.Mock;
import org.jmock.MockObjectTestCase;

public class TestExternalHostSystem extends MockObjectTestCase {

    
    private static final String USER = "USER NAME";

    private static final String PASSWORD = "SOME PASSWORD";

    private static final String SHABANG = "This Is The Shabang";

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);

    
    private DiscardProtocol protocol;
    
    private DiscardProtocol.Record record;

    private Continuation continuation;

    private UserAdder userAdder;

    private Mock mockUserAdder;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        protocol = new DiscardProtocol();
        protocol.start();
        record = protocol.recordNext();
        continuation = (Continuation) mock(Continuation.class).proxy();
        mockUserAdder = mock(UserAdder.class);
        userAdder = (UserAdder) mockUserAdder.proxy();
    }

    @Override
    protected void tearDown() throws Exception {
        protocol.stop();
        super.tearDown();
    }
    
    public void testWrite() throws Exception {
        Session session = newSession(SHABANG);
        final String in = "Hello, World";
        session.writeLine(in);
        session.stop();
        assertEquals(in + "\r\n", record.complete());
    }
    
    public void testAddUser() throws Exception {
        mockUserAdder.expects(once()).method("addUser").with(eq(USER), eq(PASSWORD));
        ExternalHostSystem system = buildSystem(SHABANG);
        system.addUser(USER, PASSWORD);
    }

    private Session newSession(String shabang) throws Exception {
        ExternalSessionFactory system = buildSystem(shabang);
        return system.newSession(continuation);
    }

    private ExternalHostSystem buildSystem(String shabang) {
        return new ExternalHostSystem(SUPPORTED_FEATURES, "localhost", protocol.getPort(),
                new NullMonitor(), shabang, userAdder);
    }
}
