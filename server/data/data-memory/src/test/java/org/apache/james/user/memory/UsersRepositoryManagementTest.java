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
import static org.assertj.core.api.Fail.fail;

import java.util.Arrays;
import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersRepositoryManagement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsersRepositoryManagementTest {
    static final DomainList NO_DOMAIN_LIST = null;

    UsersRepository usersRepository;
    UsersRepositoryManagement userManagement;

    @BeforeEach
    void setUp() throws Exception {
        usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);

        userManagement = new UsersRepositoryManagement();
        userManagement.setUsersRepository(usersRepository);
    }

    @Test
    void testUserCount() throws Exception {
        assertThat(userManagement.countUsers()).describedAs("no user yet").isEqualTo(0);
        usersRepository.addUser(Username.of("testcount1"), "testCount");
        assertThat(userManagement.countUsers()).describedAs("1 user").isEqualTo(1);
        usersRepository.addUser(Username.of("testcount2"), "testCount");
        assertThat(userManagement.countUsers()).describedAs("2 users").isEqualTo(2);
        usersRepository.removeUser(Username.of("testcount1"));
        assertThat(userManagement.countUsers()).describedAs("1 user").isEqualTo(1);
    }

    @Test
    void testAddUserAndVerify() throws Exception {
        usersRepository.addUser(Username.of("testcount1"), "testCount");
        assertThat(userManagement.verifyExists("testNotAdded")).describedAs("user not there").isFalse();
        assertThat(userManagement.verifyExists("testCount1")).describedAs("user is there").isTrue();
        usersRepository.removeUser(Username.of("testcount1"));
        assertThat(userManagement.verifyExists("testCount1")).describedAs("user not there").isFalse();
    }

    @Test
    void testDelUser() throws Exception {
        usersRepository.addUser(Username.of("testdel"), "test");
        assertThat(userManagement.verifyExists("testNotDeletable")).describedAs("user not there").isFalse();
        assertThat(userManagement.verifyExists("testdel")).describedAs("user is there").isTrue();
        usersRepository.removeUser(Username.of("testdel"));
        assertThat(userManagement.verifyExists("testdel")).describedAs("user no longer there").isFalse();
    }

    @Test
    void testListUsers() throws Exception {
        Username[] usersArray = new Username[]{Username.of("ccc"), Username.of("aaa"), Username.of("dddd"), Username.of("bbbbb")};
        List<Username> users = Arrays.asList(usersArray);

        for (Username user : users) {
            usersRepository.addUser(user, "test");
        }

        String[] userNames = userManagement.listAllUsers();
        assertThat(userNames.length).describedAs("user count").isEqualTo(users.size());

        for (String user : userNames) {
            if (!users.contains(Username.of(user))) {
                fail("user not listed");
            }
        }
    }

    @Test
    void testSetPassword() throws Exception {
        userManagement.addUser("testpwduser", "pwd1");

        assertThat(usersRepository.test(Username.of("testpwduser"), "pwd1")).describedAs("initial password").isPresent();

        // set empty pwd
        userManagement.setPassword("testpwduser", "");
        assertThat(usersRepository.test(Username.of("testpwduser"), "")).describedAs("password changed to empty").isPresent();

        // change pwd
        userManagement.setPassword("testpwduser", "pwd2");
        assertThat(usersRepository.test(Username.of("testpwduser"), "pwd2")).describedAs("password not changed to pwd2").isPresent();

        // assure case sensitivity
        userManagement.setPassword("testpwduser", "pWD2");
        assertThat(usersRepository.test(Username.of("testpwduser"), "pwd2")).describedAs("password no longer pwd2").isEmpty();
        assertThat(usersRepository.test(Username.of("testpwduser"), "pWD2")).describedAs("password changed to pWD2").isPresent();
    }
}
