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

package org.apache.james.user.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class LdapRepositoryConfigurationTest {
    public static XMLConfiguration getConfig(String configurationFile) throws Exception {
        InputStream configStream = ClassLoaderUtils.getSystemResourceAsSharedStream(configurationFile);
        FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(XMLConfiguration.class)
            .configure(new Parameters()
                .xml()
                .setListDelimiterHandler(new DisabledListDelimiterHandler()));
        XMLConfiguration xmlConfiguration = builder.getConfiguration();
        FileHandler fileHandler = new FileHandler(xmlConfiguration);
        fileHandler.load(configStream);
        configStream.close();

        return xmlConfiguration;
    }
    
    @Test
    void shouldNotBeVirtualHostedByDefault() throws Exception {
        LdapRepositoryConfiguration configuration = LdapRepositoryConfiguration.from(getConfig("usersrepository-default.xml"));
        assertThat(configuration.supportsVirtualHosting()).isFalse();
    }
    
    @Test
    void shouldSupportFallbackValue() throws Exception {
        LdapRepositoryConfiguration configuration = LdapRepositoryConfiguration.from(getConfig("usersrepository-enableVirtualHosting.xml"));
        assertThat(configuration.supportsVirtualHosting()).isTrue();
    }
    
    @Test
    void shouldSupportLDAPSpecificValue() throws Exception {
        LdapRepositoryConfiguration configuration = LdapRepositoryConfiguration.from(getConfig("usersrepository-supportsVirtualHosting.xml"));
        assertThat(configuration.supportsVirtualHosting()).isTrue();
    }
}