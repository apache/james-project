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

import jakarta.mail.MessagingException;

import org.apache.jsieve.mail.Action;
import org.apache.jsieve.mail.ActionFileInto;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs the filing of a mail into a specified destination. 
 * <h4>Thread Safety</h4>
 * <p>An instance maybe safe accessed concurrently by multiple threads.</p>
 */
public class FileIntoAction implements MailAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileIntoAction.class);

    private static final char HIERARCHY_DELIMITER = '.';

    @Override
    public void execute(Action action, Mail mail, ActionContext context) throws MessagingException {
        if (action instanceof ActionFileInto) {
            final ActionFileInto fileIntoAction = (ActionFileInto) action;
            execute(fileIntoAction, mail, context);
        }
    }

    /**
     * <p>
     * Executes the passed ActionFileInto.
     * </p>
     * 
     * <p>
     * This implementation accepts any destination with the root of <code>INBOX</code>.
     * </p>
     * 
     * <p>
     * As the current POP3 server does not support sub-folders, the mail is
     * stored in the INBOX for the recipient of the mail and the full intended
     * destination added as a prefix to the message's subject.
     * </p>
     * 
     * <p>
     * When IMAP support is added to James, it will be possible to support
     * sub-folders of <code>INBOX</code> fully.
     * </p>
     *
     * @param context not null
     */
    public void execute(ActionFileInto anAction, Mail aMail, final ActionContext context) throws MessagingException {
        String destinationMailbox = getDestinationMailbox(anAction);
        String mailbox = destinationMailbox.replace(HIERARCHY_DELIMITER, '/');
        String url = "mailbox://" + context.getRecipient().asString() + mailbox;

        context.post(url, aMail);
        LOGGER.debug("Filed Message ID: {} into destination: \"{}\"", aMail.getMessage().getMessageID(), destinationMailbox);
    }

    private String getDestinationMailbox(ActionFileInto anAction) {
        if (!(anAction.getDestination().length() > 0 && anAction.getDestination().charAt(0) == HIERARCHY_DELIMITER)) {
            return HIERARCHY_DELIMITER + anAction.getDestination();
        }
        return anAction.getDestination();
    }
}
