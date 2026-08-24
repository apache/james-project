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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.net.imap.AuthenticatingIMAPClient;
import org.apache.commons.net.imap.IMAPClient;
import org.apache.commons.net.io.CRLFLineReader;
import org.apache.james.core.Username;
import org.assertj.core.api.Assertions;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.ExternalResource;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class TestIMAPClient extends ExternalResource implements Closeable, AfterEachCallback {
    private static final Pattern EXAMINE_EXISTS = Pattern.compile("^\\* (\\d+) EXISTS$");
    private static final int MESSAGE_NUMBER_MATCHING_GROUP = 1;
    public static final String INBOX = "INBOX";

    /**
     * commons-net announces and consumes IMAP literals in octets, but subtracts the
     * {@link String#length()} of the lines it has decoded to know when a literal is over. Its
     * streams therefore have to stay octet transparent - one char per octet, which is what its
     * own ISO-8859-1 default gives. Decoding the socket as UTF-8 makes every multi-byte
     * character count for one octet less than the server announced, so the client keeps reading
     * past the literal, swallows the tagged reply as if it were message content and then blocks
     * forever waiting for a completion line that has already gone by.
     *
     * UTF-8 is handled at {@link TestIMAPClient}'s own boundary instead: see
     * {@link TestIMAPClient#asOctets(String)} and {@link TestIMAPClient#asText(String)}.
     */
    public static class OctetIMAPClient extends AuthenticatingIMAPClient {
        @Override
        protected void _connectAction_() throws IOException {
            super._connectAction_();
            _reader = new CRLFLineReader(new InputStreamReader(_input_, StandardCharsets.ISO_8859_1));
            __writer = new BufferedWriter(new OutputStreamWriter(_output_, StandardCharsets.ISO_8859_1));
        }
    }

    /**
     * Turns text into the octets to put on the wire: one char per UTF-8 octet, as
     * {@link OctetIMAPClient} expects.
     */
    private static String asOctets(String text) {
        return new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    /**
     * Reverse of {@link #asOctets(String)}: reads back the octets commons-net collected as UTF-8
     * text.
     */
    private static String asText(String octets) {
        return new String(octets.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private final IMAPClient imapClient;

    @VisibleForTesting
    TestIMAPClient(OctetIMAPClient imapClient) {
        this.imapClient = imapClient;
    }

    public TestIMAPClient() {
        this(new OctetIMAPClient());
    }

    public TestIMAPClient(IMAPClient imapClient) {
        this.imapClient = imapClient;
    }

    private String replyString() {
        return asText(imapClient.getReplyString());
    }

    private List<String> replyStrings() {
        return Stream.of(imapClient.getReplyStrings())
            .map(TestIMAPClient::asText)
            .collect(ImmutableList.toImmutableList());
    }

    public TestIMAPClient connect(String host, int port) throws IOException {
        imapClient.connect(host, port);
        return this;
    }

    public String capability() throws IOException {
        imapClient.capability();
        return replyString();
    }

    public TestIMAPClient disconnect() throws IOException {
        imapClient.disconnect();
        return this;
    }

    public TestIMAPClient login(String user, String password) throws IOException {
        final boolean login = imapClient.login(asOctets(user), asOctets(password));
        if (!login) {
            throw new IOException("Login failed");
        }
        return this;
    }

    public TestIMAPClient authenticatePlain(String user, String password) throws Exception {
        Preconditions.checkArgument(imapClient instanceof AuthenticatingIMAPClient);
        final boolean authenticatePlain = ((AuthenticatingIMAPClient) imapClient).authenticate(AuthenticatingIMAPClient.AUTH_METHOD.PLAIN, user, password);
        if (!authenticatePlain) {
            throw new Exception("Login failed");
        }
        return this;
    }

    public TestIMAPClient rawLogin(String user, String password) throws IOException {
        imapClient.sendCommand(asOctets("LOGIN " + user + " " + password));

        if (replyString().contains("NO LOGIN failed.")) {
            throw new IOException("Login failed");
        }
        return this;
    }

    public List<String> list() throws IOException {
        imapClient.list("", "*");
        return replyStrings();
    }

    public TestIMAPClient login(Username user, String password) throws IOException {
        return login(user.asString(), password);
    }

    public TestIMAPClient select(String mailbox) throws IOException {
        imapClient.select(asOctets(mailbox));
        return this;
    }

    public TestIMAPClient create(String mailbox) throws IOException {
        if (!imapClient.create(asOctets(mailbox))) {
            throw new RuntimeException(replyString());
        }
        return this;
    }

    public TestIMAPClient append(String mailboxName, String message) throws IOException {
        String noFlags = null;
        String noDateTime = null;
        if (!imapClient.append(asOctets(mailboxName), noFlags, noDateTime, asOctets(message))) {
            throw new RuntimeException(replyString());
        }
        return this;
    }

    public TestIMAPClient delete(String mailbox) throws IOException {
        imapClient.delete(asOctets(mailbox));
        return this;
    }

    public boolean hasAMessage() throws IOException {
        imapClient.fetch("1", "UID");
        return replyString()
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
            .splitToStream(replyString())
            .filter(s -> s.startsWith("*"))
            .count();
    }

    public TestIMAPClient awaitNoMessage(ConditionFactory conditionFactory) {
        conditionFactory.until(this::userDoesNotReceiveMessage);
        return this;
    }

    public boolean hasAMessageWithFlags(String flags) throws IOException {
        imapClient.fetch("1:1", "ALL");
        return isCompletedWithFlags(flags, replyString());
    }

    @VisibleForTesting
    boolean isCompletedWithFlags(String flags, String replyString) {
        return replyString.contains("OK FETCH completed")
            && Splitter.on(" ")
                .splitToStream(flags)
                .allMatch(replyString::contains);
    }

    public boolean userGetNotifiedForNewMessagesWhenSelectingMailbox(int numOfNewMessage) {
        return replyString().contains("OK [UNSEEN " + numOfNewMessage + "]");
    }

    public boolean userDoesNotReceiveMessage() throws IOException {
        imapClient.fetch("1:1", "ALL");
        return replyString()
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
        return replyString();
    }

    public String copyAllMessagesInMailboxTo(String mailboxName) throws IOException {
        imapClient.copy("1:*", asOctets(mailboxName));
        return replyString();
    }

    public String readFirstMessageInMailbox(String parameters) throws IOException {
        imapClient.fetch("1:1", parameters);
        return replyString();
    }

    public boolean userGetNotifiedForNewMessages(int numberOfMessages) throws IOException {
        imapClient.noop();

        List<String> parts = Splitter.on('\n')
            .trimResults()
            .omitEmptyStrings()
            .splitToList(replyString());
        return parts.size() == 3
            && parts.get(2).contains("OK NOOP completed.")
            && parts.contains("* " + numberOfMessages + " EXISTS")
            && parts.contains("* " + numberOfMessages + " RECENT");
    }

    public boolean userGetNotifiedForDeletion(int msn) throws IOException {
        imapClient.noop();

        List<String> parts = Splitter.on('\n')
            .trimResults()
            .omitEmptyStrings()
            .splitToList(replyString());

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
        imapClient.copy("1", asOctets(destMailbox));
    }

    public void moveFirstMessage(String destMailbox) throws IOException {
        imapClient.sendCommand(asOctets("MOVE 1 " + destMailbox));
    }

    public void expunge() throws IOException {
        imapClient.expunge();
    }

    public String getQuotaRoot(String mailbox) throws IOException {
        imapClient.sendCommand(asOctets("GETQUOTAROOT " + mailbox));
        return replyString();
    }

    public String sendCommand(String command) throws IOException {
        imapClient.sendCommand(asOctets(command));
        return replyString();
    }

    public long getMessageCount(String mailboxName) throws IOException {
        imapClient.examine(asOctets(mailboxName));
        return replyStrings().stream()
            .map(EXAMINE_EXISTS::matcher)
            .filter(Matcher::matches)
            .map(m -> m.group(MESSAGE_NUMBER_MATCHING_GROUP))
            .mapToLong(Long::valueOf)
            .sum();
    }
}
