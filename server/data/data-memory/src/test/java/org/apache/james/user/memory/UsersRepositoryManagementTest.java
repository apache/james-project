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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
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
        assertThat(userManagement.verifyExists("testNotAdded")).describedAs("user not there").isFalse();
        assertThat(userManagement.verifyExists("testCount1")).describedAs("user is there").isTrue();
        usersRepository.removeUser("testcount1");
        assertThat(userManagement.verifyExists("testCount1")).describedAs("user not there").isFalse();
    }

    @Test
    public void testDelUser() throws Exception {
        usersRepository.addUser("testdel", "test");
        assertThat(userManagement.verifyExists("testNotDeletable")).describedAs("user not there").isFalse();
        assertThat(userManagement.verifyExists("testdel")).describedAs("user is there").isTrue();
        usersRepository.removeUser("testdel");
        assertThat(userManagement.verifyExists("testdel")).describedAs("user no longer there").isFalse();
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

        assertThat(usersRepository.test("testpwduser", "pwd1")).describedAs("initial password").isTrue();

        // set empty pwd
        userManagement.setPassword("testpwduser", "");
        assertThat(usersRepository.test("testpwduser", "")).describedAs("password changed to empty").isTrue();

        // change pwd
        userManagement.setPassword("testpwduser", "pwd2");
        assertThat(usersRepository.test("testpwduser", "pwd2")).describedAs("password not changed to pwd2").isTrue();

        // assure case sensitivity
        userManagement.setPassword("testpwduser", "pWD2");
        assertThat(usersRepository.test("testpwduser", "pwd2")).describedAs("password no longer pwd2").isFalse();
        assertThat(usersRepository.test("testpwduser", "pWD2")).describedAs("password changed to pWD2").isTrue();
    }
}
