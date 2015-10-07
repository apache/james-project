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
import org.apache.mailet.base.GenericMailet;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.Vector;

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
public class UseHeaderRecipients extends GenericMailet {

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

        // Utilise features of Set Collections such that they automatically
        // ensure that no two entries are equal using the equality method
        // of the element objects.  MailAddress objects test equality based
        // on equivalent but not necessarily visually identical addresses.
        Collection<MailAddress> recipients = mail.getRecipients();
        // Wipe all the exist recipients
        recipients.clear();
        recipients.addAll(getHeaderMailAddresses(message, "Mail-For"));
        if (recipients.isEmpty()) {
            recipients.addAll(getHeaderMailAddresses(message, "To"));
            recipients.addAll(getHeaderMailAddresses(message, "Cc"));
        }
        if (isDebug) {
            log("All recipients = " + recipients.toString());
            log("Reprocessing mail using recipients in message headers");
        }

        // Return email to the "root" process.
        getMailetContext().sendMail(mail.getSender(), mail.getRecipients(), mail.getMessage());
        mail.setState(Mail.GHOST);
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
            log("Checking " + name + " headers");
        }
        Collection<MailAddress> addresses = new Vector<MailAddress>();
        String[] headers = message.getHeader(name);
        String addressString;
        InternetAddress iAddress;
        if (headers != null) {
            for (String header : headers) {
                StringTokenizer st = new StringTokenizer(header, ",", false);
                while (st.hasMoreTokens()) {
                    addressString = st.nextToken();
                    iAddress = new InternetAddress(addressString);
                    if (isDebug) {
                        log("Address = " + iAddress.toString());
                    }
                    addresses.add(new MailAddress(iAddress));
                }
            }
        }
        return addresses;
    }

}
