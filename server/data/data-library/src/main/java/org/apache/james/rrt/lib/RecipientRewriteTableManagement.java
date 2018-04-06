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

    private RecipientRewriteTable rrt;

    protected RecipientRewriteTableManagement() throws NotCompliantMBeanException {
        super(RecipientRewriteTableManagementMBean.class);
    }

    @Inject
    public void setManageableRecipientRewriteTable(RecipientRewriteTable rrt) {
        this.rrt = rrt;
    }

    @Override
    public void addRegexMapping(String user, String domain, String regex) throws Exception {
        try {
            rrt.addRegexMapping(user, Domain.of(domain), regex);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void removeRegexMapping(String user, String domain, String regex) throws Exception {
        try {
            rrt.removeRegexMapping(user, Domain.of(domain), regex);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void addAddressMapping(String user, String domain, String address) throws Exception {
        try {
            rrt.addAddressMapping(user, Domain.of(domain), address);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void removeAddressMapping(String user, String domain, String address) throws Exception {
        try {
            rrt.removeAddressMapping(user, Domain.of(domain), address);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void addErrorMapping(String user, String domain, String error) throws Exception {
        try {
            rrt.addErrorMapping(user, Domain.of(domain), error);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void removeErrorMapping(String user, String domain, String error) throws Exception {
        try {
            rrt.removeErrorMapping(user, Domain.of(domain), error);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void addDomainMapping(String domain, String targetDomain) throws Exception {
        try {
            rrt.addAliasDomainMapping(Domain.of(domain), Domain.of(targetDomain));
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void removeDomainMapping(String domain, String targetDomain) throws Exception {
        try {
            rrt.removeAliasDomainMapping(Domain.of(domain), Domain.of(targetDomain));
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public Mappings getUserDomainMappings(String user, String domain) throws Exception {
        try {
            return rrt.getUserDomainMappings(user, Domain.of(domain));
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void addMapping(String user, String domain, String mapping) throws Exception {
        try {
            rrt.addMapping(user, Domain.of(domain), MappingImpl.of(mapping));
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void removeMapping(String user, String domain, String mapping) throws Exception {
        try {
            rrt.removeMapping(user, Domain.of(domain), MappingImpl.of(mapping));
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public Map<String, Mappings> getAllMappings() throws Exception {
        try {
            return ImmutableMap.copyOf(rrt.getAllMappings());
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void addForwardMapping(String user, String domain, String address) throws Exception {
        try {
            rrt.addForwardMapping(user, Domain.of(domain), address);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void removeForwardMapping(String user, String domain, String address) throws Exception {
        try {
            rrt.removeForwardMapping(user, Domain.of(domain), address);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

}
