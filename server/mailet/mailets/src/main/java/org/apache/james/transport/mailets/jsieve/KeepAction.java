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
import org.apache.jsieve.mail.ActionKeep;
import org.apache.mailet.Mail;

/**
 * Performs the filing of a mail into the inbox. 
 * <h4>Thread Safety</h4>
 * <p>An instance maybe safe accessed concurrently by multiple threads.</p>
 */
public class KeepAction extends FileIntoAction implements MailAction {
    
    private static final String INBOX = "INBOX";

    @Override
    public void execute(Action action, Mail mail, ActionContext context)
            throws MessagingException {
        if (action instanceof ActionKeep) {
            final ActionKeep actionKeep = (ActionKeep) action;
            execute(actionKeep, mail, context);
        }
    }

    /**
     * <p>
     * Executes the passed ActionKeep.
     * </p>
     * 
     * <p>
     * In this implementation, "keep" is equivalent to "fileinto" with a
     * destination of "INBOX".
     * </p>
     * 
     * @param anAction not null
     * @param aMail not null
     * @param context not null
     * @throws MessagingException
     */
    public void execute(ActionKeep anAction, Mail aMail, ActionContext context) throws MessagingException {
        final ActionFileInto action = new ActionFileInto(INBOX);
        execute(action, aMail, context);
    }
}
