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
package org.apache.james.mailetcontainer.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.io.FileHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailetConfigImplTest {
    private XMLConfiguration xmlConfiguration;
    private FileHandler fileHandler;
    private MailetConfigImpl config;

    @BeforeEach
    void setUp() throws Exception {
        FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(XMLConfiguration.class)
            .configure(new Parameters()
                .xml()
                .setListDelimiterHandler(new DisabledListDelimiterHandler()));
        xmlConfiguration = builder.getConfiguration();
        fileHandler = new FileHandler(xmlConfiguration);

        config = new MailetConfigImpl();
    }

    @Test
    void testDotParamsFromXML() throws Exception {
        fileHandler.load(new ByteArrayInputStream("<mailet><mail.debug>true</mail.debug></mailet>".getBytes()));

        config.setConfiguration(xmlConfiguration);

        String param = config.getInitParameterNames().next();
        assertThat(param).isEqualTo("mail.debug");
        assertThat(config.getInitParameter(param)).isEqualTo("true");
    }

    @Test
    void testDotParamsFromConfig() {
        xmlConfiguration.addProperty("mail.debug", "true");

        config.setConfiguration(xmlConfiguration);

        String param = config.getInitParameterNames().next();
        assertThat(param).isEqualTo("mail.debug");
        assertThat(config.getInitParameter(param)).isEqualTo("true");
    }

    // See JAMES-1232
    @Test
    void testParamWithComma() throws Exception {
        fileHandler.load(new ByteArrayInputStream("<mailet><whatever>value1,value2</whatever></mailet>".getBytes()));

        config.setConfiguration(xmlConfiguration);

        String param = config.getInitParameterNames().next();
        assertThat(param).isEqualTo("whatever");
        assertThat(config.getInitParameter(param)).isEqualTo("value1,value2");
    }

    @Test
    void testParamWithXmlSpace() throws Exception {
        fileHandler.load(new ByteArrayInputStream(
                "<mailet><whatever xml:space=\"preserve\"> some text </whatever></mailet>".getBytes()));

        config.setConfiguration(xmlConfiguration);

        String param = config.getInitParameterNames().next();
        assertThat(param).isEqualTo("whatever");
        assertThat(config.getInitParameter(param)).isEqualTo(" some text ");

        List<String> params = new ArrayList<>();
        Iterator<String> iter = config.getInitParameterNames();
        while (iter.hasNext()) {
            params.add(iter.next());
        }
        assertThat(1).isEqualTo(params.size());
    }
}
