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


package org.apache.james.transport.matchers;

import org.apache.james.transport.matchers.HasHeader;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.Assert;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.Collection;

public class HasHeaderTest {

    private MimeMessage mockedMimeMessage;

    private FakeMail mockedMail;

    private Matcher matcher;

    private final String HEADER_NAME = "JUNIT";

    private String headerName = "defaultHeaderName";

    private String headerValue = "defaultHeaderValue";

    private void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    private void setHeaderValue(String headerValue) {
        this.headerValue = headerValue;
    }

    private void setupMockedMimeMessage() throws MessagingException {
        mockedMimeMessage = MailUtil.createMimeMessage(headerName, headerValue);
    }

    private void setupMatcher() throws MessagingException {
        setupMockedMimeMessage();
        matcher = new HasHeader();
        FakeMatcherConfig mci = new FakeMatcherConfig("HasHeader="
                + HEADER_NAME, new FakeMailContext());
        matcher.init(mci);
    }

    // test if the Header was matched
    @Test
    public void testHeaderIsMatched() throws MessagingException {
        setHeaderName(HEADER_NAME);
        String HEADER_VALUE = "test-value";
        setHeaderValue(HEADER_VALUE);

        setupMockedMimeMessage();
        mockedMail = MailUtil.createMockMail2Recipients(mockedMimeMessage);
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        Assert.assertNotNull(matchedRecipients);
        Assert.assertEquals(matchedRecipients.size(), mockedMail.getRecipients()
                .size());
    }

    // test if the Header was not matched
    @Test
    public void testHeaderIsNotMatched() throws MessagingException {
        setupMockedMimeMessage();
        mockedMail = MailUtil.createMockMail2Recipients(mockedMimeMessage);
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        Assert.assertNull(matchedRecipients);
    }
}
