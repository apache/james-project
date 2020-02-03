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
import java.util.Map;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.probe.DataProbe;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.streams.Iterators;

import com.github.steveash.guavate.Guavate;

public class DataProbeImpl implements GuiceProbe, DataProbe {
    private final DomainList domainList;
    private final UsersRepository usersRepository;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    private DataProbeImpl(
            DomainList domainList,
            UsersRepository usersRepository, 
            RecipientRewriteTable recipientRewriteTable) {
        this.domainList = domainList;
        this.usersRepository = usersRepository;
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public void addUser(String userName, String password) throws Exception {
        usersRepository.addUser(Username.of(userName), password);
    }

    @Override
    public void removeUser(String username) throws Exception {
        usersRepository.removeUser(Username.of(username));
    }

    @Override
    public String[] listUsers() throws Exception {
        return Iterators.toStream(usersRepository.list())
            .map(Username::asString)
            .toArray(String[]::new);
    }

    @Override
    public void addDomain(String domain) throws Exception {
        domainList.addDomain(Domain.of(domain));
    }

    @Override
    public boolean containsDomain(String domain) throws Exception {
        return domainList.containsDomain(Domain.of(domain));
    }

    @Override
    public String getDefaultDomain() throws Exception {
        return domainList.getDefaultDomain().name();
    }

    @Override
    public void removeDomain(String domain) throws Exception {
        domainList.removeDomain(Domain.of(domain));
    }

    @Override
    public List<String> listDomains() throws Exception {
        return domainList.getDomains().stream().map(Domain::name).collect(Guavate.toImmutableList());
    }

    @Override
    public Map<String, Mappings> listMappings() throws Exception {
        return recipientRewriteTable.getAllMappings()
            .entrySet()
            .stream()
            .collect(
                Guavate.toImmutableMap(
                    entry -> entry.getKey().asString(),
                    Map.Entry::getValue));

    }

    @Override
    public void addAddressMapping(String fromUser, String fromDomain, String toAddress) throws Exception {
        MappingSource source = MappingSource.fromUser(fromUser, fromDomain);
        recipientRewriteTable.addAddressMapping(source, toAddress);
    }

    @Override
    public void addUserAliasMapping(String fromUser, String fromDomain, String toAddress) throws Exception {
        MappingSource source = MappingSource.fromUser(fromUser, fromDomain);
        recipientRewriteTable.addAliasMapping(source, toAddress);
    }

    @Override
    public void addDomainAliasMapping(String aliasDomain, String deliveryDomain) throws Exception {
        recipientRewriteTable.addAliasDomainMapping(MappingSource.fromDomain(Domain.of(aliasDomain)), Domain.of(deliveryDomain));
    }

    @Override
    public void addGroupAliasMapping(String fromGroup, String toAddress) throws Exception {
        recipientRewriteTable.addGroupMapping(MappingSource.fromMailAddress(new MailAddress(fromGroup)), toAddress);
    }
}