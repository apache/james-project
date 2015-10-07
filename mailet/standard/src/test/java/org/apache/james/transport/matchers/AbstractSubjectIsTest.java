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

import junit.framework.TestCase;
import org.apache.mailet.base.test.FakeMimeMessage;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.base.test.MailUtil;
import org.apache.mailet.Matcher;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;

public abstract class AbstractSubjectIsTest extends TestCase {

    protected FakeMail mockedMail;

    protected Matcher matcher;

    private String subject = null;

    private FakeMimeMessage mockedMimeMessage;

    public AbstractSubjectIsTest(String arg0)
            throws UnsupportedEncodingException {
        super(arg0);
    }

    protected void setSubject(String subject) {
        this.subject = subject;
    }

    protected void setupMockedMail(MimeMessage m) {
        mockedMail = new FakeMail();
        mockedMail.setMessage(m);

    }

    protected void setupMockedMimeMessage() throws MessagingException {
        mockedMimeMessage = MailUtil.createMimeMessage("test", "test");
        mockedMimeMessage.setSubject(subject);
    }

    protected void setupMatcher() throws MessagingException {
        matcher = createMatcher();
        FakeMatcherConfig mci = new FakeMatcherConfig(getConfigOption()
                + getSubjectName(), new FakeMailContext());
        matcher.init(mci);
    }

    protected void setupAll() throws MessagingException {
        setupMockedMimeMessage();
        setupMockedMail(mockedMimeMessage);
        setupMatcher();
    }

    protected abstract String getConfigOption();

    protected abstract String getSubjectName();

    protected abstract Matcher createMatcher();
}
