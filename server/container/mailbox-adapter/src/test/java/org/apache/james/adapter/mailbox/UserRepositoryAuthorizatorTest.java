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

package org.apache.james.adapter.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UserRepositoryAuthorizatorTest {
    private static final Username ADMIN = Username.of("admin");
    private static final Username USER = Username.of("user");

    private UsersRepository usersRepository;
    private UserRepositoryAuthorizator testee;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        usersRepository = mock(UsersRepository.class);
        testee = new UserRepositoryAuthorizator(usersRepository);
    }

    @Test
    public void canLoginAsOtherUserShouldThrowMailboxExceptionWhenIsAdministratorThrows() throws Exception {
        when(usersRepository.isAdministrator(ADMIN))
            .thenThrow(new UsersRepositoryException("expected error"));

        expectedException.expect(MailboxException.class);

        testee.canLoginAsOtherUser(ADMIN, USER);
    }

    @Test
    public void canLoginAsOtherUserShouldReturnNotAdminWhenNotAdminAndNoUser() throws Exception {
        when(usersRepository.isAdministrator(ADMIN))
            .thenReturn(false);
        when(usersRepository.contains(USER))
            .thenReturn(false);
        
        assertThat(testee.canLoginAsOtherUser(ADMIN, USER)).isEqualTo(Authorizator.AuthorizationState.NOT_ADMIN);
    }

    @Test
    public void canLoginAsOtherUserShouldReturnNotAdminWhenNotAdminAndUser() throws Exception {
        when(usersRepository.isAdministrator(ADMIN))
            .thenReturn(false);
        when(usersRepository.contains(USER))
            .thenReturn(true);

        assertThat(testee.canLoginAsOtherUser(ADMIN, USER)).isEqualTo(Authorizator.AuthorizationState.NOT_ADMIN);
    }

    @Test
    public void canLoginAsOtherUserShouldReturnUnknownUserWhenUserIsNotInRepository() throws Exception {
        when(usersRepository.isAdministrator(ADMIN))
            .thenReturn(true);
        when(usersRepository.contains(USER))
            .thenReturn(false);
        
        assertThat(testee.canLoginAsOtherUser(ADMIN, USER)).isEqualTo(Authorizator.AuthorizationState.UNKNOWN_USER);
    }

    @Test
    public void canLoginAsOtherUserShouldReturnAllowedWhenAdminAndUserIsInRepository() throws Exception {
        when(usersRepository.isAdministrator(ADMIN))
            .thenReturn(true);
        when(usersRepository.contains(USER))
            .thenReturn(true);
        
        assertThat(testee.canLoginAsOtherUser(ADMIN, USER)).isEqualTo(Authorizator.AuthorizationState.ALLOWED);
    }

}
