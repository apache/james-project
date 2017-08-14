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
package org.apache.james.transport.mailets.jsieve;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.james.transport.mailets.jsieve.mdn.ActionModeAutomatic;
import org.apache.james.transport.mailets.jsieve.mdn.Disposition;
import org.apache.james.transport.mailets.jsieve.mdn.DispositionModifier;
import org.apache.james.transport.mailets.jsieve.mdn.MDNFactory;
import org.apache.james.transport.mailets.jsieve.mdn.ModifierError;
import org.apache.james.transport.mailets.jsieve.mdn.SendingModeAutomatic;
import org.apache.james.transport.mailets.jsieve.mdn.TypeDeleted;
import org.apache.jsieve.mail.Action;
import org.apache.jsieve.mail.ActionReject;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs the rejection of a mail, with a reply to the sender. 
 * <h4>Thread Safety</h4>
 * <p>An instance maybe safe accessed concurrently by multiple threads.</p>
 */
public class RejectAction implements MailAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(RejectAction.class);

    public void execute(Action action, Mail mail, ActionContext context)
            throws MessagingException {
        if (action instanceof ActionReject) {
            final ActionReject actionReject = (ActionReject) action;
            execute(actionReject, mail, context);
        }

    }

    /**
     * <p>
     * Method execute executes the passed ActionReject. It sends an RFC 2098
     * compliant reject MDN back to the sender.
     * </p>
     * <p>
     * NOTE: The Mimecontent type should be 'report', but as we do not yet have
     * a DataHandler for this yet, its currently 'text'!
     * 
     * @param anAction not null
     * @param aMail not null
     * @param context not null
     * @throws MessagingException
     */
    public void execute(ActionReject anAction, Mail aMail, ActionContext context) throws MessagingException
    {
        ActionUtils.detectAndHandleLocalLooping(aMail, context, "reject");

        // Create the MDN part
        StringBuilder humanText = new StringBuilder(128);
        humanText.append("This message was refused by the recipient's mail filtering program.");
        humanText.append("\r\n");
        humanText.append("The reason given was:");
        humanText.append("\r\n");
        humanText.append("\r\n");
        humanText.append(anAction.getMessage());

        String reporting_UA_name = null;
        try
        {
            reporting_UA_name = InetAddress.getLocalHost()
                    .getCanonicalHostName();
        }
        catch (UnknownHostException ex)
        {
            reporting_UA_name = "localhost";
        }

        String reporting_UA_product = context.getServerInfo();

        String[] originalRecipients = aMail.getMessage().getHeader(
                "Original-Recipient");
        String original_recipient = null;
        if (null != originalRecipients && originalRecipients.length > 0)
        {
            original_recipient = originalRecipients[0];
        }

        MailAddress soleRecipient = ActionUtils.getSoleRecipient(aMail);
        String final_recipient = soleRecipient.toString();

        String original_message_id = aMail.getMessage().getMessageID();

        DispositionModifier modifiers[] = {new ModifierError()};
        Disposition disposition = new Disposition(new ActionModeAutomatic(),
                new SendingModeAutomatic(), new TypeDeleted(), modifiers);

        MimeMultipart multiPart = MDNFactory.create(humanText.toString(),
                reporting_UA_name, reporting_UA_product, original_recipient,
                final_recipient, original_message_id, disposition);

        // Send the message
        MimeMessage reply = (MimeMessage) aMail.getMessage().reply(false);
        reply.setFrom(soleRecipient.toInternetAddress());
        reply.setContent(multiPart);
        reply.saveChanges();
        Address[] recipientAddresses = reply.getAllRecipients();
        if (null != recipientAddresses)
        {
            Collection<MailAddress> recipients = new ArrayList<>(recipientAddresses.length);
            for (Address recipientAddress : recipientAddresses) {
                recipients.add(new MailAddress(
                        (InternetAddress) recipientAddress));
            }
            context.post(null, recipients, reply);
        }
        else
        {
            LOGGER.info("Unable to send reject MDN. Could not determine the recipient.");
        }
    }

}
