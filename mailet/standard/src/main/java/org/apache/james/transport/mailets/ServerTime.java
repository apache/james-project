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

import java.util.Arrays;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

/**
 * Returns the current time for the mail server.  Sample configuration:
 * <pre><code>
 * &lt;mailet match="RecipientIs=time@cadenza.lokitech.com" class="ServerTime"&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 *
 * <p>The reply addresses are written by the sender. With <code>trustOnlyAuthNetwork</code> set to true (default), the
 * answer to a mail that may not relay (neither authenticated nor received from an authorized network) only goes to
 * its local reply addresses. Set it to false to answer every reply address as before, at the risk of letting senders
 * send the answer to anyone.</p>
 */
@Experimental
public class ServerTime extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerTime.class);

    private boolean trustOnlyAuthNetwork = true;

    @Override
    public void init() {
        trustOnlyAuthNetwork = getInitParameter("trustOnlyAuthNetwork", true);
        if (!trustOnlyAuthNetwork) {
            LOGGER.warn("ServerTime with trustOnlyAuthNetwork=false: the answer goes to the reply addresses of any mail, written by its sender");
        }
    }

    /**
     * Sends a message back to the sender indicating what time the server thinks it is.
     *
     * @param mail the mail being processed
     *
     * @throws MessagingException if an error is encountered while formulating the reply message
     */
    @Override
    public void service(Mail mail) throws MessagingException {
        if (!mail.hasSender()) {
            return;
        }

        MimeMessage response = (MimeMessage)mail.getMessage().reply(false);
        response.setSubject("The time is now...");
        String textBuffer = "This mail server thinks it's " + (new java.util.Date()).toString() + ".";
        response.setText(textBuffer);

        // Someone manually checking the server time by hand may send
        // an formatted message, lacking From and To headers.  If the
        // response fields are null, try setting them from the SMTP
        // MAIL FROM/RCPT TO commands used to send the inquiry.

        if (response.getFrom() == null) {
            mail.getRecipients().iterator().next().toInternetAddress()
                .ifPresent(Throwing.<Address>consumer(response::setFrom).sneakyThrow());
        }

        if (response.getAllRecipients() == null) {
            response.setRecipients(MimeMessage.RecipientType.TO, mail.getMaybeSender().get().toString());
        }

        if (trustOnlyAuthNetwork && !UseHeaderRecipients.relayAllowed(mail)) {
            // The reply addresses are written by the sender: unless it may relay, only local ones are answered
            Address[] localRecipients = Arrays.stream(response.getAllRecipients())
                .filter(this::isLocal)
                .toArray(Address[]::new);
            if (localRecipients.length == 0) {
                return;
            }
            response.setRecipients(MimeMessage.RecipientType.TO, localRecipients);
            response.setRecipients(MimeMessage.RecipientType.CC, (Address[]) null);
            response.setRecipients(MimeMessage.RecipientType.BCC, (Address[]) null);
        }

        response.saveChanges();
        getMailetContext().sendMail(response);
    }

    private boolean isLocal(Address address) {
        try {
            return address instanceof InternetAddress internetAddress
                && getMailetContext().isLocalServer(new MailAddress(internetAddress.getAddress()).getDomain());
        } catch (AddressException e) {
            return false;
        }
    }

    @Override
    public String getMailetInfo() {
        return "ServerTime Mailet";
    }
}

