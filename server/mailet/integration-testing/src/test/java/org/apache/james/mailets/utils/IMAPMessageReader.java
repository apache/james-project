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

package org.apache.james.mailets.utils;

import java.io.Closeable;
import java.io.IOException;

import org.apache.commons.net.imap.IMAPClient;

public class IMAPMessageReader implements Closeable {

    private final IMAPClient imapClient;

    public IMAPMessageReader(String host, int port) throws IOException {
        imapClient = new IMAPClient();
        imapClient.connect(host, port);
    }

    public boolean userReceivedMessage(String user, String password) throws IOException {
        return userReceivedMessageInMailbox(user, password, "INBOX");
    }

    public boolean userReceivedMessageInMailbox(String user, String password, String mailbox) throws IOException {
        imapClient.login(user, password);
        imapClient.select(mailbox);
        imapClient.fetch("1:1", "ALL");
        return imapClient.getReplyString()
            .contains("OK FETCH completed");
    }

    public boolean userDoesNotReceiveMessage(String user, String password) throws IOException {
        imapClient.login(user, password);
        imapClient.select("INBOX");
        imapClient.fetch("1:1", "ALL");
        return imapClient.getReplyString()
             .contains("BAD FETCH failed. Invalid messageset");
    }

    public String readFirstMessageInInbox(String user, String password) throws IOException {
        return readFirstMessageInInbox(user, password, "(BODY[])");
    }

    public String readFirstMessageHeadersInInbox(String user, String password) throws IOException {
        return readFirstMessageInInbox(user, password, "(RFC822.HEADER)");
    }

    private String readFirstMessageInInbox(String user, String password, String parameters) throws IOException {
        imapClient.login(user, password);
        imapClient.select("INBOX");
        imapClient.fetch("1:1", parameters);
        return imapClient.getReplyString();
    }

    @Override
    public void close() throws IOException {
        imapClient.close();
    }
}
