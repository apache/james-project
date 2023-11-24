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

import java.util.function.Function;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;

// TODO use this in Guice and Spring
public class DomainListFactory {
    private final DNSService dnsService;
    private final Function<DomainListConfiguration, DomainList> domainListFunction;

    public DomainListFactory(DNSService dnsService, Function<DomainListConfiguration, DomainList> domainListFunction) {
        this.dnsService = dnsService;
        this.domainListFunction = domainListFunction;
    }

    public DomainList create(DomainListConfiguration domainListConfiguration) {
        DomainListConfiguration newConfiguration = new DomainListConfiguration.Transformer().apply(domainListConfiguration);
        DomainList internalDomainList = domainListFunction.apply(newConfiguration);
        return wrap(internalDomainList, newConfiguration);
    }

    private DomainList wrapWithCache(DomainList domainList, DomainListConfiguration configuration) {
        if (configuration.isCacheEnabled()) {
            return new CachingDomainList(domainList, configuration);
        }
        return domainList;
    }

    private DomainList wrapWithAutoDetect(DomainList domainList, DomainListConfiguration configuration) {
        if (configuration.isAutoDetect() || configuration.isAutoDetectIp()) {
            return new AutodetectDomainList(dnsService, domainList, configuration);
        }
        return domainList;
    }

    public DomainList wrap(DomainList internalDomainList, DomainListConfiguration domainListConfiguration) {
        DomainList withCache = wrapWithCache(internalDomainList, domainListConfiguration);
        DomainList withAutoDetect = wrapWithAutoDetect(withCache, domainListConfiguration);
        return new LoggingDomainList(withAutoDetect);
    }

    public DomainList createWithAllInitialisation(DomainListConfiguration domainListConfiguration) {
        DomainList domainList = create(domainListConfiguration);
        new DomainCreator(domainList, domainListConfiguration).createConfiguredDomains();
        return domainList;
    }
}
