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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mappings;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext.LogLevel;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;

/**
 * Abstract base class which should get extended by classes which handle mapping
 * operations based on RecipientRewriteTable implementations
 */
public abstract class AbstractRecipientRewriteTableMailet extends GenericMailet {

    private DomainList domainList;

    @Inject
    public void setDomainList(DomainList domainList) {
        this.domainList = domainList;
    }

    /**
     * @see org.apache.mailet.base.GenericMailet#service(org.apache.mailet.Mail)
     */
    public void service(Mail mail) throws MessagingException {
        Collection<MailAddress> recipients = mail.getRecipients();
        Collection<MailAddress> errors = new Vector<MailAddress>();

        MimeMessage message = mail.getMessage();

        // Set Return-Path and remove all other Return-Path headers from the
        // message
        // This only works because there is a placeholder inserted by
        // MimeMessageWrapper
        message.setHeader(RFC2822Headers.RETURN_PATH, (mail.getSender() == null ? "<>" : "<" + mail.getSender() + ">"));

        Collection<MailAddress> newRecipients = new LinkedList<MailAddress>();
        for (Iterator<MailAddress> i = recipients.iterator(); i.hasNext();) {
            MailAddress recipient = i.next();
            try {
                Collection<MailAddress> usernames = processMail(mail.getSender(), recipient, message);

                // if the username is null or changed we remove it from the
                // remaining recipients
                if (usernames == null) {
                    i.remove();
                } else {
                    i.remove();
                    // if the username has been changed we add a new recipient
                    // with the new name.
                    newRecipients.addAll(usernames);
                }

            } catch (Exception ex) {
                getMailetContext().log(LogLevel.INFO, "Error while storing mail.", ex);
                errors.add(recipient);
            }
        }

        if (newRecipients.size() > 0) {
            recipients.addAll(newRecipients);
        }

        if (!errors.isEmpty()) {
            // If there were errors, we redirect the email to the ERROR
            // processor.
            // In order for this server to meet the requirements of the SMTP
            // specification, mails on the ERROR processor must be returned to
            // the sender. Note that this email doesn't include any details
            // regarding the details of the failure(s).
            // In the future we may wish to address this.
            getMailetContext().sendMail(mail.getSender(), errors, message, Mail.ERROR);
        }

        if (recipients.size() == 0) {
            // We always consume this message
            mail.setState(Mail.GHOST);
        }
    }

    /**
     * Handle the given mappings to map the original recipient to the right one
     * 
     * @param mappings
     *            a collection of mappings for the given recipient
     * @param sender
     *            the sender of the mail
     * @param recipient
     *            the original recipient of the email
     * @param message
     *            the mail message
     * @return a collection of mapped recpient addresses
     * 
     * @throws MessagingException
     */
    protected Collection<MailAddress> handleMappings(Mappings mappings, MailAddress sender, MailAddress recipient, MimeMessage message) throws MessagingException {
        Iterator<Mapping> i = mappings.iterator();
        Collection<MailAddress> remoteRecipients = new ArrayList<MailAddress>();
        Collection<MailAddress> localRecipients = new ArrayList<MailAddress>();
        while (i.hasNext()) {
            Mapping rcpt = i.next();

            if (!rcpt.hasDomain()) {
                // the mapping contains no domain name, use the default domain
                try {
                    rcpt = rcpt.appendDomain(domainList.getDefaultDomain());
                } catch (DomainListException e) {
                    throw new MessagingException("Unable to access DomainList", e);
                }
            }

            MailAddress nextMap = new MailAddress(rcpt.asString());
            if (getMailetContext().isLocalServer(nextMap.getDomain())) {
                localRecipients.add(nextMap);
            } else {
                remoteRecipients.add(nextMap);
            }
        }

        if (remoteRecipients.size() > 0) {
            try {
                getMailetContext().sendMail(sender, remoteRecipients, message);
                StringBuilder logBuffer = new StringBuilder(128).append("Mail for ").append(recipient).append(" forwarded to ");
                for (Iterator<MailAddress> j = remoteRecipients.iterator(); j.hasNext();) {
                    logBuffer.append(j.next());
                    if (j.hasNext())
                        logBuffer.append(", ");
                }
                getMailetContext().log(LogLevel.INFO, logBuffer.toString());
            } catch (MessagingException me) {
                StringBuilder logBuffer = new StringBuilder(128).append("Error forwarding mail to ");
                for (Iterator<MailAddress> j = remoteRecipients.iterator(); j.hasNext();) {
                    logBuffer.append(j.next());
                    if (j.hasNext())
                        logBuffer.append(", ");
                }
                logBuffer.append("attempting local delivery");

                getMailetContext().log(LogLevel.INFO, logBuffer.toString());
                throw me;
            }
        }

        if (localRecipients.size() > 0) {
            return localRecipients;
        } else {
            return null;
        }
    }

    /**
     * Process the mail
     * 
     * @param sender
     *            the sender of the mail
     * @param recipient
     *            the recipient of the mail
     * @param message
     *            the mail message
     * @return collection of recipients
     * 
     * @throws MessagingException
     */
    public abstract Collection<MailAddress> processMail(MailAddress sender, MailAddress recipient, MimeMessage message) throws MessagingException;
}
