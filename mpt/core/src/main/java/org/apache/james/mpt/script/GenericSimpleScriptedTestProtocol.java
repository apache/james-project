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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.apache.james.mpt.api.Continuation;
import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.protocol.FileProtocolSessionBuilder;
import org.apache.james.mpt.protocol.ProtocolSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericSimpleScriptedTestProtocol<T extends HostSystem, SELF extends GenericSimpleScriptedTestProtocol<?, ?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericSimpleScriptedTestProtocol.class);

    public interface PrepareCommand<T extends HostSystem> {
        void prepare(T system) throws Exception;
    }
    
    private static class CreateUser implements PrepareCommand<HostSystem> {
        
        final String user;
        final String password;

        CreateUser(String user, String password) {
            this.user = user;
            this.password = password;
        }
        
        public void prepare(HostSystem system) throws Exception {
            try {
                system.addUser(user, password);
            } catch (Exception e) {
                LOGGER.info("User {} already exists", user, e);
            }
        }
    }
    
    private final FileProtocolSessionBuilder builder = new FileProtocolSessionBuilder();
    private final String scriptDirectory;

    /** The Protocol session which is run before the testElements */
    private ProtocolSession preElements = new ProtocolSession();

    /** The Protocol session which contains the tests elements */
    private ProtocolSession testElements = new ProtocolSession();

    /** The Protocol session which is run after the testElements. */
    private ProtocolSession postElements = new ProtocolSession();
    
    private final T hostSystem;
    private final List<PrepareCommand<? super T>> prepareCommands;
    private Locale locale;

    public GenericSimpleScriptedTestProtocol(String scriptDirectory, T hostSystem) throws Exception {
        this.scriptDirectory = scriptDirectory;
        this.hostSystem = hostSystem;
        this.locale = Locale.getDefault();
        this.prepareCommands = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public SELF withLocale(Locale locale) {
        this.locale = locale;
        return (SELF) this;
    }
    
    @SuppressWarnings("unchecked")
    public SELF withUser(String user, String password) {
        prepareCommands.add(new CreateUser(user, password));
        return (SELF) this;
    }
    
    @SuppressWarnings("unchecked")
    public SELF withPreparedCommand(PrepareCommand<? super T> command) {
        prepareCommands.add(command);
        return (SELF) this;
    }
    
    public ProtocolSession preElements() {
        return preElements;
    }

    public ProtocolSession testElements() {
        return testElements;
    }
    
    public ProtocolSession postElements() {
        return postElements;
    }
    
    /**
     * Reads test elements from the protocol session file and adds them to the
     * {@link #testElements} ProtocolSession. Then calls {@link #runSessions}.
     * @param locale
     *            execute the test using this locale
     */
    public void run(String fileName) throws Exception {
        prepare();
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            addTestFile(fileName + ".test", testElements);
            runSessions(hostSystem);
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    private void prepare() throws Exception {
        for (PrepareCommand<? super T> cmd: prepareCommands) {
            cmd.prepare(hostSystem);
        }
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
    private void runSessions(HostSystem hostSystem) throws Exception {
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
    
    /**
     * Finds the protocol session file identified by the test name, and builds
     * protocol elements from it. All elements from the definition file are
     * added to the supplied ProtocolSession.
     * 
     * @param fileName
     *            The name of the file to read
     * @param session
     *            The ProtocolSession to add elements to.
     */
    public void addTestFile(String fileName, ProtocolSession session) throws Exception {

        fileName = scriptDirectory + fileName;
        
        // Need to find local resource.
        InputStream is = this.getClass().getResourceAsStream(fileName);

        if (is == null) {
            throw new Exception("Test Resource '" + fileName + "' not found.");
        }

        try {
            builder.addProtocolLinesFromStream(is, session, fileName);
        }
        finally {
            IOUtils.closeQuietly(is);
        }
        
    }

}
