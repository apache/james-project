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
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;


@SuppressWarnings("checkstyle:membername")
class IMAPServerCondStoreTest extends AbstractIMAPServerTest {
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

    private void setUpTestingData() {
        IntStream.range(0, 37)
            .forEach(Throwing.intConsumer(i -> inbox.appendMessage(MessageManager.AppendCommand.builder()
                .build("MIME-Version: 1.0\r\n\r\nCONTENT\r\n"), mailboxSession)));
    }

    @Test
    void fetchShouldSupportChangedSince() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));


        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(14)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(2)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap(String.format("a3 UID FETCH 1:* (FLAGS) (CHANGEDSINCE %d)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        List<String> replies = readStringUntil(clientConnection, s -> s.contains("a3 OK FETCH completed."));
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(replies)
                .filteredOn(s -> s.contains("* 2 FETCH (MODSEQ (41) FLAGS (\\Answered \\Recent) UID 2)"))
                .hasSize(1);
            softly.assertThat(replies)
                .filteredOn(s -> s.contains("* 25 FETCH (MODSEQ (42) FLAGS (\\Answered \\Recent) UID 25)"))
                .hasSize(1);
            softly.assertThat(replies)
                .filteredOn(s -> s.contains("* 35 FETCH (MODSEQ (43) FLAGS (\\Answered \\Recent) UID 35)"))
                .hasSize(1);

            softly.assertThat(replies)
                .filteredOn(s -> s.contains("* 14 FETCH (MODSEQ (39) FLAGS (\\Answered \\Recent) UID 14)"))
                .isEmpty();
            softly.assertThat(replies)
                .filteredOn(s -> s.contains("* 31 FETCH (MODSEQ (40) FLAGS (\\Answered \\Recent) UID 31)"))
                .isEmpty();
        });
    }

    @Test
    void searchShouldSupportModSeq() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));


        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(14)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(2)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap(String.format("A150 SEARCH MODSEQ %d\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("A150 OK SEARCH completed.")))
            .filteredOn(s -> s.contains("* SEARCH 2 25 31 35 (MODSEQ 43)"))
            .hasSize(1);
    }

    @Test
    void searchShouldSupportModSeqWithFlagRestrictions() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));


        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(14)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(2)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap(String.format("a SEARCH MODSEQ \"/flags/\\\\draft\" all %d\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

        // Restrictions are not applied however
        assertThat(readStringUntil(clientConnection, s -> s.contains("a OK SEARCH completed.")))
            .filteredOn(s -> s.contains("* SEARCH 2 25 31 35 (MODSEQ 43)"))
            .hasSize(1);
    }

    @Test
    void statusShouldAcceptHighestModSeqItem() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(2)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        clientConnection.write(ByteBuffer.wrap(("A042 STATUS INBOX (HIGHESTMODSEQ)\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("A042 OK STATUS completed.")))
            .filteredOn(s -> s.contains(String.format("* STATUS \"INBOX\" (HIGHESTMODSEQ %d)", highestModSeq.asLong())))
            .hasSize(1);
    }

    @Test
    void selectShouldAcceptCondstore() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed.")))
            .isNotNull();
    }

    @Test
    void selectShouldEnableCondstore() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        assertThat(readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed.")))
            .filteredOn(s -> s.contains("* 35 FETCH (MODSEQ (39) FLAGS (\\Answered \\Recent))"))
            .hasSize(1);
    }

    @Test
    void storeShouldSucceedWhenUnchangedSinceIsNotExceeded() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE %d) +FLAGS.SILENT (\\Seen)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8))));
        assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK STORE completed.")))
            .filteredOn(s -> s.contains("* 35 FETCH (MODSEQ (40) UID 35)"))
            .hasSize(1);
    }

    @Test
    void storeShouldSucceedWhenUnchangedSinceIsNotExceededAndNotSilent() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE %d) +FLAGS (\\Seen)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8))));
        assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK STORE completed.")))
            .filteredOn(s -> s.contains("* 35 FETCH (MODSEQ (40) FLAGS (\\Answered \\Recent \\Seen) UID 35)"))
            .hasSize(1);
    }

    @Test
    void storeShouldFailWhenUnchangedSinceIsExceeded() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE %d) +FLAGS.SILENT (\\Seen)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
        assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 35] STORE failed.")))
            .isNotNull();
    }

    @Test
    void storeShouldFailWhenUnchangedSinceIsZero() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

        inbox.setFlags(new Flags("dcustom"), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE 0) +FLAGS.SILENT (dcustom)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
        assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 35] STORE failed.")))
            .isNotNull();
    }

    @Test
    void storeShouldFailWhenUnchangedSinceIsZeroAndMsn() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

        inbox.setFlags(new Flags("dcustom"), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap((String.format("a103 STORE 35 (UNCHANGEDSINCE 0) +FLAGS.SILENT (dcustom)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
        assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 35] STORE failed.")))
            .isNotNull();
    }

    @Test
    void storeShouldFailWhenUnchangedSinceIsZeroAndSystemFlagsUpdate() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

        inbox.setFlags(new Flags("dcustom"), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE 0) +FLAGS.SILENT (\\Answered)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
        assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 35] STORE failed.")))
            .isNotNull();
    }

    @Test
    void storeShouldFailWhenSomeMessagesDoNotMatch() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

        readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

        ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

        inbox.setFlags(new Flags("custom"), REPLACE, MessageRange.one(MessageUid.of(7)), mailboxSession);
        inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(9)), mailboxSession);

        clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

        clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 5,7,9 (UNCHANGEDSINCE %d) +FLAGS.SILENT (\\Seen)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
        assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 5,7,9] STORE failed.")))
            .filteredOn(s -> s.contains("FETCH"))
            .isEmpty();
    }
}
