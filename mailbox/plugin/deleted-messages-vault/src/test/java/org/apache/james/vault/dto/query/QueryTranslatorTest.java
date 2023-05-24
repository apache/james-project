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

package org.apache.james.vault.dto.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.FieldName;
import org.apache.james.vault.search.Operator;
import org.apache.james.vault.search.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class QueryTranslatorTest {

    private QueryTranslator queryTranslator;

    @BeforeEach
    void beforeEach() {
        queryTranslator = new QueryTranslator(new InMemoryId.Factory());
    }

    @Test
    void translateShouldThrowWhenPassingNotAndOperator() {
        assertThatThrownBy(() -> queryTranslator.translate(new QueryDTO("or", ImmutableList.of(), Optional.empty())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("combinator 'or' is not yet handled");
    }

    @Test
    void translateShouldNotThrowWhenPassingNullOperator() {
        String nullOperator = null;
        assertThatCode(() -> queryTranslator.translate(new QueryDTO(nullOperator, ImmutableList.of(), Optional.empty())))
            .doesNotThrowAnyException();
    }

    @Test
    void translateShouldThrowWhenPassingNestedQuery() {
        assertThatThrownBy(() -> queryTranslator.translate(QueryDTO.and(
            QueryDTO.and(new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.CONTAINS.getValue(), "james"))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("nested query structure is not yet handled");
    }

    @Test
    void translateShouldNotThrowWhenPassingFlattenQuery() {
        assertThatCode(() -> queryTranslator.translate(QueryDTO.and(
            new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.CONTAINS.getValue(), "james"),
            new CriterionDTO(FieldName.SENDER.getValue(), Operator.EQUALS.getValue(), "user@james.org"),
            new CriterionDTO(FieldName.HAS_ATTACHMENT.getValue(), Operator.EQUALS.getValue(), "true")
        )))
            .doesNotThrowAnyException();
    }

    @Test
    void toDTOShouldConvertQueryWithDeletionDateBeforeOrEquals() throws Exception {
        String serializedDate = "2007-12-03T10:15:30+01:00[Europe/Paris]";
        Query query = Query.of(
            CriterionFactory.deletionDate().beforeOrEquals(ZonedDateTime.parse(serializedDate))
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.DELETION_DATE.getValue(), Operator.BEFORE_OR_EQUALS.getValue(), serializedDate)
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithDeletionDateAfterOrEquals() throws Exception {
        String serializedDate = "2007-12-03T10:15:30+01:00[Europe/Paris]";
        Query query = Query.of(
            CriterionFactory.deletionDate().afterOrEquals(ZonedDateTime.parse(serializedDate))
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.DELETION_DATE.getValue(), Operator.AFTER_OR_EQUALS.getValue(), serializedDate)
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithDeliveryDateBeforeOrEquals() throws Exception {
        String serializedDate = "2007-12-03T10:15:30+01:00[Europe/Paris]";
        Query query = Query.of(
            CriterionFactory.deliveryDate().beforeOrEquals(ZonedDateTime.parse(serializedDate))
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.DELIVERY_DATE.getValue(), Operator.BEFORE_OR_EQUALS.getValue(), serializedDate)
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithDeliveryDateAfterOrEquals() throws Exception {
        String serializedDate = "2007-12-03T10:15:30+01:00[Europe/Paris]";
        Query query = Query.of(
            CriterionFactory.deliveryDate().afterOrEquals(ZonedDateTime.parse(serializedDate))
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.DELIVERY_DATE.getValue(), Operator.AFTER_OR_EQUALS.getValue(), serializedDate)
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithRecipientsContains() throws Exception {
        Query query = Query.of(
            CriterionFactory.containsRecipient(new MailAddress("user@james.org"))
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.RECIPIENTS.getValue(), Operator.CONTAINS.getValue(), "user@james.org")
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithSenderEquals() throws Exception {
        Query query = Query.of(
            CriterionFactory.hasSender(new MailAddress("user@james.org"))
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.SENDER.getValue(), Operator.EQUALS.getValue(), "user@james.org")
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithHasAttachement() {
        Query query = Query.of(
            CriterionFactory.hasAttachment(true)
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.HAS_ATTACHMENT.getValue(), Operator.EQUALS.getValue(), "true")
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithSubjectEquals() {
        Query query = Query.of(
            CriterionFactory.subject().equals("james")
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.EQUALS.getValue(), "james")
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithSubjectEqualsIgnoreCase() {
        Query query = Query.of(
            CriterionFactory.subject().equalsIgnoreCase("james")
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.EQUALS_IGNORE_CASE.getValue(), "james")
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithSubjectContains() {
        Query query = Query.of(
            CriterionFactory.subject().contains("james")
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.CONTAINS.getValue(), "james")
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithSubjectContainsIgnoreCase() {
        Query query = Query.of(
            CriterionFactory.subject().containsIgnoreCase("james")
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.CONTAINS_IGNORE_CASE.getValue(), "james")
        ));
    }

    @Test
    void toDTOShouldConvertQueryWithContainsOriginMailbox() {
        Query query = Query.of(
            CriterionFactory.containsOriginMailbox(TestId.of(1L))
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.ORIGIN_MAILBOXES.getValue(), Operator.CONTAINS.getValue(), "1")
        ));
    }

    @Test
    void toDTOShouldConvertFlattenQuery() throws Exception {
        Query query = Query.of(
            CriterionFactory.subject().contains("james"),
            CriterionFactory.hasSender(new MailAddress("user@james.org")),
            CriterionFactory.hasAttachment(true)
        );
        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(
            new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.CONTAINS.getValue(), "james"),
            new CriterionDTO(FieldName.SENDER.getValue(), Operator.EQUALS.getValue(), "user@james.org"),
            new CriterionDTO(FieldName.HAS_ATTACHMENT.getValue(), Operator.EQUALS.getValue(), "true")
        ));
    }

    @Test
    void toDTOShouldSuccessWhenHasLimitQuery() throws Exception {
        Query query = Query.of(11,
            List.of(CriterionFactory.subject().contains("james"),
                CriterionFactory.hasSender(new MailAddress("user@james.org")),
                CriterionFactory.hasAttachment(true)));

        assertThat(queryTranslator.toDTO(query)).isEqualTo(QueryDTO.and(11L,
            new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.CONTAINS.getValue(), "james"),
            new CriterionDTO(FieldName.SENDER.getValue(), Operator.EQUALS.getValue(), "user@james.org"),
            new CriterionDTO(FieldName.HAS_ATTACHMENT.getValue(), Operator.EQUALS.getValue(), "true")));
    }
}