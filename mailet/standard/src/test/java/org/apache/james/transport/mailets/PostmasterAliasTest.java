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

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

public class PostmasterAliasTest {
    private PostmasterAlias testee;
    private MailAddress postmaster;
    private MailAddress postmasterAlias;

    @Before
    public void setUp() throws Exception {
        postmaster = new MailAddress("admin@localhost");
        postmasterAlias = new MailAddress("postmaster@localhost");
        testee = new PostmasterAlias();
        testee.init(FakeMailetConfig.builder()
            .mailetContext(FakeMailContext.builder()
                .postmaster(postmaster))
            .build());
    }

    @Test
    public void serviceShouldAcceptMailsWithNoRecipients() throws Exception {
        Mail mail = FakeMail.builder().build();

        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
    }

    @Test
    public void serviceShouldNotAlterMailsForPostmaster() throws Exception {
        Mail mail = FakeMail.builder()
            .recipient(postmaster)
            .build();

        testee.service(mail);

        assertThat(mail.getRecipients()).containsOnly(postmaster);
    }

    @Test
    public void serviceShouldNotAlterMailForOtherUsers() throws Exception {
        Mail mail = FakeMail.builder()
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .build();

        testee.service(mail);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void serviceShouldNotAlterPostmasterAliasWhenForOtherDomains() throws Exception {
        MailAddress otherDomainPostmaster = new MailAddress("postmaster@otherDomain");
        Mail mail = FakeMail.builder()
            .recipient(otherDomainPostmaster)
            .build();

        testee.service(mail);

        assertThat(mail.getRecipients()).containsOnly(otherDomainPostmaster);
    }

    @Test
    public void serviceShouldRewritePostmasterAlias() throws Exception {
        Mail mail = FakeMail.builder()
            .recipient(postmasterAlias)
            .build();

        testee.service(mail);

        assertThat(mail.getRecipients()).containsOnly(postmaster);
    }

    @Test
    public void serviceShouldNotAlterOtherRecipientsWhenRewritingPostmaster() throws Exception {
        Mail mail = FakeMail.builder()
            .recipients(postmasterAlias, MailAddressFixture.ANY_AT_JAMES)
            .build();

        testee.service(mail);

        assertThat(mail.getRecipients()).containsOnly(postmaster, MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void serviceShouldNotDuplicatePostmaster() throws Exception {
        Mail mail = FakeMail.builder()
            .recipients(postmasterAlias, postmaster)
            .build();

        testee.service(mail);

        assertThat(mail.getRecipients()).containsOnly(postmaster);
    }

}