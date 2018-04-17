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
package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping.Type;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * The abstract test for the virtual user table. Contains tests related to
 * simple, regexp, wildcard, error,... Extend this and instanciate the needed
 * virtualUserTable implementation.
 */
public abstract class AbstractRecipientRewriteTableTest {

    @Rule public ExpectedException expectedException = ExpectedException.none();

    protected AbstractRecipientRewriteTable virtualUserTable;

    public void setUp() throws Exception {
        virtualUserTable = getRecipientRewriteTable();
    }

    public void tearDown() throws Exception {

        Map<String, Mappings> mappings = virtualUserTable.getAllMappings();

        if (mappings != null) {

            for (String key : virtualUserTable.getAllMappings().keySet()) {
                String[] args = key.split("@");

                Mappings map = mappings.get(key);

                for (String aMap : map.asStrings()) {
                    try {
                        removeMapping(args[0], Domain.of(args[1]), aMap);
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        LifecycleUtil.dispose(virtualUserTable);

    }

    @Test
    public void testStoreAndGetMappings() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "*";
        Domain domain = Domain.of("test");
        String regex = "prefix_.*:admin@test";
        addMapping(user, domain, regex, Type.Regex);
        assertThat(virtualUserTable.getMappings("prefix_abc", domain)).isNotEmpty();
    }

    @Test
    public void testStoreAndRetrieveRegexMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        // String regex = "(.*):{$1}@localhost";
        // String regex2 = "(.+):{$1}@test";
        String regex = "(.*)@localhost";
        String regex2 = "(.+)@test";
        String invalidRegex = ".*):";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();

        addMapping(user, domain, regex, Type.Regex);
        addMapping(user, domain, regex2, Type.Regex);
        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("Two mappings").hasSize(2);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);
        removeMapping(user, domain, regex, Type.Regex);

        assertThatThrownBy(() -> virtualUserTable.addRegexMapping(user, domain, invalidRegex))
            .describedAs("Invalid Mapping throw exception")
            .isInstanceOf(RecipientRewriteTableException.class);


        removeMapping(user, domain, regex2, Type.Regex);


        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isNull();
    }

    @Test
    public void getAllMappingsShouldListAllEntries() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        String user2 = "test2";
        String regex = "(.*)@localhost";
        String regex2 = "(.+)@test";


        addMapping(user, Domain.LOCALHOST, regex, Type.Regex);
        addMapping(user, Domain.LOCALHOST, regex2, Type.Regex);
        addMapping(user2, Domain.LOCALHOST, user + "@" + Domain.LOCALHOST.asString(), Type.Address);

        assertThat(virtualUserTable.getAllMappings())
            .describedAs("One mappingline")
            .containsOnly(
                Pair.of(user + "@" + Domain.LOCALHOST.asString(), MappingsImpl.builder()
                    .add(MappingImpl.regex(regex))
                    .add(MappingImpl.regex(regex2))
                    .build()),
                Pair.of(user2 + "@" + Domain.LOCALHOST.asString(), MappingsImpl.builder()
                    .add(MappingImpl.address(user + "@" + Domain.LOCALHOST.asString()))
                    .build()));
    }

    @Test
    public void testStoreAndRetrieveAddressMapping() throws ErrorMappingException, RecipientRewriteTableException {

        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();

        addMapping(user, domain, address, Type.Address);
        addMapping(user, domain, address2, Type.Address);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("Two mappings").hasSize(2);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);

        removeMapping(user, domain, address, Type.Address);

