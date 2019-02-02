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
package org.apache.james.rrt.file;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.rrt.lib.RecipientRewriteTableUtil;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Class responsible to implement the Virtual User Table in XML disk file.
 */
public class XMLRecipientRewriteTable extends AbstractRecipientRewriteTable {

    /**
     * Holds the configured mappings
     */
    private Map<MappingSource, String> mappings;

    @Override
    protected void doConfigure(HierarchicalConfiguration arg0) throws ConfigurationException {
        String[] mapConf = arg0.getStringArray("mapping");
        mappings = Maps.newHashMap();
        if (mapConf != null && mapConf.length > 0) {
            for (String aMapConf : mapConf) {
                mappings.putAll(RecipientRewriteTableUtil.getXMLMappings(aMapConf));
            }
        } else {
            throw new ConfigurationException("No mapping configured");
        }
    }

    @Override
    protected Mappings mapAddress(String user, Domain domain) {
        return Optional.ofNullable(mappings)
            .map(mappings -> RecipientRewriteTableUtil.getTargetString(user, domain, mappings))
            .map(MappingsImpl::fromRawString)
            .orElse(MappingsImpl.empty());
    }

    @Override
    public Mappings getStoredMappings(MappingSource source) {
        return Optional.ofNullable(mappings)
            .map(mappings -> mappings.get(source))
            .map(MappingsImpl::fromRawString)
            .orElse(MappingsImpl.empty());
    }

    @Override
    public Map<MappingSource, Mappings> getAllMappings() {
        if (mappings != null && mappings.size() > 0) {
            Map<MappingSource, Mappings> mappingsNew = new HashMap<>();
            for (MappingSource key : mappings.keySet()) {
                mappingsNew.put(key, MappingsImpl.fromRawString(mappings.get(key)));
            }
            return mappingsNew;
        } else {
            return ImmutableMap.of();
        }
    }

    @Override
    public void addMapping(MappingSource source, Mapping mapping) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only implementation");
    }

    @Override
    public void removeMapping(MappingSource source, Mapping mapping) throws RecipientRewriteTableException {
        throw new RecipientRewriteTableException("Read-Only implementation");
    }
}
