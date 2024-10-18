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

import javax.management.MalformedObjectNameException;

import jakarta.mail.internet.AddressException;

import org.apache.commons.lang3.EnumUtils;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.DropListManagementMBean;
import org.apache.james.droplists.api.OwnerScope;
import org.apache.james.util.MDCBuilder;

public class JmxDropListProbe implements JmxProbe {

    private static final String DROPLIST_OBJECT_NAME = "org.apache.james:type=component,name=droplist";
    private static final String JMX = "JMX";

    private DropListManagementMBean dropListProxy;

    @Override
    public JmxDropListProbe connect(JmxConnection jmxc) throws IOException {
        try {
            dropListProxy = jmxc.retrieveBean(DropListManagementMBean.class, DROPLIST_OBJECT_NAME);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Invalid ObjectName? Please report this as a bug.", e);
        }
        return this;
    }

    public void addDropListEntry(String ownerScope, String owner, String deniedEntity) throws Exception {
        try (Closeable closeable = buildMdc("addDropListEntry")) {
            DropListEntry dropListEntry = getDropListEntry(checkValidOwnerScope(ownerScope), owner, deniedEntity);
            dropListProxy.add(dropListEntry);
        }
    }

    public void removeDropListEntry(String ownerScope, String owner, String deniedEntity) throws Exception {
        try (Closeable closeable = buildMdc("removeDropListEntry")) {
            DropListEntry dropListEntry = getDropListEntry(checkValidOwnerScope(ownerScope), owner, deniedEntity);
            dropListProxy.remove(dropListEntry);
        }
    }

    public List<String> getDropList(String ownerScope, String owner) throws Exception {
        try (Closeable closeable = buildMdc("getDropList")) {
            return dropListProxy.list(checkValidOwnerScope(ownerScope), owner);
        }
    }

    public String runDropListQuery(String ownerScope, String owner, String deniedEntity) throws Exception {
        try (Closeable closeable = buildMdc("dropListQuery")) {
            return dropListProxy.query(checkValidOwnerScope(ownerScope), owner, checkValidMailAddress(deniedEntity));
        }
    }

    private Closeable buildMdc(String action) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.PROTOCOL, JMX)
            .addToContext(MDCBuilder.ACTION, action)
            .build();
    }

    private DropListEntry getDropListEntry(OwnerScope ownerScope, String owner, String deniedEntity) {
        DropListEntry.Builder dropListEntryBuilder = DropListEntry.builder();
        switch (ownerScope) {
            case GLOBAL -> dropListEntryBuilder = dropListEntryBuilder.forAll();
            case DOMAIN -> dropListEntryBuilder = dropListEntryBuilder.domainOwner(checkValidDomain(owner));
            case USER -> dropListEntryBuilder = dropListEntryBuilder.userOwner(checkValidMailAddress(owner));
        }
        if (deniedEntity.contains("@")) {
            dropListEntryBuilder.denyAddress(checkValidMailAddress(deniedEntity));
        } else {
            dropListEntryBuilder.denyDomain(checkValidDomain(deniedEntity));
        }
        return dropListEntryBuilder.build();
    }

    private static MailAddress checkValidMailAddress(String address) {
        try {
            return new MailAddress(address);
        } catch (AddressException e) {
            throw new IllegalArgumentException("Invalid mail address " + address);
        }
    }

    private Domain checkValidDomain(String domainName) {
        try {
            return Domain.of(domainName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid domain " + domainName);
        }
    }

    private OwnerScope checkValidOwnerScope(String ownerScope) {
        try {
            return OwnerScope.valueOf(ownerScope.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("OwnerScope '" + ownerScope + "' is invalid. Supported values are " +
                EnumUtils.getEnumList(OwnerScope.class));
        }
    }
}