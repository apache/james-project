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

package org.apache.james.modules.objectstorage.guice;

import java.util.UUID;

import javax.inject.Inject;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.GuiceModuleTestRule;
import org.apache.james.blob.objectstorage.ContainerName;
import org.apache.james.blob.objectstorage.ObjectStorageBlobsDAO;
import org.apache.james.blob.objectstorage.swift.Credentials;
import org.apache.james.blob.objectstorage.swift.SwiftKeystone2ObjectStorage;
import org.apache.james.blob.objectstorage.swift.TenantName;
import org.apache.james.blob.objectstorage.swift.UserName;
import org.apache.james.modules.objectstorage.ObjectStorageBlobConfiguration;
import org.apache.james.modules.objectstorage.PayloadCodecFactory;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;

public class DockerSwiftTestRule implements GuiceModuleTestRule {

    private static class ContainerCleanUp implements CleanupTasksPerformer.CleanupTask {

        private final ObjectStorageBlobsDAO blobsDAO;

        @Inject
        public ContainerCleanUp(ObjectStorageBlobsDAO blobsDAO) {
            this.blobsDAO = blobsDAO;
        }

        @Override
        public Result run() {
            blobsDAO.deleteContainer();

            return Result.COMPLETED;
        }
    }

    private final PayloadCodecFactory payloadCodecFactory;
    private org.apache.james.blob.objectstorage.DockerSwiftRule swiftContainer =
        new org.apache.james.blob.objectstorage.DockerSwiftRule();

    public DockerSwiftTestRule() {
        this(PayloadCodecFactory.DEFAULT);
    }

    public DockerSwiftTestRule(PayloadCodecFactory payloadCodecFactory) {
        this.payloadCodecFactory = payloadCodecFactory;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return swiftContainer.apply(base, description);
    }

    @Override
    public void await() {
    }

    @Override
    public Module getModule() {
        SwiftKeystone2ObjectStorage.Configuration authConfiguration = SwiftKeystone2ObjectStorage.configBuilder()
            .credentials(Credentials.of("demo"))
            .tenantName(TenantName.of("test"))
            .userName(UserName.of("demo"))
            .endpoint(swiftContainer.dockerSwift().keystoneV2Endpoint())
            .build();

        ContainerName containerName = ContainerName.of(UUID.randomUUID().toString());
        ObjectStorageBlobConfiguration configuration = ObjectStorageBlobConfiguration.builder()
            .codec(payloadCodecFactory)
            .swift()
            .container(containerName)
            .keystone2(authConfiguration)
            .aesSalt("c603a7327ee3dcbc031d8d34b1096c605feca5e1")
            .aesPassword("dockerSwiftEncryption".toCharArray())
            .build();

        return binder -> {
            binder.bind(ObjectStorageBlobConfiguration.class).toInstance(configuration);

            Multibinder.newSetBinder(binder, CleanupTasksPerformer.CleanupTask.class)
                .addBinding()
                .to(ContainerCleanUp.class);
        };
    }


    public void start() {
        swiftContainer.start();
    }

    public void stop() {
        swiftContainer.stop();
    }

}