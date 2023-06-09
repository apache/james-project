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

import org.apache.james.domainlist.api.DomainListManagementMBean;
import org.apache.james.rrt.api.RecipientRewriteTableManagementMBean;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.user.api.UsersRepositoryManagementMBean;
import org.apache.james.util.MDCBuilder;

import javax.management.MalformedObjectNameException;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class JmxDataProbe implements JmxProbe {

    private static final String DOMAINLIST_OBJECT_NAME = "org.apache.james:type=component,name=domainlist";
    private static final String VIRTUALUSERTABLE_OBJECT_NAME = "org.apache.james:type=component,name=recipientrewritetable";
    private static final String USERSREPOSITORY_OBJECT_NAME = "org.apache.james:type=component,name=usersrepository";
    private static final String JMX = "JMX";
    private static final String PARAMETER = "parameter";

    private DomainListManagementMBean domainListProxy;
    private RecipientRewriteTableManagementMBean virtualUserTableProxy;
    private UsersRepositoryManagementMBean usersRepositoryProxy;

    @Override
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

    public void addUser(String userName, String password) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "addUser")
                     .addToContext(PARAMETER, userName)
                     .build()) {
            usersRepositoryProxy.addUser(userName, password);
        }
    }

    public void removeUser(String username) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "removeUser")
                     .addToContext(PARAMETER, username)
                     .build()) {
            usersRepositoryProxy.deleteUser(username);
        }
    }

    public String[] listUsers() throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "listUsers")
                     .build()) {
            return usersRepositoryProxy.listAllUsers();
        }
    }

    public void setPassword(String userName, String password) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "setPassword")
                     .addToContext(PARAMETER, userName)
                     .build()) {
            usersRepositoryProxy.setPassword(userName, password);
        }
    }

    public boolean containsDomain(String domain) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "containsDomain")
                     .addToContext(PARAMETER, domain)
                     .build()) {
            return domainListProxy.containsDomain(domain);
        }
    }

    public void addDomain(String domain) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "addDomain")
                     .addToContext(PARAMETER, domain)
                     .build()) {
            domainListProxy.addDomain(domain);
        }
    }

    public void removeDomain(String domain) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "removeDomain")
                     .addToContext(PARAMETER, domain)
                     .build()) {
            domainListProxy.removeDomain(domain);
        }
    }

    public List<String> listDomains() throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "listDomains")
                     .build()) {
            return domainListProxy.getDomains();
        }
    }

    public void addDomainMapping(String domain, String targetDomain) throws Exception {
        try (Closeable closeable =
                     MDCBuilder.create()
                             .addToContext(MDCBuilder.PROTOCOL, JMX)
                             .addToContext(MDCBuilder.ACTION, "addDomainMapping")
                             .build()) {
            virtualUserTableProxy.addDomainMapping(domain, targetDomain);
        }
    }

    public void removeDomainMapping(String domain, String targetDomain) throws Exception {
        try (Closeable closeable =
                     MDCBuilder.create()
                             .addToContext(MDCBuilder.PROTOCOL, JMX)
                             .addToContext(MDCBuilder.ACTION, "removeDomainMapping")
                             .build()) {
            virtualUserTableProxy.removeDomainMapping(domain, targetDomain);
        }
    }

    public Mappings listDomainMappings(String domain) throws Exception {
        try (Closeable closeable =
                     MDCBuilder.create()
                             .addToContext(MDCBuilder.PROTOCOL, JMX)
                             .addToContext(MDCBuilder.ACTION, "listDomainMappings")
                             .build()) {
            return virtualUserTableProxy.getDomainMappings(domain);
        }
    }

    public Map<String, Mappings> listMappings() throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "listMappings")
                     .build()) {
            return virtualUserTableProxy.getAllMappings();
        }
    }

    public void addAddressMapping(String fromUser, String fromDomain, String toAddress) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "addAddressMapping")
                     .build()) {
            virtualUserTableProxy.addAddressMapping(fromUser, fromDomain, toAddress);
        }
    }

    public void removeAddressMapping(String fromUser, String fromDomain, String toAddress) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "removeAddressMapping")
                     .build()) {
            virtualUserTableProxy.removeAddressMapping(fromUser, fromDomain, toAddress);
        }
    }

    public Mappings listUserDomainMappings(String user, String domain) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "listUserDomainMappings")
                     .build()) {
            return virtualUserTableProxy.getUserDomainMappings(user, domain);
        }
    }

    public void addRegexMapping(String user, String domain, String regex) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "addRegexMapping")
                     .build()) {
            virtualUserTableProxy.addRegexMapping(user, domain, regex);
        }
    }

    public void removeRegexMapping(String user, String domain, String regex) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, JMX)
                     .addToContext(MDCBuilder.ACTION, "removeRegexMapping")
                     .build()) {
            virtualUserTableProxy.removeRegexMapping(user, domain, regex);
        }
    }
}