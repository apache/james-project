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
import java.util.List;

import org.subethamail.smtp.internal.server.BaseCommand;
import org.subethamail.smtp.internal.util.TextUtils;
import org.subethamail.smtp.server.Session;

public class ExtendedEhloCommand extends BaseCommand {
    private final SMTPBehaviorRepository behaviorRepository;

    public ExtendedEhloCommand(SMTPBehaviorRepository behaviorRepository) {
        super("EHLO", "Introduce yourself.", "<hostname>");
        this.behaviorRepository = behaviorRepository;
    }

    public void execute(String commandString, Session sess) throws IOException {
        String[] args = BaseCommand.getArgs(commandString);
        if (args.length < 2) {
            sess.sendResponse("501 Syntax: EHLO hostname");
        } else {
            sess.resetMailTransaction();
            sess.setHelo(args[1]);
            StringBuilder response = new StringBuilder();
            response.append("250-");
            response.append(sess.getServer().getHostName());
            response.append("\r\n250-8BITMIME");
            int maxSize = sess.getServer().getMaxMessageSize();
            if (maxSize > 0) {
                response.append("\r\n250-SIZE ");
                response.append(maxSize);
            }

            if (sess.getServer().getEnableTLS() && !sess.getServer().getHideTLS()) {
                response.append("\r\n250-STARTTLS");
            }

            sess.getServer().getAuthenticationHandlerFactory().ifPresent(authFact -> {
                List<String> supportedMechanisms = authFact.getAuthenticationMechanisms();
                if (!supportedMechanisms.isEmpty()) {
                    response.append("\r\n250-AUTH ");
                    response.append(TextUtils.joinTogether(supportedMechanisms, " "));
                }
            });

            behaviorRepository.getSMTPExtensions()
                .forEach(smtpExtension -> response.append("\r\n250-").append(smtpExtension.asString()));

            response.append("\r\n250 Ok");
            sess.sendResponse(response.toString());
        }
    }
}
