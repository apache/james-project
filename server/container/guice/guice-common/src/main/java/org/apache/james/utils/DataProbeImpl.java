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

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.probe.DataProbe;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.user.api.UsersRepository;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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
        usersRepository.addUser(userName, password);
    }

    public DataProbeImpl fluentAddUser(String userName, String password) throws Exception {
        addUser(userName, password);
        return this;
    }

    @Override
    public void removeUser(String username) throws Exception {
        usersRepository.removeUser(username);
    }

    @Override
    public void setPassword(String userName, String password) {
        throw new NotImplementedException();
    }

    @Override
    public String[] listUsers() throws Exception {
        return Iterables.toArray(ImmutableList.copyOf(usersRepository.list()), String.class);
    }

    @Override
    public void addDomain(String domain) throws Exception {
        domainList.addDomain(Domain.of(domain));
    }

    public DataProbeImpl fluentAddDomain(String domain) throws Exception {
        addDomain(domain);
        return this;
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
    public Mappings listUserDomainMappings(String user, String domain) {
        throw new NotImplementedException();
    }

    @Override
    public void addAddressMapping(String fromUser, String fromDomain, String toAddress) throws Exception {
        MappingSource source = MappingSource.fromUser(fromUser, fromDomain);
        recipientRewriteTable.addAddressMapping(source, toAddress);
    }

    @Override
    public void removeAddressMapping(String fromUser, String fromDomain, String toAddress) throws Exception {
        MappingSource source = MappingSource.fromUser(fromUser, fromDomain);
        recipientRewriteTable.removeAddressMapping(source, toAddress);
    }

    @Override
    public void addRegexMapping(String user, String domain, String regex) throws Exception {
        MappingSource source = MappingSource.fromUser(user, domain);
        recipientRewriteTable.addRegexMapping(source, regex);
    }


    @Override
    public void removeRegexMapping(String user, String domain, String regex) throws Exception {
        MappingSource source = MappingSource.fromUser(user, domain);
        recipientRewriteTable.removeRegexMapping(source, regex);
    }

    @Override
    public void addDomainAliasMapping(String aliasDomain, String deliveryDomain) throws Exception {
        recipientRewriteTable.addAliasDomainMapping(MappingSource.fromDomain(Domain.of(aliasDomain)), Domain.of(deliveryDomain));
    }

    @Override
    public void addForwardMapping(String user, String domain, String address) throws Exception {
        MappingSource source = MappingSource.fromUser(user, domain);
        recipientRewriteTable.addForwardMapping(source, address);
    }

    @Override
    public void removeForwardMapping(String user, String domain, String address) throws Exception {
        MappingSource source = MappingSource.fromUser(user, domain);
        recipientRewriteTable.removeForwardMapping(source, address);
    }

    @Override
    public void addGroupMapping(String toUser, String toDomain, String fromAddress) throws Exception {
        MappingSource source = MappingSource.fromUser(toUser, toDomain);
        recipientRewriteTable.addGroupMapping(source, fromAddress);
    }

    @Override
    public void removeGroupMapping(String toUser, String toDomain, String fromAddress) throws Exception {
        MappingSource source = MappingSource.fromUser(toUser, toDomain);
        recipientRewriteTable.removeGroupMapping(source, fromAddress);
    }
}