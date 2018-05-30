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

import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;

import java.util.List;

import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.quota.search.QuotaSearchTestSystem;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;

import com.jayway.restassured.specification.RequestSpecification;

public class WebAdminQuotaSearchTestSystem {
    private final QuotaSearchTestSystem quotaSearchTestSystem;
    private final WebAdminServer webAdminServer;
    private final RequestSpecification requestSpecBuilder;

    public WebAdminQuotaSearchTestSystem(QuotaSearchTestSystem quotaSearchTestSystem, List<Routes> routes) throws Exception {
        this.quotaSearchTestSystem = quotaSearchTestSystem;
        this.webAdminServer = WebAdminUtils.createWebAdminServer(new NoopMetricFactory(), routes);
        this.webAdminServer.configure(NO_CONFIGURATION);
        this.webAdminServer.await();

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
