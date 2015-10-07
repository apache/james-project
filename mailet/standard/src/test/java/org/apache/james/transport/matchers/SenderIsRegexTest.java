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
import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.james.transport.matchers.SenderIsRegex;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;

public class SenderIsRegexTest extends AbstractSenderIsTest {

    private String regex = ".*";

    public SenderIsRegexTest(String arg0) throws UnsupportedEncodingException {
        super(arg0);
    }

    private void setRegex(String regex) {
        this.regex = regex;
    }

    // test if matched
    public void testSenderIsRegexMatchedAllRecipients()
            throws MessagingException {
        String SENDER_NAME = "test@james.apache.org";
        setSender(new MailAddress(SENDER_NAME));
        setRegex(".*@.*");
        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(matchedRecipients.size(), mockedMail.getRecipients()
                .size());
    }

    // test if not matched
    public void testSenderIsRegexNotMatchedAllRecipients()
            throws MessagingException {
        setSender(new MailAddress("t@james.apache.org"));
        setRegex("^\\.");

        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNull(matchedRecipients);
    }

    // test if throw exception if no pattern is used
    public void testThrowExceptionWithoutPattern() throws MessagingException {
        boolean exceptionCatched = false;
        setSender(new MailAddress("t@james.apache.org"));
        setRegex("");

        setupMockedMail();

        try {
            setupMatcher();
        } catch (MessagingException m) {
            exceptionCatched = true;
        }
        assertTrue(exceptionCatched);
    }

    // test if throw exception if invalid pattern is used
    public void testThrowExceptionWithInvalidPattern()
            throws MessagingException {
        boolean exceptionCatched = false;
        setSender(new MailAddress("t@james.apache.org"));
        setRegex("(.");

        setupMockedMail();

        try {
            setupMatcher();
        } catch (MessagingException m) {
            exceptionCatched = true;
        }
        assertTrue(exceptionCatched);
    }

    protected Matcher createMatcher() {
        return new SenderIsRegex();
    }

    protected String getConfigOption() {
        return "SenderIsRegex=";
    }

    protected String getConfigValue() {
        return regex;
    }
}
