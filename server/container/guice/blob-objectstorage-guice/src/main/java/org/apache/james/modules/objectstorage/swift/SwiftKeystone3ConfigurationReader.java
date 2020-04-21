/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.modules.objectstorage.swift;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.apache.james.blob.objectstorage.swift.DomainId;
import org.apache.james.blob.objectstorage.swift.DomainName;
import org.apache.james.blob.objectstorage.swift.IdentityV3;
import org.apache.james.blob.objectstorage.swift.Project;
import org.apache.james.blob.objectstorage.swift.ProjectName;
import org.apache.james.blob.objectstorage.swift.Region;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone3ObjectStorage;
import org.apache.james.blob.objectstorage.swift.UserName;

import com.google.common.base.Preconditions;

/**
 * See openstack identity V3 documentation
 *
 * @link https://developer.openstack.org/api-ref/identity/v3/#authentication-and-token-management
 */
public class SwiftKeystone3ConfigurationReader implements SwiftConfiguration {

    static final String OBJECTSTORAGE_SWIFT_KEYSTONE_3_USER_NAME =
        "objectstorage.swift.keystone3.user.name";

    static final String OBJECTSTORAGE_SWIFT_KEYSTONE_3_USER_DOMAIN =
        "objectstorage.swift.keystone3.user.domain";

    private static final String OBJECTSTORAGE_SWIFT_KEYSTONE_3_DOMAIN_ID =
        "objectstorage.swift.keystone3.scope.domainid";

    static final String OBJECTSTORAGE_SWIFT_KEYSTONE_3_PROJECT_NAME =
        "objectstorage.swift.keystone3.scope.project.name";

    private static final String OBJECTSTORAGE_SWIFT_KEYSTONE_3_PROJECT_DOMAIN_NAME =
        "objectstorage.swift.keystone3.scope.project.domainname";

    private static final String OBJECTSTORAGE_SWIFT_KEYSTONE_3_PROJECT_DOMAIN_ID =
        "objectstorage.swift.keystone3.scope.project.domainid";

    public static SwiftKeystone3ObjectStorage.Configuration readSwiftConfiguration(Configuration configuration) {
        String endpointStr = configuration.getString(OBJECTSTORAGE_SWIFT_ENDPOINT, null);
        String crendentialsStr = configuration.getString(OBJECTSTORAGE_SWIFT_CREDENTIALS, null);

        Preconditions.checkArgument(endpointStr != null,
             "%s is a mandatory configuration value", OBJECTSTORAGE_SWIFT_ENDPOINT);
        Preconditions.checkArgument(crendentialsStr != null,
             "%s is a mandatory configuration value", OBJECTSTORAGE_SWIFT_CREDENTIALS);

        URI endpoint = URI.create(endpointStr);
        Credentials credentials = Credentials.of(crendentialsStr);

        IdentityV3 identity = readIdentity(configuration);

        Optional<DomainId> domainScope = Optional.ofNullable(
                configuration.getString(OBJECTSTORAGE_SWIFT_KEYSTONE_3_DOMAIN_ID, null))
            .map(DomainId::of);

        Optional<Project> projectScope = readProjectScope(configuration);

        Optional<Region> region = Optional.ofNullable(
                configuration.getString(OBJECTSTORAGE_SWIFT_REGION, null))
            .map(Region::of);

        return SwiftKeystone3ObjectStorage.configBuilder()
            .endpoint(endpoint)
            .credentials(credentials)
            .region(region)
            .identity(identity)
            .domainId(domainScope)
            .project(projectScope)
            .build();
    }

    private static IdentityV3 readIdentity(Configuration configuration) {
        String userNameStr = configuration.getString(OBJECTSTORAGE_SWIFT_KEYSTONE_3_USER_NAME, null);
        String domainNameStr = configuration.getString(OBJECTSTORAGE_SWIFT_KEYSTONE_3_USER_DOMAIN, null);

        Preconditions.checkArgument(userNameStr != null,
            "%s is a mandatory configuration value", OBJECTSTORAGE_SWIFT_KEYSTONE_3_USER_NAME);
        Preconditions.checkArgument(domainNameStr != null,
            "%s is a mandatory configuration value", OBJECTSTORAGE_SWIFT_KEYSTONE_3_USER_DOMAIN);

        UserName userName =
            UserName.of(userNameStr);

        DomainName userDomain =
            DomainName.of(domainNameStr);
        return IdentityV3.of(userDomain, userName);
    }

    private static Optional<Project> readProjectScope(Configuration configuration) {
        Optional<ProjectName> projectName = Optional.ofNullable(
                configuration.getString(OBJECTSTORAGE_SWIFT_KEYSTONE_3_PROJECT_NAME, null))
            .map(ProjectName::of);

        Optional<DomainName> projectDomainName = Optional.ofNullable(
                configuration.getString(OBJECTSTORAGE_SWIFT_KEYSTONE_3_PROJECT_DOMAIN_NAME, null))
            .map(DomainName::of);

        Optional<DomainId> projectDomainId = Optional.ofNullable(
                configuration.getString(OBJECTSTORAGE_SWIFT_KEYSTONE_3_PROJECT_DOMAIN_ID, null))
            .map(DomainId::of);

        return projectName.flatMap(project -> projectDomainName.map(domain -> Project.of(project, domain)))
            .or(() -> projectName.flatMap(project -> projectDomainId.map(domain -> Project.of(project, domain))))
            .or(() -> projectName.map(Project::of));
    }
}
