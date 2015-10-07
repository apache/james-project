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

import junit.framework.AssertionFailedError;

import org.apache.james.transport.matchers.RecipientIsRegex;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;

public class RecipientIsRegexTest extends AbstractRecipientIsTest {

    private String regex = ".*";

    public RecipientIsRegexTest(String arg0)
            throws UnsupportedEncodingException {
        super(arg0);
    }

    private void setRegex(String regex) {
        this.regex = regex;
    }

    // test if the recipients get returned as matched
    public void testRegexIsMatchedAllRecipients() throws MessagingException {
        setRecipients(new MailAddress[] { new MailAddress(
                "test@james.apache.org") });
        setRegex(".*@.*");

        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(matchedRecipients.size(), mockedMail.getRecipients()
                .size());
    }

    // test if one recipients get returned as matched
    public void testRegexIsMatchedOneRecipient() throws MessagingException {
        setRecipients(new MailAddress[] {
                new MailAddress("test@james.apache.org"),
                new MailAddress("test2@james.apache.org") });
        setRegex("^test@.*");

        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(matchedRecipients.size(), 1);
    }

    // test if no recipient get returned cause it not match
    public void testRegexIsNotMatch() throws MessagingException {
        setRecipients(new MailAddress[] {
                new MailAddress("test@james2.apache.org"),
                new MailAddress("test2@james2.apache.org") });
        setRegex(".*\\+");

        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertEquals(matchedRecipients.size(), 0);
    }

    // test if an exception was thrown cause the regex was invalid
    public void testRegexIsNotMatchedCauseError() throws MessagingException {
        Collection<MailAddress> matchedRecipients = null;
        String invalidRegex = "(!(";
        String regexException = null;
        String exception = "Malformed pattern: " + invalidRegex;

        setRecipients(new MailAddress[] {
                new MailAddress("test@james2.apache.org"),
                new MailAddress("test2@james2.apache.org") });
        setRegex(invalidRegex);

        try {
            setupAll();
            matchedRecipients = matcher.match(mockedMail);
        } catch (MessagingException m) {
            regexException = m.getMessage();
        }

        assertNull(matchedRecipients);
        try {
            assertEquals(exception, regexException);
        } catch (AssertionFailedError e) {
            // NOTE the expected exception changes when the project is built/run
            // against non java 1.4 jvm. 
            assertEquals(exception+" (org.apache.oro.text.regex.MalformedPatternException: Unmatched parentheses.)", regexException);
        }

    }

    // test if an exception was thrown cause the regex was invalid
    public void testThrowExceptionWithEmptyPattern() throws MessagingException {
        boolean catchException = false;

        setRecipients(new MailAddress[] {
                new MailAddress("test@james2.apache.org"),
                new MailAddress("test2@james2.apache.org") });
        setRegex("");

        try {
            setupAll();
        } catch (MessagingException m) {
            catchException = true;
        }
        assertTrue(catchException);

    }

    protected String getRecipientName() {
        return regex;
    }

    protected Matcher createMatcher() {
        return new RecipientIsRegex();
    }
}
