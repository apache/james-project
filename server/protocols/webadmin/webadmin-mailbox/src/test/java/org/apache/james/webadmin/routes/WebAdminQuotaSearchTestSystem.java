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

package org.apache.james.webadmin.routes;

import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.jackson.QuotaModule;
import org.apache.james.webadmin.service.DomainQuotaService;
import org.apache.james.webadmin.service.GlobalQuotaService;
import org.apache.james.webadmin.service.UserQuotaService;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.collect.ImmutableSet;

import io.restassured.specification.RequestSpecification;

public class WebAdminQuotaSearchTestSystem {
    private final QuotaSearchTestSystem quotaSearchTestSystem;
    private final WebAdminServer webAdminServer;
    private final RequestSpecification requestSpecBuilder;

    public WebAdminQuotaSearchTestSystem(QuotaSearchTestSystem quotaSearchTestSystem) throws Exception {
        this.quotaSearchTestSystem = quotaSearchTestSystem;

        UserQuotaService userQuotaService = new UserQuotaService(quotaSearchTestSystem.getMaxQuotaManager(),
            quotaSearchTestSystem.getQuotaManager(),
            quotaSearchTestSystem.getQuotaRootResolver(),
            quotaSearchTestSystem.getQuotaSearcher());

        QuotaModule quotaModule = new QuotaModule();
        JsonTransformer jsonTransformer = new JsonTransformer(quotaModule);
        TaskManager taskManager = new MemoryTaskManager(new Hostname("foo"));
        UserQuotaRoutes userQuotaRoutes = new UserQuotaRoutes(quotaSearchTestSystem.getUsersRepository(),
            userQuotaService,
            jsonTransformer,
            ImmutableSet.of(quotaModule),
            taskManager,
            ImmutableSet.of());
        DomainQuotaRoutes domainQuotaRoutes = new DomainQuotaRoutes(
            quotaSearchTestSystem.getDomainList(),
            new DomainQuotaService(quotaSearchTestSystem.getMaxQuotaManager()),
            quotaSearchTestSystem.getUsersRepository(),
            jsonTransformer,
            ImmutableSet.of(quotaModule));
        GlobalQuotaRoutes globalQuotaRoutes = new GlobalQuotaRoutes(
            new GlobalQuotaService(quotaSearchTestSystem.getMaxQuotaManager()),
            jsonTransformer);

        this.webAdminServer = WebAdminUtils.createWebAdminServer(userQuotaRoutes, domainQuotaRoutes, globalQuotaRoutes)
            .start();

        this.requestSpecBuilder = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .build();
    }

    public QuotaSearchTestSystem getQuotaSearchTestSystem() {
        return quotaSearchTestSystem;
    }

    public WebAdminServer getWebAdminServer() {
        return webAdminServer;
    }

    public RequestSpecification getRequestSpecification() {
        return requestSpecBuilder;
    }
}
