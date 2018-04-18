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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mapping.Type;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.rrt.lib.MappingsImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class XMLRecipientRewriteTableTest extends AbstractRecipientRewriteTableTest {

    private final DefaultConfigurationBuilder defaultConfiguration = new DefaultConfigurationBuilder();

    @Override
    @Before
    public void setUp() throws Exception {
        defaultConfiguration.setDelimiterParsingDisabled(true);
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() {
        return new XMLRecipientRewriteTable() {
            @Override
            public void addMapping(MappingSource source, Mapping mapping) throws RecipientRewriteTableException {
                addMappingToConfiguration(source, mapping.getType().withoutPrefix(mapping.asString()), mapping.getType());
            }

            @Override
            public void removeMapping(MappingSource source, Mapping mapping) throws RecipientRewriteTableException {
                removeMappingFromConfiguration(source, mapping.getType().withoutPrefix(mapping.asString()), mapping.getType());
            }

            @Override
            public void addAddressMapping(MappingSource source, String address) throws RecipientRewriteTableException {
                addMapping(source, Mapping.address(address));
            }
        };
    }

    @Test
    @Ignore("addMapping doesn't handle checking for duplicate in this test implementation")
    @Override
    public void addMappingShouldThrowWhenMappingAlreadyExists() {
    }

    protected void addMappingToConfiguration(MappingSource source, String mapping, Type type) throws RecipientRewriteTableException {
        Mappings mappings = Optional.ofNullable(virtualUserTable.getUserDomainMappings(source))
            .orElse(MappingsImpl.empty());

        Mappings updatedMappings = MappingsImpl.from(mappings)
            .add(Mapping.of(type, mapping))
            .build();

        updateConfiguration(source, mappings, updatedMappings);
    }

    protected void removeMappingFromConfiguration(MappingSource source, String mapping, Type type) throws RecipientRewriteTableException {
        Mappings oldMappings = Optional.ofNullable(virtualUserTable.getUserDomainMappings(source))
            .orElseThrow(() -> new RecipientRewriteTableException("Cannot remove from null mappings"));

        Mappings updatedMappings = oldMappings.remove(Mapping.of(type, mapping));

        updateConfiguration(source, oldMappings, updatedMappings);
    }

    private void updateConfiguration(MappingSource source, Mappings oldMappings, Mappings updatedMappings) throws RecipientRewriteTableException {
        if (oldMappings != null) {
            removeMappingsFromConfig(source, oldMappings);
        }

        if (!updatedMappings.isEmpty()) {
            defaultConfiguration.addProperty("mapping", source.getFixedUser() + "@" + source.getFixedDomain() + "=" + updatedMappings.serialize());
        }

        try {
            virtualUserTable.configure(defaultConfiguration);
        } catch (Exception e) {
            if (updatedMappings.size() > 0) {
                throw new RecipientRewriteTableException("Error update mapping", e);
            }
        }
    }

    private void removeMappingsFromConfig(MappingSource source, Mappings mappings) {
        List<String> stored = new ArrayList<>();
        for (String c : defaultConfiguration.getStringArray("mapping")) {
            String mapping = source.getFixedUser() + "@" + source.getFixedDomain() + "=" + mappings.serialize();
            if (!c.equalsIgnoreCase(mapping)) {
                stored.add(c);
            }
        }
        // clear old values
        defaultConfiguration.clear();
        // add stored mappings
        for (String aStored : stored) {
            defaultConfiguration.addProperty("mapping", aStored);
        }
    }
}
