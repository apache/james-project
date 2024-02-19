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

import com.google.common.base.CharMatcher;

public class ExtendedMailFromCommand extends BaseCommand {

    public static final CharMatcher NUMBER_MATCHER = CharMatcher.inRange('0', '9');

    public ExtendedMailFromCommand() {
        super("MAIL", "Specifies the sender.", "FROM: <sender> [ <parameters> ]");
    }

    public void execute(String commandString, Session sess) throws IOException, DropConnectionException {
        if (sess.isMailTransactionInProgress()) {
            sess.sendResponse("503 Sender already specified.");
        } else {
            if (commandString.trim().equals("MAIL FROM:")) {
                sess.sendResponse("501 Syntax: MAIL FROM: <address>");
                return;
            }

            String args = this.getArgPredicate(commandString);
            if (!args.toUpperCase(Locale.ENGLISH).startsWith("FROM:")) {
                sess.sendResponse("501 Syntax: MAIL FROM: <address>  Error in parameters: \"" + this.getArgPredicate(commandString) + "\"");
                return;
            }

            String emailAddress = EmailUtils.extractEmailAddress(args, 5);
            if (EmailUtils.isValidEmailAddress(emailAddress, true)) {
                int size = 0;
                String largs = args.toLowerCase(Locale.ENGLISH);
                int sizec = largs.indexOf(" size=");
                if (sizec > -1) {
                    String ssize = largs.substring(sizec + 6).trim();
                    if (ssize.length() > 0 && NUMBER_MATCHER.matchesAllOf(ssize)) {
                        size = Integer.parseInt(ssize);
                    }
                }

                if (size > sess.getServer().getMaxMessageSize()) {
                    sess.sendResponse("552 5.3.4 Message size exceeds fixed limit");
                    return;
                }

                try {
                    sess.startMailTransaction();
                    MockMessageHandler messageHandler = (MockMessageHandler) sess.getMessageHandler();
                    messageHandler.from(emailAddress, Mail.Parameter.fromArgLine(args));
                    sess.setDeclaredMessageSize(size);
                    sess.sendResponse("250 Ok");
                } catch (DropConnectionException dropConnectionException) {
                    throw dropConnectionException;
                } catch (RejectException rejectException) {
                    sess.sendResponse(rejectException.getErrorResponse());
                }
            } else {
                sess.sendResponse("553 <" + emailAddress + "> Invalid email address.");
            }
        }
    }
}
