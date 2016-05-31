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
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class BounceTest {

    private static final String MAILET_NAME = "mailetName";

    private Bounce bounce;
    private MailAddress recipientMailAddress;
    private MailAddress senderMailAddress;
    private FakeMailContext fakeMailContext;

    @Before
    public void setUp() throws Exception {
        bounce = new Bounce();
        DNSService dnsService = mock(DNSService.class);
        bounce.setDNSService(dnsService);
        fakeMailContext = new FakeMailContext();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
        bounce.init(new FakeMailetConfig(MAILET_NAME, fakeMailContext));

        senderMailAddress = new MailAddress("sender@domain.com");
        recipientMailAddress = new MailAddress("recipient@domain.com");
    }

    @Test
    public void bounceShouldReturnAMailToTheSenderWithoutAttributes() throws Exception {
        FakeMail mail = new FakeMail();
        mail.setSender(senderMailAddress);
        mail.setName(MAILET_NAME);
        mail.setRecipients(Lists.newArrayList(recipientMailAddress));
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mimeMessage.setText("My content");
        mail.setMessage(mimeMessage);

        bounce.service(mail);

        FakeMailContext.SentMail expected = new FakeMailContext.SentMail(null,
            Lists.newArrayList(senderMailAddress),
            null,
            ImmutableMap.<String, Serializable>of());
        assertThat(fakeMailContext.getSentMails()).containsOnly(expected);
    }

}
