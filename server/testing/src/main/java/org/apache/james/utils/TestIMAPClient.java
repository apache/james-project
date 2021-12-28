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

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.net.imap.AuthenticatingIMAPClient;
import org.apache.commons.net.io.CRLFLineReader;
import org.apache.james.core.Username;
import org.assertj.core.api.Assertions;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.ExternalResource;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class TestIMAPClient extends ExternalResource implements Closeable, AfterEachCallback {
    private static final Pattern EXAMINE_EXISTS = Pattern.compile("^\\* (\\d+) EXISTS$");
    private static final int MESSAGE_NUMBER_MATCHING_GROUP = 1;
    public static final String INBOX = "INBOX";

    public static class Utf8IMAPSClient extends AuthenticatingIMAPClient {
        private static final String UTF8_ENCODING = "UTF-8";

        @Override
        protected void _connectAction_() throws IOException {
            super._connectAction_();
            _reader = new CRLFLineReader(new InputStreamReader(_input_, UTF8_ENCODING));
            __writer = new BufferedWriter(new OutputStreamWriter(_output_, UTF8_ENCODING));
        }
    }

    private final Utf8IMAPSClient imapClient;

    @VisibleForTesting
    TestIMAPClient(Utf8IMAPSClient imapClient) {
        this.imapClient = imapClient;
    }

    public TestIMAPClient() {
        this(new Utf8IMAPSClient());
    }

    public TestIMAPClient connect(String host, int port) throws IOException {
        imapClient.connect(host, port);
        return this;
    }

    public String capability() throws IOException {
        imapClient.capability();
        return imapClient.getReplyString();
    }

    public TestIMAPClient disconnect() throws IOException {
        imapClient.disconnect();
        return this;
    }

    public TestIMAPClient login(String user, String password) throws IOException {
        final boolean login = imapClient.login(user, password);
        if (!login) {
            throw new IOException("Login failed");
        }
        return this;
    }

    public TestIMAPClient authenticatePlain(String user, String password) throws Exception {
        final boolean authenticatePlain = imapClient.authenticate(AuthenticatingIMAPClient.AUTH_METHOD.PLAIN, user, password);
        if (!authenticatePlain) {
            throw new Exception("Login failed");
        }
        return this;
    }

    public TestIMAPClient rawLogin(String user, String password) throws IOException {
        imapClient.sendCommand("LOGIN " + user + " " + password);

        if (imapClient.getReplyString().contains("NO LOGIN failed.")) {
            throw new IOException("Login failed");
        }
        return this;
    }

    public List<String> list() throws IOException {
        imapClient.list("", "*");
        return ImmutableList.copyOf(imapClient.getReplyStrings());
    }

    public TestIMAPClient login(Username user, String password) throws IOException {
        return login(user.asString(), password);
    }

    public TestIMAPClient select(String mailbox) throws IOException {
        imapClient.select(mailbox);
        return this;
    }

    public TestIMAPClient create(String mailbox) throws IOException {
        if (!imapClient.create(mailbox)) {
            throw new RuntimeException(imapClient.getReplyString());
        }
        return this;
    }

    public TestIMAPClient append(String mailboxName, String message) throws IOException {
        String noFlags = null;
        String noDateTime = null;
        if (!imapClient.append(mailboxName, noFlags, noDateTime, message)) {
            throw new RuntimeException(imapClient.getReplyString());
        }
        return this;
    }

    public TestIMAPClient delete(String mailbox) throws IOException {
        imapClient.delete(mailbox);
        return this;
    }

    public boolean hasAMessage() throws IOException {
        imapClient.fetch("1", "UID");
        return imapClient.getReplyString()
            .contains("OK FETCH completed");
    }

    public TestIMAPClient awaitMessage(ConditionFactory conditionFactory) {
        conditionFactory.until(this::hasAMessage);
        return this;
    }

    public TestIMAPClient awaitMessageCount(ConditionFactory conditionFactory, int messageCount) {
        conditionFactory.untilAsserted(() -> {
            imapClient.fetch("1:*", "UID");
            Assertions.assertThat(countFetchedEntries()).isEqualTo(messageCount);
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

    public TestIMAPClient awaitNoMessage(ConditionFactory conditionFactory) {
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
                .allMatch(replyString::contains);
    }

    public boolean userGetNotifiedForNewMessagesWhenSelectingMailbox(int numOfNewMessage) {
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
