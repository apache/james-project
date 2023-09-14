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

import org.apache.james.RegistrableExtension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

public class DockerAwsS3Extension implements RegistrableExtension {

    private final DockerAwsS3Container container = new DockerAwsS3Container();

    public DockerAwsS3Extension() {

    }


    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        if (!container.getRawContainer().isRunning()) {
            container.start();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        container.stop();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        try {
            container.tryDeleteAllData();
        } catch (Exception ignored) {
            // Ignored
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerAwsS3Container.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return container;
    }

    public DockerAwsS3Container getContainer() {
        return container;
    }

}
