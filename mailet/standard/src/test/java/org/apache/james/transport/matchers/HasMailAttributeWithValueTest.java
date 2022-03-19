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

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.base.GenericMatcher;
import org.junit.jupiter.api.Test;

public class HasMailAttributeWithValueTest extends AbstractHasMailAttributeTest {

    @Override
    protected String getHasMailAttribute() {
        return MAIL_ATTRIBUTE.getName().asString() + ", " + MAIL_ATTRIBUTE.getValue().value();
    }

    @Override
    protected GenericMatcher createMatcher() {
        return new HasMailAttributeWithValue();
    }

    @Test
    void testAttributeIsNotMatchedCauseValue() throws MessagingException {
        setMailAttribute(new Attribute(MAIL_ATTRIBUTE.getName(), AttributeValue.of("false")));
        setupMockedMail();
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNull(matchedRecipients);
    }

    @Override
    protected String getMatcherName() {
        return "HasMailAttributeWithValue";
    }
}
