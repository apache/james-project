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

import org.apache.james.transport.matchers.SubjectStartsWith;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;

public class SubjectStartsWithTest extends AbstractSubjectIsTest {

    public SubjectStartsWithTest(String arg0)
            throws UnsupportedEncodingException {
        super(arg0);
    }

    // test if the recipients get returned as matched
    public void testHostIsMatchedAllRecipients() throws MessagingException {
        String SUBJECT_NAME = "testSubject";
        setSubject(SUBJECT_NAME);

        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(matchedRecipients.size(), mockedMail.getRecipients()
                .size());
    }

    // test if no recipient get returned cause it not match
    public void testHostIsNotMatch() throws MessagingException {
        setSubject("FOOBAR");

        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNull(matchedRecipients);
    }

    protected Matcher createMatcher() {
        return new SubjectStartsWith();
    }

    protected String getConfigOption() {
        return "SubjectIs=";
    }

    protected String getSubjectName() {
        return "test";
    }
}
