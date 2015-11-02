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
package org.apache.james.transport.mailets;

import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.rrt.lib.MappingsImpl.Builder;

import java.util.*;

/**
 * @since 15.12.12 11:40
 */
public class RecipientRewriteTableMock implements org.apache.james.rrt.api.RecipientRewriteTable {

    public static class Mapping {
        public final String address;
        public final Mappings target;

        public Mapping(String address, Mappings target) {
            this.address = address;
            this.target = target;
        }

        public Mapping(String address) {
            this.address = address;
            this.target = null;
        }

        public Mapping to(String... target) {
            return new Mapping(address, MappingsImpl.fromCollection(Arrays.asList(target)));
        }
    }

    public static Mapping mapFrom(String from) {
        return new Mapping(from);
    }

    public static RecipientRewriteTableMock rewriteTableMock(Mapping... mappings) {
        return new RecipientRewriteTableMock(Arrays.asList(mappings));
    }

    private final List<Mapping> mappings = new LinkedList<Mapping>();

    private RecipientRewriteTableMock(List<Mapping> mappings) {
        this.mappings.addAll(mappings);
    }

    private List<Mapping> findUserDomain(String user, String domain) {
        List<Mapping> results = new LinkedList<Mapping>();
        for (Mapping m : mappings) {
            String[] parts = m.address.split("@", 2);
            if (parts.length == 2) {
                if (user.equals(parts[0]) && domain.equals(parts[1])) {
                    results.add(m);
                }
            }
        }
        return results;
    }

    @Override
    public Mappings getMappings(String user, String domain) throws ErrorMappingException, RecipientRewriteTableException {
        Builder builder = MappingsImpl.builder();
        for (Mapping m : findUserDomain(user, domain)) {
            builder.addAll(m.target);
        }
        Mappings recipients = builder.build();
        if (recipients.isEmpty()) {
            return null;
        } else {
            return recipients;
        }
    }

    @Override
    public void addRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeRegexMapping(String user, String domain, String regex) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeAddressMapping(String user, String domain, String address) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeErrorMapping(String user, String domain, String error) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Mappings getUserDomainMappings(String user, String domain) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeMapping(String user, String domain, String mapping) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Map<String, Mappings> getAllMappings() throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addAliasDomainMapping(String aliasDomain, String realDomain) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeAliasDomainMapping(String aliasDomain, String realDomain) throws RecipientRewriteTableException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
