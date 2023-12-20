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

package com.linagora.james.blacklist.memory;

import java.util.List;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.linagora.james.blacklist.api.PerDomainAddressBlackList;

public class MemoryPerDomainAddressBlackList implements PerDomainAddressBlackList {
    private final Multimap<Domain, MailAddress> map;

    public MemoryPerDomainAddressBlackList() {
        this.map = Multimaps.synchronizedSetMultimap(HashMultimap.create());
    }

    @Override
    public void add(Domain domain, MailAddress address) {
        map.put(domain, address);
    }

    @Override
    public void remove(Domain domain, MailAddress address) {
        map.remove(domain, address);
    }

    @Override
    public void clear(Domain domain) {
        map.removeAll(domain);
    }

    @Override
    public List<MailAddress> list(Domain domain) {
        return ImmutableList.copyOf(map.get(domain));
    }
}
