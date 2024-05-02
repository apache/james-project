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

package org.apache.james.rspamd.task;

import static org.apache.james.rspamd.RspamdExtension.PASSWORD;
import static org.apache.james.rspamd.task.FeedSpamToRspamdTaskTest.BOB_SPAM_MAILBOX;
import static org.apache.james.rspamd.task.RunningOptions.ALL_MESSAGES;
import static org.apache.james.rspamd.task.RunningOptions.DEFAULT_MESSAGES_PER_SECOND;
import static org.apache.james.rspamd.task.RunningOptions.DEFAULT_SAMPLING_PROBABILITY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import jakarta.mail.Flags;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.rspamd.RspamdExtension;
import org.apache.james.rspamd.client.RspamdClientConfiguration;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.utils.UpdatableTickingClock;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

@Tag(Unstable.TAG)
public class FeedHamToRspamdTaskTest {
    @RegisterExtension
    static RspamdExtension rspamdExtension = new RspamdExtension();

    public static final String INBOX_MAILBOX_NAME = "INBOX";
    public static final Domain DOMAIN = Domain.of("domain.tld");
    public static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    public static final Username ALICE = Username.fromLocalPartWithDomain("alice", DOMAIN);
    public static final MailboxPath BOB_INBOX_MAILBOX = MailboxPath.forUser(BOB, INBOX_MAILBOX_NAME);
    public static final MailboxPath BOB_CUSTOM_MAILBOX = MailboxPath.forUser(BOB, "Custom");
    public static final MailboxPath BOB_TRASH_MAILBOX = MailboxPath.forUser(BOB, "Trash");
    public static final MailboxPath ALICE_INBOX_MAILBOX = MailboxPath.forUser(ALICE, INBOX_MAILBOX_NAME);
    public static final long THREE_DAYS_IN_SECOND = 259200;
    public static final long TWO_DAYS_IN_SECOND = 172800;
    public static final long ONE_DAY_IN_SECOND = 86400;
    public static final Instant NOW = ZonedDateTime.now().toInstant();

    static ClientAndServer mockServer = null;

    static class TestRspamdHttpClient extends RspamdHttpClient {
        private final AtomicInteger hitCounter;

        public TestRspamdHttpClient(RspamdClientConfiguration configuration) {
            super(configuration);
            this.hitCounter = new AtomicInteger(0);
        }

        @Override
        public Mono<Void> reportAsHam(Publisher<ByteBuffer> content, Options options) {
            return Mono.from(content)
                .doOnNext(e -> hitCounter.incrementAndGet())
                .then();
        }

        public int getHitCounter() {
            return hitCounter.get();
        }
    }

    private InMemoryMailboxManager mailboxManager;
    private MessageIdManager messageIdManager;
    private MailboxSessionMapperFactory mapperFactory;
    private UsersRepository usersRepository;
    private Clock clock;
    private RspamdHttpClient client;
    private RspamdClientConfiguration configuration;
    private FeedHamToRspamdTask task;
    private UpdatableTickingClock saveDateClock;

    @BeforeEach
    void setup() throws Exception {
        InMemoryIntegrationResources inMemoryIntegrationResources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = inMemoryIntegrationResources.getMailboxManager();
        saveDateClock = (UpdatableTickingClock) mailboxManager.getClock();
        DomainList domainList = mock(DomainList.class);
        Mockito.when(domainList.containsDomain(any())).thenReturn(true);
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "anyPassword");
        usersRepository.addUser(ALICE, "anyPassword");
        mailboxManager.createMailbox(BOB_INBOX_MAILBOX, mailboxManager.createSystemSession(BOB));
        mailboxManager.createMailbox(BOB_CUSTOM_MAILBOX, mailboxManager.createSystemSession(BOB));
        mailboxManager.createMailbox(BOB_TRASH_MAILBOX, mailboxManager.createSystemSession(BOB));
        mailboxManager.createMailbox(BOB_SPAM_MAILBOX, mailboxManager.createSystemSession(BOB));
        mailboxManager.createMailbox(ALICE_INBOX_MAILBOX, mailboxManager.createSystemSession(ALICE));

