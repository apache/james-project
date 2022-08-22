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

package org.apache.james.rspamd;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.james.GuiceModuleTestExtension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

public class DockerRSpamDExtension implements GuiceModuleTestExtension {
    private static final DockerRSpamD DOCKER_RSPAMD_SINGLETON = new DockerRSpamD();

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        if (!DOCKER_RSPAMD_SINGLETON.isRunning()) {
            DOCKER_RSPAMD_SINGLETON.start();
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        DOCKER_RSPAMD_SINGLETON.flushAll();
    }

    public DockerRSpamD dockerRSpamD() {
        return DOCKER_RSPAMD_SINGLETON;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == DockerRSpamD.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerRSpamD();
    }

    public URL getBaseUrl() {
        try {
            return new URL("http://127.0.0.1:" + dockerRSpamD().getPort());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
