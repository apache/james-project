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

package org.apache.james.modules.objectstorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.apache.james.blob.objectstorage.swift.DomainId;
import org.apache.james.blob.objectstorage.swift.DomainName;
import org.apache.james.blob.objectstorage.swift.IdentityV3;
import org.apache.james.blob.objectstorage.swift.Project;
import org.apache.james.blob.objectstorage.swift.ProjectName;
import org.apache.james.blob.objectstorage.swift.Region;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.UserName;
import org.apache.james.modules.objectstorage.swift.SwiftKeystone3ConfigurationReader;
import org.junit.jupiter.api.Test;

class SwiftKeystone3ConfigurationReaderTest {
    private static final String ENDPOINT = "http://auth.example.com/v2.0";
    private static final String CONFIG_ENDPOINT =
        "objectstorage.swift.endpoint=" + ENDPOINT;

    private static final String CREDENTIALS = "this_is_a_secret";
    private static final String CONFIG_CREDENTIALS =
        "objectstorage.swift.credentials=" + CREDENTIALS;

    private static final String REGION = "EMEA";
    private static final String CONFIG_REGION =
        "objectstorage.swift.region=" + REGION;

    private static final String USER_NAME = "user";
    private static final String CONFIG_USER_NAME =
        "objectstorage.swift.keystone3.user.name=" + USER_NAME;

    private static final String USER_DOMAIN_NAME = "user_domain";
    private static final String CONFIG_USER_DOMAIN_NAME =
        "objectstorage.swift.keystone3.user.domain=" + USER_DOMAIN_NAME;

    private static final String SCOPE_DOMAIN_ID = "scope_domain";
    private static final String CONFIG_SCOPE_DOMAIN_ID =
        "objectstorage.swift.keystone3.scope.domainid=" + SCOPE_DOMAIN_ID;

    private static final String SCOPE_PROJECT_NAME = "scope_project_name";
    private static final String CONFIG_SCOPE_PROJECT_NAME =
        "objectstorage.swift.keystone3.scope.project.name=" + SCOPE_PROJECT_NAME;
    private static final String SCOPE_PROJECT_DOMAIN_NAME = "scope_project_domain_name";
    private static final String CONFIG_SCOPE_PROJECT_DOMAIN_NAME =
        "objectstorage.swift.keystone3.scope.project.domainname=" + SCOPE_PROJECT_DOMAIN_NAME;
    private static final String SCOPE_PROJECT_DOMAIN_ID = "scope_project_domain_id";
    private static final String CONFIG_SCOPE_PROJECT_DOMAIN_ID =
        "objectstorage.swift.keystone3.scope.project.domainid=" + SCOPE_PROJECT_DOMAIN_ID;

