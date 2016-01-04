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
package org.apache.james.mailetcontainer.impl.matchers;

import org.apache.mailet.MailAddress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;

import javax.mail.MessagingException;
import java.util.Collection;

public class AndTest extends BaseMatchersTest {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        setupCompositeMatcher("And", And.class);
    }

    // test if all recipients was returned
    @Test
    public void testAndIntersectSameTwice() throws MessagingException {
        setupChild("RecipientIs=test@james.apache.org");
        setupChild("RecipientIs=test@james.apache.org");
        setupChild("All");

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(1, matchedRecipients.size());
        MailAddress address = (MailAddress) matchedRecipients.iterator().next();
        assertEquals(address.toString(), "test@james.apache.org");
    }

    @Test
    public void testAndNoIntersect() throws MessagingException {
        setupChild("RecipientIs=test@james.apache.org");
        setupChild("RecipientIs=test2@james.apache.org");

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(0, matchedRecipients.size());
    }
}
