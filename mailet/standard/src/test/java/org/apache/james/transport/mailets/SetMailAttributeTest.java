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

import org.apache.james.transport.mailets.SetMailAttribute;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;

import javax.mail.MessagingException;

public class SetMailAttributeTest {

    private Mailet mailet;

    private final String ATTRIBUTE_NAME1 = "org.apache.james.junit1";

    private final String ATTRIBUTE_NAME2 = "org.apache.james.junit2";

    private Mail mockedMail;

    @Before
    public void setupMailet() throws MessagingException {
        mockedMail = MailUtil.createMockMail2Recipients(null);
        mailet = new SetMailAttribute();
        FakeMailetConfig mci = new FakeMailetConfig("Test",
                new FakeMailContext());
        mci.setProperty(ATTRIBUTE_NAME1, "true");
        mci.setProperty(ATTRIBUTE_NAME2, "true");

        mailet.init(mci);
    }

    // test if the Header was add
    @Test
    public void testMailAttributeAdded() throws MessagingException {
        assertNull(mockedMail.getAttribute(ATTRIBUTE_NAME1));
        assertNull(mockedMail.getAttribute(ATTRIBUTE_NAME2));
        mailet.service(mockedMail);

        assertEquals("true", mockedMail.getAttribute(ATTRIBUTE_NAME1));
        assertEquals("true", mockedMail.getAttribute(ATTRIBUTE_NAME2));
    }
}
