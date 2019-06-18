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

package org.apache.james.modules.objectstorage.aws.s3;

import org.apache.commons.configuration.Configuration;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;

public class AwsS3ConfigurationReader {

    static final String OBJECTSTORAGE_ENDPOINT = "objectstorage.s3.endPoint";
    static final String OBJECTSTORAGE_ACCESKEYID = "objectstorage.s3.accessKeyId";
    static final String OBJECTSTORAGE_SECRETKEY = "objectstorage.s3.secretKey";

    public static AwsS3AuthConfiguration from(Configuration configuration) {

        return AwsS3AuthConfiguration.builder()
                .endpoint(configuration.getString(OBJECTSTORAGE_ENDPOINT))
                .accessKeyId(configuration.getString(OBJECTSTORAGE_ACCESKEYID))
                .secretKey(configuration.getString(OBJECTSTORAGE_SECRETKEY))
                .build();
    }
}

