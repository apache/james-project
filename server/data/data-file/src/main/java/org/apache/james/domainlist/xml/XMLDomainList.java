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

package org.apache.james.domainlist.xml;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.apache.james.lifecycle.api.Configurable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Singleton;

/**
 * Mimic the old behavior of JAMES
 */
@Singleton
public class XMLDomainList extends AbstractDomainList implements Configurable {

    private final List<String> domainNames = new ArrayList<String>();

    private boolean managementDisabled = false;

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        super.configure(config);
        for (String serverNameConf : config.getStringArray("domainnames.domainname")) {
            try {
                addDomain(serverNameConf);
            } catch (DomainListException e) {
                throw new ConfigurationException("Unable to add domain to memory", e);
            }
        }

        managementDisabled = true;
    }

    @Override
    protected List<String> getDomainListInternal() {
        return new ArrayList<String>(domainNames);
    }

    @Override
    public boolean containsDomain(String domains) throws DomainListException {
        return domainNames.contains(domains);
    }

    @Override
    public void addDomain(String domain) throws DomainListException {
        // TODO: Remove later. Temporary fix to get sure no domains can be added
        // to the XMLDomainList
        if (managementDisabled)
            throw new DomainListException("Read-Only DomainList implementation");

        String newDomain = domain.toLowerCase(Locale.US);
        if (!containsDomain(newDomain)) {
            domainNames.add(newDomain);
        }
    }

    @Override
    public void removeDomain(String domain) throws DomainListException {
        // TODO: Remove later. Temporary fix to get sure no domains can be added
        // to the XMLDomainList
        if (managementDisabled)
            throw new DomainListException("Read-Only DomainList implementation");

        domainNames.remove(domain.toLowerCase(Locale.US));
    }
}
