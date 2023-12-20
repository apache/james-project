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

package mailets;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;

import com.github.steveash.guavate.Guavate;

import spark.Service;

public class BlackListRoutes implements Routes {
    private static final String BASE_PATH = "/blacklist";

    private final PerDomainAddressBlackList blackList;
    private final JsonTransformer jsonTransformer;

    @Inject
    public BlackListRoutes(PerDomainAddressBlackList blackList) {
        this.blackList = blackList;
        this.jsonTransformer = new JsonTransformer();
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(getBasePath() + "/:domain", (req, res) -> {
            Domain domain = Domain.of(req.params("domain"));
            return blackList.list(domain)
                .stream()
                .map(MailAddress::asString)
                .collect(Guavate.toImmutableList());
        }, jsonTransformer);

        service.delete(getBasePath() + "/:domain", (req, res) -> {
            Domain domain = Domain.of(req.params("domain"));
            blackList.clear(domain);
            return Responses.returnNoContent(res);
        }, jsonTransformer);

        service.put(getBasePath() + "/:domain/:maddress", (req, res) -> {
            Domain domain = Domain.of(req.params("domain"));
            MailAddress address = new MailAddress(req.params("maddress"));
            blackList.add(domain, address);
            return Responses.returnNoContent(res);
        }, jsonTransformer);

        service.delete(getBasePath() + "/:domain/:maddress", (req, res) -> {
            Domain domain = Domain.of(req.params("domain"));
            MailAddress address = new MailAddress(req.params("maddress"));
            blackList.remove(domain, address);
            return Responses.returnNoContent(res);
        }, jsonTransformer);
    }
}
