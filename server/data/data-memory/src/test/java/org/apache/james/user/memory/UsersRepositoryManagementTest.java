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
package org.apache.james.user.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersRepositoryManagement;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the UserManagement
 */
public class UsersRepositoryManagementTest {

    private UsersRepository mockUsersRepository;
    private UsersRepositoryManagement userManagement;

    @Before
    public void setUp() throws Exception {
        mockUsersRepository = MemoryUsersRepository.withoutVirtualHosting();

        userManagement = new UsersRepositoryManagement();
        userManagement.setUsersRepository(mockUsersRepository);
    }

    @Test
    public void testUserCount() throws Exception {
        assertEquals("no user yet", 0, userManagement.countUsers());
        mockUsersRepository.addUser("testCount1", "testCount");
        assertEquals("1 user", 1, userManagement.countUsers());
        mockUsersRepository.addUser("testCount2", "testCount");
        assertEquals("2 users", 2, userManagement.countUsers());
        mockUsersRepository.removeUser("testCount1");
        assertEquals("1 user", 1, userManagement.countUsers());
    }

    @Test
    public void testAddUserAndVerify() throws Exception {
        mockUsersRepository.addUser("testCount1", "testCount");
        assertFalse("user not there", userManagement.verifyExists("testNotAdded"));
        assertTrue("user is there", userManagement.verifyExists("testCount1"));
        mockUsersRepository.removeUser("testCount1");
        assertFalse("user not there", userManagement.verifyExists("testCount1"));
    }

    @Test
    public void testDelUser() throws Exception {
        mockUsersRepository.addUser("testDel", "test");
        assertFalse("user not there", userManagement.verifyExists("testNotDeletable"));
        assertTrue("user is there", userManagement.verifyExists("testDel"));
        mockUsersRepository.removeUser("testDel");
        assertFalse("user no longer there", userManagement.verifyExists("testDel"));
    }

    @Test
    public void testListUsers() throws Exception {

        String[] usersArray = new String[]{"ccc", "aaa", "dddd", "bbbbb"};
        List<String> users = Arrays.asList(usersArray);

        for (String user : users) {
            mockUsersRepository.addUser(user, "test");
        }

        String[] userNames = userManagement.listAllUsers();
        assertEquals("user count", users.size(), userNames.length);

        for (String user : userNames) {
            if (!users.contains(user)) {
                fail("user not listed");
            }
        }
    }

    @Test
    public void testSetPassword() throws Exception {

        userManagement.addUser("testPwdUser", "pwd1");

        assertTrue("initial password", mockUsersRepository.test("testPwdUser", "pwd1"));

        // set empty pwd
        userManagement.setPassword("testPwdUser", "");
        assertTrue("password changed to empty", mockUsersRepository.test("testPwdUser", ""));

        // change pwd
        userManagement.setPassword("testPwdUser", "pwd2");
        assertTrue("password not changed to pwd2", mockUsersRepository.test("testPwdUser", "pwd2"));

        // assure case sensitivity
        userManagement.setPassword("testPwdUser", "pWD2");
        assertFalse("password no longer pwd2", mockUsersRepository.test("testPwdUser", "pwd2"));
        assertTrue("password changed to pWD2", mockUsersRepository.test("testPwdUser", "pWD2"));

    }
}
