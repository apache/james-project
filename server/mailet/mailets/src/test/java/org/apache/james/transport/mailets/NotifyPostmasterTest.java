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

import java.net.UnknownHostException;

import javax.mail.MessagingException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.AbstractRedirect.SpecialAddress;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class NotifyPostmasterTest {

    private static final String MAILET_NAME = "mailetName";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private NotifyPostmaster notifyPostmaster;
    private MailAddress postmaster;
    private FakeMailContext fakeMailContext;

    @Before
    public void setUp() throws Exception {
        notifyPostmaster = new NotifyPostmaster();
        DNSService dnsService = mock(DNSService.class);
        notifyPostmaster.setDNSService(dnsService);
        postmaster = new MailAddress("postmaster@james.org");
        fakeMailContext = FakeMailContext.builder()
                .postmaster(postmaster)
                .build();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(notifyPostmaster.getMailetInfo()).isEqualTo("NotifyPostmaster Mailet");
    }

    @Test
    public void getAllowedInitParametersShouldReturnTheParameters() {
        assertThat(notifyPostmaster.getAllowedInitParameters()).containsOnly("debug", "passThrough", "fakeDomainCheck", "inline", "attachment", "message", "notice", "sender", "sendingAddress", "prefix", "attachError", "attachStackTrace", "to");
    }

    @Test
    public void initShouldFailWhenUnknownParameterIsConfigured() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("unknwon", "value");
        expectedException.expect(MessagingException.class);

        notifyPostmaster.init(mailetConfig);
    }

    @Test
    public void getRecipientsShouldReturnPostmaster() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getRecipients()).containsOnly(postmaster);
    }

    @Test
    public void getToShouldReturnPostmasterWhenToIsNotconfigured() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getTo()).containsOnly(postmaster.toInternetAddress());
    }

    @Test
    public void getToShouldReturnPostmasterWhenToIsEqualToPostmaster() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("to", "postmaster");
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getTo()).containsOnly(postmaster.toInternetAddress());
    }

    @Test
    public void getToShouldReturnUnalteredWhenToIsEqualToUnaltered() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("to", "unaltered");
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getTo()).containsOnly(SpecialAddress.UNALTERED.toInternetAddress());
    }

    @Test
    public void getToShouldReturnPostmasterWhenToIsNotEqualToPostmasterOrUnaltered() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("to", "otherTo");
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getTo()).containsOnly(postmaster.toInternetAddress());
    }

    @Test
    public void attachErrorShouldReturnFalseWhenDefaultValue() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.attachError()).isFalse();
    }

    @Test
    public void attachErrorShouldReturnTrueWhenAttachErrorIsTrue() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("attachError", "true");
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.attachError()).isTrue();
    }

    @Test
    public void attachErrorShouldReturnTrueWhenAttachStackTraceIsTrue() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("attachStackTrace", "true");
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.attachError()).isTrue();
    }
}
