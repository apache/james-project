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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.GenericMatcher;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

public class HasMailAttributeWithValueRegexTest extends AbstractHasMailAttributeTest {

    private String regex = ".*";

    private void setRegex(String regex) {
        this.regex = regex;
    }

    @Override
    protected String getHasMailAttribute() {
        return MAIL_ATTRIBUTE.getName().asString() + ", " + regex;
    }

    @Override
    protected GenericMatcher createMatcher() {
        return new HasMailAttributeWithValueRegex();
    }

    @Override
    @Test
    public void testAttributeIsMatched() throws MessagingException {
        init();
        setRegex(".*");
        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertThat(mockedMail.getRecipients()
                .size()).isEqualTo(matchedRecipients.size());
    }

    @Test
    public void getMatcherConfigShouldNotReturnNull() throws MessagingException {
        init();
        setRegex(".*");
        setupAll();

        assertThat(matcher.getMatcherConfig()).isNotNull();
    }

    @Test
    void testHeaderIsNotMatched() throws MessagingException {
        setRegex("\\d");
        setupAll();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNull(matchedRecipients);
    }

    @Test
    void testHeaderIsNotMatchedCauseValue() throws MessagingException {

        String invalidRegex = "(!(";
        String regexException = null;
        String exception = "Malformed pattern: " + invalidRegex;

        setRegex(invalidRegex);
        setupMockedMail();

        try {
            setupMatcher();
        } catch (MessagingException m) {
            regexException = m.getMessage();
        }

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNull(matchedRecipients);
        
        try {
            assertThat(regexException).isEqualTo(exception);
        } catch (AssertionFailedError e) {
            // NOTE the expected exception changes when the project is built/run
            // against non java 1.4 jvm. 
            assertThat(regexException).isEqualTo(exception + " (org.apache.oro.text.regex.MalformedPatternException: Unmatched parentheses.)");
        }
    }

    @Override
    protected String getMatcherName() {
        return "HasMailAttributeWithValueRegex";
    }
}
