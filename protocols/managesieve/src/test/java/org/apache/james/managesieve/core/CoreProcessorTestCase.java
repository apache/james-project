/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.core;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Lists;
import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.api.commands.Authenticate;
import org.apache.james.managesieve.api.commands.Capability.Capabilities;
import org.apache.james.managesieve.util.SettableSession;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.user.api.UsersRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CoreProcessorTestCase {

    public static final String USER = "test";
    public static final String SCRIPT = "script";
    public static final String CONTENT = "content";
    public static final String OLDNAME = "oldname";
    public static final String NEW_NAME = "newName";
    private SettableSession session;
    private SieveParser sieveParser;
    private SieveRepository sieveRepository;

    private CoreProcessor core;
    private UsersRepository usersRepository;

    @Before
    public void setUp() throws Exception {
        session = new SettableSession();
        sieveParser = mock(SieveParser.class);
        sieveRepository = mock(SieveRepository.class);
        usersRepository = mock(UsersRepository.class);
        core = new CoreProcessor(sieveRepository, usersRepository, sieveParser);
        when(usersRepository.contains(USER)).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
                return true;
            }
        });
    }

    @Test
    public final void testCapabilityUnauthenticated() {
        session.setState(Session.State.AUTHENTICATED);
        when(sieveParser.getExtensions()).thenAnswer(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList("a", "b", "c");
            }
        });
        core = new CoreProcessor(sieveRepository, usersRepository, sieveParser);
        assertThat(core.capability(session)).containsEntry(Capabilities.IMPLEMENTATION, CoreProcessor.IMPLEMENTATION_DESCRIPTION)
            .containsEntry(Capabilities.VERSION, CoreProcessor.MANAGE_SIEVE_VERSION)
            .containsEntry(Capabilities.SIEVE, "a b c")
            .containsEntry(Capabilities.SASL, Authenticate.SupportedMechanism.PLAIN.toString());
    }

    @Test
    public final void testCapabilityAuthenticated() {
        session.setState(Session.State.AUTHENTICATED);
        when(sieveParser.getExtensions()).thenAnswer(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList("a", "b", "c");
            }
        });
        core = new CoreProcessor(sieveRepository, usersRepository, sieveParser);
        session.setUser(USER);
        assertThat(core.capability(session)).containsEntry(Capabilities.IMPLEMENTATION, CoreProcessor.IMPLEMENTATION_DESCRIPTION)
            .containsEntry(Capabilities.VERSION, CoreProcessor.MANAGE_SIEVE_VERSION)
            .containsEntry(Capabilities.SIEVE, "a b c")
            .containsEntry(Capabilities.OWNER, USER)
            .containsEntry(Capabilities.SASL, Authenticate.SupportedMechanism.PLAIN.toString());
    }

    @Test(expected = AuthenticationRequiredException.class)
    public final void testCheckScriptUnauthorised() throws AuthenticationRequiredException, SyntaxException {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(USER);
        core.checkScript(session, "warning");
    }

    @Test
    public final void testCheckScript() throws Exception {
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        when(sieveParser.parse(CONTENT)).thenAnswer(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList("warning1", "warning2");
            }
        });
        assertThat(core.checkScript(session, CONTENT)).containsOnly("warning1", "warning2");
    }

    @Test(expected = SyntaxException.class)
    public final void testCheckScriptSyntaxException() throws Exception {
        doThrow(new SyntaxException("Syntax exception")).when(sieveParser).parse(CONTENT);
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.checkScript(session, CONTENT);
    }

    @Test(expected = AuthenticationRequiredException.class)
    public final void testDeleteScriptUnauthorised() throws Exception {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(USER);
        core.deleteScript(session, SCRIPT);
    }

    @Test(expected = ScriptNotFoundException.class)
    public final void testDeleteScriptNonExistent() throws Exception {
        doThrow(new ScriptNotFoundException()).when(sieveRepository).deleteScript(USER, SCRIPT);
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.deleteScript(session, SCRIPT);
    }


    @Test
    public final void testDeleteScriptExistent() throws Exception {
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        sieveRepository.putScript(USER, SCRIPT, CONTENT);
        core.deleteScript(session, SCRIPT);
        verify(sieveRepository).deleteScript(USER, SCRIPT);
    }

    @Test(expected = IsActiveException.class)
    public final void testDeleteScriptActive() throws Exception {
        doThrow(new IsActiveException()).when(sieveRepository).deleteScript(USER, SCRIPT);
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.deleteScript(session, SCRIPT);
    }

    @Test(expected = AuthenticationRequiredException.class)
    public final void testGetUnauthorisedScript() throws Exception {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(USER);
        core.getScript(session, SCRIPT);
    }

    @Test(expected = ScriptNotFoundException.class)
    public final void testGetNonExistentScript() throws Exception {
        doThrow(new ScriptNotFoundException()).when(sieveRepository).getScript(USER, SCRIPT);
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.getScript(session, SCRIPT);
    }

    @Test
    public final void testGetScript() throws Exception {
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        when(sieveRepository.getScript(USER, SCRIPT)).thenAnswer(new Answer<String>() {
            public String answer(InvocationOnMock invocationOnMock) throws Throwable {
                return CONTENT;
            }
        });
        assertThat(core.getScript(session, SCRIPT)).isEqualTo(CONTENT);
    }

    @Test(expected = AuthenticationRequiredException.class)
    public final void testHaveSpaceUnauthorised() throws Exception {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(USER);
        core.haveSpace(session, SCRIPT, Long.MAX_VALUE);
    }

    @Test
    public final void testHaveSpace() throws Exception {
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.haveSpace(session, SCRIPT, Long.MAX_VALUE);
        verify(sieveRepository).haveSpace(USER, SCRIPT, Long.MAX_VALUE);
    }

    @Test(expected = AuthenticationRequiredException.class)
    public final void testListScriptsUnauthorised() throws Exception {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(USER);
        core.listScripts(session);
    }

    @Test
    public final void testListScripts() throws Exception {
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        when(sieveRepository.listScripts(USER)).thenAnswer(new Answer<List<ScriptSummary>>() {
            @Override
            public List<ScriptSummary> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList(new ScriptSummary(SCRIPT, false));
            }
        });
        sieveRepository.putScript(USER, SCRIPT, CONTENT);
        assertThat(core.listScripts(session)).containsOnly(new ScriptSummary(SCRIPT, false));
    }

    @Test(expected = AuthenticationRequiredException.class)
    public final void testPutScriptUnauthorised() throws Exception {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(USER);
        core.putScript(session, SCRIPT, CONTENT);
    }

    @Test
    public final void testPutScriptAuthorized() throws Exception {
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.putScript(session, SCRIPT, CONTENT);
        verify(sieveRepository).putScript(USER, SCRIPT, CONTENT);
    }

    @Test(expected = SyntaxException.class)
    public final void testPutScriptSyntaxException() throws Exception {
        doThrow(new SyntaxException("Syntax exception")).when(sieveParser).parse(CONTENT);
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.putScript(session, SCRIPT, CONTENT);
    }

    @Test(expected = AuthenticationRequiredException.class)
    public final void testRenameScriptUnauthorized() throws Exception {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(USER);
        core.renameScript(session, OLDNAME, NEW_NAME);
    }

    @Test
    public final void testRenameScript() throws Exception {
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.renameScript(session, OLDNAME, NEW_NAME);
        verify(sieveRepository).renameScript(USER, OLDNAME, NEW_NAME);
    }

    @Test(expected = AuthenticationRequiredException.class)
    public final void testSetActiveUnauthorised() throws Exception {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(USER);
        core.setActive(session, SCRIPT);
    }

    @Test
    public final void testSetActive() throws Exception {
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.setActive(session, SCRIPT);
        verify(sieveRepository).setActive(USER, SCRIPT);
    }

    @Test(expected = AuthenticationRequiredException.class)
    public final void testGetUnauthorisedActive() throws Exception {
        session.setState(Session.State.UNAUTHENTICATED);
        session.setUser(USER);
        core.getActive(session);
    }

    @Test(expected = ScriptNotFoundException.class)
    public final void testGetNonExistentActive() throws Exception {
        doThrow(new ScriptNotFoundException()).when(sieveRepository).getActive(USER);
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        core.getActive(session);
    }

    @Test
    public final void testGetActive() throws Exception {
        session.setState(Session.State.AUTHENTICATED);
        session.setUser(USER);
        sieveRepository.setActive(USER, SCRIPT);
        when(sieveRepository.getActive(USER)).thenAnswer(new Answer<String>() {
            public String answer(InvocationOnMock invocationOnMock) throws Throwable {
                return CONTENT;
            }
        });
        assertThat(core.getActive(session)).isEqualTo(CONTENT);
    }
}
