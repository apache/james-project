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

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

class CachingDomainList implements DomainList {
    private final DomainList underlying;
    private final LoadingCache<Domain, Boolean> cache;

    public CachingDomainList(DomainList underlying, DomainListConfiguration configuration) {
        this.underlying = underlying;

        this.cache = CacheBuilder.newBuilder()
            .expireAfterWrite(configuration.getCacheExpiracy())
            .build(new CacheLoader<>() {
                @Override
                public Boolean load(Domain key) throws DomainListException {
                    return underlying.containsDomain(key);
                }
            });
    }

    @Override
    public Domain getDefaultDomain() throws DomainListException {
        return underlying.getDefaultDomain();
    }

    @Override
    public boolean containsDomain(Domain domain) throws DomainListException {
        try {
            return cache.get(domain);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof DomainListException) {
                throw (DomainListException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Domain> getDomains() throws DomainListException {
        return underlying.getDomains();
    }

    @Override
    public void removeDomain(Domain domain) throws DomainListException {
        underlying.removeDomain(domain);
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        underlying.addDomain(domain);
    }
}
