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

package org.apache.james.mailbox.elasticsearch.query;

import static org.apache.james.backends.es.NodeMappingFactory.RAW;
import static org.apache.james.backends.es.NodeMappingFactory.SPLIT_EMAIL;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.mailbox.elasticsearch.json.HeaderCollection;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.HeaderOperator;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

public class CriterionConverter {

    private final Map<Class<?>, Function<Criterion, QueryBuilder>> criterionConverterMap;
    private final Map<Class<?>, BiFunction<String, HeaderOperator, QueryBuilder>> headerOperatorConverterMap;

    public CriterionConverter() {
        criterionConverterMap = new HashMap<>();
        headerOperatorConverterMap = new HashMap<>();
        
        registerCriterionConverters();
        registerHeaderOperatorConverters();
    }

    private void registerCriterionConverters() {
        registerCriterionConverter(SearchQuery.FlagCriterion.class, this::convertFlag);
        registerCriterionConverter(SearchQuery.UidCriterion.class, this::convertUid);
        registerCriterionConverter(SearchQuery.ConjunctionCriterion.class, this::convertConjunction);
        registerCriterionConverter(SearchQuery.HeaderCriterion.class, this::convertHeader);
        registerCriterionConverter(SearchQuery.TextCriterion.class, this::convertTextCriterion);
        registerCriterionConverter(SearchQuery.CustomFlagCriterion.class, this::convertCustomFlagCriterion);
        
        registerCriterionConverter(SearchQuery.AllCriterion.class,
            criterion -> matchAllQuery());
        
        registerCriterionConverter(SearchQuery.ModSeqCriterion.class,
            criterion -> createNumericFilter(JsonMessageConstants.MODSEQ, criterion.getOperator()));
        
        registerCriterionConverter(SearchQuery.SizeCriterion.class,
            criterion -> createNumericFilter(JsonMessageConstants.SIZE, criterion.getOperator()));

        registerCriterionConverter(SearchQuery.InternalDateCriterion.class,
            criterion -> dateRangeFilter(JsonMessageConstants.DATE, criterion.getOperator()));

        registerCriterionConverter(SearchQuery.AttachmentCriterion.class, this::convertAttachmentCriterion);
        registerCriterionConverter(SearchQuery.MimeMessageIDCriterion.class, this::convertMimeMessageIDCriterion);
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Criterion> void registerCriterionConverter(Class<T> type, Function<T, QueryBuilder> f) {
        criterionConverterMap.put(type, (Function<Criterion, QueryBuilder>) f);
    }
    
    private void registerHeaderOperatorConverters() {

        registerHeaderOperatorConverter(
            SearchQuery.ExistsOperator.class,
            (headerName, operator) ->
                nestedQuery(JsonMessageConstants.HEADERS,
                    termQuery(JsonMessageConstants.HEADERS + "." + JsonMessageConstants.HEADER.NAME, headerName),
                    ScoreMode.Avg));
        
        registerHeaderOperatorConverter(
            SearchQuery.AddressOperator.class,
            (headerName, operator) -> manageAddressFields(headerName, operator.getAddress()));
        
        registerHeaderOperatorConverter(
            SearchQuery.DateOperator.class,
            (headerName, operator) -> dateRangeFilter(JsonMessageConstants.SENT_DATE, operator));
        
        registerHeaderOperatorConverter(
            SearchQuery.ContainsOperator.class,
            (headerName, operator) ->
                nestedQuery(JsonMessageConstants.HEADERS,
                    boolQuery()
                        .must(termQuery(JsonMessageConstants.HEADERS + "." + JsonMessageConstants.HEADER.NAME, headerName))
                        .must(matchQuery(JsonMessageConstants.HEADERS + "." + JsonMessageConstants.HEADER.VALUE, operator.getValue())),
                    ScoreMode.Avg));
    }

    @SuppressWarnings("unchecked")
    private <T extends HeaderOperator> void registerHeaderOperatorConverter(Class<T> type, BiFunction<String, T, QueryBuilder> f) {
        headerOperatorConverterMap.put(type, (BiFunction<String, HeaderOperator, QueryBuilder>) f);
    }

    public QueryBuilder convertCriterion(Criterion criterion) {
        return criterionConverterMap.get(criterion.getClass()).apply(criterion);
    }

    private QueryBuilder convertAttachmentCriterion(SearchQuery.AttachmentCriterion criterion) {
        return termQuery(JsonMessageConstants.HAS_ATTACHMENT, criterion.getOperator().isSet());
    }

    private QueryBuilder convertMimeMessageIDCriterion(SearchQuery.MimeMessageIDCriterion criterion) {
        return termQuery(JsonMessageConstants.MIME_MESSAGE_ID, criterion.getMessageID());
    }

    private QueryBuilder convertCustomFlagCriterion(SearchQuery.CustomFlagCriterion criterion) {
        QueryBuilder termQueryBuilder = termQuery(JsonMessageConstants.USER_FLAGS, criterion.getFlag());
        if (criterion.getOperator().isSet()) {
            return termQueryBuilder;
        } else {
            return boolQuery().mustNot(termQueryBuilder);
        }
    }

    private QueryBuilder convertTextCriterion(SearchQuery.TextCriterion textCriterion) {
        switch (textCriterion.getType()) {
        case BODY:
            return boolQuery()
                    .should(matchQuery(JsonMessageConstants.TEXT_BODY, textCriterion.getOperator().getValue()))
                    .should(matchQuery(JsonMessageConstants.TEXT_BODY + "." + SPLIT_EMAIL,
                        textCriterion.getOperator().getValue()))
                    .should(matchQuery(JsonMessageConstants.HTML_BODY + "." + SPLIT_EMAIL,
                        textCriterion.getOperator().getValue()))
                    .should(matchQuery(JsonMessageConstants.HTML_BODY, textCriterion.getOperator().getValue()));
        case FULL:
            return boolQuery()
                    .should(matchQuery(JsonMessageConstants.TEXT_BODY, textCriterion.getOperator().getValue()))
                    .should(matchQuery(JsonMessageConstants.TEXT_BODY + "." + SPLIT_EMAIL,
                        textCriterion.getOperator().getValue()))
                    .should(matchQuery(JsonMessageConstants.HTML_BODY + "." + SPLIT_EMAIL,
                        textCriterion.getOperator().getValue()))
                    .should(matchQuery(JsonMessageConstants.HTML_BODY, textCriterion.getOperator().getValue()))
                    .should(matchQuery(JsonMessageConstants.HTML_BODY, textCriterion.getOperator().getValue()))
                    .should(matchQuery(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.TEXT_CONTENT,
                        textCriterion.getOperator().getValue()));
        case ATTACHMENTS:
            return boolQuery()
                    .should(matchQuery(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.TEXT_CONTENT,
                        textCriterion.getOperator().getValue()));
        case ATTACHMENT_FILE_NAME:
            return boolQuery()
                .should(termQuery(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.FILENAME,
                    textCriterion.getOperator().getValue()));
        }
        throw new RuntimeException("Unknown SCOPE for text criterion");
    }

    private QueryBuilder dateRangeFilter(String field, SearchQuery.DateOperator dateOperator) {
        return boolQuery().filter(
            convertDateOperator(field,
                dateOperator.getType(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    DateResolutionFormatter.computeLowerDate(
                        DateResolutionFormatter.convertDateToZonedDateTime(dateOperator.getDate()),
                        dateOperator.getDateResultion())),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    DateResolutionFormatter.computeUpperDate(
                        DateResolutionFormatter.convertDateToZonedDateTime(dateOperator.getDate()),
                        dateOperator.getDateResultion()))));
    }

