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

import junit.framework.TestCase;

import org.apache.james.transport.mailets.MailAttributesToMimeHeaders;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.apache.mailet.Mailet;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

import java.io.UnsupportedEncodingException;

public class MailAttributesToMimeHeadersTest extends TestCase {

    private Mailet mailet;

    private final String HEADER_NAME1 = "JUNIT";

    private final String HEADER_NAME2 = "JUNIT2";

    private final String MAIL_ATTRIBUTE_VALUE1 = "test1";

    private final String MAIL_ATTRIBUTE_VALUE2 = "test2";

    private final String MAIL_ATTRIBUTE_NAME1 = "org.apache.james.test";

    private final String MAIL_ATTRIBUTE_NAME2 = "org.apache.james.test2";

    private String config1 = MAIL_ATTRIBUTE_NAME1 + "; " + HEADER_NAME1;

    public MailAttributesToMimeHeadersTest(String arg0)
            throws UnsupportedEncodingException {
        super(arg0);
    }

    private void setConfig1(String config1) {
        this.config1 = config1;
    }

    private String getConfig1() {
        return config1;
    }

    private void setupMailet() throws MessagingException {
        mailet = new MailAttributesToMimeHeaders();
        FakeMailetConfig mci = new FakeMailetConfig("Test",
                new FakeMailContext());
        mci.setProperty("simplemapping", getConfig1());
        String config2 = MAIL_ATTRIBUTE_NAME2 + "; " + HEADER_NAME2;
        mci.setProperty("simplemapping", config2);
        mailet.init(mci);
    }

    private FakeMail setupMail(MimeMessage m) throws ParseException {
        FakeMail mockedMail = MailUtil.createMockMail2Recipients(m);
        mockedMail.setAttribute(MAIL_ATTRIBUTE_NAME1, MAIL_ATTRIBUTE_VALUE1);
        mockedMail.setAttribute(MAIL_ATTRIBUTE_NAME2, MAIL_ATTRIBUTE_VALUE2);
        return mockedMail;
    }

    // test if the Headers were added
    public void testHeadersArePresent() throws MessagingException {
        FakeMail mockedMail = setupMail(MailUtil.createMimeMessage());
        setupMailet();

        mailet.service(mockedMail);

        assertEquals(MAIL_ATTRIBUTE_VALUE1, mockedMail.getMessage().getHeader(
                HEADER_NAME1)[0]);

        assertEquals(MAIL_ATTRIBUTE_VALUE2, mockedMail.getMessage().getHeader(
                HEADER_NAME2)[0]);

    }

    // test if exception was thrown
    public void testInvalidConfig() throws MessagingException {
        boolean exception = false;
        FakeMail mockedMail = setupMail(MailUtil.createMimeMessage());
        setConfig1("test");

        try {
            setupMailet();
            mailet.service(mockedMail);
        } catch (MessagingException e) {
            exception = true;
        }

        assertTrue(exception);

    }
}
