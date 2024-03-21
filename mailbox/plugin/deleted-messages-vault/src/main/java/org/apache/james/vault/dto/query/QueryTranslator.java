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

import static org.apache.james.vault.dto.query.QueryTranslator.FieldValueParser.BOOLEAN_PARSER;
import static org.apache.james.vault.dto.query.QueryTranslator.FieldValueParser.MAIL_ADDRESS_PARSER;
import static org.apache.james.vault.dto.query.QueryTranslator.FieldValueParser.STRING_PARSER;
import static org.apache.james.vault.dto.query.QueryTranslator.FieldValueParser.ZONED_DATE_TIME_PARSER;
import static org.apache.james.vault.search.Operator.AFTER_OR_EQUALS;
import static org.apache.james.vault.search.Operator.BEFORE_OR_EQUALS;
import static org.apache.james.vault.search.Operator.CONTAINS;
import static org.apache.james.vault.search.Operator.CONTAINS_IGNORE_CASE;
import static org.apache.james.vault.search.Operator.EQUALS;
import static org.apache.james.vault.search.Operator.EQUALS_IGNORE_CASE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.vault.search.Combinator;
import org.apache.james.vault.search.Criterion;
import org.apache.james.vault.search.CriterionFactory;
import org.apache.james.vault.search.FieldName;
import org.apache.james.vault.search.Operator;
import org.apache.james.vault.search.Query;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;

public class QueryTranslator {

    public static class QueryTranslatorException extends RuntimeException {
        QueryTranslatorException(String message) {
            super(message);
        }
    }

    public static FieldName getField(String fieldNameString) throws QueryTranslator.QueryTranslatorException {
        return Stream.of(FieldName.values())
            .filter(fieldName -> fieldName.getValue().equals(fieldNameString))
            .findFirst()
            .orElseThrow(() -> new QueryTranslator.QueryTranslatorException("fieldName: '" + fieldNameString + "' is not supported"));
    }

