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


package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Test;

public class AllTest {

    private Matcher matcher;
    private MailAddress mailAddress1;
    private MailAddress mailAddress2;

    @Before
    public void setupMatcher() throws MessagingException {
        matcher = new All();
        FakeMatcherConfig mci = new FakeMatcherConfig("All",
                FakeMailContext.defaultContext());
        matcher.init(mci);

        mailAddress1 = new MailAddress("me@apache.org");
        mailAddress2 = new MailAddress("you@apache.org");
    }

    @Test
    public void testAllRecipientsReturned() throws MessagingException {
        FakeMail mockedMail = FakeMail.builder()
            .recipients(mailAddress1, mailAddress2)
            .build();

        assertThat(matcher.match(mockedMail)).containsExactly(mailAddress1, mailAddress2);
    }

}
