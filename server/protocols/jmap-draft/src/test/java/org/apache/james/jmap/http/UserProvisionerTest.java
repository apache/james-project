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
package org.apache.james.jmap.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.Before;
import org.junit.Test;

public class UserProvisionerTest {
    private static final Username USERNAME = Username.of("username");
    private static final Username USERNAME_WITH_DOMAIN = Username.of("username@james.org");
    private static final DomainList NO_DOMAIN_LIST = null;

    private UserProvisioner testee;
    private MemoryUsersRepository usersRepository;

    @Before
    public void setup() throws Exception {
        usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        testee = new UserProvisioner(usersRepository, new RecordingMetricFactory());
    }

    @Test
    public void filterShouldDoNothingOnNullSession() throws UsersRepositoryException {
        testee.provisionUser(null).block();

        assertThat(usersRepository.list()).toIterable()
            .isEmpty();
    }

    @Test
    public void filterShouldAddUsernameWhenNoVirtualHostingAndMailboxSessionContainsUsername() throws UsersRepositoryException {
        usersRepository.setEnableVirtualHosting(false);
        MailboxSession mailboxSession = MailboxSessionUtil.create(USERNAME);

        testee.provisionUser(mailboxSession).block();

        assertThat(usersRepository.list()).toIterable()
            .contains(USERNAME);
    }

    @Test
    public void filterShouldFailOnInvalidVirtualHosting() {
        usersRepository.setEnableVirtualHosting(false);
        MailboxSession mailboxSession = MailboxSessionUtil.create(USERNAME_WITH_DOMAIN);

        assertThatThrownBy(() -> testee.provisionUser(mailboxSession).block())
            .hasCauseInstanceOf(UsersRepositoryException.class);
    }

    @Test
    public void filterShouldNotTryToAddUserWhenReadOnlyUsersRepository() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.isReadOnly()).thenReturn(true);
        testee = new UserProvisioner(usersRepository, new RecordingMetricFactory());

        MailboxSession mailboxSession = MailboxSessionUtil.create(USERNAME_WITH_DOMAIN);

        testee.provisionUser(mailboxSession).block();

        verify(usersRepository).isReadOnly();
        verifyNoMoreInteractions(usersRepository);
    }
}
