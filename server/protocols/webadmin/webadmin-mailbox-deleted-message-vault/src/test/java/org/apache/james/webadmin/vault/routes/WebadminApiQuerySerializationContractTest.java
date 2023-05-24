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
package org.apache.james.webadmin.vault.routes;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.vault.dto.query.CriterionDTO;
import org.apache.james.vault.dto.query.QueryDTO;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.FieldName;
import org.apache.james.vault.search.Operator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;

class WebadminApiQuerySerializationContractTest {

    private static final String AND = "and";
    private static final String USER_JAMES = "james@apache.org";
    private static final MailboxId MAILBOX_1_ID = TestId.of(1L);
    private static final ZonedDateTime ZONED_DATE_TIME = ZonedDateTime.parse("2007-12-03T10:15:30+01:00[Europe/Paris]");
    private static final String SUBJECT = "James";

    private static final String HAS_ATTACHMENT_FILE = "has_attachment.json";
    private static final QueryDTO HAS_ATTACHMENT_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.HAS_ATTACHMENT.getValue(), Operator.EQUALS.getValue(), "true")), Optional.empty());

    private static final String HAS_NO_ATTACHMENT_FILE = "has_no_attachment.json";
    private static final QueryDTO HAS_NO_ATTACHMENT_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.HAS_ATTACHMENT.getValue(), Operator.EQUALS.getValue(), "false")), Optional.empty());

    private static final String HAS_SENDER_FILE = "has_sender.json";
    private static final QueryDTO HAS_SENDER_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.SENDER.getValue(), Operator.EQUALS.getValue(), USER_JAMES)), Optional.empty());

    private static final String CONTAINS_RECIPIENT_FILE = "contains_recipient.json";
    private static final QueryDTO CONTAINS_RECIPIENT_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.RECIPIENTS.getValue(), Operator.CONTAINS.getValue(), USER_JAMES)), Optional.empty());

    private static final String CONTAINS_ORIGIN_MAILBOX_FILE = "contains_origin_mailbox.json";
    private static final QueryDTO CONTAINS_ORIGIN_MAILBOX_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.ORIGIN_MAILBOXES.getValue(), Operator.CONTAINS.getValue(), MAILBOX_1_ID.serialize())), Optional.empty());

    private static final String DELIVERY_BEFORE_FILE = "zoned_date_time_before_or_equals.json";
    private static final QueryDTO DELIVERY_BEFORE_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.DELIVERY_DATE.getValue(), Operator.BEFORE_OR_EQUALS.getValue(), ZONED_DATE_TIME.toString())), Optional.empty());

    private static final String DELETED_AFTER_FILE = "zoned_date_time_after_or_equals.json";
    private static final QueryDTO DELETED_AFTER_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.DELETION_DATE.getValue(), Operator.AFTER_OR_EQUALS.getValue(), ZONED_DATE_TIME.toString())), Optional.empty());

    private static final String SUBJECT_CONTAINS_FILE = "string_contains.json";
    private static final QueryDTO SUBJECT_CONTAINS_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.CONTAINS.getValue(), SUBJECT)), Optional.empty());

    private static final String SUBJECT_CONTAINS_IGNORE_CASE_FILE = "string_contains_ignore_case.json";
    private static final QueryDTO SUBJECT_CONTAINS_IGNORE_CASE_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.CONTAINS_IGNORE_CASE.getValue(), SUBJECT)), Optional.empty());

    private static final String SUBJECT_EQUALS_FILE = "string_equals.json";
    private static final QueryDTO SUBJECT_EQUALS_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.EQUALS.getValue(), SUBJECT)), Optional.empty());

    private static final String SUBJECT_EQUALS_IGNORE_CASE_FILE = "string_equals_ignore_case.json";
    private static final QueryDTO SUBJECT_EQUALS_IGNORE_CASE_DTO = new QueryDTO(AND,
        ImmutableList.of(new CriterionDTO(FieldName.SUBJECT.getValue(), Operator.EQUALS_IGNORE_CASE.getValue(), SUBJECT)), Optional.empty());

    private static final TestId.Factory mailboxIdFactory = new TestId.Factory();
    private static final QueryTranslator queryTranslator = new QueryTranslator(mailboxIdFactory);
    private static final RestoreService restoreService = Mockito.mock(RestoreService.class);
    private static final DeletedMessagesVaultRestoreTaskDTO.Factory factory = new DeletedMessagesVaultRestoreTaskDTO.Factory(restoreService, queryTranslator);
    private static final JsonTaskSerializer taskSerializer = JsonTaskSerializer.of(DeletedMessagesVaultRestoreTaskDTO.module(factory));

    /**
     * Enforce that the format of the query serialized in json in the body of the request to the webadmin is stable.
     * For the time being the query are serialized in the same way for the webadmin API requests and for the EventSystem for the
     * distributed task manager.
     * If you break on of this test. It's time to use different serialization for the internal representation in the Event System
     * and for the user facing format.
     * You should then ensure than a query in one part of the system is interpretable in the other part.
     */
    @ParameterizedTest
    @MethodSource
    void respectAPIContract(String jsonFilePath, QueryDTO expectedDeserializedValue) throws Exception {
        String jsonContent = ClassLoaderUtils.getSystemResourceAsString("query/" + jsonFilePath);
        QueryDTO extractedQueryDTO = factory.createDTO((DeletedMessagesVaultRestoreTask) taskSerializer.deserialize(jsonContent),
            DeletedMessagesVaultRestoreTask.TYPE.asString()).getQuery();
        Assertions.assertThat(extractedQueryDTO).isEqualTo(expectedDeserializedValue);
    }

    static Stream<Arguments> respectAPIContract() {
        return Stream.of(
            Arguments.of(SUBJECT_EQUALS_FILE, SUBJECT_EQUALS_DTO),
            Arguments.of(SUBJECT_EQUALS_IGNORE_CASE_FILE, SUBJECT_EQUALS_IGNORE_CASE_DTO),
            Arguments.of(SUBJECT_CONTAINS_FILE, SUBJECT_CONTAINS_DTO),
            Arguments.of(SUBJECT_CONTAINS_IGNORE_CASE_FILE, SUBJECT_CONTAINS_IGNORE_CASE_DTO),
            Arguments.of(DELETED_AFTER_FILE, DELETED_AFTER_DTO),
            Arguments.of(DELIVERY_BEFORE_FILE, DELIVERY_BEFORE_DTO),
            Arguments.of(CONTAINS_ORIGIN_MAILBOX_FILE, CONTAINS_ORIGIN_MAILBOX_DTO),
            Arguments.of(CONTAINS_RECIPIENT_FILE, CONTAINS_RECIPIENT_DTO),
            Arguments.of(HAS_SENDER_FILE, HAS_SENDER_DTO),
            Arguments.of(HAS_ATTACHMENT_FILE, HAS_ATTACHMENT_DTO),
            Arguments.of(HAS_NO_ATTACHMENT_FILE, HAS_NO_ATTACHMENT_DTO)
        );
    }
}
