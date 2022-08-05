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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;

class SearchConfigurationTest {
    @Test
    void unknownSearchImplementationShouldThrow() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "unknown");
        assertThatThrownBy(() -> SearchConfiguration.from(configuration))
            .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void searchImplementationShouldReturnOpenSearchByDefault() throws Exception {
        assertThat(SearchConfiguration.from(new PropertiesConfiguration()).getImplementation())
            .isEqualTo(SearchConfiguration.Implementation.OpenSearch);
    }

    @Test
    void searchImplementationShouldReturnOpenSearchWhenSetToOpenSearch() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "OpenSearch");
        assertThat(SearchConfiguration.from(configuration).getImplementation())
                .isEqualTo(SearchConfiguration.Implementation.OpenSearch);
    }

    @Test
    void searchImplementationShouldReturnOpenSearchWhenSetToOpenSearchWithAlternativeCase() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "opensearch");
        assertThat(SearchConfiguration.from(configuration).getImplementation())
                .isEqualTo(SearchConfiguration.Implementation.OpenSearch);
    }

    @Test
    void searchImplementationShouldReturnScanningSearchWhenSetToScanningSearch() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("implementation", "scanning");
        assertThat(SearchConfiguration.from(configuration).getImplementation())
                .isEqualTo(SearchConfiguration.Implementation.Scanning);
    }
}