    private BoolQueryBuilder convertConjunction(SearchQuery.ConjunctionCriterion criterion) {
        return convertToBoolQuery(criterion.getCriteria().stream().map(this::convertCriterion),
            convertConjunctionType(criterion.getType()));
    }

    private BiFunction<BoolQueryBuilder, QueryBuilder, BoolQueryBuilder> convertConjunctionType(SearchQuery.Conjunction type) {
        switch (type) {
            case AND:
                return BoolQueryBuilder::must;
            case OR:
                return BoolQueryBuilder::should;
            case NOR:
                return BoolQueryBuilder::mustNot;
            default:
                throw new RuntimeException("Unexpected conjunction criteria " + type);
        }
    }

    @SuppressWarnings("ReturnValueIgnored")
    private BoolQueryBuilder convertToBoolQuery(Stream<QueryBuilder> stream, BiFunction<BoolQueryBuilder, QueryBuilder, BoolQueryBuilder> addCriterionToBoolQuery) {
        return stream.collect(Collector.of(QueryBuilders::boolQuery,
                addCriterionToBoolQuery::apply,
                addCriterionToBoolQuery::apply));
    }

    private QueryBuilder convertFlag(SearchQuery.FlagCriterion flagCriterion) {
        SearchQuery.BooleanOperator operator = flagCriterion.getOperator();
        Flags.Flag flag = flagCriterion.getFlag();
        if (flag.equals(Flags.Flag.DELETED)) {
            return boolQuery().filter(termQuery(JsonMessageConstants.IS_DELETED, operator.isSet()));
        }
        if (flag.equals(Flags.Flag.ANSWERED)) {
            return boolQuery().filter(termQuery(JsonMessageConstants.IS_ANSWERED, operator.isSet()));
        }
        if (flag.equals(Flags.Flag.DRAFT)) {
            return boolQuery().filter(termQuery(JsonMessageConstants.IS_DRAFT, operator.isSet()));
        }
        if (flag.equals(Flags.Flag.SEEN)) {
            return boolQuery().filter(termQuery(JsonMessageConstants.IS_UNREAD, !operator.isSet()));
        }
        if (flag.equals(Flags.Flag.RECENT)) {
            return boolQuery().filter(termQuery(JsonMessageConstants.IS_RECENT, operator.isSet()));
        }
        if (flag.equals(Flags.Flag.FLAGGED)) {
            return boolQuery().filter(termQuery(JsonMessageConstants.IS_FLAGGED, operator.isSet()));
        }
        throw new RuntimeException("Unknown flag used in Flag search criterion");
    }

