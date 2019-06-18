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

package org.apache.james.rrt.lib;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.api.mock.SimpleDomainList;

public interface RecipientRewriteTableFixture {

    static SimpleDomainList domainListForCucumberTests() throws DomainListException {
        SimpleDomainList domainList = new SimpleDomainList();
        domainList.addDomain(Domain.LOCALHOST);
        domainList.addDomain(Domain.of("aliasdomain"));
        domainList.addDomain(Domain.of("domain1"));
        domainList.addDomain(Domain.of("domain2"));
        domainList.addDomain(Domain.of("domain3"));
        domainList.addDomain(Domain.of("domain4"));

        return domainList;
    }
}
