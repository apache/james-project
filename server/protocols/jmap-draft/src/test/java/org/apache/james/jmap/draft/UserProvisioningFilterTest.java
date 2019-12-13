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
package org.apache.james.jmap.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

public class UserProvisioningFilterTest {
    private static final Username USERNAME = Username.of("username");
    private static final Username USERNAME_WITH_DOMAIN = Username.of("username@james.org");
    private static final DomainList NO_DOMAIN_LIST = null;

    private UserProvisioningFilter sut;
    private MemoryUsersRepository usersRepository;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @Before
    public void setup() throws Exception {
        usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        sut = new UserProvisioningFilter(usersRepository, new RecordingMetricFactory());
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    public void filterShouldDoNothingOnNullSession() throws IOException, ServletException, UsersRepositoryException {
        sut.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(usersRepository.list()).toIterable()
            .isEmpty();
    }

    @Test
    public void filterShouldAddUsernameWhenNoVirtualHostingAndMailboxSessionContainsUsername() throws Exception {
        usersRepository.setEnableVirtualHosting(false);
        MailboxSession mailboxSession = MailboxSessionUtil.create(USERNAME);
        when(request.getAttribute(AuthenticationFilter.MAILBOX_SESSION))
            .thenReturn(mailboxSession);

        sut.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(usersRepository.list()).toIterable()
            .contains(USERNAME);
    }

    @Test
    public void filterShouldFailOnInvalidVirtualHosting() {
        usersRepository.setEnableVirtualHosting(false);
        MailboxSession mailboxSession = MailboxSessionUtil.create(USERNAME_WITH_DOMAIN);
        when(request.getAttribute(AuthenticationFilter.MAILBOX_SESSION))
            .thenReturn(mailboxSession);

        assertThatThrownBy(() -> sut.doFilter(request, response, chain))
            .hasCauseInstanceOf(UsersRepositoryException.class);
    }

    @Test
    public void filterShouldNotTryToAddUserWhenReadOnlyUsersRepository() throws Exception {
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.isReadOnly()).thenReturn(true);
        sut = new UserProvisioningFilter(usersRepository, new RecordingMetricFactory());

        MailboxSession mailboxSession = MailboxSessionUtil.create(USERNAME_WITH_DOMAIN);
        when(request.getAttribute(AuthenticationFilter.MAILBOX_SESSION))
            .thenReturn(mailboxSession);

        sut.doFilter(request, response, chain);

        verify(usersRepository).isReadOnly();
        verifyNoMoreInteractions(usersRepository);
    }

    @Test
    public void filterShouldChainCallsWhenReadOnlyUsersRepository() throws Exception {
        UsersRepository usersRepository = mock(UsersRepository.class);
        when(usersRepository.isReadOnly()).thenReturn(true);
        sut = new UserProvisioningFilter(usersRepository, new RecordingMetricFactory());

        MailboxSession mailboxSession = MailboxSessionUtil.create(USERNAME_WITH_DOMAIN);
        when(request.getAttribute(AuthenticationFilter.MAILBOX_SESSION))
            .thenReturn(mailboxSession);

        sut.doFilter(request, response, chain);

        verify(chain).doFilter(eq(request), any());
    }
}
