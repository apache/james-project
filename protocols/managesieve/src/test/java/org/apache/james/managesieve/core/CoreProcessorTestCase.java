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

import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.api.commands.Capability.Capabilities;
import org.apache.james.managesieve.mock.MockSession;
import org.apache.james.managesieve.mock.MockSieveParser;
import org.apache.james.managesieve.mock.MockSieveRepository;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * <code>CoreProcessorTestCase</code>
 */
public class CoreProcessorTestCase {

    private MockSession session;
    private MockSieveParser parser;
    private MockSieveRepository repository;
    private CoreProcessor core;

    @Before
    public void setUp() throws Exception {
        session = new MockSession();
        parser = new MockSieveParser();
        repository = new MockSieveRepository();
        core = new CoreProcessor(session, repository, parser);
    }

    @Test
    public final void testCapability() {
        // Unauthenticated
        session.setAuthentication(false);
        parser.setExtensions(Arrays.asList("a", "b", "c"));
        Map<Capabilities, String> capabilities = core.capability();
        assertEquals(CoreProcessor.IMPLEMENTATION_DESCRIPTION, capabilities.get(Capabilities.IMPLEMENTATION));
        assertEquals(CoreProcessor.MANAGE_SIEVE_VERSION, capabilities.get(Capabilities.VERSION));
        assertEquals("a b c", capabilities.get(Capabilities.SIEVE));
        assertFalse(capabilities.containsKey(Capabilities.OWNER));
        assertTrue(capabilities.containsKey(Capabilities.GETACTIVE));

        // Authenticated
        session.setAuthentication(true);
        parser.setExtensions(Arrays.asList("a", "b", "c"));
        session.setUser("test");
        capabilities = core.capability();
        assertEquals(CoreProcessor.IMPLEMENTATION_DESCRIPTION, capabilities.get(Capabilities.IMPLEMENTATION));
        assertEquals(CoreProcessor.MANAGE_SIEVE_VERSION, capabilities.get(Capabilities.VERSION));
        assertEquals("a b c", capabilities.get(Capabilities.SIEVE));
        assertEquals("test", capabilities.get(Capabilities.OWNER));
        assertTrue(capabilities.containsKey(Capabilities.GETACTIVE));
    }

    @Test
    public final void testCheckScript() throws AuthenticationRequiredException, SyntaxException {
        // Unauthorised
        boolean success = false;
        session.setAuthentication(false);
        try {
            core.checkScript("warning");
        } catch (AuthenticationRequiredException ex) {
            success = true;
        }
        assertTrue("Expected AuthenticationRequiredException", success);

        // Authorised
        session.setAuthentication(true);
        session.setUser("test");
        List<String> warnings = core.checkScript("warning");
        assertEquals(2, warnings.size());
        assertEquals("warning1", warnings.get(0));
        assertEquals("warning2", warnings.get(1));

        // Syntax
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        try {
            core.checkScript("SyntaxException");
        } catch (SyntaxException ex) {
            success = true;
        }
        assertTrue("Expected SyntaxException", success);
    }

    @Test
    public final void testDeleteScript() throws Exception {
        // Unauthorised
        boolean success = false;
        session.setAuthentication(false);
        try {
            core.deleteScript("script");
        } catch (AuthenticationRequiredException ex) {
            success = true;
        }
        assertTrue("Expected AuthenticationRequiredException", success);

        // Authorised - non-existent script
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        try {
            core.deleteScript("script");
        } catch (ScriptNotFoundException ex) {
            success = true;
        }
        assertTrue("Expected ScriptNotFoundException", success);

        // Authorised - existent script
        session.setAuthentication(true);
        session.setUser("test");
        repository.putScript("test", "script", "content");
        core.deleteScript("script");
        success = false;
        try {
            repository.getScript("test", "script");
        } catch (ScriptNotFoundException ex) {
            success = true;
        }
        assertTrue("Expected ScriptNotFoundException", success);

        // Authorised - active script
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        repository.putScript("test", "script", "content");
        repository.setActive("test", "script");
        try {
            core.deleteScript("script");
        } catch (IsActiveException ex) {
            success = true;
        }
        assertTrue("Expected IsActiveException", success);
    }

