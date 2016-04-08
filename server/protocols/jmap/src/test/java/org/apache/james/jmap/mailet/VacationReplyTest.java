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

package org.apache.james.jmap.mailet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Test;

public class VacationReplyTest {

    public static final String REASON = "I am in vacation dudes !";

    @Test
    public void vacationReplyShouldGenerateASuitableAnswer() throws Exception {
        MailAddress originalSender = new MailAddress("distant@apache.org");
        MailAddress originalRecipient = new MailAddress("benwa@apache.org");
        FakeMail mail = new FakeMail();
        mail.setMessage(new MimeMessage(Session.getInstance(new Properties()) ,ClassLoader.getSystemResourceAsStream("spamMail.eml")));
        mail.setSender(originalSender);

        VacationReply vacationReply = VacationReply.builder(mail)
            .reason(REASON)
            .receivedMailRecipient(originalRecipient)
            .build();

        assertThat(vacationReply.getRecipients()).containsExactly(originalSender);
        assertThat(vacationReply.getSender()).isEqualTo(originalRecipient);
        assertThat(vacationReply.getMimeMessage().getHeader("subject")).containsExactly("Re: Original subject");
        assertThat(IOUtils.toString(vacationReply.getMimeMessage().getInputStream())).contains(REASON);
    }

    @Test(expected = NullPointerException.class)
    public void vacationReplyShouldThrowOnNullMail() {
        VacationReply.builder(null);
    }

    @Test(expected = NullPointerException.class)
    public void vacationReplyShouldThrowOnNullOriginalEMailAddress() throws Exception {
        VacationReply.builder(new FakeMail())
            .receivedMailRecipient(null)
            .build();
    }

}
