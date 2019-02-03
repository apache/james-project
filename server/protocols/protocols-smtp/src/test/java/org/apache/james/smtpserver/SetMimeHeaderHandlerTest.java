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
package org.apache.james.smtpserver;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.apache.mailet.Mail;
import org.junit.Before;
import org.junit.Test;

public class SetMimeHeaderHandlerTest {

    private static final String HEADER_NAME = "JUNIT";
    private static final String HEADER_VALUE = "test-value";

    private SMTPSession mockedSMTPSession;
    private MimeMessage mockedMimeMessage;
    private Mail mockedMail;
    private String headerName = "defaultHeaderName";
    private String headerValue = "defaultHeaderValue";

    @Before
    public void setUp() throws Exception {
        setupMockedSMTPSession();
    }

    private void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    private void setHeaderValue(String headerValue) {
        this.headerValue = headerValue;
    }

    private void setupMockedMimeMessage() throws MessagingException {
        mockedMimeMessage = Util.createMimeMessage(headerName, headerValue);
    }

    private void setupMockedSMTPSession() {
        mockedSMTPSession = new BaseFakeSMTPSession() {

            @Override
            public int getRcptCount() {
                return 0;
            }
        };
    }

    // test if the Header was add
    @Test
    public void testHeaderIsPresent() throws MessagingException {
        setHeaderName(HEADER_NAME);
        setHeaderValue(HEADER_VALUE);

        setupMockedMimeMessage();
        mockedMail = Util.createMockMail2Recipients(mockedMimeMessage);

        SetMimeHeaderHandler header = new SetMimeHeaderHandler();

        header.setHeaderName(HEADER_NAME);
        header.setHeaderValue(HEADER_VALUE);
        header.onMessage(mockedSMTPSession, mockedMail);

        assertThat(mockedMail.getMessage().getHeader(HEADER_NAME)[0]).isEqualTo(HEADER_VALUE);
    }

    // test if the Header was replaced
    @Test
    public void testHeaderIsReplaced() throws MessagingException {
        setHeaderName(HEADER_NAME);
        setHeaderValue(headerValue);

        setupMockedMimeMessage();
        mockedMail = Util.createMockMail2Recipients(mockedMimeMessage);

        SetMimeHeaderHandler header = new SetMimeHeaderHandler();

        header.setHeaderName(HEADER_NAME);
        header.setHeaderValue(HEADER_VALUE);
        header.onMessage(mockedSMTPSession, mockedMail);

        assertThat(mockedMail.getMessage().getHeader(HEADER_NAME)[0]).isEqualTo(HEADER_VALUE);
    }
}
