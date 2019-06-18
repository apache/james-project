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

import javax.mail.internet.MimeMessage;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RecipientRewriteTableTest {

    private RecipientRewriteTable mailet;

    @Mock org.apache.james.rrt.api.RecipientRewriteTable virtualTableStore;
    @Mock DomainList domainList;

    private FakeMail mail;
    private MimeMessage message;
    private FakeMailetConfig mailetConfig;
    private MailetContext mailetContext;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        mailet = new RecipientRewriteTable(virtualTableStore, domainList);

        message = MimeMessageUtil.defaultMimeMessage();

        mailetContext = FakeMailContext.defaultContext();

        mailetConfig = FakeMailetConfig.builder()
            .mailetName("vut")
            .mailetContext(mailetContext)
            .build();

        mail = FakeMail.builder().name("name").build();
    }

    @Test
    public void getMailetInfoShouldReturnCorrectInformation() throws Exception {
        assertThat(mailet.getMailetInfo()).isEqualTo("RecipientRewriteTable Mailet");
    }

    @Test(expected = NullPointerException.class)
    public void serviceShouldThrowExceptionWithNullMail() throws Exception {
        mailet.service(null);
    }

    @Test
    public void serviceShouldDoNothingIfAbsentMessageInMail() throws Exception {
        mailet.service(mail);
    }
    
    @Test
    public void serviceShouldWork() throws Exception {
        mailet.init(mailetConfig);
        mail = FakeMail.builder()
            .name("name")
            .mimeMessage(message)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .build();

        mailet.service(mail);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES);
    }
}
