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

package org.apache.james.mpt.script;

import org.apache.james.mpt.Runner;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.protocol.ProtocolSession;

/**
 * Abstract Protocol Test is the root of all of the scripted test cases. It
 * provides basic functionality for running a protocol session as a JUnit test,
 * and failing if exceptions are thrown. To create a test which reads the entire
 * protocol session from a single protocol definition file, use the
 * {@link AbstractSimpleScriptedTestProtocol}.
 * 
 * @author Darrell DeBoer
 * @author Andrew C. Oliver
 */
public abstract class AbstractProtocolTestFramework {

    /** The Protocol session which is run before the testElements */
    protected ProtocolSession preElements = new ProtocolSession();

    /** The Protocol session which contains the tests elements */
    protected ProtocolSession testElements = new ProtocolSession();

    /** The Protocol session which is run after the testElements. */
    protected ProtocolSession postElements = new ProtocolSession();

    protected final Runner runner;
    private final HostSystem hostSystem;

    private final String userName;
    private final String password;

    public AbstractProtocolTestFramework(HostSystem hostSystem, String userName, String password) {
        this.hostSystem = hostSystem;
        this.userName = userName;
        this.password = password;
        runner = new Runner();
    }

    protected void setUp() throws Exception {
        hostSystem.beforeTest();
        hostSystem.addUser(userName, password);
    }

    protected void tearDown() throws Exception {
        hostSystem.afterTest();
    }

    /**
     * <p>
     * Runs the pre,test and post protocol sessions against a local copy of the
     * Server. This is useful for rapid development and debugging.
     * </p>
     * Instead of sending requests to a socket connected to a running instance
     * of James, this method uses the {@link HostSystem} to simplify testing.
     * One mock instance is required per protocol session/connection.
     */
    protected void runSessions() throws Exception {
        class SessionContinuation implements Continuation {

            public ProtocolSession session;

            public void doContinue() {
                if (session != null) {
                    session.doContinue();
                }
            }

        }
        SessionContinuation continuation = new SessionContinuation();

        Session[] sessions = new Session[testElements.getSessionCount()];

        for (int i = 0; i < sessions.length; i++) {
            sessions[i] = hostSystem.newSession(continuation);
            sessions[i].start();
        }
        try {
            continuation.session = preElements;
            preElements.runSessions(sessions);
            continuation.session = testElements;
            testElements.runSessions(sessions);
            continuation.session = postElements;
            postElements.runSessions(sessions);
        }
        finally {
            for (Session session : sessions) {
                session.stop();
            }
        }
    }

}
