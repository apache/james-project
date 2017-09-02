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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.junit.Before;
import org.junit.Test;

public class MailetConfigImplTest {

    private DefaultConfigurationBuilder builder;
    private MailetConfigImpl config;

    @Before
    public void setUp() throws Exception {
        builder = new DefaultConfigurationBuilder();
        config = new MailetConfigImpl();
    }

    @Test
    public void testDotParamsFromXML() throws ConfigurationException {
        builder.load(new ByteArrayInputStream("<mailet><mail.debug>true</mail.debug></mailet>".getBytes()));

        config.setConfiguration(builder);

        String param = config.getInitParameterNames().next();
        assertEquals("mail.debug", param);
        assertEquals("true", config.getInitParameter(param));
    }

    @Test
    public void testDotParamsFromConfig() throws ConfigurationException {
        builder.addProperty("mail.debug", "true");

        config.setConfiguration(builder);

        String param = config.getInitParameterNames().next();
        assertEquals("mail.debug", param);
        assertEquals("true", config.getInitParameter(param));
    }

    // See JAMES-1232
    @Test
    public void testParamWithComma() throws ConfigurationException {
        builder.load(new ByteArrayInputStream("<mailet><whatever>value1,value2</whatever></mailet>".getBytes()));

        config.setConfiguration(builder);

        String param = config.getInitParameterNames().next();
        assertEquals("whatever", param);
        assertEquals("value1,value2", config.getInitParameter(param));
    }

    @Test
    public void testParamWithXmlSpace() throws ConfigurationException {
        builder.setDelimiterParsingDisabled(true);
        builder.load(new ByteArrayInputStream("<mailet><whatever xml:space=\"preserve\"> some text </whatever></mailet>".
                getBytes()));

        config.setConfiguration(builder);

        String param = config.getInitParameterNames().next();
        assertEquals("whatever", param);
        assertEquals(" some text ", config.getInitParameter(param));

        List<String> params = new ArrayList<>();
        Iterator<String> iter = config.getInitParameterNames();
        while (iter.hasNext()) {
            params.add(iter.next());
        }
        assertEquals(params.size(), 1);
    }
}
