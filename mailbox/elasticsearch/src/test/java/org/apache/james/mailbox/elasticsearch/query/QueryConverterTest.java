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

import org.apache.james.mailbox.model.SearchQuery;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;

import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_VALUES;
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class QueryConverterTest {

    public static final String MAILBOX_UUID = "12345";

    private QueryConverter queryConverter;

    @Before
    public void setUp() {
        queryConverter = new QueryConverter(new CriterionConverter());
    }

    @Test
    public void allCriterionShouldBeWellConverted()throws Exception{
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.all());
        assertThatJson(queryConverter.from(searchQuery, MAILBOX_UUID).toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "    \"filtered\": {" +
                "        \"query\": {" +
                "                    \"match_all\": {}" +
                "        }," +
                "        \"filter\": {" +
                "                    \"term\": {" +
                "                        \"mailboxId\": \"12345\"" +
                "                    }" +
                "        }" +
                "    }" +
                "}");
    }

    @Test
    public void textCriterionShouldBeWellConverted() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.bodyContains("awesome Linagora team"));
        assertThatJson(queryConverter.from(searchQuery, MAILBOX_UUID).toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "    \"filtered\": {" +
                "        \"query\": {" +
                "                    \"match\": {" +
                "                        \"textBody\": {" +
                "                            \"query\": \"awesome Linagora team\"," +
                "                            \"type\": \"boolean\"" +
                "                        }" +
                "            }" +
                "        }," +
                "        \"filter\": {" +
                "                    \"term\": {" +
                "                        \"mailboxId\": \"12345\"" +
                "                    }" +
                "        }" +
                "    }" +
                "}");
    }

    @Test
    public void filtersAloneShouldBeCombinedWithAMatchAll() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.modSeqGreaterThan(42L));
        assertThatJson(queryConverter.from(searchQuery, MAILBOX_UUID).toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"filtered\" : {" +
                "    \"query\" : {" +
                "      \"match_all\" : { }" +
                "    }," +
                "    \"filter\" : {" +
                "      \"bool\" : {" +
                "        \"must\" : [ {" +
                "          \"range\" : {" +
                "            \"modSeq\" : {" +
                "              \"from\" : 42," +
                "              \"to\" : null," +
                "              \"include_lower\" : true," +
                "              \"include_upper\" : true" +
                "            }" +
                "          }" +
                "        }, {" +
                "          \"term\" : {" +
                "            \"mailboxId\" : \"12345\"" +
                "          }" +
                "        } ]" +
                "      }" +
                "    }" +
                "  }" +
                "}");
    }

    @Test
    public void queriesAloneShouldBeCombinedWithABoolQuery() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.bodyContains("awesome Linagora team"));
        searchQuery.andCriteria(SearchQuery.bodyContains("Gold fish"));
        assertThatJson(queryConverter.from(searchQuery, MAILBOX_UUID).toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "    \"filtered\": {" +
                "        \"query\": {" +
                "                    \"bool\": {" +
                "                        \"must\": [" +
                "                            {" +
                "                                \"match\": {" +
                "                                    \"textBody\": {" +
                "                                        \"query\": \"awesome Linagora team\"," +
                "                                        \"type\": \"boolean\"" +
                "                                    }" +
                "                                }" +
                "                            }," +
                "                            {" +
                "                                \"match\": {" +
                "                                    \"textBody\": {" +
                "                                        \"query\": \"Gold fish\"," +
                "                                        \"type\": \"boolean\"" +
                "                                    }" +
                "                                }" +
                "                            }" +
                "                        ]" +
                "            }" +
                "        }," +
                "        \"filter\": {" +
                "                    \"term\": {" +
                "                        \"mailboxId\": \"12345\"" +
                "                    }" +
                "        }" +
                "    }" +
                "}");
    }

    @Test
    public void criterionInjectionShouldBeJsonProofed() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.modSeqGreaterThan(42L));
        assertThatJson(queryConverter.from(searchQuery, MAILBOX_UUID + "\"},{\"exist\":\"id\"},{\"match\":\"well done").toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"filtered\" : {" +
                "    \"query\" : {" +
                "      \"match_all\" : { }" +
                "    }," +
                "    \"filter\" : {" +
                "      \"bool\" : {" +
                "        \"must\" : [ {" +
                "          \"range\" : {" +
                "            \"modSeq\" : {" +
                "              \"from\" : 42," +
                "              \"to\" : null," +
                "              \"include_lower\" : true," +
                "              \"include_upper\" : true" +
                "            }" +
                "          }" +
                "        }, {" +
                "          \"term\" : {" +
                "            \"mailboxId\" : \"12345\\\"},{\\\"exist\\\":\\\"id\\\"},{\\\"match\\\":\\\"well done\"" +
                "          }" +
                "        } ]" +
                "      }" +
                "    }" +
                "  }" +
                "}");
    }

    @Test
    public void addressHeadersShouldBeWellConverted() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.address(SearchQuery.AddressType.Bcc, "Benoit Tellier<btellier@free.fr>"));
        assertThatJson(queryConverter.from(searchQuery, MAILBOX_UUID).toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "  \"filtered\" : {" +
                "    \"query\" : {" +
                "      \"nested\" : {" +
                "        \"query\" : {" +
                "          \"bool\" : {" +
                "            \"should\" : [ {" +
                "              \"match\" : {" +
                "                \"bcc.name\" : {" +
                "                  \"query\" : \"Benoit Tellier<btellier@free.fr>\"," +
                "                  \"type\" : \"boolean\"" +
                "                }" +
                "              }" +
                "            }, {" +
                "              \"match\" : {" +
                "                \"bcc.address\" : {" +
                "                  \"query\" : \"Benoit Tellier<btellier@free.fr>\"," +
                "                  \"type\" : \"boolean\"" +
                "                }" +
                "              }" +
                "            } ]" +
                "          }" +
                "        }," +
                "        \"path\" : \"bcc\"" +
                "      }" +
                "    }," +
                "    \"filter\" : {" +
                "      \"term\" : {" +
                "        \"mailboxId\" : \"12345\"" +
                "      }" +
                "    }" +
                "  }" +
                "}");
    }

    @Test
    public void dateHeadersShouldBeWellConverted() throws Exception {
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.andCriteria(SearchQuery.headerDateBefore(
            "Date",
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2015-02-25 21:54:38"),
            SearchQuery.DateResolution.Hour));
        assertThatJson(queryConverter.from(searchQuery, MAILBOX_UUID).toXContent(jsonBuilder(), QueryBuilder.EMPTY_PARAMS).string())
            .when(IGNORING_VALUES)
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo("{" +
                "    \"filtered\": {" +
                "        \"query\": {" +
                "            \"match_all\": {}" +
                "        }," +
                "        \"filter\": {" +
                "            \"bool\": {" +
                "                \"must\": [" +
                "                    {" +
                "                        \"range\": {" +
                "                            \"sentDate\": {" +
                "                                \"from\": null," +
                "                                \"to\": \"2015-02-25T22:00:00+01:00\"," +
                "                                \"include_lower\": true," +
                "                                \"include_upper\": true" +
                "                            }" +
                "                        }" +
                "                    }," +
                "                    {" +
                "                        \"term\": {" +
                "                            \"mailboxId\": \"12345\"" +
                "                        }" +
                "                    }" +
                "                ]" +
                "            }" +
                "        }" +
                "    }" +
                "}");
        // We just test structure as time Zone used by Date is different, depending on computer configuration
    }



}
