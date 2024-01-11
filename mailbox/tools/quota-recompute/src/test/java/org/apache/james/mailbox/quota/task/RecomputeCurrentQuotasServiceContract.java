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

package org.apache.james.mailbox.quota.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.Context;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.RunningOptions;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

public interface RecomputeCurrentQuotasServiceContract {
    Username USER_1 = Username.of("user1");
    String PASSWORD = "password";
    MailboxPath MAILBOX_PATH = MailboxPath.forUser(USER_1, "mailbox");
    CurrentQuotas EXPECTED_QUOTAS = new CurrentQuotas(QuotaCountUsage.count(1L), QuotaSizeUsage.size(103L));

    RecomputeSingleComponentCurrentQuotasService RECOMPUTE_JMAP_UPLOAD_CURRENT_QUOTAS_SERVICE = Mockito.mock(RecomputeSingleComponentCurrentQuotasService.class);

    UsersRepository usersRepository();

    SessionProvider sessionProvider();

    MailboxManager mailboxManager();

    CurrentQuotaManager currentQuotaManager();

    UserQuotaRootResolver userQuotaRootResolver();

    RecomputeCurrentQuotasService testee();

    @BeforeEach
    default void setup() {
        when(RECOMPUTE_JMAP_UPLOAD_CURRENT_QUOTAS_SERVICE.getQuotaComponent()).thenReturn(QuotaComponent.JMAP_UPLOADS);
        when(RECOMPUTE_JMAP_UPLOAD_CURRENT_QUOTAS_SERVICE.recomputeCurrentQuotas(Mockito.any())).thenReturn(Mono.empty());
    }

