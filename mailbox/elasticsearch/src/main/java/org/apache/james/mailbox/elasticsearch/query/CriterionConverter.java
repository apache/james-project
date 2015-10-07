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

import org.apache.james.mailbox.elasticsearch.json.HeaderCollection;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.HeaderOperator;

import javax.mail.Flags;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.elasticsearch.index.query.FilterBuilders.existsFilter;
import static org.elasticsearch.index.query.FilterBuilders.rangeFilter;
import static org.elasticsearch.index.query.FilterBuilders.termFilter;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

public class CriterionConverter {

    private Map<Class<?>, Function<SearchQuery.Criterion, FilteredQueryRepresentation>> criterionConverterMap;
    private Map<Class<?>, BiFunction<String, SearchQuery.HeaderOperator, FilteredQueryRepresentation>> headerOperatorConverterMap;

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
        registerCriterionConverter(SearchQuery.InternalDateCriterion.class, this::convertInternalDate);
        registerCriterionConverter(SearchQuery.HeaderCriterion.class, this::convertHeader);
        registerCriterionConverter(SearchQuery.TextCriterion.class, this::convertTextCriterion);
        
        registerCriterionConverter(
            SearchQuery.AllCriterion.class, 
            criterion -> FilteredQueryRepresentation.fromQuery(matchAllQuery()));
        
        registerCriterionConverter(
            SearchQuery.ModSeqCriterion.class,
            criterion -> createNumericFilter(JsonMessageConstants.MODSEQ, criterion.getOperator()));
        
        registerCriterionConverter(
            SearchQuery.SizeCriterion.class,
            criterion -> createNumericFilter(JsonMessageConstants.SIZE, criterion.getOperator()));
        
        registerCriterionConverter(
            SearchQuery.CustomFlagCriterion.class,
            criterion -> FilteredQueryRepresentation.fromFilter(
                    termFilter(JsonMessageConstants.USER_FLAGS, criterion.getFlag())));
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Criterion> void registerCriterionConverter(Class<T> type, Function<T, FilteredQueryRepresentation> f) {
        criterionConverterMap.put(type, (Function<Criterion, FilteredQueryRepresentation>) f);
    }
    
    private void registerHeaderOperatorConverters() {

        registerHeaderOperatorConverter(
            SearchQuery.ExistsOperator.class,
            (headerName, operator) -> FilteredQueryRepresentation.fromFilter(
                existsFilter(JsonMessageConstants.HEADERS + "." + headerName))
        );
        
        registerHeaderOperatorConverter(
            SearchQuery.AddressOperator.class,
            (headerName, operator) -> manageAddressFields(headerName, operator.getAddress()));
        
        registerHeaderOperatorConverter(
            SearchQuery.DateOperator.class,
            (headerName, operator) -> dateRangeFilter(JsonMessageConstants.SENT_DATE, operator));
        
        registerHeaderOperatorConverter(
            SearchQuery.ContainsOperator.class,
            (headerName, operator) -> FilteredQueryRepresentation.fromQuery(
                matchQuery(JsonMessageConstants.HEADERS + "." + headerName,
                    operator.getValue())));
    }

    @SuppressWarnings("unchecked")
    private <T extends HeaderOperator> void registerHeaderOperatorConverter(Class<T> type, BiFunction<String, T, FilteredQueryRepresentation> f) {
        headerOperatorConverterMap.put(type, (BiFunction<String, HeaderOperator, FilteredQueryRepresentation>) f);
    }

    public FilteredQueryRepresentation convertCriterion(SearchQuery.Criterion criterion) {
        return criterionConverterMap.get(criterion.getClass()).apply(criterion);
    }


    private FilteredQueryRepresentation convertTextCriterion(SearchQuery.TextCriterion textCriterion) {
        switch (textCriterion.getType()) {
        case BODY:
            return FilteredQueryRepresentation.fromQuery(
                matchQuery(JsonMessageConstants.TEXT_BODY, textCriterion.getOperator().getValue()));
        case FULL:
            return FilteredQueryRepresentation.fromQuery(
                boolQuery()
                    .should(matchQuery(JsonMessageConstants.TEXT_BODY, textCriterion.getOperator().getValue()))
                    .should(matchQuery(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.TEXT_CONTENT,
                        textCriterion.getOperator().getValue())));
        }
        throw new RuntimeException("Unknown SCOPE for text criterion");
    }

    private FilteredQueryRepresentation convertInternalDate(SearchQuery.InternalDateCriterion dateCriterion) {
        SearchQuery.DateOperator dateOperator = dateCriterion.getOperator();
        return dateRangeFilter(JsonMessageConstants.DATE, dateOperator);
    }

    private FilteredQueryRepresentation dateRangeFilter(String field, SearchQuery.DateOperator dateOperator) {
        SearchQuery.DateResolution dateResolution = dateOperator.getDateResultion();
        String lowDateString = DateResolutionFormater.DATE_TIME_FOMATTER.format(DateResolutionFormater.computeLowerDate(DateResolutionFormater.convertDateToZonedDateTime(dateOperator.getDate()), dateResolution));
        String upDateString = DateResolutionFormater.DATE_TIME_FOMATTER.format(
            DateResolutionFormater.computeUpperDate(
                DateResolutionFormater.convertDateToZonedDateTime(dateOperator.getDate()),
                dateResolution));
        return convertDateOperatorToFiteredQuery(field, dateOperator, lowDateString, upDateString);
    }