    static Operator getOperator(String operator) throws QueryTranslatorException {
        return Stream.of(Operator.values())
            .filter(operatorString -> operatorString.getValue().equals(operator))
            .findFirst()
            .orElseThrow(() -> new QueryTranslatorException("operator: '" + operator + "' is not supported"));
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

    interface FieldValueSerializer<T> {

        FieldValueSerializer<MailboxId> MAILBOX_ID_SERIALIZER = MailboxId::serialize;
        FieldValueSerializer<ZonedDateTime> ZONED_DATE_TIME_SERIALIZER = ZonedDateTime::toString;
        FieldValueSerializer<String> STRING_SERIALIZER = input -> input;
        FieldValueSerializer<Boolean> BOOLEAN_SERIALIZER = Object::toString;
        FieldValueSerializer<MailAddress> MAIL_ADDRESS_SERIALIZER = MailAddress::asString;

        @SuppressWarnings("rawtypes")
        static Optional<FieldValueSerializer> getSerializerForValue(Object value) {
            if (value instanceof MailboxId) {
                return Optional.of(MAILBOX_ID_SERIALIZER);
            }
            if (value instanceof ZonedDateTime) {
                return Optional.of(ZONED_DATE_TIME_SERIALIZER);
            }
            if (value instanceof String) {
                return Optional.of(STRING_SERIALIZER);
            }
            if (value instanceof Boolean) {
                return Optional.of(BOOLEAN_SERIALIZER);
            }
            if (value instanceof MailAddress) {
                return Optional.of(MAIL_ADDRESS_SERIALIZER);
            }
            return Optional.empty();
        }

       String serialize(T input);
    }

    private final ImmutableTable<FieldName, Operator, Function<String, Criterion<?>>> criterionRegistry;

    @Inject
    @VisibleForTesting
    public QueryTranslator(MailboxId.Factory mailboxIdFactory) {
        criterionRegistry = withMailboxIdCriterionParser(mailboxIdFactory);
    }

    private ImmutableTable<FieldName, Operator, Function<String, Criterion<?>>> withMailboxIdCriterionParser(MailboxId.Factory mailboxIdFactor) {
        FieldValueParser.MailboxIdValueParser mailboxIdParser = new FieldValueParser.MailboxIdValueParser(mailboxIdFactor);

        return defaultRegistryBuilder()
            .put(FieldName.ORIGIN_MAILBOXES, CONTAINS, testedValue -> CriterionFactory.containsOriginMailbox(mailboxIdParser.parse(testedValue)))
            .build();
    }

     private ImmutableTable.Builder<FieldName, Operator, Function<String, Criterion<?>>> defaultRegistryBuilder() {
        return ImmutableTable.<FieldName, Operator, Function<String, Criterion<?>>>builder()
            .put(FieldName.DELETION_DATE, BEFORE_OR_EQUALS, testedValue -> CriterionFactory.deletionDate().beforeOrEquals(ZONED_DATE_TIME_PARSER.parse(testedValue)))
            .put(FieldName.DELETION_DATE, AFTER_OR_EQUALS, testedValue -> CriterionFactory.deletionDate().afterOrEquals(ZONED_DATE_TIME_PARSER.parse(testedValue)))
            .put(FieldName.DELIVERY_DATE, BEFORE_OR_EQUALS, testedValue -> CriterionFactory.deliveryDate().beforeOrEquals(ZONED_DATE_TIME_PARSER.parse(testedValue)))
            .put(FieldName.DELIVERY_DATE, AFTER_OR_EQUALS, testedValue -> CriterionFactory.deliveryDate().afterOrEquals(ZONED_DATE_TIME_PARSER.parse(testedValue)))
            .put(FieldName.RECIPIENTS, CONTAINS, testedValue -> CriterionFactory.containsRecipient(MAIL_ADDRESS_PARSER.parse(testedValue)))
            .put(FieldName.SENDER, EQUALS, testedValue -> CriterionFactory.hasSender(MAIL_ADDRESS_PARSER.parse(testedValue)))
            .put(FieldName.HAS_ATTACHMENT, EQUALS, testedValue -> CriterionFactory.hasAttachment(BOOLEAN_PARSER.parse(testedValue)))
            .put(FieldName.SUBJECT, EQUALS, testedValue -> CriterionFactory.subject().equals(STRING_PARSER.parse(testedValue)))
            .put(FieldName.SUBJECT, EQUALS_IGNORE_CASE, testedValue -> CriterionFactory.subject().equalsIgnoreCase(STRING_PARSER.parse(testedValue)))
            .put(FieldName.SUBJECT, CONTAINS, testedValue -> CriterionFactory.subject().contains(STRING_PARSER.parse(testedValue)))
            .put(FieldName.SUBJECT, CONTAINS_IGNORE_CASE, testedValue -> CriterionFactory.subject().containsIgnoreCase(STRING_PARSER.parse(testedValue)));
    }

    private Criterion<?> translate(CriterionDTO dto) throws QueryTranslatorException {
        return Optional.ofNullable(getCriterionParser(dto))
            .map(criterionGeneratingFunction -> criterionGeneratingFunction.apply(dto.getValue()))
            .orElseThrow(() -> new QueryTranslatorException("pair of fieldName: '" + dto.getFieldName() + "' and operator: '" + dto.getOperator() + "' is not supported"));
    }

    private Function<String, Criterion<?>> getCriterionParser(CriterionDTO dto) {
        return getCriterionParser(getField(dto.getFieldName()), getOperator(dto.getOperator()));
    }

    private Function<String, Criterion<?>> getCriterionParser(FieldName fieldName, Operator operator) {
        return criterionRegistry.get(fieldName, operator);
    }

    public Query translate(QueryElement queryElement) throws QueryTranslatorException {
        if (queryElement instanceof QueryDTO) {
            return translate((QueryDTO) queryElement);
        } else if (queryElement instanceof CriterionDTO) {
            return Query.of(translate((CriterionDTO) queryElement));
        }
        throw new IllegalArgumentException("cannot resolve query type: " + queryElement.getClass().getName());
    }

    public QueryDTO toDTO(Query query) throws QueryTranslatorException {
        List<QueryElement> queryElements = query.getCriteria().stream()
            .map(this::toDTO)
            .collect(ImmutableList.toImmutableList());
        return new QueryDTO(Combinator.AND.getValue(), queryElements, query.getLimit());
    }

    private CriterionDTO toDTO(Criterion<?> criterion) {
        FieldName fieldName = criterion.getField().fieldName();
        Operator operator = criterion.getValueMatcher().operator();
        Object value = criterion.getValueMatcher().expectedValue();

        @SuppressWarnings("rawtypes")
        FieldValueSerializer fieldValueSerializer = FieldValueSerializer.getSerializerForValue(value).orElseThrow(
            () -> new IllegalArgumentException("Value of type " + value.getClass().getSimpleName()
                + "' is not handled by the combinaison of operator : " + operator.name()
                + " and field :" + fieldName.name()));

        @SuppressWarnings("unchecked")
        CriterionDTO result = new CriterionDTO(fieldName.getValue(), operator.getValue(), fieldValueSerializer.serialize(value));
        return result;
    }

    Query translate(QueryDTO queryDTO) throws QueryTranslatorException {
        Preconditions.checkArgument(combinatorIsValid(queryDTO.getCombinator()), "combinator '%s' is not yet handled", queryDTO.getCombinator());
        Preconditions.checkArgument(queryDTO.getCriteria().stream().allMatch(this::isCriterion), "nested query structure is not yet handled");

        return Query.and(queryDTO.getCriteria().stream()
            .map(queryElement -> (CriterionDTO) queryElement)
            .map(Throwing.function(this::translate))
            .collect(ImmutableList.toImmutableList()), queryDTO.getLimit());
    }

    private boolean combinatorIsValid(String combinator) {
        return Combinator.AND.getValue().equals(combinator)
            || Objects.isNull(combinator);
    }

    private boolean isCriterion(QueryElement queryElement) {
        return queryElement instanceof CriterionDTO;
    }
}
