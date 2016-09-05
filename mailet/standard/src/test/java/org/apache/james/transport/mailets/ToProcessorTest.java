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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;

public class ToProcessorTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Mailet mailet;
    private FakeMailetConfig mailetConfig;
    private Logger logger;

    @Before
    public void setup() {
        mailet = new ToProcessor();
        logger = mock(Logger.class);
        mailetConfig = new FakeMailetConfig("Test", FakeMailContext.builder().logger(logger).build());
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(mailet.getMailetInfo()).isEqualTo("ToProcessor Mailet");
    }

    @Test
    public void initShouldThrowWhenProcessorIsNotGiven() throws MessagingException {
        mailetConfig.setProperty("notice", "error in message");
        expectedException.expect(MailetException.class);

        mailet.init(mailetConfig);
    }

    @Test
    public void serviceShouldSetTheStateOfTheMail() throws MessagingException {
        String processor = "error";
        mailetConfig.setProperty("processor", processor);
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .recipients(new MailAddress("test@james.apache.org"), new MailAddress("test2@james.apache.org"))
                .build();
        mailet.service(mail);

        assertThat(mail.getState()).isEqualTo(processor);
    }

    @Test
    public void serviceShouldSetTheErrorMessageOfTheMailWhenNotAlreadySet() throws MessagingException {
        String processor = "error";
        mailetConfig.setProperty("processor", processor);
        String notice = "error in message";
        mailetConfig.setProperty("notice", notice);
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .recipients(new MailAddress("test@james.apache.org"), new MailAddress("test2@james.apache.org"))
                .build();
        mailet.service(mail);

        assertThat(mail.getErrorMessage()).isEqualTo(notice);
    }

    @Test
    public void serviceShouldAppendTheErrorMessageOfTheMailWhenSomeErrorMessageOnMail() throws MessagingException {
        String processor = "error";
        mailetConfig.setProperty("processor", processor);
        String notice = "error in message";
        mailetConfig.setProperty("notice", notice);
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .recipients(new MailAddress("test@james.apache.org"), new MailAddress("test2@james.apache.org"))
                .build();
        String initialErrorMessage = "first";
        mail.setErrorMessage(initialErrorMessage);
        mailet.service(mail);

        assertThat(mail.getErrorMessage()).isEqualTo(initialErrorMessage + "\r\n" + notice);
    }

    @Test
    public void serviceShouldLogWhenDebug() throws MessagingException {
        mailetConfig.setProperty("processor", "error");
        mailetConfig.setProperty("notice", "error in message");
        mailetConfig.setProperty("debug", "true");
        mailet.init(mailetConfig);

        Mail mail = FakeMail.builder()
                .recipients(new MailAddress("test@james.apache.org"), new MailAddress("test2@james.apache.org"))
                .build();
        String initialErrorMessage = "first";
        mail.setErrorMessage(initialErrorMessage);
        mailet.service(mail);

        verify(logger).info("Sending mail " + mail +" to error");
    }
}
