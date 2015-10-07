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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import javax.mail.MessagingException;

import junit.framework.TestCase;

import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;

public abstract class AbstractRecipientIsTest extends TestCase {

    protected FakeMail mockedMail;

    protected Matcher matcher;

    private MailAddress[] recipients;

    public AbstractRecipientIsTest(String arg0)
            throws UnsupportedEncodingException {
        super(arg0);
    }

    protected void setRecipients(MailAddress[] recipients) {
        this.recipients = recipients;
    }

    protected void setupMockedMail() {
        mockedMail = new FakeMail();
        mockedMail.setRecipients(Arrays.asList(recipients));

    }

    protected void setupMatcher() throws MessagingException {
        matcher = createMatcher();
        FakeMatcherConfig mci = new FakeMatcherConfig("RecipientIs="
                + getRecipientName(), new FakeMailContext());
        matcher.init(mci);
    }

    protected void setupAll() throws MessagingException {
        setupMockedMail();
        setupMatcher();
    }

    protected abstract String getRecipientName();

    protected abstract Matcher createMatcher();
}