    @Test
    void readUnscopedKeystone3Configuration() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_USER_DOMAIN_NAME)));
        assertThat(SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isEqualTo(
                SwiftKeystone3ObjectStorage.configBuilder()
                    .endpoint(URI.create(ENDPOINT))
                    .credentials(Credentials.of(CREDENTIALS))
                    .identity(IdentityV3.of(DomainName.of(USER_DOMAIN_NAME), UserName.of(USER_NAME)))
                    .build()
            );
    }

    @Test
    void readUnscopedKeystone3ConfigurationWithRegion() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_USER_DOMAIN_NAME,
            CONFIG_REGION)));
        assertThat(SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isEqualTo(
                SwiftKeystone3ObjectStorage.configBuilder()
                    .endpoint(URI.create(ENDPOINT))
                    .credentials(Credentials.of(CREDENTIALS))
                    .identity(IdentityV3.of(DomainName.of(USER_DOMAIN_NAME), UserName.of(USER_NAME)))
                    .region(Optional.of(Region.of(REGION)))
                    .build()
            );
    }

    @Test
    void failsToReadKeystone3ConfigurationWithoutEndpoint() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_USER_DOMAIN_NAME)));
        assertThatThrownBy(() -> SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsToReadKeystone3ConfigurationWithoutCredentials() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_USER_NAME,
            CONFIG_USER_DOMAIN_NAME)));
        assertThatThrownBy(() -> SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsToReadKeystone3ConfigurationWithoutUserName() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_DOMAIN_NAME)));
        assertThatThrownBy(() -> SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsToReadKeystone3ConfigurationWithoutUserDomainName() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME)));
        assertThatThrownBy(() -> SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readDomainScopedKeystone3Configuration() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_USER_DOMAIN_NAME,
            CONFIG_SCOPE_DOMAIN_ID)));
        assertThat(SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isEqualTo(
                SwiftKeystone3ObjectStorage.configBuilder()
                    .endpoint(URI.create(ENDPOINT))
                    .credentials(Credentials.of(CREDENTIALS))
                    .identity(IdentityV3.of(DomainName.of(USER_DOMAIN_NAME), UserName.of(USER_NAME)))
                    .domainId(DomainId.of(SCOPE_DOMAIN_ID))
                    .build()
            );
    }

    @Test
    void readProjectScopedKeystone3Configuration() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_USER_DOMAIN_NAME,
            CONFIG_SCOPE_PROJECT_NAME,
            CONFIG_REGION)));
        assertThat(SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isEqualTo(
                SwiftKeystone3ObjectStorage.configBuilder()
                    .endpoint(URI.create(ENDPOINT))
                    .credentials(Credentials.of(CREDENTIALS))
                    .identity(IdentityV3.of(DomainName.of(USER_DOMAIN_NAME), UserName.of(USER_NAME)))
                    .region(Optional.of(Region.of(REGION)))
                    .project(Project.of(ProjectName.of(SCOPE_PROJECT_NAME)))
                    .build()
            );
    }

    @Test
    void readProjectOfDomainNameScopedKeystone3Configuration() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_USER_DOMAIN_NAME,
            CONFIG_SCOPE_PROJECT_NAME,
            CONFIG_SCOPE_PROJECT_DOMAIN_NAME,
            CONFIG_REGION)));
        assertThat(SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isEqualTo(
                SwiftKeystone3ObjectStorage.configBuilder()
                    .endpoint(URI.create(ENDPOINT))
                    .credentials(Credentials.of(CREDENTIALS))
                    .identity(IdentityV3.of(
                        DomainName.of(USER_DOMAIN_NAME),
                        UserName.of(USER_NAME)))
                    .region(Optional.of(Region.of(REGION)))
                    .project(Project.of(
                        ProjectName.of(SCOPE_PROJECT_NAME),
                        DomainName.of(SCOPE_PROJECT_DOMAIN_NAME)))
                    .build()
            );
    }

    @Test
    void readProjectOfDomainIdScopedKeystone3Configuration() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.read(new StringReader(StringUtils.joinWith("\n",
            CONFIG_ENDPOINT,
            CONFIG_CREDENTIALS,
            CONFIG_USER_NAME,
            CONFIG_USER_DOMAIN_NAME,
            CONFIG_SCOPE_PROJECT_NAME,
            CONFIG_SCOPE_PROJECT_DOMAIN_ID,
            CONFIG_REGION)));
        assertThat(SwiftKeystone3ConfigurationReader.readSwiftConfiguration(configuration))
            .isEqualTo(
                SwiftKeystone3ObjectStorage.configBuilder()
                    .endpoint(URI.create(ENDPOINT))
                    .credentials(Credentials.of(CREDENTIALS))
                    .identity(IdentityV3.of(
                        DomainName.of(USER_DOMAIN_NAME),
                        UserName.of(USER_NAME)))
                    .region(Optional.of(Region.of(REGION)))
                    .project(Project.of(
                        ProjectName.of(SCOPE_PROJECT_NAME),
                        DomainId.of(SCOPE_PROJECT_DOMAIN_ID)))
                    .build()
            );
    }
}
