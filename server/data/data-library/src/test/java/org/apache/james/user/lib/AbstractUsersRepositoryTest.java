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
package org.apache.james.user.lib;

import java.util.ArrayList;
import java.util.Iterator;
import junit.framework.Assert;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Test basic behaviors of UsersFileRepository
 */
public abstract class AbstractUsersRepositoryTest {

    /**
     * Users repository
     */
    protected UsersRepository usersRepository;

    /**
     * Create the repository to be tested.
     *
     * @return the user repository
     * @throws Exception
     */
    protected abstract UsersRepository getUsersRepository() throws Exception;

    /**
     * @see junit.framework.TestCase#setUp()
     */
    @Before
    public void setUp() throws Exception {
        this.usersRepository = getUsersRepository();
    }

    /**
     * @see junit.framework.TestCase#tearDown()
     */
    @After
    public void tearDown() throws Exception {
        disposeUsersRepository();
    }

    @Test
    public void testUsersRepositoryEmpty() throws UsersRepositoryException {
        assertEquals("users repository not empty", 0, usersRepository.countUsers());
        assertFalse("users repository not empty", usersRepository.list().hasNext());
    }

    @Test
    public void testAddUserOnce() throws UsersRepositoryException {
        usersRepository.addUser("username", "password");
        try {
            usersRepository.addUser("username", "password2");
            fail("User added twice!");
        } catch (UsersRepositoryException e) {
            // UsersRepositoryException must be thrown by implementation.
        }
        try {
            usersRepository.addUser("username2", "password2");
            assertTrue(usersRepository.contains("username2"));
            usersRepository.addUser("username3", "password3");
            assertTrue(usersRepository.contains("username3"));
        } catch (UnsupportedOperationException e) {
        }
    }

    @Test
    public void testUserAddedIsFound() throws UsersRepositoryException {
        usersRepository.addUser("username", "password");
        User user = usersRepository.getUserByName("username");
        assertNotNull(user);
        assertEquals("username does not match", user.getUserName(), "username");
        assertTrue("user not contained in the repository", usersRepository.contains("username"));

        User u = usersRepository.getUserByName("uSERNAMe");
        assertNull("found the user searching for a different case!", u);

        // String realname = usersRepository.getRealName("uSERNAMe");
        // assertNull("name is not null", realname);
        // assertEquals("name is different", "username", realname);
    }

    @Test
    public void testUserListing() throws UsersRepositoryException {
        ArrayList<String> keys = new ArrayList<String>(3);
        keys.add("username1");
        keys.add("username2");
        keys.add("username3");
        for (String username : keys) {
            usersRepository.addUser(username, username);
        }
        assertEquals("Wrong number of users found", keys.size(), usersRepository.countUsers());

        // check list return all and only the expected users
        ArrayList<String> check = new ArrayList<String>(keys);
        for (Iterator<String> i = usersRepository.list(); i.hasNext();) {
            String username = i.next();
            if (getPasswordsEnabled()) {
                assertTrue(usersRepository.test(username, username));
                User u = usersRepository.getUserByName(username);
                u.setPassword("newpass");
                usersRepository.updateUser(u);
            }
            assertTrue(check.contains(username));
            check.remove(username);
        }
        assertEquals("Some user has not be found", 0, check.size());
    }

    @Test
    public void testUpperCaseSameUser() throws UsersRepositoryException {
        usersRepository.addUser("myUsername", "password");
        try {
            usersRepository.addUser("MyUsername", "password");
            Assert.fail("We should not be able to insert same users, even with different cases");
        } catch (UsersRepositoryException e) {
            Assert.assertTrue("The exception message must contain the username value but was=" + e.getMessage(), e.
                    getMessage().contains("MyUsername"));
        }
    }

    @Test
    public void testUserPassword() throws UsersRepositoryException {
        usersRepository.addUser("username", "password");
        assertEquals("didn't accept the correct password ", usersRepository.test("username", "password"),
                getPasswordsEnabled());
        assertFalse("accepted the wrong password #1", usersRepository.test("username", "password2"));
        assertFalse("accepted the wrong password #2", usersRepository.test("username2", "password"));
        assertFalse("accepted the wrong password #3", usersRepository.test("username", "Password"));
        assertFalse("accepted the wrong password #4", usersRepository.test("username", "passwords"));
        assertFalse("accepted the wrong password #5", usersRepository.test("userName", "password"));
    }

    @Test
    public void testUserAddRemoveCycle() throws UsersRepositoryException {
        assertFalse("accepted login when no user existed", usersRepository.test("username", "password"));
        try {
            usersRepository.removeUser("username");
            // UsersFileRepository accept this call for every argument
            // fail("removing an unknown user didn't fail!");
        } catch (UsersRepositoryException e) {
            // Do nothing, we should come here if test works.
        }
        usersRepository.addUser("username", "password");
        assertEquals("didn't accept the correct password", usersRepository.test("username", "password"),
                getPasswordsEnabled());
        User user = usersRepository.getUserByName("username");
        user.setPassword("newpass");
        try {
            usersRepository.updateUser(user);
            assertEquals("new password accepted", usersRepository.test("username", "newpass"), getPasswordsEnabled());
            assertFalse("old password rejected", usersRepository.test("username", "password"));
        } catch (UnsupportedOperationException e) {
            // if updating users is not allowed check that this is a repository
            // without password checking
            assertFalse(getPasswordsEnabled());
        }
        try {
            usersRepository.removeUser("username");
        } catch (Exception e) {
            e.printStackTrace();
            fail("removing the user failed!");
        }
        assertFalse("user not existing", usersRepository.contains("username"));
        assertFalse("new password rejected", usersRepository.test("username", "newpass"));
        try {
            usersRepository.updateUser(user);
            fail();
        } catch (UsersRepositoryException e) {
        }
    }

    /**
     * Dispose the repository
     *
     * @throws UsersRepositoryException
     */
    protected void disposeUsersRepository() throws UsersRepositoryException {
        if (usersRepository != null) {
            LifecycleUtil.dispose(this.usersRepository);
        }
    }

    private boolean getPasswordsEnabled() {
        return true;
    }
}