    private FilteredQueryRepresentation convertConjunction(SearchQuery.ConjunctionCriterion criterion) {
        return criterion.getCriteria().stream()
            .map(this::convertCriterion)
            .collect(FilteredQueryCollector.collector(criterion.getType()));
    }

    private FilteredQueryRepresentation convertFlag(SearchQuery.FlagCriterion flagCriterion) {
        SearchQuery.BooleanOperator operator = flagCriterion.getOperator();
        Flags.Flag flag = flagCriterion.getFlag();
        if (flag.equals(Flags.Flag.DELETED) ) {
            return FilteredQueryRepresentation.fromFilter(termFilter(JsonMessageConstants.IS_DELETED, operator.isSet()));
        }
        if (flag.equals(Flags.Flag.ANSWERED) ) {
            return FilteredQueryRepresentation.fromFilter(
                termFilter(JsonMessageConstants.IS_ANSWERED, operator.isSet()));
        }
        if (flag.equals(Flags.Flag.DRAFT) ) {
            return FilteredQueryRepresentation.fromFilter(
                termFilter(JsonMessageConstants.IS_DRAFT, operator.isSet()));
        }
        if (flag.equals(Flags.Flag.SEEN) ) {
            return FilteredQueryRepresentation.fromFilter(
                termFilter(JsonMessageConstants.IS_UNREAD, !operator.isSet()));
        }
        if (flag.equals(Flags.Flag.RECENT) ) {
            return FilteredQueryRepresentation.fromFilter(
                termFilter(JsonMessageConstants.IS_RECENT, operator.isSet()));
        }
        if (flag.equals(Flags.Flag.FLAGGED) ) {
            return FilteredQueryRepresentation.fromFilter(
                termFilter(JsonMessageConstants.IS_FLAGGED, operator.isSet()));
        }
        throw new RuntimeException("Unknown flag used in Flag search criterion");
    }

    private FilteredQueryRepresentation createNumericFilter(String fieldName, SearchQuery.NumericOperator operator) {
        switch (operator.getType()) {
        case EQUALS:
            return FilteredQueryRepresentation.fromFilter(
                rangeFilter(fieldName).gte(operator.getValue()).lte(operator.getValue()));
        case GREATER_THAN:
            return FilteredQueryRepresentation.fromFilter(rangeFilter(fieldName).gte(operator.getValue()));
        case LESS_THAN:
            return FilteredQueryRepresentation.fromFilter(rangeFilter(fieldName).lte(operator.getValue()));
        default:
            throw new RuntimeException("A non existing numeric operator was triggered");
        }
    }

    private FilteredQueryRepresentation convertUid(SearchQuery.UidCriterion uidCriterion) {
        if (uidCriterion.getOperator().getRange().length == 0) {
            return FilteredQueryRepresentation.empty();
        }
        return Arrays.stream(uidCriterion.getOperator().getRange())
            .map(this::uidRangeFilter)
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.OR));
    }

    private FilteredQueryRepresentation uidRangeFilter(SearchQuery.NumericRange numericRange) {
        return FilteredQueryRepresentation.fromFilter(
            rangeFilter(JsonMessageConstants.ID)
                .lte(numericRange.getHighValue())
                .gte(numericRange.getLowValue()));
    }

    private FilteredQueryRepresentation convertHeader(SearchQuery.HeaderCriterion headerCriterion) {
        return headerOperatorConverterMap.get(headerCriterion.getOperator().getClass())
            .apply(
                headerCriterion.getHeaderName().toLowerCase(),
                headerCriterion.getOperator());
    }

    private FilteredQueryRepresentation manageAddressFields(String headerName, String value) {
        return FilteredQueryRepresentation.fromQuery(
            nestedQuery(getFieldNameFromHeaderName(headerName), boolQuery().should(matchQuery(getFieldNameFromHeaderName(headerName) + "." + JsonMessageConstants.EMailer.NAME, value)).should(matchQuery(getFieldNameFromHeaderName(headerName) + "." + JsonMessageConstants.EMailer.ADDRESS, value))));
    }

    private String getFieldNameFromHeaderName(String headerName) {
        switch (headerName.toLowerCase()) {
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

    private FilteredQueryRepresentation convertDateOperatorToFiteredQuery(String field, SearchQuery.DateOperator dateOperator, String lowDateString, String upDateString) {
        switch (dateOperator.getType()) {
        case BEFORE:
            return FilteredQueryRepresentation.fromFilter(
                rangeFilter(field).lte(upDateString));
        case AFTER:
            return FilteredQueryRepresentation.fromFilter(
                rangeFilter(field).gte(lowDateString));
        case ON:
            return FilteredQueryRepresentation.fromFilter(
                rangeFilter(field).lte(upDateString).gte(lowDateString));
        }
        throw new RuntimeException("Unknown date operator");
    }

}
