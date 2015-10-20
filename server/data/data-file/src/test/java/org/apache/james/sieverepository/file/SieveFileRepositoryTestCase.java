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
package org.apache.james.sieverepository.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.DuplicateUserException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;
import org.junit.Before;
import org.junit.Test;

/**
 * <code>SieveFileRepositoryTestCase</code>
 */
public class SieveFileRepositoryTestCase {

    private static final String SIEVE_ROOT = FileSystem.FILE_PROTOCOL + "sieve";
    private final FileSystem fs = new FileSystem() {

        @Override
        public File getBasedir() throws FileNotFoundException {
            return new File(System.getProperty("java.io.tmpdir"));
        }

        @Override
        public InputStream getResource(String url) throws IOException {
            return new FileInputStream(getFile(url));
        }

        @Override
        public File getFile(String fileURL) throws FileNotFoundException {
            return new File(getBasedir(), fileURL.substring(FileSystem.FILE_PROTOCOL.length()));
        }
    };

    /**
     * setUp.
     *
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        File root = fs.getFile(SIEVE_ROOT);
        // Remove files from the previous test, if any
        if (root.exists()) {
            FileUtils.forceDelete(root);
        }
        FileUtils.forceMkdir(root);
    }

    /**
     * Test method for .
     */
    @Test
    public final void testSieveFileRepository() {
        SieveRepository repo = new SieveFileRepository(fs);
        assertTrue(repo instanceof SieveRepository);
        assertTrue(repo instanceof SieveFileRepository);
    }

