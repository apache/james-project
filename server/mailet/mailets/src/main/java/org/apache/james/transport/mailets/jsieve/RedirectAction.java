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

import java.util.ArrayList;
import java.util.Collection;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.logging.Log;
import org.apache.jsieve.mail.Action;
import org.apache.jsieve.mail.ActionRedirect;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

/**
 * Performs the redirection of a mail. 
 * <h4>Thread Safety</h4>
 * <p>An instance maybe safe accessed concurrently by multiple threads.</p>
 */
public class RedirectAction implements MailAction {

    public void execute(Action action, Mail mail, ActionContext context)
            throws MessagingException {
        if (action instanceof ActionRedirect) {
            final ActionRedirect actionRedirect = (ActionRedirect) action;
            execute(actionRedirect, mail, context);
        }

    }

    /**
     * Method execute executes the passed ActionRedirect.
     * 
     * @param anAction not nul
     * @param aMail not null
     * @param context not null
     * @throws MessagingException
     */
    public void execute(ActionRedirect anAction, Mail aMail, ActionContext context) throws MessagingException
    {
        ActionUtils.detectAndHandleLocalLooping(aMail, context, "redirect");
        Collection<MailAddress> recipients = new ArrayList<>(1);
        recipients.add(new MailAddress(new InternetAddress(anAction.getAddress())));
        MailAddress sender = aMail.getSender();
        context.post(sender, recipients, aMail.getMessage());
        Log log = context.getLog();
        if (log.isDebugEnabled()) {
            log.debug("Redirected Message ID: "
                + aMail.getMessage().getMessageID() + " to \""
                + anAction.getAddress() + "\"");
        }
        DiscardAction.removeRecipient(aMail, context);
    }
}
