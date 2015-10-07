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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

import junit.framework.TestCase;

import org.apache.james.transport.mailets.Null;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Mailet;

public class NullTest extends TestCase {

    private MimeMessage mockedMimeMessage;

    private Mail mockedMail;

    private Mailet mailet;

    public NullTest(String arg0) throws UnsupportedEncodingException {
        super(arg0);
    }

    private void setupMockedMail(MimeMessage m) throws ParseException {
        mockedMail = new FakeMail();
        mockedMail.setMessage(m);
        mockedMail.setRecipients(Arrays.asList(new MailAddress("test@james.apache.org"),
                new MailAddress("test2@james.apache.org")));

    }

    private void setupMailet() throws MessagingException {
        mailet = new Null();
    }

    // test if the right state was set
    public void testNullMailet() throws MessagingException {
        setupMockedMail(mockedMimeMessage);
        setupMailet();

        mailet.service(mockedMail);

        String PROCESSOR = "ghost";
        assertEquals(PROCESSOR, mockedMail.getState());
    }

}
