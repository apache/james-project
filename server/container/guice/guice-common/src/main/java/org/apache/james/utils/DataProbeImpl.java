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

    @Override
    public void removeUser(String username) throws Exception {
        usersRepository.removeUser(username);
    }

    @Override
    public void setPassword(String userName, String password) throws Exception {
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
        return recipientRewriteTable.getAllMappings();
    }

    @Override
    public Mappings listUserDomainMappings(String user, String domain) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void addAddressMapping(String user, String domain, String toAddress) throws Exception {
        recipientRewriteTable.addAddressMapping(user, Domain.of(domain), toAddress);
    }

    @Override
    public void removeAddressMapping(String user, String domain, String fromAddress) throws Exception {
        recipientRewriteTable.removeAddressMapping(user, Domain.of(domain), fromAddress);
    }

    @Override
    public void addRegexMapping(String user, String domain, String regex) throws Exception {
        recipientRewriteTable.addRegexMapping(user, Domain.of(domain), regex);
    }


    @Override
    public void removeRegexMapping(String user, String domain, String regex) throws Exception {
        recipientRewriteTable.removeRegexMapping(user, Domain.of(domain), regex);
    }

    @Override
    public void addDomainAliasMapping(String aliasDomain, String deliveryDomain) throws Exception {
        recipientRewriteTable.addAliasDomainMapping(Domain.of(aliasDomain), Domain.of(deliveryDomain));
    }

    @Override
    public void addForwardMapping(String user, String domain, String address) throws Exception {
        recipientRewriteTable.addForwardMapping(user, Domain.of(domain), address);
    }

    @Override
    public void removeForwardMapping(String user, String domain, String address) throws Exception {
        recipientRewriteTable.removeForwardMapping(user, Domain.of(domain), address);
    }

    @Override
    public void addGroupMapping(String toUser, String toDomain, String fromAddress) throws Exception {
        recipientRewriteTable.addGroupMapping(toUser, Domain.of(toDomain), fromAddress);
    }

    @Override
    public void removeGroupMapping(String toUser, String toDomain, String fromAddress) throws Exception {
        recipientRewriteTable.removeGroupMapping(toUser, Domain.of(toDomain), fromAddress);
    }
}