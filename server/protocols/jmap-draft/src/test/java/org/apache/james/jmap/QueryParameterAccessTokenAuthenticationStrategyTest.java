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
package org.apache.james.jmap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.apache.james.jmap.api.SimpleTokenManager;
import org.apache.james.jmap.exceptions.MailboxSessionCreationException;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.jmap.model.AttachmentAccessToken;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.junit.Before;
import org.junit.Test;

public class QueryParameterAccessTokenAuthenticationStrategyTest {

    private static final String USERNAME = "usera@domain.tld";
    private static final String VALID_ATTACHMENT_TOKEN = "usera@domain.tld_"
            + "2016-06-29T13:41:22.124Z_"
            + "DiZa0O14MjLWrAA8P6MG35Gt5CBp7mt5U1EH/M++rIoZK7nlGJ4dPW0dvZD7h4m3o5b/Yd8DXU5x2x4+s0HOOKzD7X0RMlsU7JHJMNLvTvRGWF/C+MUyC8Zce7DtnRVPEQX2uAZhL2PBABV07Vpa8kH+NxoS9CL955Bc1Obr4G+KN2JorADlocFQA6ElXryF5YS/HPZSvq1MTC6aJIP0ku8WRpRnbwgwJnn26YpcHXcJjbkCBtd9/BhlMV6xNd2hTBkfZmYdoNo+UKBaXWzLxAlbLuxjpxwvDNJfOEyWFPgHDoRvzP+G7KzhVWjanHAHrhF0GilEa/MKpOI1qHBSwA==";

    private SimpleTokenManager mockedSimpleTokenManager;
    private MailboxManager mockedMailboxManager;
    private QueryParameterAccessTokenAuthenticationStrategy testee;
    private HttpServletRequest request;

    @Before
    public void setup() {
        mockedSimpleTokenManager = mock(SimpleTokenManager.class);
        mockedMailboxManager = mock(MailboxManager.class);
        request = mock(HttpServletRequest.class);

        testee = new QueryParameterAccessTokenAuthenticationStrategy(mockedSimpleTokenManager, mockedMailboxManager);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenNoAccessTokenProvided() {
        when(request.getParameter("access_token"))
            .thenReturn(null);

        assertThatThrownBy(() -> testee.createMailboxSession(request))
            .isExactlyInstanceOf(UnauthorizedException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenAccessTokenIsNotValid() {
        when(request.getParameter("access_token"))
            .thenReturn("bad");

        assertThatThrownBy(() -> testee.createMailboxSession(request))
                .isExactlyInstanceOf(UnauthorizedException.class);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenMailboxExceptionHasOccurred() throws Exception {
        when(mockedMailboxManager.createSystemSession(USERNAME))
                .thenThrow(new MailboxException());

        when(request.getParameter("access_token"))
            .thenReturn(VALID_ATTACHMENT_TOKEN);
        when(request.getPathInfo())
            .thenReturn("/blobId");

        when(mockedSimpleTokenManager.isValid(AttachmentAccessToken.from(VALID_ATTACHMENT_TOKEN, "blobId")))
            .thenReturn(true);

        assertThatThrownBy(() -> testee.createMailboxSession(request))
                .isExactlyInstanceOf(MailboxSessionCreationException.class);
    }
}