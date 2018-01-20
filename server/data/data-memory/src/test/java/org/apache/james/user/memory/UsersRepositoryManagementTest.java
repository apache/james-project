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

public class UsersRepositoryManagementTest {

    private UsersRepository usersRepository;
    private UsersRepositoryManagement userManagement;

    @Before
    public void setUp() throws Exception {
        usersRepository = MemoryUsersRepository.withoutVirtualHosting();

        userManagement = new UsersRepositoryManagement();
        userManagement.setUsersRepository(usersRepository);
    }

    @Test
    public void testUserCount() throws Exception {
        assertEquals("no user yet", 0, userManagement.countUsers());
        usersRepository.addUser("testcount1", "testCount");
        assertEquals("1 user", 1, userManagement.countUsers());
        usersRepository.addUser("testcount2", "testCount");
        assertEquals("2 users", 2, userManagement.countUsers());
        usersRepository.removeUser("testcount1");
        assertEquals("1 user", 1, userManagement.countUsers());
    }

    @Test
    public void testAddUserAndVerify() throws Exception {
        usersRepository.addUser("testcount1", "testCount");
        assertFalse("user not there", userManagement.verifyExists("testNotAdded"));
        assertTrue("user is there", userManagement.verifyExists("testCount1"));
        usersRepository.removeUser("testcount1");
        assertFalse("user not there", userManagement.verifyExists("testCount1"));
    }

    @Test
    public void testDelUser() throws Exception {
        usersRepository.addUser("testdel", "test");
        assertFalse("user not there", userManagement.verifyExists("testNotDeletable"));
        assertTrue("user is there", userManagement.verifyExists("testdel"));
        usersRepository.removeUser("testdel");
        assertFalse("user no longer there", userManagement.verifyExists("testdel"));
    }

    @Test
    public void testListUsers() throws Exception {
        String[] usersArray = new String[]{"ccc", "aaa", "dddd", "bbbbb"};
        List<String> users = Arrays.asList(usersArray);

        for (String user : users) {
            usersRepository.addUser(user, "test");
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
        userManagement.addUser("testpwduser", "pwd1");

        assertTrue("initial password", usersRepository.test("testpwduser", "pwd1"));

        // set empty pwd
        userManagement.setPassword("testpwduser", "");
        assertTrue("password changed to empty", usersRepository.test("testpwduser", ""));

        // change pwd
        userManagement.setPassword("testpwduser", "pwd2");
        assertTrue("password not changed to pwd2", usersRepository.test("testpwduser", "pwd2"));

        // assure case sensitivity
        userManagement.setPassword("testpwduser", "pWD2");
        assertFalse("password no longer pwd2", usersRepository.test("testpwduser", "pwd2"));
        assertTrue("password changed to pWD2", usersRepository.test("testpwduser", "pWD2"));
    }
}
