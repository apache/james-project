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

import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.base.test.MailUtil;

import junit.framework.TestCase;

public abstract class AbstractHasMailAttributeTest extends TestCase {

    protected FakeMail mockedMail;

    protected Matcher matcher;

    protected static final String MAIL_ATTRIBUTE_NAME = "org.apache.james.test.junit";

    protected static final String MAIL_ATTRIBUTE_VALUE = "true";

    protected String mailAttributeName = "org.apache.james";

    protected String mailAttributeValue = "false";

    public AbstractHasMailAttributeTest() {
        super(null);
    }

    protected void setMailAttributeName(String mailAttributeName) {
        this.mailAttributeName = mailAttributeName;
    }

    protected void setMailAttributeValue(String mailAttributeValue) {
        this.mailAttributeValue = mailAttributeValue;
    }

    protected void setupMockedMail() throws MessagingException {
        mockedMail = MailUtil.createMockMail2Recipients();
        mockedMail.setAttribute(mailAttributeName,
                mailAttributeValue);
    }

    protected void setupMatcher() throws MessagingException {
        matcher = createMatcher();
        FakeMatcherConfig mci = FakeMatcherConfig.builder()
                .matcherName(getMatcherName())
                .condition(getHasMailAttribute())
                .build();

        matcher.init(mci);

    }

    // test if the mail attribute was matched
    public void testAttributeIsMatched() throws MessagingException {
        init();

        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(matchedRecipients.size(), mockedMail.getRecipients()
                .size());
    }

    protected void init() {
        setMailAttributeName(MAIL_ATTRIBUTE_NAME);
        setMailAttributeValue(MAIL_ATTRIBUTE_VALUE);
    }

    protected void setupAll() throws MessagingException {
        setupMockedMail();
        setupMatcher();
    }

    // test if the mail attribute was not matched
    public void testAttributeIsNotMatched() throws MessagingException {
        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNull(matchedRecipients);
    }

    protected abstract String getHasMailAttribute();

    protected abstract GenericMatcher createMatcher();

    protected abstract String getMatcherName();
}
