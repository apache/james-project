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
package org.apache.james.core;

import org.apache.mailet.base.test.FakeMimeMessage;
import org.apache.mailet.base.test.MailUtil;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Mail;

import javax.mail.MessagingException;
import java.util.ArrayList;
import static org.junit.Assert.*;
import org.junit.Test;

public class MailImplTest extends MailTestAllImplementations {

    @Override
    protected Mail createMailImplementation() {
        return new MailImpl();
    }

    @Test
    public void testConstr1() throws MessagingException {
        MailImpl mail = new MailImpl();

        helperTestInitialState(mail);
        helperTestMessageSize(mail, 0); // MimeMessageWrapper default is 0
        assertNull("no initial message", mail.getMessage());
        assertNull("no initial sender", mail.getSender());
        assertNull("no initial name", mail.getName());
    }

    @Test
    public void testConstr2() throws MessagingException {
        ArrayList recepients = new ArrayList();
        String name = MailUtil.newId();
        String sender = "sender@localhost";
        MailAddress senderMailAddress = new MailAddress(sender);
        MailImpl mail = new MailImpl(name, senderMailAddress, recepients);

        helperTestInitialState(mail); // MimeMessageWrapper default is 0
        helperTestMessageSize(mail, 0); // MimeMessageWrapper default is 0
        assertNull("no initial message", mail.getMessage());
        assertEquals("sender", sender, mail.getSender().toString());
        assertEquals("name", name, mail.getName());

        mail.setMessage(new FakeMimeMessage());
        assertNotNull("message", mail.getMessage());
    }

    @Test
    public void testConstr3() throws MessagingException {
        ArrayList recepients = new ArrayList();
        String name = MailUtil.newId();
        String sender = "sender@localhost";
        MailAddress senderMailAddress = new MailAddress(sender);
        FakeMimeMessage mimeMessage = new FakeMimeMessage();
        MailImpl mail = new MailImpl(name, senderMailAddress, recepients, mimeMessage);

        helperTestInitialState(mail);
        helperTestMessageSize(mail, 0);
        assertEquals("initial message", mimeMessage.getMessageID(), mail.getMessage().getMessageID());
        assertEquals("sender", sender, mail.getSender().toString());
        assertEquals("name", name, mail.getName());
        mail.dispose();
    }

    @Test
    public void testDuplicate() throws MessagingException {
        MailImpl mail = new MailImpl();
        MailImpl duplicate = (MailImpl) mail.duplicate();
        assertNotSame("is real duplicate", mail, duplicate);
        helperTestInitialState(duplicate);
        helperTestMessageSize(duplicate, 0);
    }

    @Test
    public void testDuplicateNewName() throws MessagingException {
        String newName = "aNewName";

        MailImpl mail = new MailImpl();
        assertFalse("before + after names differ", newName.equals(mail.getName()));

        MailImpl duplicate = (MailImpl) mail.duplicate(newName);
        assertEquals("new name set", newName, duplicate.getName());
        helperTestInitialState(duplicate);
        helperTestMessageSize(duplicate, 0);
    }
}
