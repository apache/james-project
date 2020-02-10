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

import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.DumbBlobStoreContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerAwsS3Extension.class)
public class S3DumbBlobStoreTest implements DumbBlobStoreContract {

    private S3DumbBlobStore testee;

    @BeforeEach
    void setUp(DockerAwsS3Container dockerAwsS3) {

        AwsS3AuthConfiguration configuration = AwsS3AuthConfiguration.builder()
            .endpoint(dockerAwsS3.getEndpoint())
            .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
            .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
            .build();

        testee = new S3DumbBlobStore(configuration, dockerAwsS3.dockerAwsS3().region());
    }

    @AfterEach
    void tearDown() {
        testee.deleteAllBuckets().block();
        testee.close();
    }

    @Override
    public DumbBlobStore testee() {
        return testee;
    }

}
