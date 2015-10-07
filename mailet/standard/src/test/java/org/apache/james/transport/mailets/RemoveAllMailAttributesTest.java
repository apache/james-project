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


package org.apache.james.transport.mailets;

import org.apache.james.transport.mailets.RemoveAllMailAttributes;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

public class RemoveAllMailAttributesTest {

    private Mail mockedMail;

    private Mailet mailet;

    @Before
    public void setUp() throws Exception {
        mailet = new RemoveAllMailAttributes();
        FakeMailetConfig mci = new FakeMailetConfig("Test", new FakeMailContext());
        mailet.init(mci);
    }

    private void setupMockedMail(MimeMessage m) throws ParseException {
        mockedMail = MailUtil.createMockMail2Recipients(m);
        mockedMail.setAttribute("org.apache.james.test.junit", "true");
    }

    // test if ToProcessor works
    @Test
    public void testRemoveAllMailAttributes() throws MessagingException {
        setupMockedMail(null);
        // check if the mail has a attribute
        Assert.assertTrue(mockedMail.getAttributeNames().hasNext());

        mailet.service(mockedMail);

        // check if all was removed
        Assert.assertFalse(mockedMail.getAttributeNames().hasNext());
    }

}
