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

package org.apache.james.mock.smtp.server;

import java.io.IOException;
import java.util.Locale;

import org.apache.james.mock.smtp.server.model.Mail;
import org.subethamail.smtp.DropConnectionException;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.internal.server.BaseCommand;
import org.subethamail.smtp.internal.util.EmailUtils;
import org.subethamail.smtp.server.Session;

public class ExtendedRcptToCommand extends BaseCommand {
    public ExtendedRcptToCommand() {
        super("RCPT", "Specifies the recipient. Can be used any number of times.", "TO: <recipient> [ <parameters> ]");
    }

    public void execute(String commandString, Session sess) throws IOException, DropConnectionException {
        if (!sess.isMailTransactionInProgress()) {
            sess.sendResponse("503 Error: need MAIL command");
        } else if (sess.getServer().getMaxRecipients() >= 0 && sess.getRecipientCount() >= sess.getServer().getMaxRecipients()) {
            sess.sendResponse("452 Error: too many recipients");
        } else {
            String args = this.getArgPredicate(commandString);
            if (!args.toUpperCase(Locale.ENGLISH).startsWith("TO:")) {
                sess.sendResponse("501 Syntax: RCPT TO: <address>  Error in parameters: \"" + args + "\"");
            } else {
                String recipientAddress = EmailUtils.extractEmailAddress(args, 3);

                try {
                    MockMessageHandler messageHandler = (MockMessageHandler) sess.getMessageHandler();
                    messageHandler.recipient(recipientAddress, Mail.Parameter.fromArgLine(args));
                    sess.addRecipient(recipientAddress);
                    sess.sendResponse("250 Ok");
                } catch (DropConnectionException dropConnectionException) {
                    throw dropConnectionException;
                } catch (RejectException rejectException) {
                    sess.sendResponse(rejectException.getErrorResponse());
                }

            }
        }
    }
}