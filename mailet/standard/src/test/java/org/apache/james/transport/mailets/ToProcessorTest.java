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

import org.apache.james.transport.mailets.ToProcessor;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Assert;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;
import java.util.Arrays;

public class ToProcessorTest {

    private MimeMessage mockedMimeMessage;

    private Mail mockedMail;

    private Mailet mailet;

    private String processor = null;

    private String notice = null;

    private void setProcessor(String processor) {
        this.processor = processor;
    }

    private void setNotice(String notice) {
        this.notice = notice;
    }

    private void setupMockedMail(MimeMessage m) throws ParseException {
        mockedMail = new FakeMail();
        mockedMail.setMessage(m);
        mockedMail.setRecipients(Arrays.asList(new MailAddress("test@james.apache.org"),
                new MailAddress("test2@james.apache.org")));

    }

    private void setupMailet() throws MessagingException {
        mailet = new ToProcessor();
        FakeMailetConfig mci = new FakeMailetConfig("Test",
                new FakeMailContext());
        if (processor != null) {
            mci.setProperty("processor", processor);
        }
        if (notice != null) {
            mci.setProperty("notice", notice);
        }
        mailet.init(mci);
    }

    // test if ToProcessor works
    @Test
    public void testValidToProcessor() throws MessagingException {
        setProcessor("error");
        setNotice("error in message");
        setupMockedMail(mockedMimeMessage);
        setupMailet();

        mailet.service(mockedMail);

        Assert.assertEquals(processor, mockedMail.getState());
        Assert.assertEquals(notice, mockedMail.getErrorMessage());

    }

    // test if exception was thrown
    @Test
    public void testExceptionThrown() throws MessagingException {
        boolean exceptionThrown = false;
        setProcessor(null);
        setNotice("error in message");
        setupMockedMail(mockedMimeMessage);

        try {
            setupMailet();
            mailet.service(mockedMail);
        } catch (MessagingException m) {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
    }

}
