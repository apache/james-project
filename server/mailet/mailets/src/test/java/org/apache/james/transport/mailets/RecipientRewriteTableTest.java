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

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.FakeMimeMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import static org.apache.james.transport.mailets.RecipientRewriteTableMock.mapFrom;
import static org.apache.james.transport.mailets.RecipientRewriteTableMock.rewriteTableMock;
import static org.junit.Assert.assertEquals;

public class RecipientRewriteTableTest {

    private org.apache.james.transport.mailets.RecipientRewriteTable table;

    @Before
    public void setUp() throws Exception {
        final FakeMailContext mockMailetContext = new FakeMailContext() {

            @Override
            public boolean isLocalServer(String serverName) {
                return serverName.equals("localhost");

            }
        };

        table = createRecipientRewriteMailet(
            rewriteTableMock(mapFrom("test@localhost").to("whatever@localhost", "blah@localhost")),
            mockMailetContext
        );
    }

    private static RecipientRewriteTable createRecipientRewriteMailet(
            org.apache.james.rrt.api.RecipientRewriteTable vut,
            MailetContext mailContext) throws MessagingException {
        RecipientRewriteTable rrt = new org.apache.james.transport.mailets.RecipientRewriteTable();

        FakeMailetConfig mockMailetConfig = new FakeMailetConfig("vut", mailContext, new Properties());
        // mockMailetConfig.put("recipientrewritetable", "vut");
        rrt.setRecipientRewriteTable(vut);
        rrt.init(mockMailetConfig);
        return rrt;
    }

    @After
    public void tearDown() throws Exception {
        table = null;
    }

    @Test
    public void testAddressMapping() throws Exception {
        Mail mail = createMail(new String[]{"test@localhost", "apache@localhost"});
        table.service(mail);

        assertEquals(3, mail.getRecipients().size());
        Iterator<MailAddress> it = mail.getRecipients().iterator();
        assertEquals("whatever@localhost", it.next().toString());
        assertEquals("blah@localhost", it.next().toString());
        assertEquals("apache@localhost", it.next().toString());

    }

    /**
     * @return
     * @throws MessagingException
     */
    private Mail createMail(String[] recipients) throws MessagingException {
        Mail mail = new FakeMail();
        ArrayList<MailAddress> a = new ArrayList<MailAddress>(recipients.length);
        for (String recipient : recipients) {
            a.add(new MailAddress(recipient));
        }
        mail.setRecipients(a);
        mail.setMessage(new FakeMimeMessage());
        return mail;
    }

    @Test
    public void testMixedLocalAndRemoteRecipients() throws Exception {
        RecordingMailContext context = new RecordingMailContext();
        RecipientRewriteTable mailet = createRecipientRewriteMailet(
            rewriteTableMock(mapFrom("mixed@localhost").to("a@localhost", "b@remote.com")),
            context
        );
        Mail mail = createMail(new String[]{"mixed@localhost"});
        mailet.service(mail);
        //the mail must be send via the context to b@remote.com, the other
        //recipient a@localhost must be in the recipient list of the message
        //after processing.
        assertEquals(context.getSendmails().size(), 1);
        MimeMessage msg = context.getSendmails().get(0).getMessage();
        if (msg == null) {
            msg = context.getSendmails().get(0).getMail().getMessage();
        }
        if (msg.getRecipients(Message.RecipientType.TO).length == 1) {
            assertEquals(msg.getRecipients(Message.RecipientType.TO)[0].toString(), "b@remote.com");
        } else {
            assertEquals(context.getSendmails().get(0).getRecipients().size(), 1);
            MailAddress rec = context.getSendmails().get(0).getRecipients().iterator().next();
            assertEquals(rec.toInternetAddress().toString(), "b@remote.com");
        }

        assertEquals(mail.getRecipients().size(), 1);
        MailAddress localRec = mail.getRecipients().iterator().next();
        assertEquals(localRec.toInternetAddress().toString(), "a@localhost");
    }

}
