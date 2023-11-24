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

package org.apache.james.domainlist.lib;

import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;

/**
 * All implementations of the DomainList interface should extends this abstract
 * class
 */
// TODO Binding guice + spring
// TODO ecrire une factory et passer les classes utilitaires en package private
public abstract class AbstractDomainList implements DomainList, Configurable {
    private DomainListConfiguration configuration;

    public AbstractDomainList() {

    }

    // TODO kill meeeee
    public AbstractDomainList(DNSService dns) {

    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        configure(DomainListConfiguration.from(config));
    }

    public void configure(DomainListConfiguration domainListConfiguration) throws ConfigurationException {
        this.configuration = domainListConfiguration;
    }

    public void configure(DomainListConfiguration.Builder configurationBuilder) throws ConfigurationException {
        configure(configurationBuilder.build());
    }

    @Override
    public Domain getDefaultDomain() throws DomainListException {
        return configuration.getDefaultDomain();
    }
}
