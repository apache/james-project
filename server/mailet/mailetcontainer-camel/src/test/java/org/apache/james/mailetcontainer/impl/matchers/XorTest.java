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
import java.util.Iterator;

public class XorTest extends BaseMatchersTest {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        setupCompositeMatcher("Xor", Xor.class);
    }

    // test if all recipients was returned
    @Test
    public void testIntersectSame() throws MessagingException {
        setupChild("RecipientIsRegex=test@james.apache.org");
        setupChild("RecipientIsRegex=test@james.apache.org");

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(0, matchedRecipients.size());
    }

    @Test
    public void testNoIntersect() throws MessagingException {
        setupChild("RecipientIsRegex=test@james.apache.org");
        setupChild("RecipientIsRegex=test2@james.apache.org");

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(2, matchedRecipients.size());

        Iterator<MailAddress> iterator = matchedRecipients.iterator();
        MailAddress address = (MailAddress) iterator.next();
        assertEquals(address, "test@james.apache.org");
        address = (MailAddress) iterator.next();
        assertEquals(address, "test2@james.apache.org");
    }
}
