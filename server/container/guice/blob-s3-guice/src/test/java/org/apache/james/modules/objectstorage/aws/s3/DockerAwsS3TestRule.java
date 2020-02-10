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

package org.apache.james.modules.objectstorage.aws.s3;

import java.util.UUID;

import org.apache.james.GuiceModuleTestRule;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Singleton;
import org.apache.james.blob.objectstorage.aws.Region;
import org.apache.james.modules.objectstorage.S3BlobConfiguration;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;

public class DockerAwsS3TestRule implements GuiceModuleTestRule {

    public DockerAwsS3TestRule() {
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                ensureAwsS3started();
                base.evaluate();
            }
        };
    }

    private void ensureAwsS3started() {
        DockerAwsS3Singleton.singleton.dockerAwsS3();
    }

    @Override
    public void await() {
    }

    @Override
    public Module getModule() {
        BucketName defaultBucketName = BucketName.of(UUID.randomUUID().toString());
        AwsS3AuthConfiguration authConfiguration = AwsS3AuthConfiguration.builder()
            .endpoint(DockerAwsS3Singleton.singleton.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        Region region = DockerAwsS3Container.REGION;
        S3BlobConfiguration configuration = S3BlobConfiguration.builder()
            .authConfiguration(authConfiguration)
            .region(region)
            .defaultBucketName(defaultBucketName)
            .bucketPrefix(UUID.randomUUID().toString())
            .build();

        return binder -> {
            binder.bind(BucketName.class).toInstance(defaultBucketName);
            binder.bind(Region.class).toInstance(region);
            binder.bind(AwsS3AuthConfiguration.class).toInstance(authConfiguration);
            binder.bind(S3BlobConfiguration.class).toInstance(configuration);
        };
    }

    public void start() {
        ensureAwsS3started();
    }

    public void stop() {
        //nothing to stop
    }
}

