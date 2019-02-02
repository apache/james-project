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

package org.apache.james.mpt.imapmailbox.cyrus.host;

import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.imapmailbox.MailboxMessageAppender;
import org.apache.james.mpt.protocol.ProtocolSession;

import com.google.inject.Inject;

public class MailboxMessageAppenderOnCyrusHost implements MailboxMessageAppender {
    private static final String LOCATION = "cyrus.MailboxMessageProviderOnCyrusHost";

    private final CyrusHostSystem hostSystem;

    @Inject
    private MailboxMessageAppenderOnCyrusHost(CyrusHostSystem hostSystem) {
        this.hostSystem = hostSystem;
    }

    @Override
    public void fillMailbox(MailboxPath mailboxPath) {
        String mailboxName = hostSystem.createMailboxStringFromMailboxPath(mailboxPath);
        ProtocolSession protocolSession = hostSystem.logAndGetAdminProtocolSession(new ProtocolSession());
        protocolSession.cl(String.format("a001 SETACL %s %s %s", mailboxName, "cyrus", "lrswipkxtecda"));
        protocolSession.sl("a001 OK .*", LOCATION);
        appendMessage(protocolSession, "a002", mailboxName, "");
        appendMessage(protocolSession, "a003", mailboxName, "\\Seen");
        appendMessage(protocolSession, "a004", mailboxName, "\\Flagged");
        appendMessage(protocolSession, "a005", mailboxName, "\\Deleted");
        hostSystem.executeProtocolSession(hostSystem.logoutAndGetProtocolSession(protocolSession));
    }

    private void appendMessage(ProtocolSession protocolSession, String commandId, String mailbox,  String flagString) {
        protocolSession.cl(String.format("%s APPEND %s (%s) {310}", commandId, mailbox, flagString));
        protocolSession.sl("\\+ go ahead", LOCATION);
        protocolSession.cl("Date: Mon, 7 Feb 1994 21:52:25 -0800 (PST)");
        protocolSession.cl("From: Fred Foobar <foobar@Blurdybloop.COM>");
        protocolSession.cl("Subject: afternoon meeting 2");
        protocolSession.cl("To: mooch@owatagu.siam.edu");
        protocolSession.cl("Message-Id: <B27397-0100000@Blurdybloop.COM>");
        protocolSession.cl("MIME-Version: 1.0");
        protocolSession.cl("Content-Type: TEXT/PLAIN; CHARSET=US-ASCII");
        protocolSession.cl("");
        protocolSession.cl("Hello Joe, could we change that to 4:00pm tomorrow?");
        protocolSession.cl("");
        protocolSession.sl(String.format("%s OK.*", commandId), LOCATION);
    }

}