    @Test
    default void recomputeCurrentQuotasShouldReturnCompleteWhenNoData() {
        assertThat(testee().recomputeCurrentQuotas(new Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    default void recomputeCurrentQuotasShouldReturnCompleteWhenUserWithNoMessage() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        assertThat(testee().recomputeCurrentQuotas(new Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    default void recomputeCurrentQuotasShouldReturnPartialWhenRecomputeJMAPCurrentUploadUsageFail() throws Exception {
        when(RECOMPUTE_JMAP_UPLOAD_CURRENT_QUOTAS_SERVICE.recomputeCurrentQuotas(Mockito.any())).thenReturn(Mono.error(new RuntimeException()));
        usersRepository().addUser(USER_1, PASSWORD);

        assertThat(testee().recomputeCurrentQuotas(new Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    default void recomputeCurrentQuotasShouldRunRecomputeMailboxUserCurrentQuotasOnly() throws Exception {
        when(RECOMPUTE_JMAP_UPLOAD_CURRENT_QUOTAS_SERVICE.recomputeCurrentQuotas(Mockito.any())).thenReturn(Mono.error(new RuntimeException()));
        usersRepository().addUser(USER_1, PASSWORD);

        assertThat(testee().recomputeCurrentQuotas(new Context(),
            RunningOptions.of(RunningOptions.DEFAULT_USERS_PER_SECOND, ImmutableList.of(QuotaComponent.MAILBOX))).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    default void recomputeCurrentQuotasShouldComputeEmptyQuotasWhenUserWithNoMessage() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        testee().recomputeCurrentQuotas(new Context(), RunningOptions.DEFAULT).block();

        assertThat(Mono.from(currentQuotaManager().getCurrentQuotas(userQuotaRootResolver().forUser(USER_1))).block())
            .isEqualTo(CurrentQuotas.emptyQuotas());
    }

    @Test
    default void recomputeCurrentQuotasShouldReturnCompleteWhenUserWithMessage() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        MailboxSession session = sessionProvider().createSystemSession(USER_1);
        mailboxManager().createMailbox(MAILBOX_PATH, session);

        MessageManager messageManager = mailboxManager().getMailbox(MAILBOX_PATH, session);
        appendAMessageForUser(messageManager, session);

        assertThat(testee().recomputeCurrentQuotas(new Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    default void recomputeCurrentQuotasShouldRecomputeCurrentQuotasCorrectlyWhenUserWithMessage() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        MailboxSession session = sessionProvider().createSystemSession(USER_1);
        mailboxManager().createMailbox(MAILBOX_PATH, session);

        MessageManager messageManager = mailboxManager().getMailbox(MAILBOX_PATH, session);
        appendAMessageForUser(messageManager, session);

        testee().recomputeCurrentQuotas(new Context(), RunningOptions.DEFAULT).block();

        assertThat(Mono.from(currentQuotaManager().getCurrentQuotas(userQuotaRootResolver().forUser(USER_1))).block())
            .isEqualTo(EXPECTED_QUOTAS);
    }

    @Test
    default void recomputeCurrentQuotasShouldResetCurrentQuotasWhenIncorrectQuotas() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        MailboxSession session = sessionProvider().createSystemSession(USER_1);
        mailboxManager().createMailbox(MAILBOX_PATH, session);

        MessageManager messageManager = mailboxManager().getMailbox(MAILBOX_PATH, session);
        appendAMessageForUser(messageManager, session);

        QuotaRoot quotaRoot = userQuotaRootResolver().forUser(USER_1);

        QuotaOperation operation = new QuotaOperation(quotaRoot, QuotaCountUsage.count(3L), QuotaSizeUsage.size(390L));
        Mono.from(currentQuotaManager().increase(operation)).block();

        testee().recomputeCurrentQuotas(new Context(), RunningOptions.DEFAULT).block();

        assertThat(Mono.from(currentQuotaManager().getCurrentQuotas(userQuotaRootResolver().forUser(USER_1))).block())
            .isEqualTo(EXPECTED_QUOTAS);
    }

    @Test
    default void recomputeCurrentQuotasShouldResetCurrentQuotasWhenNegativeQuotas() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        MailboxSession session = sessionProvider().createSystemSession(USER_1);
        mailboxManager().createMailbox(MAILBOX_PATH, session);

        MessageManager messageManager = mailboxManager().getMailbox(MAILBOX_PATH, session);
        appendAMessageForUser(messageManager, session);

        QuotaRoot quotaRoot = userQuotaRootResolver().forUser(USER_1);

        QuotaOperation operation = new QuotaOperation(quotaRoot, QuotaCountUsage.count(300L), QuotaSizeUsage.size(390L));
        Mono.from(currentQuotaManager().decrease(operation)).block();

        testee().recomputeCurrentQuotas(new Context(), RunningOptions.DEFAULT).block();

        assertThat(Mono.from(currentQuotaManager().getCurrentQuotas(userQuotaRootResolver().forUser(USER_1))).block())
            .isEqualTo(EXPECTED_QUOTAS);
    }

    @Test
    default void recomputeCurrentQuotasShouldNotUpdateContextWhenNoData() {
        Context context = new Context();
        testee().recomputeCurrentQuotas(context, RunningOptions.DEFAULT).block();


        RecursiveComparisonConfiguration recursiveComparisonConfiguration = new RecursiveComparisonConfiguration();
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingInt(AtomicInteger::get), AtomicInteger.class);
        recursiveComparisonConfiguration.registerComparatorForType(Comparator.comparingLong(AtomicLong::get), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicInteger.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicLong.class);
        recursiveComparisonConfiguration.registerEqualsForType((o, o2) -> o.get() == o2.get(), AtomicBoolean.class);

        assertThat(context.snapshot())
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(new Context().snapshot());
    }

    @Test
    default void recomputeCurrentQuotasShouldUpdateContextWhenUserWithNoMessage() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        Context context = new Context();
        testee().recomputeCurrentQuotas(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot().getResults())
            .containsExactlyInAnyOrderElementsOf(new Context(ImmutableMap.of(QuotaComponent.MAILBOX, new Context.Statistic(1L, ImmutableList.of()),
                QuotaComponent.JMAP_UPLOADS, new Context.Statistic(1L, ImmutableList.of()))).snapshot().getResults());
    }

    @Test
    default void recomputeCurrentQuotasShouldUpdateContextWhenUserWithMessage() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        MailboxSession session = sessionProvider().createSystemSession(USER_1);
        mailboxManager().createMailbox(MAILBOX_PATH, session);

        MessageManager messageManager = mailboxManager().getMailbox(MAILBOX_PATH, session);
        appendAMessageForUser(messageManager, session);

        Context context = new Context();
        testee().recomputeCurrentQuotas(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot().getResults())
            .containsExactlyInAnyOrderElementsOf(new Context(ImmutableMap.of(QuotaComponent.MAILBOX, new Context.Statistic(1L, ImmutableList.of()),
                QuotaComponent.JMAP_UPLOADS, new Context.Statistic(1L, ImmutableList.of()))).snapshot().getResults());
    }

    @Test
    default void recomputeCurrentQuotasShouldUpdateContextWhenIncorrectQuotas() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);
        usersRepository().addUser(Username.of("user2"), PASSWORD);

        MailboxSession session = sessionProvider().createSystemSession(USER_1);
        mailboxManager().createMailbox(MAILBOX_PATH, session);

        MessageManager messageManager = mailboxManager().getMailbox(MAILBOX_PATH, session);
        appendAMessageForUser(messageManager, session);

        QuotaRoot quotaRoot = userQuotaRootResolver().forUser(USER_1);

        QuotaOperation operation = new QuotaOperation(quotaRoot, QuotaCountUsage.count(3L), QuotaSizeUsage.size(390L));
        Mono.from(currentQuotaManager().increase(operation)).block();

        Context context = new Context();
        testee().recomputeCurrentQuotas(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot().getResults())
            .containsExactlyInAnyOrderElementsOf(new Context(ImmutableMap.of(QuotaComponent.MAILBOX, new Context.Statistic(2L, ImmutableList.of()),
                QuotaComponent.JMAP_UPLOADS, new Context.Statistic(2L, ImmutableList.of()))).snapshot().getResults());
    }

    default void appendAMessageForUser(MessageManager messageManager, MailboxSession session) throws Exception {
        String recipient = "test@localhost.com";
        String body = "This is a message";
        messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(body, StandardCharsets.UTF_8)),
            session);
    }
}
