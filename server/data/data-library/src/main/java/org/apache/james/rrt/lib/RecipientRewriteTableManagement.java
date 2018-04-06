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

import javax.inject.Inject;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

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
        rrt.addRegexMapping(user, Domain.of(domain), regex);
    }

    @Override
    public void removeRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        rrt.removeRegexMapping(user, Domain.of(domain), regex);
    }

    @Override
    public void addAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        rrt.addAddressMapping(user, Domain.of(domain), address);
    }

    @Override
    public void removeAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        rrt.removeAddressMapping(user, Domain.of(domain), address);
    }

    @Override
    public void addErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        rrt.addErrorMapping(user, Domain.of(domain), error);
    }

    @Override
    public void removeErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        rrt.removeErrorMapping(user, Domain.of(domain), error);
    }

    @Override
    public void addDomainMapping(String domain, String targetDomain) throws RecipientRewriteTableException {
        rrt.addAliasDomainMapping(Domain.of(domain), Domain.of(targetDomain));
    }

    @Override
    public void removeDomainMapping(String domain, String targetDomain) throws RecipientRewriteTableException {
        rrt.removeAliasDomainMapping(Domain.of(domain), Domain.of(targetDomain));
    }

    @Override
    public Mappings getUserDomainMappings(String user, String domain) throws RecipientRewriteTableException {
        return rrt.getUserDomainMappings(user, Domain.of(domain));
    }

    @Override
    public void addMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        rrt.addMapping(user, Domain.of(domain), MappingImpl.of(mapping));
    }

    @Override
    public void removeMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        rrt.removeMapping(user, Domain.of(domain), MappingImpl.of(mapping));
    }

    @Override
    public Map<String, Mappings> getAllMappings() throws RecipientRewriteTableException {
        return ImmutableMap.copyOf(rrt.getAllMappings());
    }

    @Override
    public void addForwardMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        rrt.addForwardMapping(user, Domain.of(domain), address);
    }

    @Override
    public void removeForwardMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        rrt.removeForwardMapping(user, Domain.of(domain), address);
    }

    @Override
    public void addGroupMapping(String toUser, String toDomain, String fromAddress) {
        try {
            rrt.addGroupMapping(toUser, Domain.of(toDomain), fromAddress);
        } catch (RecipientRewriteTableException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeGroupMapping(String toUser, String toDomain, String fromAddress) {
        try {
            rrt.removeForwardMapping(toUser, Domain.of(toDomain), fromAddress);
        } catch (RecipientRewriteTableException e) {
            throw new RuntimeException(e);
        }
    }
}
