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

package org.apache.james.blob.objectstorage.aws;

import static org.apache.james.blob.objectstorage.aws.DockerAwsS3Container.REGION;

public class S3ConfigurationHelper {

    public static S3BlobStoreConfiguration.Builder.ReadyToBuild baseBlobStoreConfiguration(DockerAwsS3Container s3Container) {
        return S3BlobStoreConfiguration.builder()
            .authConfiguration(authConfiguration(s3Container))
            .region(REGION);
    }

    public static AwsS3AuthConfiguration authConfiguration(DockerAwsS3Container s3Container) {
        return AwsS3AuthConfiguration.builder()
            .endpoint(s3Container.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();
    }
}
