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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.api.UserAdder;
import org.apache.james.mpt.host.ExternalHostSystem;
import org.apache.james.mpt.monitor.NullMonitor;
import org.apache.james.mpt.session.ExternalSessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestExternalHostSystem {
    
    private static final String USER = "USER NAME";

    private static final String PASSWORD = "SOME PASSWORD";

    private static final String SHABANG = "This Is The Shabang";

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);

    private DiscardProtocol protocol;
    private DiscardProtocol.Record record;
    private Continuation continuation;
    private UserAdder userAdder;

    @Before
    public void setUp() throws Exception {
        protocol = new DiscardProtocol();
        protocol.start();
        record = protocol.recordNext();
        continuation = mock(Continuation.class);
        userAdder = mock(UserAdder.class);
    }

    @After
    public void tearDown() {
        protocol.stop();
    }

    @Test
    public void testWrite() throws Exception {
        Session session = newSession(SHABANG);
        final String in = "Hello, World";
        session.writeLine(in);
        session.stop();

        assertThat(record.complete()).isEqualTo(in + "\r\n");
    }

    @Test
    public void testAddUser() throws Exception {
        ExternalHostSystem system = buildSystem(SHABANG);
        system.addUser(USER, PASSWORD);
        verify(userAdder, times(1)).addUser(USER, PASSWORD);
        verifyNoMoreInteractions(userAdder);
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
