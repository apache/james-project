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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.net.imap.IMAPClient;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.ExternalResource;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

public class IMAPMessageReader extends ExternalResource implements Closeable, AfterEachCallback {

    private static final Pattern EXAMINE_EXISTS = Pattern.compile("^\\* (\\d+) EXISTS$");
    private static final int MESSAGE_NUMBER_MATCHING_GROUP = 1;
    public static final String INBOX = "INBOX";

    private final IMAPClient imapClient;

    @VisibleForTesting
    IMAPMessageReader(IMAPClient imapClient) {
        this.imapClient = imapClient;
    }

    public IMAPMessageReader() {
        this(new IMAPClient());
    }

    public IMAPMessageReader connect(String host, int port) throws IOException {
        imapClient.connect(host, port);
        return this;
    }

    public IMAPMessageReader disconnect() throws IOException {
        imapClient.disconnect();
        return this;
    }

    public IMAPMessageReader login(String user, String password) throws IOException {
        imapClient.login(user, password);
        return this;
    }

    public IMAPMessageReader select(String mailbox) throws IOException {
        imapClient.select(mailbox);
        return this;
    }

    public IMAPMessageReader delete(String mailbox) throws IOException {
        imapClient.delete(mailbox);
        return this;
    }

    public boolean hasAMessage() throws IOException {
        imapClient.fetch("1:1", "ALL");
        return imapClient.getReplyString()
            .contains("OK FETCH completed");
    }

    public IMAPMessageReader awaitMessage(ConditionFactory conditionFactory) throws IOException {
        conditionFactory.until(this::hasAMessage);
        return this;
    }

    public IMAPMessageReader awaitMessageCount(ConditionFactory conditionFactory, int messageCount) {
        conditionFactory.until(() -> {
            try {
                imapClient.fetch("1:*", "ALL");
                return countFetchedEntries() == messageCount;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return this;
    }

    private long countFetchedEntries() {
        return Splitter.on("\n")
            .trimResults()
            .splitToList(imapClient.getReplyString())
            .stream()
            .filter(s -> s.startsWith("*"))
            .count();
    }

    public IMAPMessageReader awaitNoMessage(ConditionFactory conditionFactory) throws IOException {
        conditionFactory.until(this::userDoesNotReceiveMessage);
        return this;
    }

    public boolean hasAMessageWithFlags(String flags) throws IOException {
        imapClient.fetch("1:1", "ALL");
        String replyString = imapClient.getReplyString();
        return isCompletedWithFlags(flags, replyString);
    }

    @VisibleForTesting
    boolean isCompletedWithFlags(String flags, String replyString) {
        return replyString.contains("OK FETCH completed")
            && Splitter.on(" ")
                .splitToList(flags)
                .stream()
                .allMatch(s -> replyString.contains(s));
    }

    public boolean userGetNotifiedForNewMessagesWhenSelectingMailbox(int numOfNewMessage) throws IOException {
        return imapClient.getReplyString().contains("OK [UNSEEN " + numOfNewMessage + "]");
    }

    public boolean userDoesNotReceiveMessage() throws IOException {
        imapClient.fetch("1:1", "ALL");
        return imapClient.getReplyString()
             .contains("BAD FETCH failed. Invalid messageset");
    }

    public String readFirstMessage() throws IOException {
        return readFirstMessageInMailbox("(BODY[])");
    }

    public String readFirstMessageHeaders() throws IOException {
        return readFirstMessageInMailbox("(RFC822.HEADER)");
    }

    public String setFlagsForAllMessagesInMailbox(String flag) throws IOException {
        imapClient.store("1:*", "+FLAGS", flag);
        return imapClient.getReplyString();
    }

    public String copyAllMessagesInMailboxTo(String mailboxName) throws IOException {
        imapClient.copy("1:*", mailboxName);
        return imapClient.getReplyString();
    }

    private String readFirstMessageInMailbox(String parameters) throws IOException {
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
        if (imapClient.isConnected()) {
            imapClient.disconnect();
        }
    }

    @Override
    protected void after() {
        try {
            this.close();
        } catch (IOException e) {
            //ignore exception during close
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        after();
    }

    public void copyFirstMessage(String destMailbox) throws IOException {
        imapClient.copy("1", destMailbox);
    }

    public void moveFirstMessage(String destMailbox) throws IOException {
        imapClient.sendCommand("MOVE 1 " + destMailbox);
    }

    public void expunge() throws IOException {
        imapClient.expunge();
    }

    public String getQuotaRoot(String mailbox) throws IOException {
        imapClient.sendCommand("GETQUOTAROOT " + mailbox);
        return imapClient.getReplyString();
    }

    public long getMessageCount(String mailboxName) throws IOException {
        imapClient.examine(mailboxName);
        return Stream.of(imapClient.getReplyStrings())
            .map(EXAMINE_EXISTS::matcher)
            .filter(Matcher::matches)
            .map(m -> m.group(MESSAGE_NUMBER_MATCHING_GROUP))
            .mapToLong(Long::valueOf)
            .sum();
    }
}
