/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership. The ASF licenses this file    *
 * to you under the Apache License, Version 2.0 (the             *
 * "License"); you may not use this file except in compliance   *
 * with the License. You may obtain a copy of the License at     *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the     *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.blob.objectstorage.aws;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public class DockerCephS3Extension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    private final DockerCephS3Container container = new DockerCephS3Container();

    @Override
    public void beforeAll(ExtensionContext context) {
        container.start();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        container.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == DockerCephS3Container.class;
    }

    @Override
    public DockerCephS3Container resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return container;
    }
}