        removeMapping(user, domain, address2, Type.Address);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isNull();
    }

    @Test
    public void testStoreAndRetrieveErrorMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String error = "bounce!";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();

        addMapping(user, domain, error, Type.Error);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);

        assertThatThrownBy(() ->
            virtualUserTable.getMappings(user, domain))
            .describedAs("Exception thrown on to many mappings")
            .isInstanceOf(ErrorMappingException.class);

        removeMapping(user, domain, error, Type.Error);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isNull();
    }

    @Test
    public void testStoreAndRetrieveWildCardAddressMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        String user2 = "test2";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();

        addMapping(RecipientRewriteTable.WILDCARD, domain, address, Type.Address);
        addMapping(user, domain, address2, Type.Address);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("One mappings").hasSize(1);
        assertThat(virtualUserTable.getMappings(user2, domain)).describedAs("One mappings").hasSize(1);

        removeMapping(user, domain, address2, Type.Address);
        removeMapping(RecipientRewriteTable.WILDCARD, domain, address, Type.Address);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();
        assertThat(virtualUserTable.getMappings(user2, domain)).describedAs("No mapping").isNull();
    }

    @Test
    public void testRecursiveMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user1 = "user1";
        String user2 = "user2";
        String user3 = "user3";
        Domain domain1 = Domain.of("domain1");
        Domain domain2 = Domain.of("domain2");
        Domain domain3 = Domain.of("domain3");

        virtualUserTable.setRecursiveMapping(true);

            assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isNull();

            addMapping(user1, domain1, user2 + "@" + domain2.asString(), Type.Address);
            addMapping(user2, domain2, user3 + "@" + domain3.asString(), Type.Address);
            assertThat(virtualUserTable.getMappings(user1, domain1)).containsOnly(MappingImpl.address(user3 + "@" + domain3.asString()));
            addMapping(user3, domain3, user1 + "@" + domain1.asString(), Type.Address);
            
            assertThatThrownBy(() ->
                virtualUserTable.getMappings(user1, domain1))
                .describedAs("Exception thrown on to many mappings")
                .isInstanceOf(ErrorMappingException.class);

            // disable recursive mapping
            virtualUserTable.setRecursiveMapping(false);
            assertThat(virtualUserTable.getMappings(user1, domain1)).describedAs("Not recursive mapped").containsExactly(MappingImpl.address(user2 + "@" + domain2.asString()));
    }

    @Test
    public void testAliasDomainMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String domain = "realdomain";
        Domain aliasDomain = Domain.of("aliasdomain");
        String user = "user";
        String user2 = "user2";

        assertThat(virtualUserTable.getAllMappings()).describedAs("No mappings").isNull();

        addMapping(RecipientRewriteTable.WILDCARD, aliasDomain, user2 + "@" + domain, Type.Address);
        addMapping(RecipientRewriteTable.WILDCARD, aliasDomain, domain, Type.Domain);

        assertThat(virtualUserTable.getMappings(user, aliasDomain))
            .describedAs("Domain mapped as first, Address mapped as second")
            .isEqualTo(MappingsImpl.builder()
                .add(MappingImpl.address(user + "@" + domain))
                .add(MappingImpl.address(user2 + "@" + domain))
                .build());

        removeMapping(RecipientRewriteTable.WILDCARD, aliasDomain, user2 + "@" + domain, Type.Address);

        removeMapping(RecipientRewriteTable.WILDCARD, aliasDomain, domain, Type.Domain);
    }

    @Test
    public void addMappingShouldThrowWhenMappingAlreadyExists() throws Exception {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";

        expectedException.expect(RecipientRewriteTableException.class);

        addMapping(user, domain, address, Type.Address);
        addMapping(user, domain, address, Type.Address);
    }

    @Test
    public void addMappingShouldNotThrowWhenMappingAlreadyExistsWithAnOtherType() throws Exception {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";

        addMapping(user, domain, address, Type.Address);
        addMapping(user, domain, address, Type.Regex);

        assertThat(virtualUserTable.getMappings(user, domain)).hasSize(2);
    }

    @Test
    public void addForwardMappingShouldStore() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        addMapping(user, domain, address, Type.Forward);
        addMapping(user, domain, address2, Type.Forward);

        assertThat(virtualUserTable.getMappings(user, domain)).hasSize(2);
    }

    @Test
    public void removeForwardMappingShouldDelete() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        addMapping(user, domain, address, Type.Forward);
        addMapping(user, domain, address2, Type.Forward);


        removeMapping(user, domain, address, Type.Forward);
        removeMapping(user, domain, address2, Type.Forward);

        assertThat(virtualUserTable.getMappings(user, domain)).isNull();
    }

    protected abstract AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception;


    protected void addMapping(String user, Domain domain, String mapping, Type type) throws RecipientRewriteTableException {
        switch (type) {
            case Error:
                virtualUserTable.addErrorMapping(user, domain, mapping);
                break;
            case Regex:
                virtualUserTable.addRegexMapping(user, domain, mapping);
                break;
            case Address:
                virtualUserTable.addAddressMapping(user, domain, mapping);
                break;
            case Domain:
                virtualUserTable.addAliasDomainMapping(domain, Domain.of(mapping));
                break;
            case Forward:
                virtualUserTable.addForwardMapping(user, domain, mapping);
                break;
            default:
                throw new RuntimeException("Invalid mapping type: " + type.asPrefix());
        }
    }

    protected void removeMapping(String user, Domain domain, String mapping, Type type) throws RecipientRewriteTableException {
        switch (type) {
            case Error:
                virtualUserTable.removeErrorMapping(user, domain, mapping);
                break;
            case Regex:
                virtualUserTable.removeRegexMapping(user, domain, mapping);
                break;
            case Address:
                virtualUserTable.removeAddressMapping(user, domain, mapping);
                break;
            case Domain:
                virtualUserTable.removeAliasDomainMapping(domain, Domain.of(mapping));
                break;
            case Forward:
                virtualUserTable.removeForwardMapping(user, domain, mapping);
                break;
            default:
                throw new RuntimeException("Invalid mapping type: " + type.asPrefix());
        }
    }

    private void removeMapping(String user, Domain domain, String rawMapping) throws RecipientRewriteTableException {
        Type type = Mapping.detectType(rawMapping);
        String mappingSuffix = type.withoutPrefix(rawMapping);

        removeMapping(user, domain, mappingSuffix, type);
    }
}
