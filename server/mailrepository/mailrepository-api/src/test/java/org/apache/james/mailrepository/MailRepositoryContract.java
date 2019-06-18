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

package org.apache.james.mailrepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.DiscreteDistribution;
import org.apache.james.utils.DiscreteDistribution.DistributionEntry;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.runnable.ThrowingRunnable;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;

public interface MailRepositoryContract {

    Attribute TEST_ATTRIBUTE = Attribute.convertToAttribute("testAttribute", "testValue");
    MailKey MAIL_1 = new MailKey("mail1");
    MailKey MAIL_2 = new MailKey("mail2");
    MailKey UNKNOWN_KEY = new MailKey("random");

    default MailImpl createMail(MailKey key) throws MessagingException {
        return createMail(key, "original body");
    }

    default MailImpl createMail(MailKey key, String body) throws MessagingException {
        return MailImpl.builder()
            .name(key.asString())
            .sender("sender@localhost")
            .addRecipient("rec1@domain.com")
            .addRecipient("rec2@domain.com")
            .addAttribute(TEST_ATTRIBUTE)
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("test")
                .setText(body)
                .build())
            .build();
    }


    default void checkMailEquality(Mail actual, Mail expected) {
        assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(actual.getMessage().getContent()).isEqualTo(expected.getMessage().getContent());
            softly.assertThat(actual.getMessageSize()).isEqualTo(expected.getMessageSize());
            softly.assertThat(actual.getName()).isEqualTo(expected.getName());
            softly.assertThat(actual.getState()).isEqualTo(expected.getState());
            softly.assertThat(actual.getAttribute(TEST_ATTRIBUTE.getName())).isEqualTo(expected.getAttribute(TEST_ATTRIBUTE.getName()));
            softly.assertThat(actual.getErrorMessage()).isEqualTo(expected.getErrorMessage());
            softly.assertThat(actual.getRemoteHost()).isEqualTo(expected.getRemoteHost());
            softly.assertThat(actual.getRemoteAddr()).isEqualTo(expected.getRemoteAddr());
            softly.assertThat(actual.getLastUpdated()).isEqualTo(expected.getLastUpdated());
            softly.assertThat(actual.getPerRecipientSpecificHeaders()).isEqualTo(expected.getPerRecipientSpecificHeaders());
        }));
    }

    MailRepository retrieveRepository() throws Exception;

    @Test
    default void sizeShouldReturnZeroWhenEmpty() throws Exception {
        MailRepository testee = retrieveRepository();
        assertThat(testee.size()).isEqualTo(0L);
    }

    @Test
    default void sizeShouldReturnMailCount() throws Exception {
        MailRepository testee = retrieveRepository();

        testee.store(createMail(MAIL_1));
        testee.store(createMail(MAIL_2));

        assertThat(testee.size()).isEqualTo(2L);
    }

    @Test
    default void sizeShouldBeIncrementedByOneWhenDuplicates() throws Exception {
        MailRepository testee = retrieveRepository();

        testee.store(createMail(MAIL_1));
        testee.store(createMail(MAIL_1));

        assertThat(testee.size()).isEqualTo(1L);
    }

    @Test
    default void sizeShouldBeDecrementedByRemove() throws Exception {
        MailRepository testee = retrieveRepository();

        testee.store(createMail(MAIL_1));
        testee.remove(MAIL_1);

        assertThat(testee.size()).isEqualTo(0L);
    }

    @Test
    default void storeRegularMailShouldNotFailWhenNullSender() throws Exception {
        MailRepository testee = retrieveRepository();
        Mail mail = FakeMail.builder()
            .name(MAIL_1.asString())
            .sender(MailAddress.nullSender())
            .recipient(MailAddressFixture.RECIPIENT1)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("test")
                .setText("String body")
                .build())
            .build();

        testee.store(mail);

        assertThat(testee.retrieve(MAIL_1).getMaybeSender()).isEqualTo(MaybeSender.nullSender());
    }

    @Test
    default void storeRegularMailShouldNotFail() throws Exception {
        MailRepository testee = retrieveRepository();
        Mail mail = createMail(MAIL_1);

        testee.store(mail);
    }

    @Test
    default void storeBigMailShouldNotFail() throws Exception {
        MailRepository testee = retrieveRepository();
        String bigString = Strings.repeat("my mail is big 🐋", 1_000_000);
        Mail mail = createMail(MAIL_1, bigString);

        testee.store(mail);
    }

    @Test
    default void retrieveShouldGetStoredMail() throws Exception {
        MailRepository testee = retrieveRepository();
        Mail mail = createMail(MAIL_1);

        testee.store(mail);

        assertThat(testee.retrieve(MAIL_1)).satisfies(actual -> checkMailEquality(actual, mail));
    }

    @Test
    default void removeAllShouldRemoveStoredMails() throws Exception {
        MailRepository testee = retrieveRepository();
        testee.store(createMail(MAIL_1));

        testee.removeAll();

        assertThat(testee.size()).isEqualTo(0L);
    }

    @Test
    default void retrieveShouldReturnNullAfterRemoveAll() throws Exception {
        MailRepository testee = retrieveRepository();
        testee.store(createMail(MAIL_1));

        testee.removeAll();

        assertThat(testee.retrieve(MAIL_1)).isNull();
    }

    @Test
    default void removeAllShouldBeIdempotent() throws Exception {
        MailRepository testee = retrieveRepository();
        testee.store(createMail(MAIL_1));

        testee.removeAll();
        testee.removeAll();

        assertThat(testee.size()).isEqualTo(0L);
    }

    @Test
    default void removeAllShouldNotFailWhenEmpty() throws Exception {
        MailRepository testee = retrieveRepository();

        testee.removeAll();
    }

    @Test
    default void retrieveShouldGetStoredEmojiMail() throws Exception {
        MailRepository testee = retrieveRepository();
        Mail mail = createMail(MAIL_1, "my content contains 🐋");

        testee.store(mail);

        assertThat(testee.retrieve(MAIL_1).getMessage().getContent()).isEqualTo("my content contains 🐋");
    }

    @Test
    default void retrieveBigMailShouldHaveSameHash() throws Exception {
        MailRepository testee = retrieveRepository();
        String bigString = Strings.repeat("my mail is big 🐋", 1_000_000);
        Mail mail = createMail(MAIL_1, bigString);
        testee.store(mail);

        Mail actual = testee.retrieve(MAIL_1);

        assertThat(Hashing.sha256().hashString((String)actual.getMessage().getContent(), StandardCharsets.UTF_8))
            .isEqualTo(Hashing.sha256().hashString(bigString, StandardCharsets.UTF_8));
    }


    @Test
    default void retrieveShouldReturnAllMailProperties() throws Exception {
        MailRepository testee = retrieveRepository();
        MailImpl mail = createMail(MAIL_1);
        mail.setErrorMessage("Error message");
        mail.setRemoteAddr("172.5.2.3");
        mail.setRemoteHost("smtp@domain.com");
        mail.setLastUpdated(new Date());
        mail.addSpecificHeaderForRecipient(PerRecipientHeaders.Header.builder()
            .name("name")
            .value("value")
            .build(),
            new MailAddress("bob@domain.com"));

        testee.store(mail);

        assertThat(testee.retrieve(MAIL_1)).satisfies(actual -> checkMailEquality(actual, mail));
    }

    @Test
    default void newlyCreatedRepositoryShouldNotContainAnyMail() throws Exception {
        MailRepository testee = retrieveRepository();

        assertThat(testee.list()).isEmpty();
    }

    @Test
    default void retrievingUnknownMailShouldReturnNull() throws Exception {
        MailRepository testee = retrieveRepository();

        assertThat(testee.retrieve(UNKNOWN_KEY)).isNull();
    }

    @Test
    default void removingUnknownMailShouldHaveNoEffect() throws Exception {
        MailRepository testee = retrieveRepository();

        testee.remove(UNKNOWN_KEY);
    }

    @Test
    default void retrieveShouldReturnNullWhenKeyWasRemoved() throws Exception {
        MailRepository testee = retrieveRepository();
        testee.store(createMail(MAIL_1));

        testee.remove(MAIL_1);

        assertThat(retrieveRepository().list()).doesNotContain(MAIL_1);
        assertThat(retrieveRepository().retrieve(MAIL_1)).isNull();
    }

    @Test
    default void removeShouldnotAffectUnrelatedMails() throws Exception {
        MailRepository testee = retrieveRepository();
        testee.store(createMail(MAIL_1));
        testee.store(createMail(MAIL_2));

        testee.remove(MAIL_1);

        assertThat(retrieveRepository().list()).contains(MAIL_2);
    }

    @Test
    default void removedMailsShouldNotBeListed() throws Exception {
        MailRepository testee = retrieveRepository();

        MailKey key3 = new MailKey("mail3");
        Mail mail1 = createMail(MAIL_1);
        Mail mail2 = createMail(MAIL_2);
        Mail mail3 = createMail(key3);
        retrieveRepository().store(mail1);
        retrieveRepository().store(mail2);
        retrieveRepository().store(mail3);

        testee.remove(ImmutableList.of(mail1, mail3));

        assertThat(retrieveRepository().list())
            .contains(MAIL_2)
            .doesNotContain(MAIL_1, key3);
    }

    @Test
    default void removedMailShouldNotBeListed() throws Exception {
        MailRepository testee = retrieveRepository();

        MailKey key3 = new MailKey("mail3");
        Mail mail1 = createMail(MAIL_1);
        Mail mail2 = createMail(MAIL_2);
        Mail mail3 = createMail(key3);
        retrieveRepository().store(mail1);
        retrieveRepository().store(mail2);
        retrieveRepository().store(mail3);

        testee.remove(mail2);

        assertThat(retrieveRepository().list())
            .contains(MAIL_1, key3)
            .doesNotContain(MAIL_2);
    }

    @Test
    default void removeShouldHaveNoEffectForUnknownMails() throws Exception {
        MailRepository testee = retrieveRepository();

        testee.remove(ImmutableList.of(createMail(UNKNOWN_KEY)));

        assertThat(retrieveRepository().list()).isEmpty();
    }

    @Test
    default void removeShouldHaveNoEffectOnSizeWhenUnknownKeys() throws Exception {
        MailRepository testee = retrieveRepository();

        Mail mail1 = createMail(MAIL_1);
        testee.store(mail1);

        testee.remove(ImmutableList.of(createMail(UNKNOWN_KEY)));

        assertThat(testee.size()).isEqualTo(1);
    }

    @Test
    default void storeShouldHaveNoEffectOnSizeWhenAlreadyStoredMail() throws Exception {
        MailRepository testee = retrieveRepository();

        Mail mail1 = createMail(MAIL_1);
        testee.store(mail1);
        testee.store(mail1);

        assertThat(testee.size()).isEqualTo(1);
    }

    @Test
    default void removeShouldHaveNoEffectForUnknownMail() throws Exception {
        MailRepository testee = retrieveRepository();

        testee.remove(createMail(UNKNOWN_KEY));

        assertThat(retrieveRepository().list()).isEmpty();
    }

    @Test
    default void listShouldReturnStoredMailsKeys() throws Exception {
        MailRepository testee = retrieveRepository();
        testee.store(createMail(MAIL_1));

        testee.store(createMail(MAIL_2));

        assertThat(testee.list()).containsOnly(MAIL_1, MAIL_2);
    }

    @Test
    default void storingMessageWithSameKeyTwiceShouldUpdateMessageContent() throws Exception {
        MailRepository testee = retrieveRepository();
        testee.store(createMail(MAIL_1));

        Mail updatedMail = createMail(MAIL_1, "modified content");
        testee.store(updatedMail);

        assertThat(testee.list()).hasSize(1);
        assertThat(testee.retrieve(MAIL_1)).satisfies(actual -> checkMailEquality(actual, updatedMail));
    }

    @Test
    default void storingMessageWithSameKeyTwiceShouldUpdateMessageAttributes() throws Exception {
        MailRepository testee = retrieveRepository();
        Mail mail = createMail(MAIL_1);
        testee.store(mail);

        mail.setAttribute(new Attribute(TEST_ATTRIBUTE.getName(), AttributeValue.of("newValue")));
        testee.store(mail);

        assertThat(testee.list()).hasSize(1);
        assertThat(testee.retrieve(MAIL_1)).satisfies(actual -> checkMailEquality(actual, mail));
    }


    @Test
    default void storingMessageWithPerRecipientHeadersShouldAllowMultipleHeadersPerUser() throws Exception {

        MailRepository testee = retrieveRepository();
        Mail mail = createMail(MAIL_1);
        MailAddress recipient1 = new MailAddress("rec1@domain.com");
        mail.addSpecificHeaderForRecipient(PerRecipientHeaders.Header.builder().name("foo").value("bar").build(), recipient1);
        mail.addSpecificHeaderForRecipient(PerRecipientHeaders.Header.builder().name("fizz").value("buzz").build(), recipient1);
        testee.store(mail);

        assertThat(testee.list()).hasSize(1).containsOnly(MAIL_1);
        assertThat(testee.retrieve(MAIL_1)).satisfies(actual -> checkMailEquality(actual, mail));
    }

    @RepeatedTest(100)
    default void storingAndRemovingMessagesConcurrentlyShouldLeadToConsistentResult() throws Exception {
        MailRepository testee = retrieveRepository();
        int nbKeys = 20;
        ConcurrentHashMap.KeySetView<MailKey, Boolean> expectedResult = ConcurrentHashMap.newKeySet();
        List<Object> locks = IntStream.range(0, nbKeys)
            .boxed()
            .collect(Guavate.toImmutableList());

        ThrowingRunnable add = () -> {
            int keyIndex = computeKeyIndex(nbKeys, Math.abs(ThreadLocalRandom.current().nextInt()));
            MailKey key =  computeKey(keyIndex);
            synchronized (locks.get(keyIndex)) {
                testee.store(createMail(key));
                expectedResult.add(key);
            }
        };

        ThrowingRunnable remove = () -> {
            int keyIndex = computeKeyIndex(nbKeys, Math.abs(ThreadLocalRandom.current().nextInt()));
            MailKey key =  computeKey(keyIndex);
            synchronized (locks.get(keyIndex)) {
                testee.remove(key);
                expectedResult.remove(key);
            }
        };

        DiscreteDistribution<ThrowingRunnable> distribution = DiscreteDistribution.create(
            ImmutableList.of(
                new DistributionEntry<>(add, 0.25),
                new DistributionEntry<>(remove, 0.75)));

        ConcurrentTestRunner.builder()
            .operation((a, b) -> distribution.sample().run())
            .threadCount(5)
            .operationCount(20)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(testee.list()).containsOnlyElementsOf(expectedResult);
    }

    default MailKey computeKey(int keyIndex) {
        return new MailKey("mail" + keyIndex);
    }

    default int computeKeyIndex(int nbKeys, Integer i) {
        return i % nbKeys;
    }

}
