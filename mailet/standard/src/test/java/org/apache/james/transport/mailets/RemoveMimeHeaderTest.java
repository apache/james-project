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

import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.Assert;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

public class RemoveMimeHeaderTest {

    private final static String HEADER1 = "header1";

    private final static String HEADER2 = "header2";

    private GenericMailet setupMockedMailet(String name1, String name2) throws MessagingException {
        GenericMailet mailet = new RemoveMimeHeader();
        FakeMailetConfig mci = new FakeMailetConfig("Test", new FakeMailContext());
        if (name1 != null) mci.setProperty("name", name1);
        if (name2 != null) mci.setProperty("name", name2);

        mailet.init(mci);
        return mailet;
    }

    private MimeMessage getMockedMimeMessage() throws MessagingException {
        MimeMessage mockedMimeMessage = MailUtil.createMimeMessage();
        mockedMimeMessage.setHeader(HEADER1, "true");
        mockedMimeMessage.setHeader(HEADER2, "true");
        mockedMimeMessage.saveChanges();
        return mockedMimeMessage;
    }

    private Mail getMockedMail(MimeMessage message) {
        Mail m = new FakeMail();
        m.setMessage(message);
        return m;
    }

    @Test
    public void testOneHeaderRemoved() throws MessagingException {
        GenericMailet mailet = setupMockedMailet(HEADER1, null);
        Mail mail = getMockedMail(getMockedMimeMessage());

        // Get sure both headers are present
        Assert.assertNotNull("Header present", mail.getMessage().getHeader(HEADER1));
        Assert.assertNotNull("Header present", mail.getMessage().getHeader(HEADER2));

        mailet.service(mail);

        // The first header should be removed
        Assert.assertNull("Header removed", mail.getMessage().getHeader(HEADER1));
        Assert.assertNotNull("Header present", mail.getMessage().getHeader(HEADER2));
    }

    @Test
    public void testTwoHeaderRemoved() throws MessagingException {
        GenericMailet mailet = setupMockedMailet(HEADER1, HEADER2);
        Mail mail = getMockedMail(getMockedMimeMessage());

        // Get sure both headers are present
        Assert.assertNotNull("Header present", mail.getMessage().getHeader(HEADER1));
        Assert.assertNotNull("Header present", mail.getMessage().getHeader(HEADER2));

        mailet.service(mail);

        // Both header should be removed
        Assert.assertNull("Header removed", mail.getMessage().getHeader(HEADER1));
        Assert.assertNull("Header removed", mail.getMessage().getHeader(HEADER2));
    }

    @Test
    public void testNoHeaderRemoved() throws MessagingException {
        GenericMailet mailet = setupMockedMailet("h1", "h2");
        Mail mail = getMockedMail(getMockedMimeMessage());

        // Get sure both headers are present
        Assert.assertNotNull("Header present", mail.getMessage().getHeader(HEADER1));
        Assert.assertNotNull("Header present", mail.getMessage().getHeader(HEADER2));

        mailet.service(mail);

        // Both header should be removed
        Assert.assertNotNull("Header present", mail.getMessage().getHeader(HEADER1));
        Assert.assertNotNull("header present", mail.getMessage().getHeader(HEADER2));
    }

    @Test
    public void testInvalidConfig() throws MessagingException {
        boolean exception = false;
        try {
            setupMockedMailet(null, null);
        } catch (MessagingException e) {
            exception = true;
        }
        Assert.assertTrue("Exception thrown", exception);
    }


}