    private QueryBuilder createNumericFilter(String fieldName, SearchQuery.NumericOperator operator) {
        switch (operator.getType()) {
        case EQUALS:
            return boolQuery().filter(rangeQuery(fieldName).gte(operator.getValue()).lte(operator.getValue()));
        case GREATER_THAN:
            return boolQuery().filter(rangeQuery(fieldName).gt(operator.getValue()));
        case LESS_THAN:
            return boolQuery().filter(rangeQuery(fieldName).lt(operator.getValue()));
        default:
            throw new RuntimeException("A non existing numeric operator was triggered");
        }
    }

    private BoolQueryBuilder convertUid(SearchQuery.UidCriterion uidCriterion) {
        if (uidCriterion.getOperator().getRange().length == 0) {
            return boolQuery();
        }
        return boolQuery().filter(
            convertToBoolQuery(
                Arrays.stream(uidCriterion.getOperator().getRange())
                    .map(this::uidRangeFilter), BoolQueryBuilder::should));
    }

    private QueryBuilder uidRangeFilter(SearchQuery.UidRange numericRange) {
        return rangeQuery(JsonMessageConstants.UID)
                .lte(numericRange.getHighValue().asLong())
                .gte(numericRange.getLowValue().asLong());
    }

    private QueryBuilder convertHeader(SearchQuery.HeaderCriterion headerCriterion) {
        return headerOperatorConverterMap.get(headerCriterion.getOperator().getClass())
            .apply(
                headerCriterion.getHeaderName().toLowerCase(Locale.US),
                headerCriterion.getOperator());
    }

    private QueryBuilder manageAddressFields(String headerName, String value) {
        return nestedQuery(getFieldNameFromHeaderName(headerName),
            boolQuery()
                .should(matchQuery(getFieldNameFromHeaderName(headerName) + "." + JsonMessageConstants.EMailer.NAME, value))
                .should(matchQuery(getFieldNameFromHeaderName(headerName) + "." + JsonMessageConstants.EMailer.ADDRESS, value))
                .should(matchQuery(getFieldNameFromHeaderName(headerName) + "." + JsonMessageConstants.EMailer.ADDRESS + "." + RAW, value)),
            ScoreMode.Avg);
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
        }
        throw new RuntimeException("Header not recognized as Addess Header : " + headerName);
    }

    private QueryBuilder convertDateOperator(String field, SearchQuery.DateComparator dateComparator, String lowDateString, String upDateString) {
        switch (dateComparator) {
        case BEFORE:
            return rangeQuery(field).lte(upDateString);
        case AFTER:
            return rangeQuery(field).gt(lowDateString);
        case ON:
            return rangeQuery(field).lte(upDateString).gte(lowDateString);
        }
        throw new RuntimeException("Unknown date operator");
    }

}
