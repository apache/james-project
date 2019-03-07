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

package org.apache.james.webadmin.vault.routes.query;

import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldName.DELETION_DATE;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldName.DELIVERY_DATE;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldName.HAS_ATTACHMENT;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldName.ORIGIN_MAILBOXES;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldName.RECIPIENTS;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldName.SENDER;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldName.SUBJECT;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldValueParser.BOOLEAN_PARSER;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldValueParser.MAIL_ADDRESS_PARSER;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldValueParser.STRING_PARSER;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.FieldValueParser.ZONED_DATE_TIME_PARSER;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.Operator.AFTER_OR_EQUALS;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.Operator.BEFORE_OR_EQUALS;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.Operator.CONTAINS;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.Operator.CONTAINS_IGNORE_CASE;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.Operator.EQUALS;
import static org.apache.james.webadmin.vault.routes.query.QueryTranslator.Operator.EQUALS_IGNORE_CASE;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.vault.search.Criterion;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.Query;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableTable;

public class QueryTranslator {

    public static class QueryTranslatorException extends RuntimeException {
        QueryTranslatorException(String message) {
            super(message);
        }
    }

    enum Combinator {
        AND("and");

        private final String value;

        Combinator(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    enum FieldName {
        DELETION_DATE("deletionDate"),
        DELIVERY_DATE("deliveryDate"),
        RECIPIENTS("recipients"),
        SENDER("sender"),
        HAS_ATTACHMENT("hasAttachment"),
        ORIGIN_MAILBOXES("originMailboxes"),
        SUBJECT("subject");

        static FieldName getField(String fieldNameString) throws QueryTranslatorException {
            return Stream.of(values())
                .filter(fieldName -> fieldName.value.equals(fieldNameString))
                .findFirst()
                .orElseThrow(() -> new QueryTranslatorException("fieldName: '" + fieldNameString + "' is not supported"));
        }

        private final String value;

        FieldName(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    enum Operator {
        EQUALS("equals"),
        EQUALS_IGNORE_CASE("equalsIgnoreCase"),
        CONTAINS("contains"),
        CONTAINS_IGNORE_CASE("containsIgnoreCase"),
        BEFORE_OR_EQUALS("beforeOrEquals"),
        AFTER_OR_EQUALS("afterOrEquals");

        static Operator getOperator(String operator) throws QueryTranslatorException {
            return Stream.of(values())
                .filter(operatorString -> operatorString.value.equals(operator))
                .findFirst()
                .orElseThrow(() -> new QueryTranslatorException("operator: '" + operator + "' is not supported"));
        }

        private final String value;

        Operator(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    interface FieldValueParser<T> {

        class MailboxIdValueParser implements FieldValueParser<MailboxId> {

            final MailboxId.Factory mailboxIdFactory;

            MailboxIdValueParser(MailboxId.Factory mailboxIdFactory) {
                this.mailboxIdFactory = mailboxIdFactory;
            }

            @Override
            public MailboxId parse(String mailboxIdString) {
                return mailboxIdFactory.fromString(mailboxIdString);
            }
        }

        FieldValueParser<ZonedDateTime> ZONED_DATE_TIME_PARSER = ZonedDateTime::parse;
        FieldValueParser<String> STRING_PARSER = input -> input;
        FieldValueParser<Boolean> BOOLEAN_PARSER = Boolean::valueOf;
        FieldValueParser<MailAddress> MAIL_ADDRESS_PARSER = FieldValueParser::parseMailAddress;

        static MailAddress parseMailAddress(String mailAddressString) throws QueryTranslatorException {
            try {
                return new MailAddress(mailAddressString);
            } catch (AddressException e) {
                throw new QueryTranslatorException("mailAddress(" + mailAddressString + ") parsing got error: " + e.getMessage());
            }
        }

        T parse(String input);
    }

    private final ImmutableTable<FieldName, Operator, Function<String, Criterion>> criterionRegistry;

    @Inject
    public QueryTranslator(MailboxId.Factory mailboxIdFactory) {
        criterionRegistry = withMailboxIdCriterionParser(mailboxIdFactory);
    }

    private ImmutableTable<FieldName, Operator, Function<String, Criterion>> withMailboxIdCriterionParser(MailboxId.Factory mailboxIdFactor) {
        FieldValueParser.MailboxIdValueParser mailboxIdParser = new FieldValueParser.MailboxIdValueParser(mailboxIdFactor);

        return defaultRegistryBuilder()
            .put(ORIGIN_MAILBOXES, CONTAINS, testedValue -> CriterionFactory.containsOriginMailbox(mailboxIdParser.parse(testedValue)))
            .build();
    }

    private ImmutableTable.Builder<FieldName, Operator, Function<String, Criterion>> defaultRegistryBuilder() {
        return ImmutableTable.<FieldName, Operator, Function<String, Criterion>>builder()
            .put(DELETION_DATE, BEFORE_OR_EQUALS, testedValue -> CriterionFactory.deletionDate().beforeOrEquals(ZONED_DATE_TIME_PARSER.parse(testedValue)))
            .put(DELETION_DATE, AFTER_OR_EQUALS, testedValue -> CriterionFactory.deletionDate().afterOrEquals(ZONED_DATE_TIME_PARSER.parse(testedValue)))
            .put(DELIVERY_DATE, BEFORE_OR_EQUALS, testedValue -> CriterionFactory.deliveryDate().beforeOrEquals(ZONED_DATE_TIME_PARSER.parse(testedValue)))
            .put(DELIVERY_DATE, AFTER_OR_EQUALS, testedValue -> CriterionFactory.deliveryDate().afterOrEquals(ZONED_DATE_TIME_PARSER.parse(testedValue)))
            .put(RECIPIENTS, CONTAINS, testedValue -> CriterionFactory.containsRecipient(MAIL_ADDRESS_PARSER.parse(testedValue)))
            .put(SENDER, EQUALS, testedValue -> CriterionFactory.hasSender(MAIL_ADDRESS_PARSER.parse(testedValue)))
            .put(HAS_ATTACHMENT, EQUALS, testedValue -> CriterionFactory.hasAttachment(BOOLEAN_PARSER.parse(testedValue)))
            .put(SUBJECT, EQUALS, testedValue -> CriterionFactory.subject().equals(STRING_PARSER.parse(testedValue)))
            .put(SUBJECT, EQUALS_IGNORE_CASE, testedValue -> CriterionFactory.subject().equalsIgnoreCase(STRING_PARSER.parse(testedValue)))
            .put(SUBJECT, CONTAINS, testedValue -> CriterionFactory.subject().contains(STRING_PARSER.parse(testedValue)))
            .put(SUBJECT, CONTAINS_IGNORE_CASE, testedValue -> CriterionFactory.subject().containsIgnoreCase(STRING_PARSER.parse(testedValue)));
    }

    private Criterion translate(CriterionDTO dto) throws QueryTranslatorException {
        return Optional.ofNullable(getCriterionParser(dto))
            .map(criterionGeneratingFunction -> criterionGeneratingFunction.apply(dto.getValue()))
            .orElseThrow(() -> new QueryTranslatorException("pair of fieldName: '" + dto.getFieldName() + "' and operator: '" + dto.getOperator() + "' is not supported"));
    }

    private Function<String, Criterion> getCriterionParser(CriterionDTO dto) {
        return criterionRegistry.get(
            FieldName.getField(dto.getFieldName()),
            Operator.getOperator(dto.getOperator()));
    }

    public Query translate(QueryElement queryElement) throws QueryTranslatorException {
        if (queryElement instanceof QueryDTO) {
            return translate((QueryDTO) queryElement);
        } else if (queryElement instanceof CriterionDTO) {
            return Query.of(translate((CriterionDTO) queryElement));
        }
        throw new IllegalArgumentException("cannot resolve query type: " + queryElement.getClass().getName());
    }

    Query translate(QueryDTO queryDTO) throws QueryTranslatorException {
        Preconditions.checkArgument(combinatorIsValid(queryDTO.getCombinator()), "combinator '" + queryDTO.getCombinator() + "' is not yet handled");
        Preconditions.checkArgument(queryDTO.getCriteria().stream().allMatch(this::isCriterion), "nested query structure is not yet handled");

        return Query.and(queryDTO.getCriteria().stream()
            .map(queryElement -> (CriterionDTO) queryElement)
            .map(Throwing.function(this::translate))
            .collect(Guavate.toImmutableList()));
    }

    private boolean combinatorIsValid(String combinator) {
        return Combinator.AND.getValue().equals(combinator)
            || Objects.isNull(combinator);
    }

    private boolean isCriterion(QueryElement queryElement) {
        return queryElement instanceof CriterionDTO;
    }
}
