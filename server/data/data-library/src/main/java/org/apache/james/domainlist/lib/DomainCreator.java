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

package org.apache.james.domainlist.lib;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Startable;

import com.github.fge.lambdas.Throwing;
import javax.inject.Inject;

public class DomainCreator implements Startable {
    private final DomainList domainList;
    private final DomainListConfiguration configuration;

    @Inject
    public DomainCreator(DomainList domainList, DomainListConfiguration configuration) {
        this.domainList = domainList;
        this.configuration = configuration;
    }

    public void createConfiguredDomains() {
        configuration.getConfiguredDomains().stream()
            .filter(Throwing.predicate((Domain domain) -> !domainList.containsDomain(domain)).sneakyThrow())
            .forEach(Throwing.consumer(domainList::addDomain).sneakyThrow());
    }
}
