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

package org.apache.james.mailbox.tika;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class TextExtractorConfigurationTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(TextExtractorConfiguration.class)
            .verify();
    }

    @Test
    public void readTextExtractorConfigurationReturnEmptyWithNoBlacklist() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThat(TextExtractorConfiguration.readTextExtractorConfiguration(configuration))
            .isEqualTo(new TextExtractorConfiguration(ImmutableList.of()));
    }

    @Test
    public void readTextExtractorConfigurationReturnConfigurationWithBlacklist() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.load(new StringReader("textextractor.contentType.blacklist=application/ics, application/zip"));

        assertThat(TextExtractorConfiguration.readTextExtractorConfiguration(configuration))
            .isEqualTo(TextExtractorConfiguration.builder()
                .contentTypeBlacklist(ImmutableList.of("application/ics", "application/zip"))
                .build());
    }
}
