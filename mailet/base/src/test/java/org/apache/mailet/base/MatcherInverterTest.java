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

package org.apache.mailet.base;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

import javax.mail.MessagingException;
import java.util.Arrays;
import java.util.Collection;

public class MatcherInverterTest {

    @Test
    public void testAllMatch() throws MessagingException {
        MatcherInverter inverter = new MatcherInverter(new GenericMatcher() {

            public Collection<MailAddress> match(Mail mail) throws MessagingException {
                return null;
            }
        });
        FakeMail mail = new FakeMail();
        mail.setRecipients(Arrays.asList(new MailAddress("user", "domain")));

        assertNotNull("Should match all recipients", inverter.match(mail));
    }

    @Test
    public void testNonMatch() throws MessagingException {
        final MailAddress address1 = new MailAddress("user", "domain");
        final MailAddress address2 = new MailAddress("user", "domain2");

        MatcherInverter inverter = new MatcherInverter(new GenericMatcher() {

            public Collection<MailAddress> match(Mail mail) throws MessagingException {
                return mail.getRecipients();
            }
        });
        FakeMail mail = new FakeMail();
        mail.setRecipients(Arrays.asList(address1, address2));

        assertNull("Should match all recipients", inverter.match(mail));
    }

    @Test
    public void testOneMatch() throws MessagingException {
        final MailAddress address1 = new MailAddress("user", "domain");
        final MailAddress address2 = new MailAddress("user", "domain2");

        MatcherInverter inverter = new MatcherInverter(new GenericMatcher() {

            public Collection<MailAddress> match(Mail mail) throws MessagingException {
                return Arrays.asList(address1);
            }
        });
        FakeMail mail = new FakeMail();
        mail.setRecipients(Arrays.asList(address1, address2));

        assertEquals("Should match one recipient", address2.toString(), inverter.match(mail).iterator().next().toString());
    }
}
