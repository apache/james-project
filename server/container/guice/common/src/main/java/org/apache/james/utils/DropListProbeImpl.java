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

package org.apache.james.utils;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;
import org.apache.james.probe.DropListProbe;

public class DropListProbeImpl implements GuiceProbe, DropListProbe {

    private final DropList dropList;

    @Inject
    public DropListProbeImpl(DropList dropList) {
        this.dropList = dropList;
    }

    @Override
    public void addDropListEntry(DropListEntry dropListEntry) {
        dropList.add(dropListEntry).block();
    }

    @Override
    public void removeDropListEntry(DropListEntry dropListEntry) {
        dropList.remove(dropListEntry).block();
    }

    @Override
    public List<DropListEntry> getDropList(OwnerScope ownerScope, String owner) {
        return dropList.list(ownerScope, owner)
            .collectList()
            .block();
    }

    @Override
    public DropList.Status dropListQuery(OwnerScope ownerScope, String owner, MailAddress sender) {
        return dropList.query(ownerScope, owner, sender)
            .block();
    }
}