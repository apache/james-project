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
package org.apache.james.quota.search;

import static org.apache.james.core.CoreFixture.Domains.ALPHABET_TLD;
import static org.apache.james.core.CoreFixture.Domains.DOMAIN_TLD;
import static org.apache.james.core.CoreFixture.Domains.SIMPSON_COM;
import static org.apache.james.core.CoreFixture.Users.BENOIT_AT_DOMAIN_TLD;
import static org.apache.james.quota.search.QuotaBoundaryFixture._50;
import static org.apache.james.quota.search.QuotaBoundaryFixture._75;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.CoreFixture.Users.Alphabet;
import org.apache.james.core.CoreFixture.Users.Simpson;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.user.api.UsersRepositoryException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

public interface QuotaSearcherContract {

    String PASSWORD = "any";

    @Test
    default void moreThanShouldFilterOutTooSmallValues(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(SIMPSON_COM);
        testSystem.getUsersRepository().addUser(Simpson.BART, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.HOMER, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.LISA, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Simpson.BART, withSize(49));
        appendMessage(testSystem, Simpson.HOMER, withSize(50));
        appendMessage(testSystem, Simpson.LISA, withSize(51));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .moreThan(_50)))
            .containsOnly(Simpson.HOMER, Simpson.LISA);
    }

    @Test
    default void lessThanShouldFilterOutTooBigValues(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(SIMPSON_COM);
        testSystem.getUsersRepository().addUser(Simpson.BART, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.HOMER, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.LISA, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Simpson.BART, withSize(49));
        appendMessage(testSystem, Simpson.HOMER, withSize(50));
        appendMessage(testSystem, Simpson.LISA, withSize(51));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .lessThan(_50)))
            .containsOnly(Simpson.HOMER, Simpson.BART);
    }

    @Test
    default void rangeShouldFilterValuesOutOfRange(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(SIMPSON_COM);
        testSystem.getUsersRepository().addUser(Simpson.BART, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.HOMER, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.LISA, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Simpson.BART, withSize(40));
        appendMessage(testSystem, Simpson.HOMER, withSize(51));
        appendMessage(testSystem, Simpson.LISA, withSize(60));
        appendMessage(testSystem, BENOIT_AT_DOMAIN_TLD, withSize(80));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .moreThan(_50)
                    .lessThan(_75)))
            .containsOnly(Simpson.HOMER, Simpson.LISA);
    }

    @Test
    default void hasDomainShouldFilterOutValuesWithDifferentDomains(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(SIMPSON_COM);
        testSystem.getDomainList().addDomain(DOMAIN_TLD);
        testSystem.getUsersRepository().addUser(Simpson.BART, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.LISA, PASSWORD);
        testSystem.getUsersRepository().addUser(BENOIT_AT_DOMAIN_TLD, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Simpson.BART, withSize(49));
        appendMessage(testSystem, Simpson.LISA, withSize(51));
        appendMessage(testSystem, BENOIT_AT_DOMAIN_TLD, withSize(50));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .hasDomain(SIMPSON_COM)))
            .containsOnly(Simpson.BART, Simpson.LISA);
    }

    @Test
    default void andShouldCombineClauses(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(SIMPSON_COM);
        testSystem.getDomainList().addDomain(DOMAIN_TLD);
        testSystem.getUsersRepository().addUser(Simpson.BART, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.LISA, PASSWORD);
        testSystem.getUsersRepository().addUser(BENOIT_AT_DOMAIN_TLD, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Simpson.BART, withSize(49));
        appendMessage(testSystem, Simpson.LISA, withSize(51));
        appendMessage(testSystem, BENOIT_AT_DOMAIN_TLD, withSize(50));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .hasDomain(SIMPSON_COM)
                    .lessThan(_50)))
            .containsOnly(Simpson.BART);
    }

    @Test
    default void resultShouldBeAlphabeticallyOrdered(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(ALPHABET_TLD);
        testSystem.getUsersRepository().addUser(Alphabet.AAA, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ABA, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ABB, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ACB, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Alphabet.AAA, withSize(49));
        appendMessage(testSystem, Alphabet.ABA, withSize(50));
        appendMessage(testSystem, Alphabet.ACB, withSize(51));
        appendMessage(testSystem, Alphabet.ABB, withSize(50));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()))
            .containsExactly(Alphabet.AAA, Alphabet.ABA, Alphabet.ABB, Alphabet.ACB);
    }

    @Test
    default void limitShouldBeTheMaximumValueOfReturnedResults(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(ALPHABET_TLD);
        testSystem.getUsersRepository().addUser(Alphabet.AAA, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ABA, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ABB, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ACB, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Alphabet.AAA, withSize(49));
        appendMessage(testSystem, Alphabet.ABA, withSize(50));
        appendMessage(testSystem, Alphabet.ACB, withSize(51));
        appendMessage(testSystem, Alphabet.ABB, withSize(50));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .withLimit(Limit.of(2))))
            .containsOnly(Alphabet.AAA, Alphabet.ABA);
    }

    @Test
    default void offsetShouldSkipSomeResults(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(ALPHABET_TLD);
        testSystem.getUsersRepository().addUser(Alphabet.AAA, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ABA, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ABB, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ACB, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Alphabet.AAA, withSize(49));
        appendMessage(testSystem, Alphabet.ABA, withSize(50));
        appendMessage(testSystem, Alphabet.ACB, withSize(51));
        appendMessage(testSystem, Alphabet.ABB, withSize(50));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .withOffset(Offset.of(2))))
            .containsOnly(Alphabet.ABB, Alphabet.ACB);
    }

    @Test
    default void searchShouldReturnEmptyOnTooBigOffset(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(SIMPSON_COM);
        testSystem.getDomainList().addDomain(DOMAIN_TLD);
        testSystem.getUsersRepository().addUser(Simpson.BART, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.HOMER, PASSWORD);
        testSystem.getUsersRepository().addUser(Simpson.LISA, PASSWORD);
        testSystem.getUsersRepository().addUser(BENOIT_AT_DOMAIN_TLD, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Simpson.BART, withSize(49));
        appendMessage(testSystem, Simpson.HOMER, withSize(50));
        appendMessage(testSystem, Simpson.LISA, withSize(51));
        appendMessage(testSystem, BENOIT_AT_DOMAIN_TLD, withSize(50));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .withOffset(Offset.of(5))))
            .isEmpty();
    }

    @Test
    default void pagingShouldBeSupported(QuotaSearchTestSystem testSystem) throws Exception {
        testSystem.getDomainList().addDomain(ALPHABET_TLD);
        testSystem.getUsersRepository().addUser(Alphabet.AAA, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ABA, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ABB, PASSWORD);
        testSystem.getUsersRepository().addUser(Alphabet.ACB, PASSWORD);
        testSystem.getMaxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        appendMessage(testSystem, Alphabet.AAA, withSize(49));
        appendMessage(testSystem, Alphabet.ABA, withSize(50));
        appendMessage(testSystem, Alphabet.ACB, withSize(51));
        appendMessage(testSystem, Alphabet.ABB, withSize(50));
        testSystem.await();

        assertThat(
            testSystem.getQuotaSearcher()
                .search(QuotaQuery.builder()
                    .withLimit(Limit.of(2))
                    .withOffset(Offset.of(1))))
            .containsExactly(Alphabet.ABA, Alphabet.ABB);
    }

    default void appendMessage(QuotaSearchTestSystem testSystem, Username username, MessageManager.AppendCommand appendCommand) throws MailboxException, UsersRepositoryException, DomainListException {
        MailboxManager mailboxManager = testSystem.getMailboxManager();
        MailboxSession session = mailboxManager.createSystemSession(username);

        MailboxPath mailboxPath = MailboxPath.inbox(session);
        mailboxManager.createMailbox(mailboxPath, session);
        mailboxManager.getMailbox(mailboxPath, session)
            .appendMessage(appendCommand, session);
    }

    default MessageManager.AppendCommand withSize(int size) {
        byte[] bytes = Strings.repeat("a", size).getBytes(StandardCharsets.UTF_8);
        return MessageManager.AppendCommand.from(new ByteArrayInputStream(bytes));
    }
}
