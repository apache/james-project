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

import java.util.Collection;
import java.util.Map;

import javax.inject.Inject;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.RecipientRewriteTableManagementMBean;

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

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTableManagementMBean#addRegexMapping
     * (java.lang.String, java.lang.String, java.lang.String)
     */
    public void addRegexMapping(String user, String domain, String regex) throws Exception {
        try {
            rrt.addRegexMapping(user, domain, regex);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTableManagementMBean#removeRegexMapping
     * (java.lang.String, java.lang.String, java.lang.String)
     */
    public void removeRegexMapping(String user, String domain, String regex) throws Exception {
        try {
            rrt.removeRegexMapping(user, domain, regex);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTableManagementMBean#addAddressMapping
     * (java.lang.String, java.lang.String, java.lang.String)
     */
    public void addAddressMapping(String user, String domain, String address) throws Exception {
        try {
            rrt.addAddressMapping(user, domain, address);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTableManagementMBean#removeAddressMapping
     * (java.lang.String, java.lang.String, java.lang.String)
     */
    public void removeAddressMapping(String user, String domain, String address) throws Exception {
        try {
            rrt.removeAddressMapping(user, domain, address);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTableManagementMBean#addErrorMapping
     * (java.lang.String, java.lang.String, java.lang.String)
     */
    public void addErrorMapping(String user, String domain, String error) throws Exception {
        try {
            rrt.addErrorMapping(user, domain, error);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTableManagementMBean#removeErrorMapping
     * (java.lang.String, java.lang.String, java.lang.String)
     */
    public void removeErrorMapping(String user, String domain, String error) throws Exception {
        try {
            rrt.removeErrorMapping(user, domain, error);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    public void addDomainMapping(String domain, String targetDomain) throws Exception {
        try {
            rrt.addAliasDomainMapping(domain, targetDomain);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    public void removeDomainMapping(String domain, String targetDomain) throws Exception {
        try {
            rrt.removeAliasDomainMapping(domain, targetDomain);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * @see org.apache.james.rrt.api.RecipientRewriteTableManagementMBean
     * #getUserDomainMappings(java.lang.String, java.lang.String)
     */
    public Collection<String> getUserDomainMappings(String user, String domain) throws Exception {
        try {
            return rrt.getUserDomainMappings(user, domain);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTableManagementMBean
     * #addMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public void addMapping(String user, String domain, String mapping) throws Exception {
        try {
            rrt.addMapping(user, domain, mapping);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTableManagementMBean#removeMapping
     * (java.lang.String, java.lang.String, java.lang.String)
     */
    public void removeMapping(String user, String domain, String mapping) throws Exception {
        try {
            rrt.removeMapping(user, domain, mapping);
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * @see
     * org.apache.james.rrt.api.RecipientRewriteTableManagementMBean#getAllMappings()
     */
    public Map<String, Collection<String>> getAllMappings() throws Exception {
        try {
            return rrt.getAllMappings();
        } catch (RecipientRewriteTableException e) {
            throw new Exception(e.getMessage());
        }
    }

}
