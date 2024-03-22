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

import java.util.Map;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.RecipientRewriteTableManagementMBean;

import com.google.common.collect.ImmutableMap;

/**
 * Management for RecipientRewriteTables
 */
public class RecipientRewriteTableManagement extends StandardMBean implements RecipientRewriteTableManagementMBean {

    private final RecipientRewriteTable rrt;

    @Inject
    protected RecipientRewriteTableManagement(RecipientRewriteTable rrt) throws NotCompliantMBeanException {
        super(RecipientRewriteTableManagementMBean.class);
        this.rrt = rrt;
    }

    @Override
    public void addRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromUser(user, domain);
        rrt.addRegexMapping(source, regex);
    }

    @Override
    public void removeRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromUser(user, domain);
        rrt.removeRegexMapping(source, regex);
    }

    @Override
    public void addAddressMapping(String fromUser, String fromDomain, String toAddress) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromUser(fromUser, fromDomain);
        rrt.addAddressMapping(source, toAddress);
    }

    @Override
    public void removeAddressMapping(String fromUser, String fromDomain, String toAddress) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromUser(fromUser, fromDomain);
        rrt.removeAddressMapping(source, toAddress);
    }

    @Override
    public void addErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromUser(user, domain);
        rrt.addErrorMapping(source, error);
    }

    @Override
    public void removeErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromUser(user, domain);
        rrt.removeErrorMapping(source, error);
    }

    @Override
    public void addDomainMapping(String domain, String targetDomain) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromDomain(Domain.of(domain));
        rrt.addDomainMapping(source, Domain.of(targetDomain));
    }

    @Override
    public void removeDomainMapping(String domain, String targetDomain) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromDomain(Domain.of(domain));
        rrt.removeDomainMapping(source, Domain.of(targetDomain));
    }

    @Override
    public Mappings getDomainMappings(String domain) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromDomain(Domain.of(domain));
        return rrt.getStoredMappings(source);
    }

    @Override
    public Mappings getUserDomainMappings(String user, String domain) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromUser(user, domain);
        return rrt.getStoredMappings(source);
    }

    @Override
    public void addMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromUser(user, domain);
        rrt.addMapping(source, Mapping.of(mapping));
    }

    @Override
    public void removeMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        MappingSource source = MappingSource.fromUser(user, domain);
        rrt.removeMapping(source, Mapping.of(mapping));
    }

    @Override
    public Map<String, Mappings> getAllMappings() throws RecipientRewriteTableException {
        return rrt.getAllMappings()
            .entrySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(
                    entry -> entry.getKey().asString(),
                    entry -> entry.getValue()));
    }
}
