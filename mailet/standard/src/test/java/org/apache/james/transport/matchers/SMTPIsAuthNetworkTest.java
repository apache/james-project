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

import org.apache.james.transport.matchers.SMTPIsAuthNetwork;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.Assert;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.ParseException;
import java.util.Collection;

public class SMTPIsAuthNetworkTest {

    private FakeMail mockedMail;

    private Matcher matcher;

    private boolean isAuthorized = false;

    private void setIsAuthorized(boolean isAuthorized) {
        this.isAuthorized = isAuthorized;
    }

    private void setupMockedMail() throws ParseException {
        mockedMail = MailUtil.createMockMail2Recipients(null);
        if (isAuthorized) {
            String MAIL_ATTRIBUTE_NAME = "org.apache.james.SMTPIsAuthNetwork";
            mockedMail.setAttribute(MAIL_ATTRIBUTE_NAME, "true");
        }
    }

    private void setupMatcher() throws MessagingException {
        matcher = new SMTPIsAuthNetwork();
        FakeMatcherConfig mci = new FakeMatcherConfig("SMTPIsAuthNetwork",
                new FakeMailContext());
        matcher.init(mci);
    }

    @Test
    public void testIsAuthNetwork() throws MessagingException {
        setIsAuthorized(true);
        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        Assert.assertNotNull(matchedRecipients);
        Assert.assertEquals(matchedRecipients.size(), mockedMail.getRecipients()
                .size());
    }

    @Test
    public void testIsNotAuthNetwork() throws MessagingException {
        setIsAuthorized(false);
        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        Assert.assertNull(matchedRecipients);
    }
}
