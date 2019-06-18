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

import org.apache.commons.configuration.Configuration;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.apache.james.blob.objectstorage.swift.Identity;
import org.apache.james.blob.objectstorage.swift.Region;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.TenantName;
import org.apache.james.blob.objectstorage.swift.UserName;

import com.google.common.base.Preconditions;

public class SwiftKeystone2ConfigurationReader implements SwiftConfiguration {

    static final String OBJECTSTORAGE_SWIFT_KEYSTONE_2_USERNAME =
        "objectstorage.swift.keystone2.username";
    static final String OBJECTSTORAGE_SWIFT_KEYSTONE_2_TENANTNAME =
        "objectstorage.swift.keystone2.tenantname";

    public static SwiftKeystone2ObjectStorage.Configuration readSwiftConfiguration(Configuration configuration) {
        String endpointStr = configuration.getString(OBJECTSTORAGE_SWIFT_ENDPOINT, null);
        String crendentialsStr = configuration.getString(OBJECTSTORAGE_SWIFT_CREDENTIALS, null);
        String userNameStr = configuration.getString(OBJECTSTORAGE_SWIFT_KEYSTONE_2_USERNAME, null);
        String tenantNameStr = configuration.getString(OBJECTSTORAGE_SWIFT_KEYSTONE_2_TENANTNAME, null);

        Preconditions.checkArgument(endpointStr != null,
            OBJECTSTORAGE_SWIFT_ENDPOINT + " is a mandatory configuration value");
        Preconditions.checkArgument(crendentialsStr != null,
            OBJECTSTORAGE_SWIFT_CREDENTIALS + " is a mandatory configuration value");
        Preconditions.checkArgument(userNameStr != null,
            OBJECTSTORAGE_SWIFT_KEYSTONE_2_USERNAME + " is a mandatory configuration value");
        Preconditions.checkArgument(tenantNameStr != null,
            OBJECTSTORAGE_SWIFT_KEYSTONE_2_TENANTNAME + " is a mandatory configuration value");

        URI endpoint = URI.create(endpointStr);
        Credentials credentials = Credentials.of(crendentialsStr);
        UserName userName = UserName.of(userNameStr);
        TenantName tenantName = TenantName.of(tenantNameStr);
        Identity identity = Identity.of(tenantName, userName);

        Optional<Region> region = Optional.ofNullable(
                configuration.getString(OBJECTSTORAGE_SWIFT_REGION, null))
            .map(Region::of);

        return SwiftKeystone2ObjectStorage.configBuilder()
            .endpoint(endpoint)
            .credentials(credentials)
            .region(region)
            .identity(identity)
            .build();
    }
}
