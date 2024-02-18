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

package org.apache.james.mailbox.opensearch.query;

import static org.apache.james.backends.opensearch.IndexCreationFactory.RAW;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.mail.Flags;

import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.HeaderOperator;
import org.apache.james.mailbox.opensearch.json.HeaderCollection;
import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

public class CriterionConverter {

    private final Map<Class<?>, Function<Criterion, Query>> criterionConverterMap;
    private final Map<Class<?>, BiFunction<String, HeaderOperator, Query>> headerOperatorConverterMap;

    public CriterionConverter() {
        criterionConverterMap = new HashMap<>();
        headerOperatorConverterMap = new HashMap<>();
        
        registerCriterionConverters();
        registerHeaderOperatorConverters();
    }

    private void registerCriterionConverters() {
        registerCriterionConverter(SearchQuery.FlagCriterion.class, this::convertFlag);
        registerCriterionConverter(SearchQuery.UidCriterion.class, this::convertUid);
        registerCriterionConverter(SearchQuery.MessageIdCriterion.class, this::convertMessageId);
        registerCriterionConverter(SearchQuery.ConjunctionCriterion.class, this::convertConjunction);
        registerCriterionConverter(SearchQuery.HeaderCriterion.class, this::convertHeader);
        registerCriterionConverter(SearchQuery.SubjectCriterion.class, this::convertSubject);
        registerCriterionConverter(SearchQuery.TextCriterion.class, this::convertTextCriterion);
        registerCriterionConverter(SearchQuery.CustomFlagCriterion.class, this::convertCustomFlagCriterion);
        
        registerCriterionConverter(SearchQuery.AllCriterion.class,
            criterion -> new MatchAllQuery.Builder().build()._toQuery());
        
        registerCriterionConverter(SearchQuery.ModSeqCriterion.class,
            criterion -> createNumericFilter(JsonMessageConstants.MODSEQ, criterion.getOperator()));
        
        registerCriterionConverter(SearchQuery.SizeCriterion.class,
            criterion -> createNumericFilter(JsonMessageConstants.SIZE, criterion.getOperator()));

        registerCriterionConverter(SearchQuery.InternalDateCriterion.class,
            criterion -> dateRangeFilter(JsonMessageConstants.DATE, criterion.getOperator()));

        registerCriterionConverter(SearchQuery.SaveDateCriterion.class,
            criterion -> dateRangeFilter(JsonMessageConstants.SAVE_DATE, criterion.getOperator()));

        registerCriterionConverter(SearchQuery.AttachmentCriterion.class, this::convertAttachmentCriterion);
        registerCriterionConverter(SearchQuery.MimeMessageIDCriterion.class, this::convertMimeMessageIDCriterion);
        registerCriterionConverter(SearchQuery.ThreadIdCriterion.class, this::convertThreadIdCriterion);
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Criterion> void registerCriterionConverter(Class<T> type, Function<T, Query> f) {
        criterionConverterMap.put(type, (Function<Criterion, Query>) f);
    }
    
    private void registerHeaderOperatorConverters() {
        registerHeaderOperatorConverter(
            SearchQuery.ExistsOperator.class,
            (headerName, operator) -> new NestedQuery.Builder()
                .path(JsonMessageConstants.HEADERS)
                .query(new TermQuery.Builder()
                    .field(JsonMessageConstants.HEADERS + "." + JsonMessageConstants.HEADER.NAME)
                    .value(new FieldValue.Builder().stringValue(headerName).build())
                    .build()
                    ._toQuery())
                .scoreMode(ChildScoreMode.Avg)
                .build()
                ._toQuery());
        
        registerHeaderOperatorConverter(
            SearchQuery.AddressOperator.class,
            (headerName, operator) -> manageAddressFields(headerName, operator.getAddress()));
        
        registerHeaderOperatorConverter(
            SearchQuery.DateOperator.class,
            (headerName, operator) -> dateRangeFilter(JsonMessageConstants.SENT_DATE, operator));
        
        registerHeaderOperatorConverter(
            SearchQuery.ContainsOperator.class,
            (headerName, operator) -> new NestedQuery.Builder()
                .path(JsonMessageConstants.HEADERS)
                .query(new BoolQuery.Builder()
                    .must(new TermQuery.Builder()
                        .field(JsonMessageConstants.HEADERS + "." + JsonMessageConstants.HEADER.NAME)
                        .value(new FieldValue.Builder().stringValue(headerName).build())
                        .build()
                        ._toQuery())
                    .must(new MatchQuery.Builder()
                        .field(JsonMessageConstants.HEADERS + "." + JsonMessageConstants.HEADER.VALUE)
                        .query(new FieldValue.Builder().stringValue(operator.getValue()).build())
                        .build()
                        ._toQuery())
                    .build()
                    ._toQuery())
                .scoreMode(ChildScoreMode.Avg)
                .build()
                ._toQuery());
    }

    @SuppressWarnings("unchecked")
    private <T extends HeaderOperator> void registerHeaderOperatorConverter(Class<T> type, BiFunction<String, T, Query> f) {
        headerOperatorConverterMap.put(type, (BiFunction<String, HeaderOperator, Query>) f);
    }

    public Query convertCriterion(Criterion criterion) {
        return criterionConverterMap.get(criterion.getClass()).apply(criterion);
    }

    private Query convertAttachmentCriterion(SearchQuery.AttachmentCriterion criterion) {
        return new TermQuery.Builder()
            .field(JsonMessageConstants.HAS_ATTACHMENT)
            .value(new FieldValue.Builder().booleanValue(criterion.getOperator().isSet()).build())
            .build()
            ._toQuery();
    }

    private Query convertMimeMessageIDCriterion(SearchQuery.MimeMessageIDCriterion criterion) {
        return new TermQuery.Builder()
            .field(JsonMessageConstants.MIME_MESSAGE_ID)
            .value(new FieldValue.Builder().stringValue(criterion.getMessageID()).build())
            .build()
            ._toQuery();
    }

    private Query convertThreadIdCriterion(SearchQuery.ThreadIdCriterion criterion) {
        return new TermQuery.Builder()
            .field(JsonMessageConstants.THREAD_ID)
            .value(new FieldValue.Builder().stringValue(criterion.getThreadId().serialize()).build())
            .build()
            ._toQuery();
    }

    private Query convertCustomFlagCriterion(SearchQuery.CustomFlagCriterion criterion) {
        Query termQuery = new TermQuery.Builder()
            .field(JsonMessageConstants.USER_FLAGS)
            .value(new FieldValue.Builder().stringValue(criterion.getFlag()).build())
            .build()
            ._toQuery();
        if (criterion.getOperator().isSet()) {
            return termQuery;
        } else {
            return new BoolQuery.Builder()
                .mustNot(termQuery)
                .build()
                ._toQuery();
        }
    }

    private Query convertTextCriterion(SearchQuery.TextCriterion textCriterion) {
        switch (textCriterion.getType()) {
        case BODY:
            return new BoolQuery.Builder()
                .should(new MatchQuery.Builder()
                    .field(JsonMessageConstants.TEXT_BODY)
                    .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                    .operator(Operator.And)
                    .build()
                    ._toQuery())
                .should(new MatchQuery.Builder()
                    .field(JsonMessageConstants.HTML_BODY)
                    .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                    .operator(Operator.And)
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        case FULL:
            return new BoolQuery.Builder()
                .should(new MatchQuery.Builder()
                    .field(JsonMessageConstants.TEXT_BODY)
                    .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                    .operator(Operator.And)
                    .build()
                    ._toQuery())
                .should(new MatchQuery.Builder()
                    .field(JsonMessageConstants.HTML_BODY)
                    .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                    .operator(Operator.And)
                    .build()
                    ._toQuery())
                .should(new MatchQuery.Builder()
                    .field(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.TEXT_CONTENT)
                    .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                    .operator(Operator.And)
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        case ATTACHMENTS:
            return new BoolQuery.Builder()
                .should(new MatchQuery.Builder()
                    .field(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.TEXT_CONTENT)
                    .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        case ATTACHMENT_FILE_NAME:
            return new BoolQuery.Builder()
                .should(new MatchQuery.Builder()
                    .field(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.FILENAME)
                    .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        default:
            throw new RuntimeException("Unknown SCOPE for text criterion");
        }
    }

    private Query dateRangeFilter(String field, SearchQuery.DateOperator dateOperator) {
        return new BoolQuery.Builder()
            .filter(convertDateOperator(field,
                dateOperator.getType(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    DateResolutionFormatter.computeLowerDate(
                        DateResolutionFormatter.convertDateToZonedDateTime(dateOperator.getDate()),
                        dateOperator.getDateResultion())),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    DateResolutionFormatter.computeUpperDate(
                        DateResolutionFormatter.convertDateToZonedDateTime(dateOperator.getDate()),
                        dateOperator.getDateResultion()))))
            .build()
            ._toQuery();
    }

    private Query convertConjunction(SearchQuery.ConjunctionCriterion criterion) {
        return convertToBoolQuery(criterion.getCriteria().stream().map(this::convertCriterion),
            convertConjunctionType(criterion.getType()));
    }

    private BiFunction<BoolQuery.Builder, Query, BoolQuery.Builder> convertConjunctionType(SearchQuery.Conjunction type) {
        switch (type) {
            case AND:
                return BoolQuery.Builder::must;
            case OR:
                return BoolQuery.Builder::should;
            case NOR:
                return BoolQuery.Builder::mustNot;
            default:
                throw new RuntimeException("Unexpected conjunction criteria " + type);
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    private Query convertToBoolQuery(Stream<Query> stream, BiFunction<BoolQuery.Builder, Query, BoolQuery.Builder> addCriterionToBoolQuery) {
        BoolQuery.Builder builder = new BoolQuery.Builder();
        stream.forEach(query -> addCriterionToBoolQuery.apply(builder, query));
        return builder.build()._toQuery();
    }

    private Query convertFlag(SearchQuery.FlagCriterion flagCriterion) {
        SearchQuery.BooleanOperator operator = flagCriterion.getOperator();
        Flags.Flag flag = flagCriterion.getFlag();
        if (flag.equals(Flags.Flag.DELETED)) {
            return new BoolQuery.Builder()
                .filter(new TermQuery.Builder()
                    .field(JsonMessageConstants.IS_DELETED)
                    .value(new FieldValue.Builder().booleanValue(operator.isSet()).build())
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        }
        if (flag.equals(Flags.Flag.ANSWERED)) {
            return new BoolQuery.Builder()
                .filter(new TermQuery.Builder()
                    .field(JsonMessageConstants.IS_ANSWERED)
                    .value(new FieldValue.Builder().booleanValue(operator.isSet()).build())
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        }
        if (flag.equals(Flags.Flag.DRAFT)) {
            return new BoolQuery.Builder()
                .filter(new TermQuery.Builder()
                    .field(JsonMessageConstants.IS_DRAFT)
                    .value(new FieldValue.Builder().booleanValue(operator.isSet()).build())
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        }
        if (flag.equals(Flags.Flag.SEEN)) {
            return new BoolQuery.Builder()
                .filter(new TermQuery.Builder()
                    .field(JsonMessageConstants.IS_UNREAD)
                    .value(new FieldValue.Builder().booleanValue(!operator.isSet()).build())
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        }
        if (flag.equals(Flags.Flag.RECENT)) {
            return new BoolQuery.Builder()
                .filter(new TermQuery.Builder()
                    .field(JsonMessageConstants.IS_RECENT)
                    .value(new FieldValue.Builder().booleanValue(operator.isSet()).build())
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        }
        if (flag.equals(Flags.Flag.FLAGGED)) {
            return new BoolQuery.Builder()
                .filter(new TermQuery.Builder()
                    .field(JsonMessageConstants.IS_FLAGGED)
                    .value(new FieldValue.Builder().booleanValue(operator.isSet()).build())
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        }
        throw new RuntimeException("Unknown flag used in Flag search criterion");
    }

    private Query createNumericFilter(String fieldName, SearchQuery.NumericOperator operator) {
        switch (operator.getType()) {
        case EQUALS:
            return new BoolQuery.Builder()
                .filter(new RangeQuery.Builder()
                    .field(fieldName)
                    .gte(JsonData.of(operator.getValue()))
                    .lte(JsonData.of(operator.getValue()))
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        case GREATER_THAN:
            return new BoolQuery.Builder()
                .filter(new RangeQuery.Builder()
                    .field(fieldName)
                    .gt(JsonData.of(operator.getValue()))
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        case LESS_THAN:
            return new BoolQuery.Builder()
                .filter(new RangeQuery.Builder()
                    .field(fieldName)
                    .lt(JsonData.of(operator.getValue()))
                    .build()
                    ._toQuery())
                .build()
                ._toQuery();
        default:
            throw new RuntimeException("A non existing numeric operator was triggered");
        }
    }

    private Query convertUid(SearchQuery.UidCriterion uidCriterion) {
        if (uidCriterion.getOperator().getRange().length == 0) {
            return new BoolQuery.Builder().build()._toQuery();
        }
        return new BoolQuery.Builder()
            .filter(convertToBoolQuery(
                Arrays.stream(uidCriterion.getOperator().getRange())
                    .map(this::uidRangeFilter), BoolQuery.Builder::should))
            .build()
            ._toQuery();
    }

    private Query convertMessageId(SearchQuery.MessageIdCriterion messageIdCriterion) {
        return new TermQuery.Builder()
            .field(JsonMessageConstants.MESSAGE_ID)
            .value(new FieldValue.Builder().stringValue(messageIdCriterion.getMessageId().serialize()).build())
            .build()
            ._toQuery();
    }

    private Query uidRangeFilter(SearchQuery.UidRange numericRange) {
        return new RangeQuery.Builder()
            .field(JsonMessageConstants.UID)
            .lte(JsonData.of(numericRange.getHighValue().asLong()))
            .gte(JsonData.of(numericRange.getLowValue().asLong()))
            .build()
            ._toQuery();
    }

    private Query convertHeader(SearchQuery.HeaderCriterion headerCriterion) {
        return headerOperatorConverterMap.get(headerCriterion.getOperator().getClass())
            .apply(
                headerCriterion.getHeaderName().toLowerCase(Locale.US),
                headerCriterion.getOperator());
    }

    private Query convertSubject(SearchQuery.SubjectCriterion headerCriterion) {
        return new MatchQuery.Builder()
            .field(JsonMessageConstants.SUBJECT)
            .query(new FieldValue.Builder()
                .stringValue(headerCriterion.getSubject())
                .build())
            .operator(Operator.And)
            .build()
            ._toQuery();
    }

    private Query manageAddressFields(String headerName, String value) {
        return new BoolQuery.Builder()
            .should(new MatchQuery.Builder()
                .field(getFieldNameFromHeaderName(headerName) + "." + JsonMessageConstants.EMailer.NAME)
                .query(new FieldValue.Builder().stringValue(value).build())
                .build()
                ._toQuery())
            .should(new MatchQuery.Builder()
                .field(getFieldNameFromHeaderName(headerName) + "." + JsonMessageConstants.EMailer.ADDRESS)
                .query(new FieldValue.Builder().stringValue(value).build())
                .build()
                ._toQuery())
            .should(new MatchQuery.Builder()
                .field(getFieldNameFromHeaderName(headerName) + "." + JsonMessageConstants.EMailer.DOMAIN)
                .query(new FieldValue.Builder().stringValue(value).build())
                .build()
                ._toQuery())
            .should(new MatchQuery.Builder()
                .field(getFieldNameFromHeaderName(headerName) + "." + JsonMessageConstants.EMailer.ADDRESS + "." + RAW)
                .query(new FieldValue.Builder().stringValue(value).build())
                .build()
                ._toQuery())
            .build()
            ._toQuery();
    }

    private String getFieldNameFromHeaderName(String headerName) {
        switch (headerName.toLowerCase(Locale.US)) {
        case HeaderCollection.TO:
            return JsonMessageConstants.TO;
        case HeaderCollection.CC:
            return JsonMessageConstants.CC;
        case HeaderCollection.BCC:
            return JsonMessageConstants.BCC;
        case HeaderCollection.FROM:
            return JsonMessageConstants.FROM;
        default:
            throw new RuntimeException("Header not recognized as Addess Header : " + headerName);
        }
    }

    private Query convertDateOperator(String field, SearchQuery.DateComparator dateComparator, String lowDateString, String upDateString) {
        switch (dateComparator) {
        case BEFORE:
            return new RangeQuery.Builder()
                .field(field)
                .lt(JsonData.of(lowDateString))
                .build()
                ._toQuery();
        case AFTER:
            return new RangeQuery.Builder()
                .field(field)
                .gte(JsonData.of(upDateString))
                .build()
                ._toQuery();
        case ON:
            return new RangeQuery.Builder()
                .field(field)
                .lt(JsonData.of(upDateString))
                .gte(JsonData.of(lowDateString))
                .build()
                ._toQuery();
        default:
            throw new RuntimeException("Unknown date operator");
        }
    }

}
