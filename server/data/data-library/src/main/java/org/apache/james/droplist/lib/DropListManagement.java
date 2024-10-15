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

package org.apache.james.droplist.lib;

import java.util.List;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.DropListManagementMBean;
import org.apache.james.droplists.api.OwnerScope;

public class DropListManagement extends StandardMBean implements DropListManagementMBean {

    private final DropList dropList;

    @Inject
    public DropListManagement(DropList dropList) throws NotCompliantMBeanException {
        super(DropListManagementMBean.class);
        this.dropList = dropList;
    }

    @Override
    public String query(OwnerScope ownerScope, String owner, MailAddress sender) {
        return dropList.query(ownerScope, owner, sender)
            .map(Enum::name)
            .block();
    }

    @Override
    public List<String> list(OwnerScope ownerScope, String owner) {
        return dropList.list(ownerScope, owner)
            .map(DropListEntry::getDeniedEntity)
            .collectList()
            .block();
    }

    @Override
    public void remove(DropListEntry entry) {
        dropList.remove(entry).block();
    }

    @Override
    public void add(DropListEntry entry) {
        dropList.add(entry).block();
    }
}