    /**
     * Test method for .
     * @throws StorageException 
     * @throws DuplicateUserException 
     * @throws QuotaExceededException 
     * @throws UserNotFoundException 
     * @throws IsActiveException 
     * @throws ScriptNotFoundException 
     * @throws FileNotFoundException 
     */
    @Test
    public final void testDeleteScript() throws DuplicateUserException, StorageException,
            UserNotFoundException, QuotaExceededException, ScriptNotFoundException,
            IsActiveException, FileNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);
        String scriptName = "script";
        String content = "01234567";

        // Delete existent inactive script
        repo.putScript(user, scriptName, content);
        repo.deleteScript(user, scriptName);
        assertTrue("Script deletion failed", !new File(fs.getFile(SIEVE_ROOT), user + '/'
                + scriptName).exists());

        // Delete existent active script
        repo.putScript(user, scriptName, content);
        repo.setActive(user, scriptName);
        boolean isActiveExceptionThrown = false;
        try {
            repo.deleteScript(user, scriptName);
        } catch (IsActiveException ex) {
            isActiveExceptionThrown = true;
        }
        assertTrue(isActiveExceptionThrown);

        // Delete non existent script
        boolean scriptNotFoundExceptionThrown = false;
        try {
            repo.deleteScript(user, "nonExistent");
        } catch (ScriptNotFoundException ex) {
            scriptNotFoundExceptionThrown = true;
        }
        assertTrue(scriptNotFoundExceptionThrown);
    }

    /**
     * Test method for .
     * @throws StorageException 
     * @throws DuplicateUserException 
     * @throws UserNotFoundException 
     * @throws QuotaExceededException 
     * @throws ScriptNotFoundException 
     */
    @Test
    public final void testGetScript() throws DuplicateUserException, StorageException, UserNotFoundException,
            QuotaExceededException, ScriptNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);
        String scriptName = "script";
        String content = "01234567";

        // Non existent script
        boolean scriptNotFoundExceptionThrown = false;
        try {
            repo.getScript(user, scriptName);
        } catch (ScriptNotFoundException ex) {
            scriptNotFoundExceptionThrown = true;
        }
        assertTrue(scriptNotFoundExceptionThrown);

        // Existent script
        repo.putScript(user, scriptName, content);
        assertEquals("Script content did not match", content, repo.getScript(user, scriptName));
    }

    /**
     * Test method for .
     * @throws DuplicateUserException 
     * @throws QuotaExceededException 
     * @throws UserNotFoundException 
     * @throws StorageException 
     * @throws ScriptNotFoundException 
     */
    @Test
    public final void testHaveSpace() throws DuplicateUserException, UserNotFoundException, QuotaExceededException,
            StorageException, ScriptNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);
        String scriptName = "script";
        long defaultQuota = Long.MAX_VALUE - 1;
        long userQuota = Long.MAX_VALUE / 2;
        boolean quotaExceededExceptionThrown;

        // No quota
        repo.haveSpace(user, scriptName, defaultQuota + 1);

        // Default quota
        repo.setQuota(defaultQuota);
        // Default quota - not exceeded
        repo.haveSpace(user, scriptName, defaultQuota);
        // Default quota - exceeded
        quotaExceededExceptionThrown = false;
        try {
            repo.haveSpace(user, scriptName, defaultQuota + 1);
        } catch (QuotaExceededException ex) {
            quotaExceededExceptionThrown = true;
        }
        assertTrue(quotaExceededExceptionThrown);

        // User quota file
        repo.setQuota(user, userQuota);
        // User quota - not exceeded
        repo.haveSpace(user, scriptName, userQuota);
        // User quota - exceeded
        quotaExceededExceptionThrown = false;
        try {
            repo.haveSpace(user, scriptName, userQuota + 1);
        } catch (QuotaExceededException ex) {
            quotaExceededExceptionThrown = true;
        }
        assertTrue(quotaExceededExceptionThrown);

        // Script replacement
        String content = "01234567";
        repo.putScript(user, scriptName, content);
        // Script replacement, quota not exceeded
        repo.haveSpace(user, scriptName, userQuota);
        // Script replacement, quota exceeded
        quotaExceededExceptionThrown = false;
        try {
            repo.haveSpace(user, scriptName, userQuota + 1);
        } catch (QuotaExceededException ex) {
            quotaExceededExceptionThrown = true;
        }
        assertTrue(quotaExceededExceptionThrown);

        // Active script
        repo.setActive(user, scriptName);
        // User quota - not exceeded
        repo.haveSpace(user, scriptName, userQuota);
    }

    /**
     * Test method for .
     * @throws StorageException 
     * @throws DuplicateUserException 
     * @throws UserNotFoundException 
     * @throws QuotaExceededException 
     * @throws ScriptNotFoundException 
     */
    @Test
    public final void testListScripts() throws DuplicateUserException, StorageException, UserNotFoundException,
            QuotaExceededException, ScriptNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);
        String scriptName = "script";
        String content = "01234567";
        String scriptName1 = "script1";
        String content1 = "abcdefgh";

        // No scripts
        assertTrue(repo.listScripts(user).isEmpty());

        // Inactive script
        repo.putScript(user, scriptName, content);
        List<ScriptSummary> summaries = repo.listScripts(user);
        assertEquals(1, summaries.size());
        assertEquals(scriptName, summaries.get(0).getName());
        assertTrue(!summaries.get(0).isActive());

        // Active script
        repo.setActive(user, scriptName);
        summaries = repo.listScripts(user);
        assertEquals(1, summaries.size());
        assertEquals(scriptName, summaries.get(0).getName());
        assertTrue(summaries.get(0).isActive());

        // One of each
        repo.putScript(user, scriptName1, content1);
        summaries = repo.listScripts(user);
        assertEquals(2, summaries.size());
        assertTrue(summaries.contains(new ScriptSummary(scriptName, true)));
        assertTrue(summaries.contains(new ScriptSummary(scriptName1, false)));
    }

    /**
     * Test method for .
     * @throws DuplicateUserException 
     * @throws QuotaExceededException 
     * @throws StorageException 
     * @throws UserNotFoundException 
     * @throws FileNotFoundException 
     */
    @Test
    public final void testPutScript() throws DuplicateUserException, UserNotFoundException,
            StorageException, QuotaExceededException, FileNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);
        String scriptName = "script";
        String content = "01234567";

        // test new script
        repo.putScript(user, scriptName, content);
        assertTrue("Script creation failed", new File(fs.getFile(SIEVE_ROOT), user + '/'
                + scriptName).exists());

        // test script replacement
        repo.putScript(user, scriptName, content);
        assertTrue("Script replacement failed", new File(fs.getFile(SIEVE_ROOT), user + '/'
                + scriptName).exists());

        // test quota
        repo.setQuota(content.length());
        repo.putScript(user, scriptName, content);
        repo.setQuota(content.length() - 1);
        boolean quotaExceededExceptionThrown = false;
        try {
            repo.putScript(user, scriptName, content);
        } catch (QuotaExceededException ex) {
            quotaExceededExceptionThrown = true;
        }
        assertTrue(quotaExceededExceptionThrown);
    }

    /**
     * Test method for .
     * @throws StorageException 
     * @throws DuplicateUserException 
     * @throws DuplicateException 
     * @throws IsActiveException 
     * @throws UserNotFoundException 
     * @throws ScriptNotFoundException 
     * @throws QuotaExceededException 
     */
    @Test
    public final void testRenameScript() throws DuplicateUserException, StorageException, UserNotFoundException,
            IsActiveException, DuplicateException, ScriptNotFoundException, QuotaExceededException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);
        String scriptName = "script";
        String content = "01234567";
        String scriptName1 = "script1";

        // Non existent script
        boolean scriptNotFoundExceptionThrown = false;
        try {
            repo.renameScript(user, scriptName, scriptName1);
        } catch (ScriptNotFoundException ex) {
            scriptNotFoundExceptionThrown = true;
        }
        assertTrue(scriptNotFoundExceptionThrown);

        // Existent script
        repo.putScript(user, scriptName, content);
        repo.renameScript(user, scriptName, scriptName1);
        assertEquals("Script content did not match", content, repo.getScript(user, scriptName1));

        // Propagate active script
        repo.setActive(user, scriptName1);
        repo.renameScript(user, scriptName1, scriptName);
        assertEquals("Script content did not match", content, repo.getActive(user));

        // Duplicate script
        repo.setActive(user, "");
        boolean duplicateExceptionThrown = false;
        try {
            repo.renameScript(user, scriptName, scriptName);
        } catch (DuplicateException ex) {
            duplicateExceptionThrown = true;
        }
        assertTrue(duplicateExceptionThrown);
    }

    /**
     * Test method for .
     * @throws StorageException 
     * @throws DuplicateUserException 
     * @throws QuotaExceededException 
     * @throws UserNotFoundException 
     * @throws ScriptNotFoundException 
     */
    @Test
    public final void testGetActive() throws DuplicateUserException, StorageException, UserNotFoundException,
            QuotaExceededException, ScriptNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);
        String scriptName = "script";
        String content = "01234567";

        // Non existent script
        boolean scriptNotFoundExceptionThrown = false;
        try {
            repo.getActive(user);
        } catch (ScriptNotFoundException ex) {
            scriptNotFoundExceptionThrown = true;
        }
        assertTrue(scriptNotFoundExceptionThrown);

        // Inactive script
        repo.putScript(user, scriptName, content);
        scriptNotFoundExceptionThrown = false;
        try {
            repo.getActive(user);
        } catch (ScriptNotFoundException ex) {
            scriptNotFoundExceptionThrown = true;
        }
        assertTrue(scriptNotFoundExceptionThrown);

        // Active script
        repo.setActive(user, scriptName);
        assertEquals("Script content did not match", content, repo.getActive(user));
    }

    /**
     * Test method for .
     * @throws StorageException 
     * @throws DuplicateUserException 
     * @throws UserNotFoundException 
     * @throws ScriptNotFoundException 
     * @throws QuotaExceededException 
     */
    @Test
    public final void testSetActive() throws DuplicateUserException, StorageException,
            UserNotFoundException, ScriptNotFoundException, QuotaExceededException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);
        String scriptName = "script";
        String content = "01234567";
        String scriptName1 = "script1";
        String content1 = "abcdefgh";

        // Non existent script
        boolean scriptNotFoundExceptionThrown = false;
        try {
            repo.setActive(user, scriptName);
        } catch (ScriptNotFoundException ex) {
            scriptNotFoundExceptionThrown = true;
        }
        assertTrue(scriptNotFoundExceptionThrown);

        // Existent script
        repo.putScript(user, scriptName, content);
        repo.setActive(user, scriptName);
        assertEquals("Script content did not match", content, repo.getActive(user));

        // Switch active script
        repo.putScript(user, scriptName1, content1);
        scriptNotFoundExceptionThrown = false;
        repo.setActive(user, scriptName1);
        assertEquals("Script content did not match", content1, repo.getActive(user));

        // Disable active script
        repo.setActive(user, "");
        scriptNotFoundExceptionThrown = false;
        try {
            repo.getActive(user);
        } catch (ScriptNotFoundException ex) {
            scriptNotFoundExceptionThrown = true;
        }
        assertTrue(scriptNotFoundExceptionThrown);
    }

    @Test
    public final void testAddUser() throws DuplicateUserException, StorageException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";

        repo.addUser(user);
        assertTrue(repo.hasUser(user));
    }

    @Test
    public final void testRemoveUser() throws StorageException, DuplicateUserException, UserNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";

        // Non existent user
        boolean userNotFoundExceptionThrown = false;
        try {
            repo.removeUser(user);
        } catch (UserNotFoundException ex) {
            userNotFoundExceptionThrown = true;
        }
        assertTrue(userNotFoundExceptionThrown);

        // Existent user
        repo.addUser(user);
        repo.removeUser(user);
        assertTrue(!repo.hasUser(user));
    }

    @Test
    public final void testHasUser() throws DuplicateUserException, StorageException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";

        // Non existent user
        assertTrue(!repo.hasUser(user));

        // Existent user
        repo.addUser(user);
        assertTrue(repo.hasUser(user));
    }

    @Test
    public final void testGetQuota() throws StorageException, QuotaNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);

        // Non existent quota
        boolean quotaNotFoundExceptionThrown = false;
        try {
            repo.getQuota();
        } catch (QuotaNotFoundException ex) {
            quotaNotFoundExceptionThrown = true;
        }
        assertTrue(quotaNotFoundExceptionThrown);

        // Existent Quota
        repo.setQuota(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, repo.getQuota());
    }

    @Test
    public final void testHasQuota() throws StorageException {
        SieveRepository repo = new SieveFileRepository(fs);

        // Non existent quota
        assertTrue(!repo.hasQuota());

        // Existent quota
        repo.setQuota(Long.MAX_VALUE);
        assertTrue(repo.hasQuota());
    }

    @Test
    public final void testRemoveQuota() throws StorageException, QuotaNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);

        // Non existent quota
        boolean quotaNotFoundExceptionThrown = false;
        try {
            repo.removeQuota();
        } catch (QuotaNotFoundException ex) {
            quotaNotFoundExceptionThrown = true;
        }
        assertTrue(quotaNotFoundExceptionThrown);

        // Existent quota
        repo.setQuota(Long.MAX_VALUE);
        repo.removeQuota();
        assertTrue(!repo.hasQuota());
    }

    @Test
    public final void testSetQuota() throws QuotaNotFoundException, StorageException {
        SieveRepository repo = new SieveFileRepository(fs);

        repo.setQuota(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, repo.getQuota());
    }

    @Test
    public final void testGetUserQuota() throws StorageException, QuotaNotFoundException, DuplicateUserException,
            UserNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);

        // Non existent quota
        boolean quotaNotFoundExceptionThrown = false;
        try {
            repo.getQuota(user);
        } catch (QuotaNotFoundException ex) {
            quotaNotFoundExceptionThrown = true;
        }
        assertTrue(quotaNotFoundExceptionThrown);

        // Existent Quota
        repo.setQuota(user, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, repo.getQuota(user));
    }

    @Test
    public final void testHasUserQuota() throws StorageException, DuplicateUserException, UserNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);

        // Non existent quota
        assertTrue(!repo.hasQuota(user));

        // Existent quota
        repo.setQuota(user, Long.MAX_VALUE);
        assertTrue(repo.hasQuota(user));
    }

    @Test
    public final void testRemoveUserQuota() throws StorageException, QuotaNotFoundException, DuplicateUserException,
            UserNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);

        // Non existent quota
        boolean quotaNotFoundExceptionThrown = false;
        try {
            repo.removeQuota(user);
        } catch (QuotaNotFoundException ex) {
            quotaNotFoundExceptionThrown = true;
        }
        assertTrue(quotaNotFoundExceptionThrown);

        // Existent quota
        repo.setQuota(user, Long.MAX_VALUE);
        repo.removeQuota(user);
        assertTrue(!repo.hasQuota(user));
    }

    @Test
    public final void testSetUserQuota() throws QuotaNotFoundException, StorageException, DuplicateUserException,
            UserNotFoundException {
        SieveRepository repo = new SieveFileRepository(fs);
        String user = "test";
        repo.addUser(user);

        repo.setQuota(user, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, repo.getQuota(user));
    }
}
