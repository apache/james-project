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
package org.apache.james.backends.cassandra.init;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.init.configuration.QueryLoggerConfiguration;
import org.junit.Test;

public class QueryLoggerConfigurationTest {

    @Test
    public void fromShouldNotThrowWithMinimalConfigAboutAConstantThresholdSlowQueryLogger() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.query.logger.constant.threshold", 100);

        assertThatCode(() -> QueryLoggerConfiguration.from(configuration))
            .doesNotThrowAnyException();
    }

    @Test
    public void fromShouldNotThrowWithPersonalizedConfigAboutPercentileSlowQuerryLogger() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        configuration.addProperty("cassandra.query.slow.query.latency.threshold.percentile", 90);
        configuration.addProperty("cassandra.query.logger.max.logged.parameters", 9);
        configuration.addProperty("cassandra.query.logger.max.query.string.length", 9000);
        configuration.addProperty("cassandra.query.logger.max.parameter.value.length", 90);

        assertThatCode(() -> QueryLoggerConfiguration.from(configuration))
            .doesNotThrowAnyException();
    }

    @Test
    public void fromShouldThrowIfConfigAboutLoggerIsInvalid() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("cassandra.query.slow.query.latency.threshold.percentile", 90);
        configuration.addProperty("cassandra.query.logger.constant.threshold", 100);

        assertThatThrownBy(() -> QueryLoggerConfiguration.from(configuration))
            .isInstanceOf(IllegalStateException.class);
    }

}
