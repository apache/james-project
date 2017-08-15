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

import java.io.UnsupportedEncodingException;
import java.util.Collection;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

/**
 * <p>Mailet designed to process the recipients from the mail headers rather
 * than the recipients specified in the SMTP message header.  This can be
 * useful if your mail is redirected on-route by a mail server that
 * substitutes a fixed recipient address for the original.</p>
 * <p/>
 * <p>To use this, match against the redirection address using the
 * <code>RecipientIs</code> matcher and set the mailet 'class' to
 * <code>UseHeaderRecipients</code>.  This will cause the email to be
 * re-injected into the root process with the recipient substituted
 * by all the recipients in the Mail-For, To and Cc headers
 * of the message.</p>
 * <p/>
 * <p>e.g.</p>
 * <pre><code>
 *    &lt;mailet match="RecipientIs=forwarded@myhost"
 *            class="UseHeaderRecipients"&gt;
 *    &lt;/mailet&gt;
 * </code></pre>
 *
 * @version 1.0.0, 24/11/2000
 */
@Experimental
public class UseHeaderRecipients extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(UseHeaderRecipients.class);

    /**
     * Controls certain log messages
     */
    private boolean isDebug = false;

    /**
     * Initialize the mailet
     * <p/>
     * initializes the DEBUG flag
     */
    public void init() {
        isDebug = (getInitParameter("debug") == null) ? false : Boolean.valueOf(getInitParameter("debug"));
    }

    /**
     * Process an incoming email, removing the currently identified
     * recipients and replacing them with the recipients indicated in
     * the Mail-For, To and Cc headers of the actual email.
     *
     * @param mail incoming email
     */
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();

        mail.setRecipients(headersAddresses(message));

        if (isDebug) {
            LOGGER.debug("All recipients = " + mail.getRecipients());
            LOGGER.debug("Reprocessing mail using recipients in message headers");
        }

        // Return email to the "root" process.
        getMailetContext().sendMail(mail.getSender(), mail.getRecipients(), mail.getMessage());
        mail.setState(Mail.GHOST);
    }

    public Collection<MailAddress> headersAddresses(MimeMessage mimeMessage) throws MessagingException {
        Collection<MailAddress> mailForHeaderAddresses = getHeaderMailAddresses(mimeMessage, "Mail-For");
        if (!mailForHeaderAddresses.isEmpty()) {
            return mailForHeaderAddresses;
        }
        return ImmutableList.<MailAddress>builder()
            .addAll(getHeaderMailAddresses(mimeMessage, "To"))
            .addAll(getHeaderMailAddresses(mimeMessage, "Cc"))
            .addAll(getHeaderMailAddresses(mimeMessage, "Bcc"))
            .build();
    }


    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "UseHeaderRecipients Mailet";
    }

    /**
     * Work through all the headers of the email with a matching name and
     * extract all the mail addresses as a collection of addresses.
     *
     * @param message the mail message to read
     * @param name    the header name as a String
     * @return the collection of MailAddress objects.
     */
    private Collection<MailAddress> getHeaderMailAddresses(MimeMessage message, String name) throws MessagingException {
        if (isDebug) {
            LOGGER.debug("Checking " + name + " headers");
        }
        String[] headers = message.getHeader(name);
        ImmutableList.Builder<MailAddress> addresses = ImmutableList.builder();

        if (headers != null) {
            for (String header : headers) {
               addresses.addAll(getMailAddressesFromHeaderLine(header));
            }
        }
        return addresses.build();
    }

    private ImmutableList<MailAddress> getMailAddressesFromHeaderLine(String header) throws MessagingException {
        String unfoldedDecodedString = sanitizeHeaderString(header);
        Iterable<String> headerParts = Splitter.on(",").split(unfoldedDecodedString);
        return getMailAddressesFromHeadersParts(headerParts);
    }

    private ImmutableList<MailAddress> getMailAddressesFromHeadersParts(Iterable<String> headerParts) throws AddressException {
        ImmutableList.Builder<MailAddress> result = ImmutableList.builder();
        for (String headerPart : headerParts) {
            if (isDebug) {
                LOGGER.debug("Address = " + headerPart);
            }
            result.addAll(readMailAddresses(headerPart));
        }
        return result.build();
    }

    private Collection<MailAddress> readMailAddresses(String headerPart) throws AddressException {
        AddressList addressList = LenientAddressParser.DEFAULT
            .parseAddressList(MimeUtil.unfold(headerPart));

        ImmutableList.Builder<Mailbox> mailboxList = ImmutableList.builder();

        for (Address address: addressList) {
            mailboxList.addAll(convertAddressToMailboxCollection(address));
        }

        return FluentIterable.from(mailboxList.build())
            .transform(this::toMailAddress)
            .toList();
    }

    private MailAddress toMailAddress(Mailbox mailbox) {
        try {
            return new MailAddress(mailbox.getAddress());
        } catch (AddressException e) {
            throw Throwables.propagate(e);
        }
    }

    private Collection<Mailbox> convertAddressToMailboxCollection(Address address) {
        if (address instanceof Mailbox) {
            return ImmutableList.of((Mailbox) address);
        } else if (address instanceof Group) {
            return ImmutableList.copyOf(((Group) address).getMailboxes());
        }
        return ImmutableList.of();
    }

    private String sanitizeHeaderString(String header) throws MessagingException {
        try {
            return MimeUtility.unfold(MimeUtility.decodeText(header));
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Can not decode header", e);
        }
    }

}
