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

package org.apache.james.modules;

import java.util.UUID;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.Region;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3MinioDocker;
import org.apache.james.blob.objectstorage.aws.S3MinioExtension;
import org.apache.james.blob.objectstorage.aws.sse.S3SSECConfiguration;

import com.google.inject.Module;

public class S3SSECBlobStoreExtension extends S3MinioExtension implements GuiceModuleTestExtension {

    private final S3SSECConfiguration ssecConfiguration;

    public S3SSECBlobStoreExtension(S3SSECConfiguration ssecConfiguration) {
        this.ssecConfiguration = ssecConfiguration;
    }

    @Override
    public Module getModule() {
        S3MinioDocker s3MinioDocker = minioDocker();
        BucketName defaultBucketName = BucketName.of(UUID.randomUUID().toString());
        AwsS3AuthConfiguration awsS3AuthConfiguration = s3MinioDocker.getAwsS3AuthConfiguration();

        Region region = Region.of("garage");
        S3BlobStoreConfiguration configuration = S3BlobStoreConfiguration.builder()
            .authConfiguration(awsS3AuthConfiguration)
            .region(region)
            .defaultBucketName(defaultBucketName)
            .bucketPrefix(UUID.randomUUID().toString())
            .ssecEnabled()
            .ssecConfiguration(ssecConfiguration)
            .build();

        return binder -> {
            binder.bind(BucketName.class).toInstance(defaultBucketName);
            binder.bind(Region.class).toInstance(region);
            binder.bind(AwsS3AuthConfiguration.class).toInstance(awsS3AuthConfiguration);
            binder.bind(S3BlobStoreConfiguration.class).toInstance(configuration);
        };
    }
}
