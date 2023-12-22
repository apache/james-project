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

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Singleton;
import org.apache.james.modules.objectstorage.aws.s3.DockerAwsS3TestRule;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class AwsS3BlobStoreExtension implements GuiceModuleTestExtension {

    private final DockerAwsS3TestRule awsS3TestRule;

    public AwsS3BlobStoreExtension() {
        this.awsS3TestRule = new DockerAwsS3TestRule();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        ensureAwsS3started();
    }

    private void ensureAwsS3started() {
        DockerAwsS3Singleton.singleton.dockerAwsS3();
    }

    @Override
    public Module getModule() {
        return awsS3TestRule.getModule();
    }
}
