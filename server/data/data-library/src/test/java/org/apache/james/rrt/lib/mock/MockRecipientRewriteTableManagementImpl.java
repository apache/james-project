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
package org.apache.james.rrt.lib.mock;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;

public class MockRecipientRewriteTableManagementImpl implements RecipientRewriteTable {

    final HashMap store = new HashMap();

    @Override
    public void addAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        addRawMapping(user, domain, address);
    }

    @Override
    public void addErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        addRawMapping(user, domain, RecipientRewriteTable.ERROR_PREFIX + error);
    }

    @Override
    public void addMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        if (mapping.startsWith(RecipientRewriteTable.ERROR_PREFIX)) {
            addErrorMapping(user, domain, mapping.substring(RecipientRewriteTable.ERROR_PREFIX.length()));
        } else if (mapping.startsWith(RecipientRewriteTable.REGEX_PREFIX)) {
            addErrorMapping(user, domain, mapping.substring(RecipientRewriteTable.REGEX_PREFIX.length()));
        } else {
            addAddressMapping(user, domain, mapping);
        }
    }

    @Override
    public void addRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        addRawMapping(user, domain, RecipientRewriteTable.REGEX_PREFIX + regex);
    }

    @Override
    public Map getAllMappings() throws RecipientRewriteTableException {
        if (store.size() > 0) {
            return store;
        } else {
            return null;
        }
    }

    @Override
    public Mappings getUserDomainMappings(String user, String domain) throws RecipientRewriteTableException {
        String mapping = (String) store.get(user + "@" + domain);
        if (mapping != null) {
            return MappingsImpl.fromRawString(mapping);
        } else {
            return null;
        }
    }

    @Override
    public void removeAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        removeRawMapping(user, domain, address);
    }

    @Override
    public void removeErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        removeRawMapping(user, domain, RecipientRewriteTable.ERROR_PREFIX + error);
    }

    @Override
    public void removeMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        if (mapping.startsWith(RecipientRewriteTable.ERROR_PREFIX)) {
            removeErrorMapping(user, domain, mapping.substring(RecipientRewriteTable.ERROR_PREFIX.length()));
        } else if (mapping.startsWith(RecipientRewriteTable.REGEX_PREFIX)) {
            removeErrorMapping(user, domain, mapping.substring(RecipientRewriteTable.REGEX_PREFIX.length()));
        } else {
            removeAddressMapping(user, domain, mapping);
        }
    }

    @Override
    public void removeRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        removeRawMapping(user, domain, RecipientRewriteTable.REGEX_PREFIX + regex);
    }

    @Override
    public Mappings getMappings(String user, String domain) throws ErrorMappingException,
            RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void addRawMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        String key = user + "@" + domain;
        String mappings = (String) store.get(key);

        if (mappings != null) {
            MappingsImpl map = MappingsImpl.fromRawString(mappings);

            if (map.contains(mapping)) {
                throw new RecipientRewriteTableException("Mapping " + mapping + " already exist!");
            } else {
                Mappings updateMappings = MappingsImpl.from(map).add(mapping).build();
                store.put(key, updateMappings.serialize());
            }
        } else {
            store.put(key, mapping);
        }
    }

    private void removeRawMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        MappingsImpl map;
        String key = user + "@" + domain;
        String mappings = (String) store.get(key);
        if (mappings != null) {
            map = MappingsImpl.fromRawString(mappings);
            if (map.contains(mapping)) {
                store.put(key, map.remove(mapping).serialize());
            }
        }
        throw new RecipientRewriteTableException("Mapping does not exist");
    }

    @Override
    public void addAliasDomainMapping(String aliasDomain, String realDomain) throws RecipientRewriteTableException {
        addRawMapping(null, aliasDomain, RecipientRewriteTable.ALIASDOMAIN_PREFIX + realDomain);
    }

    @Override
    public void removeAliasDomainMapping(String aliasDomain, String realDomain) throws RecipientRewriteTableException {
        removeRawMapping(null, aliasDomain, RecipientRewriteTable.ALIASDOMAIN_PREFIX + realDomain);
    }
}
