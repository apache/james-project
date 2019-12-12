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

import java.util.UUID;

import javax.inject.Inject;

import org.apache.james.GuiceModuleTestRule;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.objectstorage.DockerAwsS3Singleton;
import org.apache.james.blob.objectstorage.ObjectStorageBlobStore;
import org.apache.james.blob.objectstorage.PayloadCodec;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.modules.objectstorage.ObjectStorageBlobConfiguration;
import org.apache.james.modules.objectstorage.ObjectStorageProvider;
import org.apache.james.modules.objectstorage.PayloadCodecFactory;
import org.apache.james.utils.GuiceProbe;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;

public class DockerAwsS3TestRule implements GuiceModuleTestRule {

    public static class TestAwsS3BlobStoreProbe implements GuiceProbe {

        private final ObjectStorageBlobStore awss3BlobStore;

        @Inject
        TestAwsS3BlobStoreProbe(ObjectStorageBlobStore awss3BlobStore) {
            this.awss3BlobStore = awss3BlobStore;
        }

        public PayloadCodec getAwsS3PayloadCodec() {
            return awss3BlobStore.getPayloadCodec();
        }
    }

    private final PayloadCodecFactory payloadCodecFactory;

    public DockerAwsS3TestRule() {
        this(PayloadCodecFactory.DEFAULT);
    }

    public DockerAwsS3TestRule(PayloadCodecFactory payloadCodecFactory) {
        this.payloadCodecFactory = payloadCodecFactory;
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

        ObjectStorageBlobConfiguration configuration = ObjectStorageBlobConfiguration.builder()
            .codec(payloadCodecFactory)
            .provider(ObjectStorageProvider.AWSS3)
            .authConfiguration(authConfiguration)
            .aesSalt("c603a7327ee3dcbc031d8d34b1096c605feca5e1")
            .aesPassword("dockerAwsS3Encryption".toCharArray())
            .defaultBucketName(defaultBucketName)
            .bucketPrefix(UUID.randomUUID().toString())
            .build();

        return binder -> {
            binder.bind(ObjectStorageBlobConfiguration.class).toInstance(configuration);

            Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(TestAwsS3BlobStoreProbe.class);
        };
    }

    public void start() {
        ensureAwsS3started();
    }

    public void stop() {
        //nothing to stop
    }
}

