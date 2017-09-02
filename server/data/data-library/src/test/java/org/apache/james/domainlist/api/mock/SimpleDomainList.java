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
package org.apache.james.domainlist.api.mock;

import java.util.LinkedList;
import java.util.List;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;

import com.google.common.collect.ImmutableList;

/**
 * Simplest implementation for ManageableDomainList
 */
public class SimpleDomainList implements DomainList {

    private final List<String> domains = new LinkedList<>();

    @Override
    public boolean containsDomain(String domain) throws DomainListException {
        return domains.contains(domain);
    }

    @Override
    public List<String> getDomains() throws DomainListException {
        return ImmutableList.copyOf(domains);
    }

    @Override
    public void addDomain(String domain) throws DomainListException {
        if (domains.contains(domain)) {
            throw new DomainListException("Domain " + domain + " already exist");
        }
        domains.add(domain);
    }

    @Override
    public void removeDomain(String domain) throws DomainListException {
        if (!domains.remove(domain)) {
            throw new DomainListException("Domain " + domain + " does not exist");
        }
    }

    @Override
    public String getDefaultDomain() {
        return "localhost";
    }
}