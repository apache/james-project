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

package org.apache.james.cli.probe.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.management.MalformedObjectNameException;

import org.apache.james.domainlist.api.DomainListManagementMBean;
import org.apache.james.probe.DataProbe;
import org.apache.james.rrt.api.RecipientRewriteTableManagementMBean;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.user.api.UsersRepositoryManagementMBean;
import org.apache.james.util.MDCBuilder;

public class JmxDataProbe implements DataProbe, JmxProbe {

    private final static String DOMAINLIST_OBJECT_NAME = "org.apache.james:type=component,name=domainlist";
    private final static String VIRTUALUSERTABLE_OBJECT_NAME = "org.apache.james:type=component,name=recipientrewritetable";
    private final static String USERSREPOSITORY_OBJECT_NAME = "org.apache.james:type=component,name=usersrepository";

    private DomainListManagementMBean domainListProxy;
    private RecipientRewriteTableManagementMBean virtualUserTableProxy;
    private UsersRepositoryManagementMBean usersRepositoryProxy;

    public JmxDataProbe connect(JmxConnection jmxc) throws IOException {
        try {
            domainListProxy = jmxc.retrieveBean(DomainListManagementMBean.class, DOMAINLIST_OBJECT_NAME);
            virtualUserTableProxy = jmxc.retrieveBean(RecipientRewriteTableManagementMBean.class, VIRTUALUSERTABLE_OBJECT_NAME);
            usersRepositoryProxy = jmxc.retrieveBean(UsersRepositoryManagementMBean.class, USERSREPOSITORY_OBJECT_NAME);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Invalid ObjectName? Please report this as a bug.", e);
        }
        return this;
    }

    @Override
    public void addUser(String userName, String password) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "addUser")
                     .addContext("parameter", userName)
                     .build()) {
            usersRepositoryProxy.addUser(userName, password);
        }
    }

    @Override
    public void removeUser(String username) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "removeUser")
                     .addContext("parameter", username)
                     .build()) {
            usersRepositoryProxy.deleteUser(username);
        }
    }

    @Override
    public String[] listUsers() throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "listUsers")
                     .build()) {
            return usersRepositoryProxy.listAllUsers();
        }
    }

    @Override
    public void setPassword(String userName, String password) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "setPassword")
                     .addContext("parameter", userName)
                     .build()) {
            usersRepositoryProxy.setPassword(userName, password);
        }
    }

    @Override
    public boolean containsDomain(String domain) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "containsDomain")
                     .addContext("parameter", domain)
                     .build()) {
            return domainListProxy.containsDomain(domain);
        }
    }

    @Override
    public String getDefaultDomain() throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "getDefaultDomain")
                     .build()) {
            return domainListProxy.getDefaultDomain();
        }
    }

    @Override
    public void addDomain(String domain) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "addDomain")
                     .addContext("parameter", domain)
                     .build()) {
            domainListProxy.addDomain(domain);
        }
    }

    @Override
    public void removeDomain(String domain) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "removeDomain")
                     .addContext("parameter", domain)
                     .build()) {
            domainListProxy.removeDomain(domain);
        }
    }

    @Override
    public List<String> listDomains() throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "listDomains")
                     .build()) {
            return domainListProxy.getDomains();
        }
    }

    @Override
    public Map<String, Mappings> listMappings() throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "listMappings")
                     .build()) {
            return virtualUserTableProxy.getAllMappings();
        }
    }

    @Override
    public void addAddressMapping(String user, String domain, String toAddress) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "addAddressMapping")
                     .build()) {
            virtualUserTableProxy.addAddressMapping(user, domain, toAddress);
        }
    }

    @Override
    public void removeAddressMapping(String user, String domain, String fromAddress) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "removeAddressMapping")
                     .build()) {
            virtualUserTableProxy.removeAddressMapping(user, domain, fromAddress);
        }
    }

    @Override
    public Mappings listUserDomainMappings(String user, String domain) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "listUserDomainMappings")
                     .build()) {
            return virtualUserTableProxy.getUserDomainMappings(user, domain);
        }
    }

    @Override
    public void addRegexMapping(String user, String domain, String regex) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "addRegexMapping")
                     .build()) {
            virtualUserTableProxy.addRegexMapping(user, domain, regex);
        }
    }

    @Override
    public void removeRegexMapping(String user, String domain, String regex) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.PROTOCOL, "CLI")
                     .addContext(MDCBuilder.ACTION, "removeRegexMapping")
                     .build()) {
            virtualUserTableProxy.removeRegexMapping(user, domain, regex);
        }
    }

}