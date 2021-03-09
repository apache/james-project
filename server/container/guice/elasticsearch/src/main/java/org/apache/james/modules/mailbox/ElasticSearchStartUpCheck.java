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

package org.apache.james.modules.mailbox;

import java.io.IOException;

import javax.inject.Inject;

import org.apache.james.backends.es.v7.ElasticSearchConfiguration;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.elasticsearch.Version;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.core.MainResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchStartUpCheck implements StartUpCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchConfiguration.class);

    private static final Version RECOMMENDED_ES_VERSION = Version.fromString("6.3.2");
    private static final String VERSION_CHECKING_ERROR_MESSAGE = "Error when checking ES version";

    public static final String CHECK_NAME = "ElasticSearchStartUpCheck";

    private final ReactorElasticSearchClient client;

    @Inject
    private ElasticSearchStartUpCheck(ReactorElasticSearchClient client) {
        this.client = client;
    }

    @Override
    public CheckResult check() {
        try {
            MainResponse.Version esVersion = client.info(RequestOptions.DEFAULT)
                .getVersion();

                return CheckResult.builder()
                    .checkName(checkName())
                    .resultType(ResultType.GOOD)
                    .build();

//            String esVersionCompatibilityWarn = String.format(
//                "ES version(%s) is not compatible with the recommendation(%s)",
//                esVersion.toString(),
//                RECOMMENDED_ES_VERSION.toString());
//            LOGGER.warn(esVersionCompatibilityWarn);
//
//            return CheckResult.builder()
//                .checkName(checkName())
//                .resultType(ResultType.BAD)
//                .description(esVersionCompatibilityWarn)
//                .build();
        } catch (IOException e) {
            LOGGER.error(VERSION_CHECKING_ERROR_MESSAGE, e);
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.BAD)
                .description(VERSION_CHECKING_ERROR_MESSAGE + ": " + e.getMessage())
                .build();
        }
    }

    @Override
    public String checkName() {
        return CHECK_NAME;
    }
}