    @Test
    public final void testGetScript() throws ScriptNotFoundException, AuthenticationRequiredException, UserNotFoundException, StorageException, QuotaExceededException {
        // Unauthorised
        boolean success = false;
        session.setAuthentication(false);
        try {
            core.getScript("script");
        } catch (AuthenticationRequiredException ex) {
            success = true;
        }
        assertTrue("Expected AuthenticationRequiredException", success);

        // Authorised - non-existent script
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        try {
            core.getScript("script");
            System.out.println("yop yop");
        } catch (ScriptNotFoundException ex) {
            success = true;
            ex.printStackTrace();
        } catch (Exception e) {
            System.out.println("Euh ... ");
            e.printStackTrace();
            System.out.println("Yolo");
        }
        assertTrue("Expected ScriptNotFoundException", success);

        // Authorised - existent script
        session.setAuthentication(true);
        session.setUser("test");
        repository.putScript("test", "script", "content");
        core.getScript("script");
    }

    @Test
    public final void testHaveSpace() throws Exception {
        // Unauthorised
        boolean success = false;
        session.setAuthentication(false);
        try {
            core.haveSpace("script", Long.MAX_VALUE);
        } catch (AuthenticationRequiredException ex) {
            success = true;
        }
        assertTrue("Expected AuthenticationRequiredException", success);

        // Authorised - existent script
        session.setAuthentication(true);
        session.setUser("test");
        core.haveSpace("script", Long.MAX_VALUE);
    }

    @Test
    public final void testListScripts() throws Exception {
        // Unauthorised
        boolean success = false;
        session.setAuthentication(false);
        try {
            core.listScripts();
        } catch (AuthenticationRequiredException ex) {
            success = true;
        }
        assertTrue("Expected AuthenticationRequiredException", success);

        // Authorised - non-existent script
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        List<ScriptSummary> summaries = core.listScripts();
        assertTrue(summaries.isEmpty());

        // Authorised - existent script
        session.setAuthentication(true);
        session.setUser("test");
        repository.putScript("test", "script", "content");
        summaries = core.listScripts();
        assertEquals(1, summaries.size());
    }

    @Test
    public final void testPutScript() throws Exception {
        // Unauthorised
        boolean success = false;
        session.setAuthentication(false);
        try {
            core.putScript("script", "content");
        } catch (AuthenticationRequiredException ex) {
            success = true;
        }
        assertTrue("Expected AuthenticationRequiredException", success);

        // Authorised
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        core.putScript("script", "content");
        assertEquals("content", repository.getScript("test", "script"));

        // Syntax
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        try {
            core.putScript("script", "SyntaxException");
        } catch (SyntaxException ex) {
            success = true;
        }
        assertTrue("Expected SyntaxException", success);
    }

    @Test
    public final void testRenameScript() throws Exception {
        // Unauthorised
        boolean success = false;
        session.setAuthentication(false);
        try {
            core.renameScript("oldName", "newName");
        } catch (AuthenticationRequiredException ex) {
            success = true;
        }
        assertTrue("Expected AuthenticationRequiredException", success);

        // Authorised
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        repository.putScript("test", "oldName", "content");
        core.renameScript("oldName", "newName");
        assertEquals("content", repository.getScript("test", "oldName"));
    }

    @Test
    public final void testSetActive() throws Exception {
        // Unauthorised
        boolean success = false;
        session.setAuthentication(false);
        try {
            core.setActive("script");
        } catch (AuthenticationRequiredException ex) {
            success = true;
        }
        assertTrue("Expected AuthenticationRequiredException", success);

        // Authorised
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        repository.putScript("test", "script", "content");
        core.setActive("script");
        assertEquals("content", repository.getActive("test"));
    }

    @Test
    public final void testGetActive() throws Exception {
        // Unauthorised
        boolean success = false;
        session.setAuthentication(false);
        try {
            core.getActive();
        } catch (AuthenticationRequiredException ex) {
            success = true;
        }
        assertTrue("Expected AuthenticationRequiredException", success);

        // Authorised - non-existent script
        success = false;
        session.setAuthentication(true);
        session.setUser("test");
        try {
            core.getActive();
        } catch (ScriptNotFoundException ex) {
            success = true;
        }
        assertTrue("Expected ScriptNotFoundException", success);

        // Authorised - existent script, inactive
        session.setAuthentication(true);
        session.setUser("test");
        repository.putScript("test", "script", "content");
        try {
            core.getActive();
        } catch (ScriptNotFoundException ex) {
            success = true;
        }
        assertTrue("Expected ScriptNotFoundException", success);

        // Authorised - existent script, active
        session.setAuthentication(true);
        session.setUser("test");
        repository.setActive("test", "script");
        core.getActive();
    }
}
