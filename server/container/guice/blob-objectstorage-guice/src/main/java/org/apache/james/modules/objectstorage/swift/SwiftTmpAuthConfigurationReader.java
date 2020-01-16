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
import org.apache.james.blob.objectstorage.swift.Identity;
import org.apache.james.blob.objectstorage.swift.PassHeaderName;
import org.apache.james.blob.objectstorage.swift.Region;
import org.apache.james.blob.objectstorage.swift.SwiftTempAuthObjectStorage;
import org.apache.james.blob.objectstorage.swift.TenantName;
import org.apache.james.blob.objectstorage.swift.UserHeaderName;
import org.apache.james.blob.objectstorage.swift.UserName;

import com.google.common.base.Preconditions;

public class SwiftTmpAuthConfigurationReader implements SwiftConfiguration {

    static final String OBJECTSTORAGE_SWIFT_TEMPAUTH_USERNAME =
        "objectstorage.swift.tempauth.username";
    static final String OBJECTSTORAGE_SWIFT_TEMPAUTH_TENANTNAME =
        "objectstorage.swift.tempauth.tenantname";
    static final String OBJECTSTORAGE_SWIFT_TEMPAUTH_PASS_HEADER_NAME =
        "objectstorage.swift.tempauth.passheadername";
    static final String OBJECTSTORAGE_SWIFT_TEMPAUTH_USER_HEADER_NAME =
        "objectstorage.swift.tempauth.userheadername";

    public static SwiftTempAuthObjectStorage.Configuration readSwiftConfiguration(Configuration configuration) {
        String endpointStr = configuration.getString(OBJECTSTORAGE_SWIFT_ENDPOINT, null);
        String crendentialsStr = configuration.getString(OBJECTSTORAGE_SWIFT_CREDENTIALS, null);
        String userNameStr = configuration.getString(OBJECTSTORAGE_SWIFT_TEMPAUTH_USERNAME, null);
        String tenantNameStr = configuration.getString(OBJECTSTORAGE_SWIFT_TEMPAUTH_TENANTNAME, null);

        Preconditions.checkArgument(endpointStr != null,
            "%s is a mandatory configuration value", OBJECTSTORAGE_SWIFT_ENDPOINT);
        Preconditions.checkArgument(crendentialsStr != null,
            "%s is a mandatory configuration value", OBJECTSTORAGE_SWIFT_CREDENTIALS);
        Preconditions.checkArgument(userNameStr != null,
            "%s is a mandatory configuration value", OBJECTSTORAGE_SWIFT_TEMPAUTH_USERNAME);
        Preconditions.checkArgument(tenantNameStr != null,
            "%s is a mandatory configuration value", OBJECTSTORAGE_SWIFT_TEMPAUTH_TENANTNAME);

        URI endpoint = URI.create(endpointStr);
        Credentials credentials = Credentials.of(crendentialsStr);
        UserName userName = UserName.of(userNameStr);
        TenantName tenantName = TenantName.of(tenantNameStr);
        Identity identity = Identity.of(tenantName, userName);

        Optional<Region> region = Optional.ofNullable(
                configuration.getString(SwiftConfiguration.OBJECTSTORAGE_SWIFT_REGION, null))
            .map(Region::of);

        Optional<PassHeaderName> passHeaderName = Optional.ofNullable(
                configuration.getString(OBJECTSTORAGE_SWIFT_TEMPAUTH_PASS_HEADER_NAME, null))
            .map(PassHeaderName::of);

        Optional<UserHeaderName> userHeaderName = Optional.ofNullable(
                configuration.getString(OBJECTSTORAGE_SWIFT_TEMPAUTH_USER_HEADER_NAME, null))
            .map(UserHeaderName::of);

        return SwiftTempAuthObjectStorage.configBuilder()
            .endpoint(endpoint)
            .credentials(credentials)
            .region(region)
            .identity(identity)
            .tempAuthHeaderPassName(passHeaderName)
            .tempAuthHeaderUserName(userHeaderName)
            .build();
    }
}
