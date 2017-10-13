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

package org.apache.james.utils;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import org.apache.commons.net.imap.IMAPClient;

import com.google.common.base.Splitter;

public class IMAPMessageReader implements Closeable {

    private static final String INBOX = "INBOX";

    private final IMAPClient imapClient;

    public IMAPMessageReader(String host, int port) throws IOException {
        imapClient = new IMAPClient();
        imapClient.connect(host, port);
    }

    public void connectAndSelect(String user, String password, String mailbox) throws IOException{
        imapClient.login(user, password);
        imapClient.select(mailbox);
    }

    public boolean countReceivedMessage(String user, String password, int numberOfMessages) throws IOException {
        return countReceivedMessageInMailbox(user, password, INBOX, numberOfMessages);
    }

    public boolean countReceivedMessageInMailbox(String user, String password, String mailbox, int numberOfMessages) throws IOException {
        connectAndSelect(user, password, mailbox);

        return imapClient.getReplyString()
            .contains(numberOfMessages + " EXISTS");
    }

    public boolean userReceivedMessage(String user, String password) throws IOException {
        return userReceivedMessageInMailbox(user, password, INBOX);
    }

    public boolean userReceivedMessageInMailbox(String user, String password, String mailbox) throws IOException {
        connectAndSelect(user, password, mailbox);
        imapClient.fetch("1:1", "ALL");
        return imapClient.getReplyString()
            .contains("OK FETCH completed");
    }

    public boolean userGetNotifiedForNewMessagesWhenSelectingMailbox(String user, String password, int numOfNewMessage, String mailboxName) throws IOException {
        connectAndSelect(user, password, mailboxName);

        return imapClient.getReplyString().contains("OK [UNSEEN " + numOfNewMessage +"]");
    }

    public boolean userDoesNotReceiveMessage(String user, String password) throws IOException {
        return userDoesNotReceiveMessageInMailbox(user, password, INBOX);
    }

    public boolean userDoesNotReceiveMessageInMailbox(String user, String password, String mailboxName) throws IOException {
        connectAndSelect(user, password, mailboxName);
        imapClient.fetch("1:1", "ALL");
        return imapClient.getReplyString()
             .contains("BAD FETCH failed. Invalid messageset");
    }

    public String readFirstMessageInInbox(String user, String password) throws IOException {

        return readFirstMessageInMailbox(user, password, "(BODY[])", INBOX);
    }

    public String readFirstMessageHeadersInMailbox(String user, String password, String mailboxName) throws IOException {
        return readFirstMessageInMailbox(user, password, "(RFC822.HEADER)", mailboxName);
    }

    public String readFirstMessageHeadersInInbox(String user, String password) throws IOException {
        return readFirstMessageInMailbox(user, password, "(RFC822.HEADER)", INBOX);
    }

    public String setFlagsForAllMessagesInMailbox(String flag) throws IOException {
        imapClient.store("1:*", "+FLAGS", flag);
        return imapClient.getReplyString();
    }

    private String readFirstMessageInMailbox(String user, String password, String parameters, String mailboxName) throws IOException {
        imapClient.login(user, password);
        imapClient.select(mailboxName);
        imapClient.fetch("1:1", parameters);
        return imapClient.getReplyString();
    }

    public boolean userGetNotifiedForNewMessages(int numberOfMessages) throws IOException {
        imapClient.noop();

        String replyString = imapClient.getReplyString();
        List<String> parts = Splitter.on('\n')
            .trimResults()
            .omitEmptyStrings()
            .splitToList(replyString);
        return parts.size() == 3
            && parts.get(2).contains("OK NOOP completed.")
            && parts.contains("* " + numberOfMessages + " EXISTS")
            && parts.contains("* " + numberOfMessages + " RECENT");
    }

    public boolean userGetNotifiedForDeletion(int msn) throws IOException {
        imapClient.noop();

        String replyString = imapClient.getReplyString();
        List<String> parts = Splitter.on('\n')
            .trimResults()
            .omitEmptyStrings()
            .splitToList(replyString);

        return parts.size() == 2
            && parts.get(1).contains("OK NOOP completed.")
            && parts.contains("* " + msn + " EXPUNGE");
    }

    @Override
    public void close() throws IOException {
        imapClient.close();
    }

    public void copyFirstMessage(String destMailbox) throws IOException {
        imapClient.copy("1", destMailbox);
    }
}
