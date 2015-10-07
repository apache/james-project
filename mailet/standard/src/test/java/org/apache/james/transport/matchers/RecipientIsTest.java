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

import org.apache.james.transport.matchers.RecipientIs;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;

public class RecipientIsTest extends AbstractRecipientIsTest {

    public RecipientIsTest(String arg0) throws UnsupportedEncodingException {
        super(arg0);
    }

    protected String getRecipientName() {
        return "test@james.apache.org";
    }

    // test if the recipients get returned as matched
    public void testHostIsMatchedAllRecipients() throws MessagingException {
        setRecipients(new MailAddress[] { new MailAddress(
                "test@james.apache.org") });

        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(matchedRecipients.size(), mockedMail.getRecipients()
                .size());
    }

    // test if one recipients get returned as matched
    public void testHostIsMatchedOneRecipient() throws MessagingException {
        setRecipients(new MailAddress[] {
                new MailAddress("test@james.apache.org"),
                new MailAddress("test2@james.apache.org") });

        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(matchedRecipients.size(), 1);
    }

    // test if no recipient get returned cause it not match
    public void testHostIsNotMatch() throws MessagingException {
        setRecipients(new MailAddress[] {
                new MailAddress("test@james2.apache.org"),
                new MailAddress("test2@james2.apache.org") });

        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertEquals(matchedRecipients.size(), 0);
    }

    protected Matcher createMatcher() {
        return new RecipientIs();
    }
}
