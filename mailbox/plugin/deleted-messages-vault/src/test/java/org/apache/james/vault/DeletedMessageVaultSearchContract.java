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

package org.apache.james.vault;

import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_2;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_OTHER_USER;
import static org.apache.james.vault.DeletedMessageFixture.DELETION_DATE;
import static org.apache.james.vault.DeletedMessageFixture.DELIVERY_DATE;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_1;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_2;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_3;
import static org.apache.james.vault.DeletedMessageFixture.SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT3;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.apache.mailet.base.MailAddressFixture.SENDER2;
import static org.apache.mailet.base.MailAddressFixture.SENDER3;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.User;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DeletedMessageVaultSearchContract {
    DeletedMessageVault getVault();

    interface AllContracts extends SubjectContract, DeletionDateContract, DeliveryDateContract, RecipientsContract, SenderContract,
        HasAttachmentsContract, OriginMailboxesContract, PerUserContract, MultipleSearchCriterionsContract, StringByLocaleContract {
    }

    interface DeliveryDateContract extends DeletedMessageVaultSearchContract {

        @Test
        default void shouldReturnMessagesWithDeliveryBeforeDateWhenBeforeOrEquals() {
            DeletedMessage message1 = storeMessageWithDeliveryDate(DELIVERY_DATE);
            storeMessageWithDeliveryDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deliveryDate().beforeOrEquals(DELIVERY_DATE.plusMinutes(30)))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesWithDeliveryEqualDateWhenBeforeOrEquals() {
            DeletedMessage message1 = storeMessageWithDeliveryDate(DELIVERY_DATE);
            storeMessageWithDeliveryDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deliveryDate().beforeOrEquals(DELIVERY_DATE))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesWithDeliveryAfterDateWhenAfterOrEquals() {
            storeMessageWithDeliveryDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeliveryDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deliveryDate().afterOrEquals(DELIVERY_DATE.plusMinutes(30)))))
                .containsOnly(message2);
        }

        @Test
        default void shouldReturnMessagesWithDeliveryEqualDateWhenAfterOrEquals() {
            storeMessageWithDeliveryDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeliveryDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deliveryDate().afterOrEquals(DELIVERY_DATE.plusMinutes(60)))))
                .containsOnly(message2);
        }
    }

    interface DeletionDateContract extends DeletedMessageVaultSearchContract {

        @Test
        default void shouldReturnMessagesWithDeletionBeforeDateWhenBeforeOrEquals() {
            DeletedMessage message1 = storeMessageWithDeletionDate(DELIVERY_DATE);
            storeMessageWithDeletionDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deletionDate().beforeOrEquals(DELIVERY_DATE.plusMinutes(30)))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesWithDeletionEqualDateWhenBeforeOrEquals() {
            DeletedMessage message1 = storeMessageWithDeletionDate(DELIVERY_DATE);
            storeMessageWithDeletionDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deletionDate().beforeOrEquals(DELIVERY_DATE))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesWithDeletionAfterDateWhenAfterOrEquals() {
            storeMessageWithDeletionDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeletionDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deletionDate().afterOrEquals(DELIVERY_DATE.plusMinutes(30)))))
                .containsOnly(message2);
        }

        @Test
        default void shouldReturnMessagesWithDeletionEqualDateWhenAfterOrEquals() {
            storeMessageWithDeletionDate(DELIVERY_DATE);
            DeletedMessage message2 = storeMessageWithDeletionDate(DELIVERY_DATE.plusMinutes(60));

            assertThat(search(Query.of(CriterionFactory.deletionDate().afterOrEquals(DELIVERY_DATE.plusMinutes(60)))))
                .containsOnly(message2);
        }
    }

    interface RecipientsContract extends DeletedMessageVaultSearchContract {

        @Test
        default void shouldReturnMessagesWithRecipientWhenContains() {
            DeletedMessage message1 = storeMessageWithRecipients(RECIPIENT1, RECIPIENT2);
            DeletedMessage message2 = storeMessageWithRecipients(RECIPIENT1);
            storeMessageWithRecipients(RECIPIENT3);

            assertThat(search(Query.of(CriterionFactory.containsRecipient(RECIPIENT1))))
                .containsOnly(message1, message2);

        }

        @Test
        default void shouldReturnNoMessageWhenDoesntContains() {
            storeMessageWithRecipients(RECIPIENT1, RECIPIENT2);
            storeMessageWithRecipients(RECIPIENT1);
            storeMessageWithRecipients(RECIPIENT2);

            assertThat(search(Query.of(CriterionFactory.containsRecipient(RECIPIENT3))))
                .isEmpty();
        }
    }

    interface SenderContract extends DeletedMessageVaultSearchContract {

        @Test
        default void shouldReturnMessagesWithSenderWhenEquals() {
            DeletedMessage message1 = storeMessageWithSender(MaybeSender.of(SENDER));
            storeMessageWithSender(MaybeSender.of(SENDER2));

            assertThat(search(Query.of(CriterionFactory.hasSender(SENDER))))
                .containsOnly(message1);

        }

        @Test
        default void shouldReturnNoMessageWhenSenderDoesntEquals() {
            storeMessageWithSender(MaybeSender.of(SENDER));
            storeMessageWithSender(MaybeSender.of(SENDER2));

            assertThat(search(Query.of(CriterionFactory.hasSender(SENDER3))))
                .isEmpty();
        }

        @Test
        default void shouldNotReturnMessagesWithNullSenderWhenEquals() {
            DeletedMessage message1 = storeMessageWithSender(MaybeSender.of(SENDER));
            storeMessageWithSender(MaybeSender.of(SENDER2));
            storeMessageWithSender(MaybeSender.nullSender());
            storeMessageWithSender(MaybeSender.nullSender());

            assertThat(search(Query.of(CriterionFactory.hasSender(SENDER))))
                .containsOnly(message1);
        }
    }

    interface HasAttachmentsContract extends DeletedMessageVaultSearchContract {

        @Test
        default void shouldReturnMessagesWithAttachmentWhenHasAttachment() {
            DeletedMessage message1 = storeMessageWithHasAttachment(true);
            storeMessageWithHasAttachment(false);
            DeletedMessage message3 = storeMessageWithHasAttachment(true);

            assertThat(search(Query.of(CriterionFactory.hasAttachment())))
                .containsOnly(message1, message3);
        }

        @Test
        default void shouldReturnMessagesWithOutAttachmentWhenHasNoAttachement() {
            DeletedMessage message1 = storeMessageWithHasAttachment(false);
            DeletedMessage message2 = storeMessageWithHasAttachment(false);
            storeMessageWithHasAttachment(true);

            assertThat(search(Query.of(CriterionFactory.hasNoAttachment())))
                .containsOnly(message1, message2);
        }
    }

    interface OriginMailboxesContract extends DeletedMessageVaultSearchContract {

        @Test
        default void shouldReturnMessagesWithOriginMailboxesWhenContains() {
            DeletedMessage message1 = storeMessageWithOriginMailboxes(MAILBOX_ID_1, MAILBOX_ID_2);
            DeletedMessage message2 = storeMessageWithOriginMailboxes(MAILBOX_ID_1);
            storeMessageWithOriginMailboxes(MAILBOX_ID_3);

            assertThat(search(Query.of(CriterionFactory.containsOriginMailbox(MAILBOX_ID_1))))
                .containsOnly(message1, message2);

        }

        @Test
        default void shouldReturnNoMessageWhenOriginMailboxesDoesntContains() {
            storeMessageWithOriginMailboxes(MAILBOX_ID_1, MAILBOX_ID_2);
            storeMessageWithOriginMailboxes(MAILBOX_ID_1);
            storeMessageWithOriginMailboxes(MAILBOX_ID_2);

            assertThat(search(Query.of(CriterionFactory.containsOriginMailbox(MAILBOX_ID_3))))
                .isEmpty();
        }
    }

    interface SubjectContract extends DeletedMessageVaultSearchContract {

        String APACHE_JAMES_PROJECT = "apache james project";
        String OPEN_SOURCE_SOFTWARE = "open source software";

        @Test
        default void shouldReturnMessagesContainsAtTheMiddle() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().contains("james"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesContainsAtTheBeginning() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().contains("apache"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesContainsAtTheEnd() {
            storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().contains("software"))))
                .containsOnly(message2);
        }

        @Test
        default void shouldNotReturnMissingSubjectMessagesWhenContains() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageNoSubject();

            assertThat(search(Query.of(CriterionFactory.subject().contains("james"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldNotReturnMessagesContainsIgnoreCaseWhenContains() {
            storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().contains("SoftWare"))))
                .isEmpty();
        }

        @Test
        default void shouldReturnMessagesContainsIgnoreCaseAtTheMiddle() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().containsIgnoreCase("JAmEs"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesContainsIgnoreCaseAtTheBeginning() {
            storeMessageWithSubject(APACHE_JAMES_PROJECT);
            DeletedMessage message2 = storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().containsIgnoreCase("SouRCE"))))
                .containsOnly(message2);
        }

        @Test
        default void shouldReturnMessagesContainsIgnoreCaseAtTheEnd() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(
                    CriterionFactory.subject().containsIgnoreCase("ProJECT"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesContainsWhenContainsIgnoreCase() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(
                    CriterionFactory.subject().containsIgnoreCase("project"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldNotReturnMissingSubjectMessagesWhenContainsIgnoreCase() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageNoSubject();

            assertThat(search(Query.of(CriterionFactory.subject().containsIgnoreCase("JAMes"))))
                .containsOnly(message1);
        }

        @Test
        default void shouldReturnMessagesStrictlyEquals() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equals(APACHE_JAMES_PROJECT))))
                .containsOnly(message1);
        }

        @Test
        default void shouldNotReturnMessagesContainsWhenEquals() {
            storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equals("james"))))
                .isEmpty();
        }

        @Test
        default void shouldNotReturnMessagesContainsIgnoreCaseWhenEquals() {
            storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equals("proJECT"))))
                .isEmpty();
        }

        @Test
        default void shouldNotReturnMessagesEqualsIgnoreCaseWhenEquals() {
            storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equals("Apache James Project"))))
                .isEmpty();
        }

        @Test
        default void shouldReturnMessagesWhenEqualsIgnoreCase() {
            storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equals("Apache James PROJECT"))))
                .isEmpty();
        }

        @Test
        default void shouldReturnMessagesStrictlyEqualsWhenEqualsIgnoreCase() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equalsIgnoreCase(APACHE_JAMES_PROJECT))))
                .containsOnly(message1);
        }

        @Test
        default void shouldNotReturnMessagesContainsWhenEqualsIgnoreCase() {
            storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equalsIgnoreCase("james"))))
                .isEmpty();
        }

        @Test
        default void shouldNotReturnMessagesContainsIgnoreCaseWhenEqualsIgnoreCase() {
            storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageWithSubject(OPEN_SOURCE_SOFTWARE);

            assertThat(search(Query.of(CriterionFactory.subject().equalsIgnoreCase("proJECT"))))
                .isEmpty();
        }

        @Test
        default void shouldNotReturnMissingSubjectMessagesWhenEquals() {
            DeletedMessage message1 = storeMessageWithSubject(APACHE_JAMES_PROJECT);
            storeMessageNoSubject();

            assertThat(search(Query.of(CriterionFactory.subject().equals(APACHE_JAMES_PROJECT))))
                .containsOnly(message1);
        }
    }

    interface MultipleSearchCriterionsContract extends DeletedMessageVaultSearchContract {

        @Test
        default void searchShouldReturnOnlyMessageWhichMatchMultipleCriterions() {
            DeletedMessage message1 = storeDefaultMessage();
            DeletedMessage message2 = storeDefaultMessage();
            DeletedMessage message3 = storeDefaultMessage();
            storeMessageWithOriginMailboxes(MAILBOX_ID_2);
            storeMessageWithSender(MaybeSender.of(SENDER2));
            storeMessageWithDeletionDate(DELETION_DATE.minusHours(1));

            assertThat(search(Query.of(
                    CriterionFactory.containsOriginMailbox(MAILBOX_ID_1),
                    CriterionFactory.hasSender(SENDER),
                    CriterionFactory.deletionDate().afterOrEquals(DELETION_DATE))))
                .containsOnly(message1, message2, message3);
        }

        @Test
        default void searchShouldReturnAllMessageWhenSearchForAllCriterions() {
            DeletedMessage message1 = storeDefaultMessage();
            DeletedMessage message2 = storeDefaultMessage();
            DeletedMessage message3 = storeDefaultMessage();
            DeletedMessage message4 = storeMessageWithOriginMailboxes(MAILBOX_ID_2);
            DeletedMessage message5 = storeMessageWithSender(MaybeSender.of(SENDER2));
            DeletedMessage message6 = storeMessageWithDeletionDate(DELETION_DATE.minusHours(1));

            assertThat(search(Query.ALL))
                .containsOnly(message1, message2, message3, message4, message5, message6);
        }

        @Test
        default void searchShouldReturnAllMessageEvenNullSubjectWhenSearchForAllCriterions() {
            DeletedMessage message1 = storeDefaultMessage();
            DeletedMessage message2 = storeDefaultMessage();
            DeletedMessage message3 = storeDefaultMessage();
            DeletedMessage message4 = storeMessageNoSubject();

            assertThat(search(Query.ALL))
                .containsOnly(message1, message2, message3, message4);
        }

        @Test
        default void searchShouldReturnAllMessageEvenNullSenderWhenSearchForAllCriterions() {
            DeletedMessage message1 = storeDefaultMessage();
            DeletedMessage message2 = storeDefaultMessage();
            DeletedMessage message3 = storeDefaultMessage();
            DeletedMessage message4 = storeMessageWithSender(MaybeSender.nullSender());

            assertThat(search(Query.ALL))
                .containsOnly(message1, message2, message3, message4);
        }

        @Test
        default void searchShouldReturnMessageWhenHavingSameCriterionTypes() {
            DeletedMessage message1 = storeMessageWithRecipients(RECIPIENT1, RECIPIENT2, RECIPIENT3);
            DeletedMessage message2 = storeMessageWithRecipients(RECIPIENT1, RECIPIENT2);
            storeMessageWithRecipients(RECIPIENT1);

            assertThat(search(Query.of(
                    CriterionFactory.containsRecipient(RECIPIENT1),
                    CriterionFactory.containsRecipient(RECIPIENT2))))
                .containsOnly(message1, message2);
        }

        @Test
        default void searchShouldReturnEmptyWhenHavingSameCriterionTypesButOppositeMatching() {
            storeMessageWithDeletionDate(DELETION_DATE);
            storeMessageWithDeletionDate(DELETION_DATE);
            storeMessageWithDeletionDate(DELETION_DATE.plusHours(2));
            storeMessageWithDeletionDate(DELETION_DATE.minusHours(2));

            assertThat(search(Query.of(
                    CriterionFactory.deletionDate().afterOrEquals(DELETION_DATE.plusHours(1)),
                    CriterionFactory.deletionDate().beforeOrEquals(DELETION_DATE.minusHours(1)))))
                .isEmpty();
        }
    }

    interface PerUserContract extends DeletedMessageVaultSearchContract {
        @Test
        default void searchForAnUserShouldNotReturnMessagesFromAnotherUser() {
            DeletedMessage message1 = storeDeletedMessage(DELETED_MESSAGE);
            DeletedMessage message2 = storeDeletedMessage(DELETED_MESSAGE_2);
            storeDeletedMessage(DELETED_MESSAGE_OTHER_USER);

            assertThat(search(USER, Query.ALL))
                .containsOnly(message1, message2);
        }
    }

    interface StringByLocaleContract extends DeletedMessageVaultSearchContract {

        @Disabled("MAILBOX-384 DeletedMessageVault search will return a wrong result in case of using string " +
            "equalsIgnoreCase with a special locale")
        @Test
        default void shouldReturnsMessageWhenPassingAStringInDifferentLocaleToContainsIgnoreCase() {
            Locale turkishLocale = Locale.forLanguageTag("tr");
            String subjectInTurkishLanguage = "TITLE";
            DeletedMessage message1 = storeMessageWithSubject(subjectInTurkishLanguage);

            String subjectLowercase = subjectInTurkishLanguage.toLowerCase(turkishLocale);
            assertThat(search(Query.of(CriterionFactory.subject().containsIgnoreCase(subjectLowercase))))
                .contains(message1);
        }
    }

    AtomicLong MESSAGE_ID_GENERATOR = new AtomicLong(0);

    default List<DeletedMessage> search(Query query) {
        return search(USER, query);
    }

    default List<DeletedMessage> search(User user, Query query) {
        return Flux.from(getVault().search(user, query)).collectList().block();
    }

    default DeletedMessage storeMessageWithDeliveryDate(ZonedDateTime deliveryDate) {
        DeletedMessage deletedMessage = defaultDeletedMessageFinalStage(deliveryDate, DELETION_DATE)
            .build();

        return storeDeletedMessage(deletedMessage);
    }

    default DeletedMessage storeMessageWithDeletionDate(ZonedDateTime delitionDate) {
        DeletedMessage deletedMessage = defaultDeletedMessageFinalStage(DELIVERY_DATE, delitionDate)
            .build();

        return storeDeletedMessage(deletedMessage);
    }

    default DeletedMessage storeMessageWithRecipients(MailAddress... recipients) {
        DeletedMessage deletedMessage = DeletedMessage.builder()
            .messageId(InMemoryMessageId.of(MESSAGE_ID_GENERATOR.incrementAndGet()))
            .originMailboxes(MAILBOX_ID_1)
            .user(USER)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .sender(MaybeSender.of(SENDER))
            .recipients(recipients)
            .hasAttachment(false)
            .size(CONTENT.length)
            .build();

        return storeDeletedMessage(deletedMessage);
    }

    default DeletedMessage storeMessageWithSender(MaybeSender sender) {
        DeletedMessage deletedMessage = DeletedMessage.builder()
            .messageId(InMemoryMessageId.of(MESSAGE_ID_GENERATOR.incrementAndGet()))
            .originMailboxes(MAILBOX_ID_1)
            .user(USER)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .sender(sender)
            .recipients(RECIPIENT1, RECIPIENT2)
            .hasAttachment(false)
            .size(CONTENT.length)
            .build();

        return storeDeletedMessage(deletedMessage);
    }

    default DeletedMessage storeMessageWithHasAttachment(boolean hasAttachment) {
        DeletedMessage deletedMessage = DeletedMessage.builder()
            .messageId(InMemoryMessageId.of(MESSAGE_ID_GENERATOR.incrementAndGet()))
            .originMailboxes(MAILBOX_ID_1)
            .user(USER)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .sender(MaybeSender.of(SENDER))
            .recipients(RECIPIENT1, RECIPIENT2)
            .hasAttachment(hasAttachment)
            .size(CONTENT.length)
            .build();

        return storeDeletedMessage(deletedMessage);
    }

    default DeletedMessage storeMessageWithOriginMailboxes(MailboxId... originMailboxIds) {
        DeletedMessage deletedMessage = DeletedMessage.builder()
            .messageId(InMemoryMessageId.of(MESSAGE_ID_GENERATOR.incrementAndGet()))
            .originMailboxes(originMailboxIds)
            .user(USER)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .sender(MaybeSender.of(SENDER))
            .recipients(RECIPIENT1, RECIPIENT2)
            .hasAttachment(true)
            .size(CONTENT.length)
            .build();

        return storeDeletedMessage(deletedMessage);
    }

    default DeletedMessage storeMessageNoSubject() {
        DeletedMessage deletedMessage = defaultDeletedMessageFinalStage(DELIVERY_DATE, DELETION_DATE)
            .build();

        return storeDeletedMessage(deletedMessage);
    }

    default DeletedMessage storeMessageWithSubject(String subject) {
        DeletedMessage deletedMessage = defaultDeletedMessageFinalStage(DELIVERY_DATE, DELETION_DATE)
            .subject(subject)
            .build();

        return storeDeletedMessage(deletedMessage);
    }

    default DeletedMessage storeDefaultMessage() {
        DeletedMessage deletedMessage = defaultDeletedMessageFinalStage(DELIVERY_DATE, DELETION_DATE)
            .subject(SUBJECT)
            .build();

        return storeDeletedMessage(deletedMessage);
    }

    default DeletedMessage storeDeletedMessage(DeletedMessage deletedMessage) {
        Mono.from(getVault().append(deletedMessage, new ByteArrayInputStream(CONTENT)))
            .block();
        return deletedMessage;
    }

    default DeletedMessage.Builder.FinalStage defaultDeletedMessageFinalStage(ZonedDateTime deliveryDate, ZonedDateTime deletionDate) {
        return DeletedMessage.builder()
            .messageId(InMemoryMessageId.of(MESSAGE_ID_GENERATOR.incrementAndGet()))
            .originMailboxes(MAILBOX_ID_1)
            .user(USER)
            .deliveryDate(deliveryDate)
            .deletionDate(deletionDate)
            .sender(MaybeSender.of(SENDER))
            .recipients(RECIPIENT1)
            .hasAttachment(false)
            .size(CONTENT.length);
    }
}
