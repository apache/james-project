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
package org.apache.james.samples.mailets;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.NewsAddress;
import org.apache.mailet.*;

/**
 * Mailet just prints out the details of a message. Sometimes Useful for
 * debugging.
 */
public class InstrumentationMailet implements Mailet {

    private MailetConfig config;

    @Override
    public void destroy() {
    }

    @Override
    public String getMailetInfo() {
        return "Example mailet";
    }

    @Override
    public MailetConfig getMailetConfig() {
        return config;
    }

    @Override
    public void init(MailetConfig config) throws MessagingException {
        this.config = config;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        MailetContext context = config.getMailetContext();
        context.log("######## MAIL STARTS");
        context.log("");

        MimeMessage message = mail.getMessage();

        context.log("Mail named: " + mail.getName());

        for (Iterator it = mail.getAttributeNames(); it.hasNext();) {
            String attributeName = (String) it.next();
            context.log("Attribute " + attributeName);
        }
        context.log("Message size: " + mail.getMessageSize());
        context.log("Last updated: " + mail.getLastUpdated());
        context.log("Remote Address: " + mail.getRemoteAddr());
        context.log("Remote Host: " + mail.getRemoteHost());
        context.log("State: " + mail.getState());
        context.log("Sender host: " + mail.getSender().getDomain());
        context.log("Sender user: " + mail.getSender().getLocalPart());
        Collection<MailAddress> recipients = mail.getRecipients();
        for (MailAddress address : recipients) {
            context.log("Recipient: " + address.getLocalPart() + "@" + address.getDomain());
        }

        context.log("Subject: " + message.getSubject());
        context.log("MessageID: " + message.getMessageID());
        context.log("Received: " + message.getReceivedDate());
        context.log("Sent: " + message.getSentDate());

        Enumeration allHeadersLines = message.getAllHeaderLines();
        while (allHeadersLines.hasMoreElements()) {
            String header = (String) allHeadersLines.nextElement();
            context.log("Header Line:= " + header);
        }

        Enumeration allHeadersEnumeration = message.getAllHeaders();
        while (allHeadersEnumeration.hasMoreElements()) {
            Header header = (Header) allHeadersEnumeration.nextElement();
            context.log("Header: " + header.getName() + "=" + header.getValue());
        }

        Address[] to = message.getRecipients(RecipientType.TO);
        printAddresses(to, "TO: ");
        Address[] cc = message.getRecipients(RecipientType.CC);
        printAddresses(cc, "CC: ");
        Address[] bcc = message.getRecipients(RecipientType.BCC);
        printAddresses(bcc, "BCC: ");

        Flags flags = message.getFlags();
        Flag[] systemFlags = flags.getSystemFlags();
        for (Flag systemFlag : systemFlags) {
            context.log("System Flag:" + systemFlag);
        }
        String[] userFlags = flags.getUserFlags();
        for (String userFlag : userFlags) {
            context.log("User flag: " + userFlag);
        }

        String mimeType = message.getContentType();
        context.log("Mime type: " + mimeType);
        if ("text/plain".equals(mimeType)) {
            try {
                Object content = message.getContent();
                context.log("Content: " + content);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        context.log("");
        context.log("######## MAIL ENDS");
    }

    private void printAddresses(Address[] addresses, String prefix) {
        MailetContext context = config.getMailetContext();
        for (Address address1 : addresses) {
            if (address1 instanceof InternetAddress) {
                InternetAddress address = (InternetAddress) address1;
                context.log(prefix + address.getPersonal() + "@" + address.getAddress());
            } else if (address1 instanceof NewsAddress) {
                NewsAddress address = (NewsAddress) address1;
                context.log(prefix + address.getNewsgroup() + "@" + address.getHost());
            }
        }
    }
}
