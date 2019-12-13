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
import org.apache.james.linshare.LinshareConfiguration;
import org.apache.james.linshare.LinshareExtension;
import org.apache.james.linshare.LinshareFixture;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class LinshareGuiceExtension implements GuiceModuleTestExtension {
    private final LinshareExtension linshareExtension;

    public LinshareGuiceExtension() {
        linshareExtension = new LinshareExtension();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        linshareExtension.beforeEach(extensionContext);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        linshareExtension.getLinshare()
            .stop();
    }

    @Override
    public Module getModule() {
        return Modules.combine(
            binder -> binder.bind(BlobExportImplChoice.class)
                .toInstance(BlobExportImplChoice.LINSHARE),
            binder -> {
                try {
                    binder.bind(LinshareConfiguration.class)
                        .toInstance(linshareExtension.configurationWithJwtFor(LinshareFixture.USER_1));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        );
    }

    public LinshareExtension getLinshareJunitExtension() {
        return linshareExtension;
    }
}
