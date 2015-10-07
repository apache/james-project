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


package org.apache.james.transport.mailets;

import org.apache.james.transport.mailets.SetMimeHeader;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

public class SetMimeHeaderTest {

    private Mailet mailet;

    private final String HEADER_NAME = "JUNIT";

    private final String HEADER_VALUE = "test-value";

    private String headerName = "defaultHeaderName";

    private String headerValue = "defaultHeaderValue";

    private void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    private void setHeaderValue(String headerValue) {
        this.headerValue = headerValue;
    }

    @Before
    public void setUp() throws Exception {
        mailet = new SetMimeHeader();
        FakeMailetConfig mci = new FakeMailetConfig("Test",
                new FakeMailContext());
        mci.setProperty("name", HEADER_NAME);
        mci.setProperty("value", HEADER_VALUE);

        mailet.init(mci);
    }

    // test if the Header was add
    @Test
    public void testHeaderIsPresent() throws MessagingException {
        MimeMessage mockedMimeMessage = MailUtil.createMimeMessage(headerName, headerValue);
        FakeMail mockedMail = MailUtil.createMockMail2Recipients(mockedMimeMessage);

        mailet.service(mockedMail);

        Assert.assertEquals(HEADER_VALUE, mockedMail.getMessage().getHeader(HEADER_NAME)[0]);

    }

    // test if the Header was replaced
    @Test
    public void testHeaderIsReplaced() throws MessagingException {
        setHeaderName(HEADER_NAME);
        setHeaderValue(headerValue);

        MimeMessage mockedMimeMessage = MailUtil.createMimeMessage(headerName, headerValue);
        FakeMail mockedMail = MailUtil.createMockMail2Recipients(mockedMimeMessage);

        mailet.service(mockedMail);

        Assert.assertEquals(HEADER_VALUE, mockedMail.getMessage().getHeader(HEADER_NAME)[0]);
    }
}
