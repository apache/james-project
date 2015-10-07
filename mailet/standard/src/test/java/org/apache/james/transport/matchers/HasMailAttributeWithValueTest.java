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

import org.apache.james.transport.matchers.HasMailAttributeWithValue;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMatcher;

import javax.mail.MessagingException;
import java.util.Collection;

public class HasMailAttributeWithValueTest extends AbstractHasMailAttributeTest {

    public HasMailAttributeWithValueTest() {
        super();
    }

    protected String getHasMailAttribute() {
        return MAIL_ATTRIBUTE_NAME + ", " + MAIL_ATTRIBUTE_VALUE;
    }

    protected GenericMatcher createMatcher() {
        return new HasMailAttributeWithValue();
    }

    // test if the mail attribute was not matched cause diffrent value
    public void testAttributeIsNotMatchedCauseValue() throws MessagingException {
        setMailAttributeName(MAIL_ATTRIBUTE_NAME);
        setupMockedMail(mockedMimeMessage);
        setupMatcher();

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNull(matchedRecipients);
    }

    protected String getConfigOption() {
        return "HasMailAttributeWithValue=";
    }
}