        clock = new UpdatableTickingClock(NOW);
        configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty(), true);
        client = new RspamdHttpClient(configuration);
        messageIdManager = inMemoryIntegrationResources.getMessageIdManager();
        mapperFactory = mailboxManager.getMapperFactory();
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, RunningOptions.DEFAULT, clock, configuration);
    }

    @AfterEach
    void afterEach() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test
    void shouldReturnDefaultInformationWhenDataIsEmpty() {
        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(0)
                .reportedHamMessageCount(0)
                .errorCount(0)
                .build());
    }

    @Test
    void taskShouldReportAllHamMessagesOfAllUsersByDefault() throws MailboxException {
        appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW));
        appendHamMessage(ALICE_INBOX_MAILBOX, Date.from(NOW));

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(2)
                .reportedHamMessageCount(2)
                .errorCount(0)
                .build());
    }

    @Test
    void taskShouldHitToRspamdServerWhenLearnHam() throws MailboxException {
        appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW));
        appendHamMessage(ALICE_INBOX_MAILBOX, Date.from(NOW));

        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), PASSWORD, Optional.empty(), true);
        TestRspamdHttpClient rspamdHttpClient = new TestRspamdHttpClient(configuration);

        FeedHamToRspamdTask feedHamToRspamdTask = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory,
            rspamdHttpClient, RunningOptions.DEFAULT, clock, configuration);


        Task.Result result = feedHamToRspamdTask.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(rspamdHttpClient.getHitCounter()).isEqualTo(2);
    }

    @Test
    void taskShouldReportHamMessageInPeriod() throws MailboxException {
        RunningOptions runningOptions = new RunningOptions(Optional.of(TWO_DAYS_IN_SECOND),
            DEFAULT_MESSAGES_PER_SECOND, DEFAULT_SAMPLING_PROBABILITY, ALL_MESSAGES);
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)));

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(1)
                .reportedHamMessageCount(1)
                .errorCount(0)
                .build());
    }

    @Test
    void taskShouldNotReportHamMessageNotInPeriod() throws MailboxException {
        RunningOptions runningOptions = new RunningOptions(Optional.of(TWO_DAYS_IN_SECOND),
            DEFAULT_MESSAGES_PER_SECOND, DEFAULT_SAMPLING_PROBABILITY, ALL_MESSAGES);
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        saveDateClock.setInstant(NOW.minusSeconds(THREE_DAYS_IN_SECOND));
        appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(THREE_DAYS_IN_SECOND)));

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(0)
                .reportedHamMessageCount(0)
                .errorCount(0)
                .build());
    }

    @Test
    void taskShouldCountAndReportOnlyHamMessagesInPeriodBasedOnSaveDate() throws MailboxException {
        Date nowInternalDate = Date.from(NOW);
        saveDateClock.setInstant(NOW.minusSeconds(THREE_DAYS_IN_SECOND));
        appendHamMessage(BOB_INBOX_MAILBOX, nowInternalDate);
        saveDateClock.setInstant(NOW.minusSeconds(ONE_DAY_IN_SECOND));
        appendHamMessage(BOB_INBOX_MAILBOX, nowInternalDate);

        RunningOptions runningOptions = new RunningOptions(Optional.of(TWO_DAYS_IN_SECOND),
            DEFAULT_MESSAGES_PER_SECOND, DEFAULT_SAMPLING_PROBABILITY, ALL_MESSAGES);
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);
        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(1)
                .reportedHamMessageCount(1)
                .errorCount(0)
                .build());
    }

    @Test
    void taskWithSamplingProbabilityIsZeroShouldReportNonHamMessage() {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 0, ALL_MESSAGES);
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        IntStream.range(0, 10)
            .forEach(Throwing.intConsumer(any -> appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)))));

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(10)
                .reportedHamMessageCount(0)
                .errorCount(0)
                .build());
    }

    @Test
    void taskWithDefaultSamplingProbabilityShouldReportAllHamMessages() {
        IntStream.range(0, 10)
            .forEach(Throwing.intConsumer(any -> appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)))));

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(10)
                .reportedHamMessageCount(10)
                .errorCount(0)
                .build());
    }

    @Test
    void taskWithVeryLowSamplingProbabilityShouldReportNotAllHamMessages() {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 0.01, ALL_MESSAGES);
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        IntStream.range(0, 10)
            .forEach(Throwing.intConsumer(any -> appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)))));

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(10);
            assertThat(task.snapshot().getReportedHamMessageCount()).isLessThan(10);
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void taskWithVeryHighSamplingProbabilityShouldReportMoreThanZeroMessage() {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 0.99, ALL_MESSAGES);
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        IntStream.range(0, 10)
            .forEach(Throwing.intConsumer(any -> appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)))));

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(10);
            assertThat(task.snapshot().getReportedHamMessageCount()).isPositive();
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void taskWithAverageSamplingProbabilityShouldReportSomeMessages() {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 0.5, ALL_MESSAGES);
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        IntStream.range(0, 10)
            .forEach(Throwing.intConsumer(any -> appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)))));

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(10);
            assertThat(task.snapshot().getReportedHamMessageCount()).isBetween(1L, 9L); // skip 0 and 10 cases cause their probability is very low (0.5^10)
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldReportUnclassifiedWhenClassifiedAsSpamIsTrue() throws Exception {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.of(true));
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "Unrelated: at all");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldNotReportHamWhenClassifiedAsSpamIsTrue() throws Exception {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.of(true));
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "org.apache.james.rspamd.flag: NO");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isZero();
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldReportSpamWhenClassifiedAsSpamIsTrue() throws Exception {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.of(true));
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "org.apache.james.rspamd.flag: YES");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldReportUnclassifiedWhenClassifiedAsSpamIsOmited() throws Exception {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.empty());
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "Unrelated: at all");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldReportHamWhenClassifiedAsSpamIsOmited() throws Exception {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.empty());
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "org.apache.james.rspamd.flag: NO");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldNotReportSpamWhenClassifiedAsSpamIsOmited() throws Exception {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.empty());
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "org.apache.james.rspamd.flag: YES");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldReportUnclassifiedWhenClassifiedAsSpamIsFalse() throws Exception {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.of(false));
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "Unrelated: at all");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldReportHamWhenClassifiedAsSpamIsFalse() throws Exception {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.of(false));
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "org.apache.james.rspamd.flag: NO");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldNotReportSpamWhenClassifiedAsSpamIsFalse() throws Exception {
        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.of(false));
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, client, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "org.apache.james.rspamd.flag: YES");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.COMPLETED);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isZero();
            assertThat(task.snapshot().getErrorCount()).isZero();
        });
    }

    @Test
    void shouldNotReportMessagesInTrashAndSpamMailboxes() throws MailboxException {
        appendHamMessage(BOB_TRASH_MAILBOX, Date.from(NOW));
        appendHamMessage(BOB_SPAM_MAILBOX, Date.from(NOW));

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(0)
                .reportedHamMessageCount(0)
                .errorCount(0)
                .build());
    }

    @Test
    void shouldReportMessagesInHamMailboxes() throws MailboxException {
        appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW));
        appendHamMessage(BOB_CUSTOM_MAILBOX, Date.from(NOW));

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(2)
                .reportedHamMessageCount(2)
                .errorCount(0)
                .build());
    }

    @Test
    void mixedMailboxesCase() throws MailboxException {
        appendHamMessage(BOB_INBOX_MAILBOX, Date.from(NOW));
        appendHamMessage(BOB_CUSTOM_MAILBOX, Date.from(NOW));
        appendHamMessage(BOB_TRASH_MAILBOX, Date.from(NOW));
        appendHamMessage(BOB_SPAM_MAILBOX, Date.from(NOW));

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(FeedHamToRspamdTask.Context.Snapshot.builder()
                .hamMessageCount(2)
                .reportedHamMessageCount(2)
                .errorCount(0)
                .build());
    }

    @Test
    void errorCountShouldIncrementWhenRspamdTimeout() throws Exception {
        mockServer = ClientAndServer.startClientAndServer(0);

        mockServer
            .when(HttpRequest.request().withPath("/learnham"))
            .respond(httpRequest -> HttpResponse.response().withStatusCode(200), Delay.delay(TimeUnit.SECONDS, 10));

        RspamdHttpClient httpClient = new RspamdHttpClient(new RspamdClientConfiguration(new URI(String.format("http://localhost:%s", mockServer.getLocalPort())).toURL(),
            PASSWORD, Optional.of(3)));

        RunningOptions runningOptions = new RunningOptions(Optional.empty(),
            DEFAULT_MESSAGES_PER_SECOND, 1.0, Optional.of(false));
        task = new FeedHamToRspamdTask(mailboxManager, usersRepository, messageIdManager, mapperFactory, httpClient, runningOptions, clock, configuration);

        appendMessage(BOB_INBOX_MAILBOX, Date.from(NOW.minusSeconds(ONE_DAY_IN_SECOND)), "org.apache.james.rspamd.flag: NO");

        Task.Result result = task.run();

        SoftAssertions.assertSoftly(softly -> {
            assertThat(result).isEqualTo(Task.Result.PARTIAL);
            assertThat(task.snapshot().getHamMessageCount()).isEqualTo(1);
            assertThat(task.snapshot().getReportedHamMessageCount()).isEqualTo(0);
            assertThat(task.snapshot().getErrorCount()).isEqualTo(1);
        });
    }


    private void appendHamMessage(MailboxPath mailboxPath, Date internalDate) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());
        mailboxManager.getMailbox(mailboxPath, session)
            .appendMessage(new ByteArrayInputStream(String.format("random content %4.3f", Math.random()).getBytes()),
                internalDate,
                session,
                true,
                new Flags());
    }

    private void appendMessage(MailboxPath mailboxPath, Date internalDate, String header) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());
        mailboxManager.getMailbox(mailboxPath, session)
            .appendMessage(new ByteArrayInputStream((header + "\r\n\r\n" + String.format("random content %4.3f", Math.random())).getBytes()),
                internalDate,
                session,
                true,
                new Flags());
    }
}
