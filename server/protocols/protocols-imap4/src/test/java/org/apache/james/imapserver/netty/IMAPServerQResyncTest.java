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

package org.apache.james.imapserver.netty;

import static jakarta.mail.Flags.Flag.ANSWERED;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.mailbox.MessageManager.FlagsUpdateMode.REPLACE;
import static org.apache.james.mailbox.MessageManager.MailboxMetaData.FetchGroup.NO_COUNT;
import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UidValidity;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;


@SuppressWarnings("checkstyle:membername")
class IMAPServerQResyncTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private MailboxSession mailboxSession;
    private MessageManager inbox;
    private SocketChannel clientConnection;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        int port = imapServer.getListenAddresses().get(0).getPort();
        mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);
        inbox = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession);

        clientConnection = SocketChannel.open();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
        setUpTestingData();

        clientConnection = SocketChannel.open();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
        readBytes(clientConnection);
    }

    @AfterEach
    void tearDown() throws Exception {
        clientConnection.close();
        imapServer.destroy();
    }

    @Test
    void selectShouldNotAnswerEmptyVanishedResponses() throws Exception {
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d 88 2:37 (1,10,28 2,11,29)))\r\n", uidValidity.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("VANISHED"))
            .isEmpty();
    }

    @Test
    void selectShouldReturnDeletedMessagesWhenNoSubsequentModification() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .delete(ImmutableList.of(MessageUid.of(10)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 2:37 (1,10,28 2,11,29)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10"))
            .hasSize(1);
    }

    @Test
    void selectShouldReturnDeletedMessagesWhenSequenceMatchDataAndNoKnownUid() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .delete(ImmutableList.of(MessageUid.of(10)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d (1,10,28 2,11,29)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10"))
            .hasSize(1);
    }

    @Test
    void selectShouldReturnDeletedMessagesWhenKnownUidSet() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
            MessageUid.of(25), MessageUid.of(26),
            MessageUid.of(32)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 5:11,28:36 (1,10,28 2,11,29)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10:11,32"))
            .hasSize(1);
    }

    @Test
    void knownUidSetShouldBeUsedToRestrictVanishedResponses() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
            MessageUid.of(25), MessageUid.of(26),
            MessageUid.of(32)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        // MSN 1 => UID 2 MATCH
        // MSN 13 => UID 17 MATCH
        // MSN 28 => UID 30 MISMATCH stored value is 34
        // Thus we know we can skip resynchronisation for UIDs up to 17
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 1:37 (1,13,28 2,17,30)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("* VANISHED (EARLIER) 25:26,32"))
            .hasSize(1);
    }

    @Test
    void knownUidSetShouldTolerateDeletedMessages() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
            MessageUid.of(25), MessageUid.of(26),
            MessageUid.of(32)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        // MSN 1 => UID 2 MATCH
        // MSN 13 => UID 17 MATCH
        // MSN 28 => UID 32 MISMATCH stored value is 34 (32 not being stored)
        // Thus we know we can skip resynchronisation for UIDs up to 17
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 1:37 (1,13,28 2,17,32)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("* VANISHED (EARLIER) 25:26,32"))
            .hasSize(1);
    }

    @Test
    void selectShouldReturnDeletedMessagesWhenNoSequenceMatchData() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .delete(ImmutableList.of(MessageUid.of(10)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 2:37))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10"))
            .hasSize(1);
    }

    @Test
    void selectShouldReturnDeletedMessagesWhenNoSequenceMatchDataAndKnownUid() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .delete(ImmutableList.of(MessageUid.of(10)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10"))
            .hasSize(1);
    }

    @Test
    void selectShouldCombineIntoRangesWhenRespondingVanished() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
            MessageUid.of(25), MessageUid.of(26),
            MessageUid.of(32)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 2:37 (1,10,28 2,11,29)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10:12,25:26,32"))
            .hasSize(1);
    }

    @Test
    void enableQRESYNCShouldReturnHighestModseq() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();

        UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
            .getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMailboxEntity().getUidValidity();

        inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
            MessageUid.of(25), MessageUid.of(26),
            MessageUid.of(32)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap("I00104 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed."));

        clientConnection.write(ByteBuffer.wrap(("a2 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));

        List<String> replies = readStringUntil(clientConnection, s -> s.contains("a2 OK ENABLE completed."));
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(replies)
                .filteredOn(s -> s.contains("* OK [HIGHESTMODSEQ 41] Highest"))
                .hasSize(1);
            softly.assertThat(replies)
                .filteredOn(s -> s.contains("* ENABLED QRESYNC"))
                .hasSize(1);
        });
    }

    private void setUpTestingData() {
        IntStream.range(0, 37)
            .forEach(Throwing.intConsumer(i -> inbox.appendMessage(MessageManager.AppendCommand.builder()
                .build("MIME-Version: 1.0\r\n\r\nCONTENT\r\n"), mailboxSession)));
    }

    @Test
    void fetchShouldAllowChangedSinceModifier() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 1:37 (FLAGS) (CHANGEDSINCE %d)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK FETCH completed.")))
            .filteredOn(s -> s.contains("* 10 FETCH (MODSEQ (39) FLAGS (\\Answered \\Recent) UID 10)"))
            .hasSize(1);
    }

    @Test
    void fetchShouldNotReturnChangedItemsOutOfRange() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 12:37 (FLAGS) (CHANGEDSINCE %d)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK FETCH completed.")))
            .filteredOn(s -> s.contains("FLAGS")) // No FLAGS FETCH responses
            .hasSize(1);
    }

    @Test
    void fetchShouldSupportVanishedModifiedWithEarlierTag() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.of(14)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 12:37 (FLAGS) (CHANGEDSINCE %d VANISHED)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK FETCH completed.")))
            .filteredOn(s -> s.contains("* VANISHED (EARLIER) 14"))
            .hasSize(1);
    }

    @Test
    void fetchShouldSupportVanishedModifiedWithoutChangedSince() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.of(14)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 12:37 (FLAGS) (VANISHED)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(readBytes(clientConnection))).contains("I00104 BAD FETCH VANISHED used without CHANGEDSINCE");
    }

    @Test
    void fetchShouldRejectVanishedWhenNoQRESYNC() throws Exception {
        inbox.delete(ImmutableList.of(MessageUid.of(14)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();
        clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 12:37 (FLAGS) (CHANGEDSINCE %d VANISHED)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(readBytes(clientConnection))).contains("I00104 BAD FETCH QRESYNC not enabled.");
    }

    @Test
    void unsolicitedNotificationsShouldBeSent() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);

        inbox.delete(ImmutableList.of(MessageUid.of(14)), mailboxSession);

        ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .getMetaData(IGNORE, mailboxSession, NO_COUNT)
            .getHighestModSeq();
        clientConnection.write(ByteBuffer.wrap("I00104 NOOP\r\n".getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK NOOP completed.")))
            .filteredOn(s -> s.contains("* VANISHED 14"))
            .hasSize(1);
    }

    @Test
    void expungeShouldReturnVanishedWhenQResyncIsActive() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(11)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(12)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(26)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(("I00104 EXPUNGE\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [HIGHESTMODSEQ 44] EXPUNGE completed.")))
            .filteredOn(s -> s.contains("* VANISHED 10:12,25:26,31"))
            .hasSize(1);
    }

    @Test
    void uidExpungeShouldReturnExpungededWhenQResyncIsActive() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(11)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(12)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(26)), mailboxSession);
        memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
            .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(("I00104 UID EXPUNGE 1:37\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [HIGHESTMODSEQ 44] EXPUNGE completed.")))
            .filteredOn(s -> s.contains("* VANISHED 10:12,25:26,31"))
            .hasSize(1);
    }

    @Test
    void implicitMailboxSelectionChangesShouldReturnClosedNotifications() throws Exception {
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.forUser(USER, "other"), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        clientConnection.write(ByteBuffer.wrap(("a3 SELECT other\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK [READ-WRITE] SELECT completed.")))
            .filteredOn(s -> s.contains("* OK [CLOSED]"))
            .hasSize(1);
    }

    @Test
    void closeShouldNotReturnHighestModseqWhenUsingQResync() throws Exception {
        // See https://www.rfc-editor.org/errata_search.php?rfc=5162
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.forUser(USER, "other"), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);
        clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));

        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        clientConnection.write(ByteBuffer.wrap(("a3 CLOSE\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK CLOSE completed.")))
            .isNotNull();
    }
}
