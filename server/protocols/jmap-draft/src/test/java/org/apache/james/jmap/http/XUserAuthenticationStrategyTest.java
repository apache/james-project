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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.Before;
import org.junit.Test;

import io.netty.handler.codec.http.HttpHeaders;
import reactor.netty.http.server.HttpServerRequest;

public class XUserAuthenticationStrategyTest {
    private XUserAuthenticationStrategy testee;
    private HttpServerRequest mockedRequest;
    private HttpHeaders mockedHeaders;

    @Before
    public void setup() throws Exception {
        MailboxManager mockedMailboxManager = mock(MailboxManager.class);
        mockedRequest = mock(HttpServerRequest.class);
        mockedHeaders = mock(HttpHeaders.class);

        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

        MailboxSession fakeMailboxSession = mock(MailboxSession.class);
        when(mockedMailboxManager.createSystemSession(any()))
            .thenReturn(fakeMailboxSession);

        when(mockedRequest.requestHeaders())
            .thenReturn(mockedHeaders);

        testee = new XUserAuthenticationStrategy(usersRepository, mockedMailboxManager);
    }

    @Test
    public void createMailboxSessionShouldReturnEmptyWhenHeaderIsEmpty() {
        when(mockedHeaders.get("X-User"))
            .thenReturn("");

        assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
            .isEmpty();
    }

    @Test
    public void createMailboxSessionShouldReturnEmptyWhenHeaderIsNull() {
        when(mockedHeaders.get("X-User"))
            .thenReturn(null);

        assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
            .isEmpty();
    }


    @Test
    public void createMailboxSessionShouldFailWhenInvalidUser() {
        when(mockedHeaders.get("X-User"))
            .thenReturn("btellier"); // invalid because virtual hosting is turned on

        assertThatThrownBy(() -> testee.createMailboxSession(mockedRequest).blockOptional())
            .isInstanceOf(UnauthorizedException.class);
    }


    @Test
    public void createMailboxSessionShouldReturnSessionWhenValid() {
        when(mockedHeaders.get("X-User"))
            .thenReturn("btellier@localhost");

        assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
            .get()
            .isInstanceOf(MailboxSession.class);
    }
}
