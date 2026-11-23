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

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.mailet.AttributeName;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
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
 * <p>The headers are written by the sender. With <code>trustOnlyAuthNetwork</code> set to true (default), a mail that
 * may not relay (neither authenticated nor received from an authorized network) only keeps its local recipients.
 * Set it to false to keep every recipient of the headers as before, at the risk of letting senders relay through
 * this mailet.</p>
 *
 * @version 1.0.0, 24/11/2000
 */
@Experimental
public class UseHeaderRecipients extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(UseHeaderRecipients.class);
    // Set by the SMTP server for mails received from a network allowed to relay
    private static final AttributeName SMTP_AUTH_NETWORK = AttributeName.of("org.apache.james.SMTPIsAuthNetwork");

    /**
     * Controls certain log messages
     */
    private boolean isDebug = false;
    private boolean trustOnlyAuthNetwork = true;

    /**
     * Initialize the mailet
     * <p/>
     * initializes the DEBUG flag
     */
    @Override
    public void init() {
        isDebug = (getInitParameter("debug") == null) ? false : Boolean.parseBoolean(getInitParameter("debug"));
        trustOnlyAuthNetwork = getInitParameter("trustOnlyAuthNetwork", true);
        if (!trustOnlyAuthNetwork) {
            LOGGER.warn("UseHeaderRecipients with trustOnlyAuthNetwork=false: the headers of any mail, written by its sender, can address remote recipients");
        }
    }

    /**
     * Process an incoming email, removing the currently identified
     * recipients and replacing them with the recipients indicated in
     * the Mail-For, To and Cc headers of the actual email.
     *
     * @param mail incoming email
     */
    @Override
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();

        Collection<MailAddress> recipients = headersAddresses(message);
        if (trustOnlyAuthNetwork && !relayAllowed(mail)) {
            // Headers are written by the sender: unless it may relay, it only reaches local recipients
            recipients = recipients.stream()
                .filter(recipient -> getMailetContext().isLocalServer(recipient.getDomain()))
                .collect(ImmutableList.toImmutableList());
        }
        mail.setRecipients(recipients);

        if (isDebug) {
            LOGGER.debug("All recipients = {}", mail.getRecipients());
            LOGGER.debug("Reprocessing mail using recipients in message headers");
        }

        // Return email to the "root" process.
        getMailetContext().sendMail(mail.duplicate());
        mail.setState(Mail.GHOST);
    }

    private static boolean relayAllowed(Mail mail) {
        return mail.loggedInUser().isPresent()
            || mail.getAttribute(SMTP_AUTH_NETWORK)
                .map(attribute -> attribute.getValue().value())
                .filter(Boolean.TRUE::equals)
                .isPresent();
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


    @Override
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
            LOGGER.debug("Checking {} headers", name);
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

    private ImmutableList<MailAddress> getMailAddressesFromHeadersParts(Iterable<String> headerParts) {
        ImmutableList.Builder<MailAddress> result = ImmutableList.builder();
        for (String headerPart : headerParts) {
            if (isDebug) {
                LOGGER.debug("Address = {}", headerPart);
            }
            result.addAll(readMailAddresses(headerPart));
        }
        return result.build();
    }

    private Collection<MailAddress> readMailAddresses(String headerPart) {
        return LenientAddressParser.DEFAULT
            .parseAddressList(MimeUtil.unfold(headerPart))
            .flatten()
            .stream()
            .map(this::toMailAddress)
            .collect(ImmutableList.toImmutableList());
    }

    private MailAddress toMailAddress(Mailbox mailbox) {
        try {
            return new MailAddress(mailbox.getAddress());
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

    private String sanitizeHeaderString(String header) throws MessagingException {
        try {
            return MimeUtility.unfold(MimeUtility.decodeText(header));
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Can not decode header", e);
        }
    }

}
