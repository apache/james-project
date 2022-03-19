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

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;
import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMessage.RecipientType;
import jakarta.mail.internet.NewsAddress;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mailet just prints out the details of a message. Sometimes Useful for
 * debugging.
 */
public class InstrumentationMailet implements Mailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentationMailet.class);

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
        LOGGER.info("######## MAIL STARTS");
        LOGGER.info("");

        MimeMessage message = mail.getMessage();

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Mail named: " + mail.getName());

            mail.attributeNames()
                .forEach(attributeName -> LOGGER.info("Attribute " + attributeName));

            LOGGER.info("Message size: " + mail.getMessageSize());
            LOGGER.info("Last updated: " + mail.getLastUpdated());
            LOGGER.info("Remote Address: " + mail.getRemoteAddr());
            LOGGER.info("Remote Host: " + mail.getRemoteHost());
            LOGGER.info("State: " + mail.getState());
            LOGGER.info("Sender host: " + mail.getMaybeSender().asOptional().map(mailAddress -> mailAddress.getDomain().name()));
            LOGGER.info("Sender user: " + mail.getMaybeSender().asOptional().map(MailAddress::getLocalPart));
            Collection<MailAddress> recipients = mail.getRecipients();
            for (MailAddress address : recipients) {
                LOGGER.info("Recipient: " + address.getLocalPart() + "@" + address.getDomain().name());
            }

            LOGGER.info("Subject: " + message.getSubject());
            LOGGER.info("MessageID: " + message.getMessageID());
            LOGGER.info("Received: " + message.getReceivedDate());
            LOGGER.info("Sent: " + message.getSentDate());

            Enumeration<String> allHeadersLines = message.getAllHeaderLines();
            while (allHeadersLines.hasMoreElements()) {
                String header = allHeadersLines.nextElement();
                LOGGER.info("Header Line:= " + header);
            }

            Enumeration<Header> allHeadersEnumeration = message.getAllHeaders();
            while (allHeadersEnumeration.hasMoreElements()) {
                Header header = allHeadersEnumeration.nextElement();
                LOGGER.info("Header: " + header.getName() + "=" + header.getValue());
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
                LOGGER.info("System Flag:" + systemFlag);
            }
            String[] userFlags = flags.getUserFlags();
            for (String userFlag : userFlags) {
                LOGGER.info("User flag: " + userFlag);
            }

            String mimeType = message.getContentType();
            LOGGER.info("Mime type: " + mimeType);
            if ("text/plain".equals(mimeType)) {
                try {
                    Object content = message.getContent();
                    LOGGER.info("Content: " + content);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        LOGGER.info("");
        LOGGER.info("######## MAIL ENDS");
    }

    private void printAddresses(Address[] addresses, String prefix) {
        for (Address address1 : addresses) {
            if (address1 instanceof InternetAddress) {
                InternetAddress address = (InternetAddress) address1;
                LOGGER.info(prefix + address.getPersonal() + "@" + address.getAddress());
            } else if (address1 instanceof NewsAddress) {
                NewsAddress address = (NewsAddress) address1;
                LOGGER.info(prefix + address.getNewsgroup() + "@" + address.getHost());
            }
        }
    }
}
