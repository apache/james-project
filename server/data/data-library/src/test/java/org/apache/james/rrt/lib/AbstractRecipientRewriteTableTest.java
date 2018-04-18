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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.fge.lambdas.Throwing;

/**
 * The abstract test for the virtual user table. Contains tests related to
 * simple, regexp, wildcard, error,... Extend this and instanciate the needed
 * virtualUserTable implementation.
 */
public abstract class AbstractRecipientRewriteTableTest {

    protected abstract AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    protected AbstractRecipientRewriteTable virtualUserTable;

    public void setUp() throws Exception {
        virtualUserTable = getRecipientRewriteTable();
    }

    public void tearDown() throws Exception {
        Map<String, Mappings> mappings = virtualUserTable.getAllMappings();

        if (mappings != null) {
            for (String key : virtualUserTable.getAllMappings().keySet()) {
                Mappings map = mappings.get(key);
                String[] args = key.split("@");

                map.asStream()
                    .forEach(Throwing.consumer(mapping ->
                        virtualUserTable.removeMapping(args[0], Domain.of(args[1]), mapping)));
            }
        }

        LifecycleUtil.dispose(virtualUserTable);
    }

    @Test
    public void testStoreAndGetMappings() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "*";
        Domain domain = Domain.of("test");
        virtualUserTable.addMapping(user, domain, Mapping.regex("prefix_.*:admin@test"));
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

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());

        virtualUserTable.addMapping(user, domain, Mapping.regex(regex));
        virtualUserTable.addMapping(user, domain, Mapping.regex(regex2));
        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("Two mappings").hasSize(2);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);
        virtualUserTable.removeMapping(user, domain, Mapping.regex(regex));

        assertThatThrownBy(() -> virtualUserTable.addRegexMapping(user, domain, invalidRegex))
            .describedAs("Invalid Mapping throw exception")
            .isInstanceOf(RecipientRewriteTableException.class);


        virtualUserTable.removeMapping(user, domain, Mapping.regex(regex2));


        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isEmpty();
    }

    @Test
    public void getAllMappingsShouldListAllEntries() throws Exception {
        String user = "test";
        String user2 = "test2";
        String regex = "(.*)@localhost";
        String regex2 = "(.+)@test";

        virtualUserTable.addMapping(user, Domain.LOCALHOST, Mapping.regex(regex));
        virtualUserTable.addMapping(user, Domain.LOCALHOST, Mapping.regex(regex2));
        virtualUserTable.addMapping(user2, Domain.LOCALHOST, Mapping.address(user + "@" + Domain.LOCALHOST.asString()));

        assertThat(virtualUserTable.getAllMappings())
            .describedAs("One mappingline")
            .containsOnly(
                Pair.of(user + "@" + Domain.LOCALHOST.asString(), MappingsImpl.builder()
                    .add(Mapping.regex(regex))
                    .add(Mapping.regex(regex2))
                    .build()),
                Pair.of(user2 + "@" + Domain.LOCALHOST.asString(), MappingsImpl.builder()
                    .add(Mapping.address(user + "@" + Domain.LOCALHOST.asString()))
                    .build()));
    }

    @Test
    public void testStoreAndRetrieveAddressMapping() throws ErrorMappingException, RecipientRewriteTableException {

        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());

        virtualUserTable.addMapping(user, domain, Mapping.address(address));
        virtualUserTable.addMapping(user, domain, Mapping.address(address2));

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("Two mappings").hasSize(2);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);

        virtualUserTable.removeMapping(user, domain, Mapping.address(address));
        virtualUserTable.removeMapping(user, domain, Mapping.address(address2));

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isEmpty();
    }

    @Test
    public void testStoreAndRetrieveErrorMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String error = "bounce!";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());

        virtualUserTable.addMapping(user, domain, Mapping.error(error));
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);

        assertThatThrownBy(() ->
            virtualUserTable.getMappings(user, domain))
            .describedAs("Exception thrown on to many mappings")
            .isInstanceOf(ErrorMappingException.class);

        virtualUserTable.removeMapping(user, domain, Mapping.error(error));

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isEmpty();
    }

    @Test
    public void testStoreAndRetrieveWildCardAddressMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        String user2 = "test2";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());

        virtualUserTable.addMapping(RecipientRewriteTable.WILDCARD, domain, Mapping.address(address));
        virtualUserTable.addMapping(user, domain, Mapping.address(address2));

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("One mappings").hasSize(1);
        assertThat(virtualUserTable.getMappings(user2, domain)).describedAs("One mappings").hasSize(1);

        virtualUserTable.removeMapping(user, domain, Mapping.address(address2));
        virtualUserTable.removeMapping(RecipientRewriteTable.WILDCARD, domain, Mapping.address(address));

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
        assertThat(virtualUserTable.getMappings(user2, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
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

        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isEmpty();

        virtualUserTable.addMapping(user1, domain1, Mapping.address(user2 + "@" + domain2.asString()));
        virtualUserTable.addMapping(user2, domain2, Mapping.address(user3 + "@" + domain3.asString()));
        assertThat(virtualUserTable.getMappings(user1, domain1)).containsOnly(Mapping.address(user3 + "@" + domain3.asString()));
        virtualUserTable.addMapping(user3, domain3, Mapping.address(user1 + "@" + domain1.asString()));

        assertThatThrownBy(() ->
            virtualUserTable.getMappings(user1, domain1))
            .describedAs("Exception thrown on to many mappings")
            .isInstanceOf(ErrorMappingException.class);

        // disable recursive mapping
        virtualUserTable.setRecursiveMapping(false);
        assertThat(virtualUserTable.getMappings(user1, domain1)).describedAs("Not recursive mapped").containsExactly(Mapping.address(user2 + "@" + domain2.asString()));
    }

    @Test
    public void testAliasDomainMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String domain = "realdomain";
        Domain aliasDomain = Domain.of("aliasdomain");
        String user = "user";
        String user2 = "user2";

        assertThat(virtualUserTable.getAllMappings()).describedAs("No mappings").isEmpty();

        virtualUserTable.addMapping(RecipientRewriteTable.WILDCARD, aliasDomain, Mapping.address(user2 + "@" + domain));
        virtualUserTable.addMapping(RecipientRewriteTable.WILDCARD, aliasDomain, Mapping.domain(Domain.of(domain)));

        assertThat(virtualUserTable.getMappings(user, aliasDomain))
            .describedAs("Domain mapped as first, Address mapped as second")
            .isEqualTo(MappingsImpl.builder()
                .add(Mapping.address(user + "@" + domain))
                .add(Mapping.address(user2 + "@" + domain))
                .build());

        virtualUserTable.removeMapping(RecipientRewriteTable.WILDCARD, aliasDomain, Mapping.address(user2 + "@" + domain));
        virtualUserTable.removeMapping(RecipientRewriteTable.WILDCARD, aliasDomain, Mapping.domain(Domain.of(domain)));
    }

    @Test
    public void addMappingShouldThrowWhenMappingAlreadyExists() throws Exception {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";

        expectedException.expect(RecipientRewriteTableException.class);

        virtualUserTable.addAddressMapping(user, domain, address);
        virtualUserTable.addAddressMapping(user, domain, address);
    }

    @Test
    public void addMappingShouldNotThrowWhenMappingAlreadyExistsWithAnOtherType() throws Exception {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";

        virtualUserTable.addMapping(user, domain, Mapping.address(address));
        virtualUserTable.addMapping(user, domain, Mapping.regex(address));

        assertThat(virtualUserTable.getMappings(user, domain)).hasSize(2);
    }

    @Test
    public void addForwardMappingShouldStore() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        virtualUserTable.addMapping(user, domain, Mapping.forward(address));
        virtualUserTable.addMapping(user, domain, Mapping.forward(address2));

        assertThat(virtualUserTable.getMappings(user, domain)).hasSize(2);
    }

    @Test
    public void removeForwardMappingShouldDelete() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        virtualUserTable.addMapping(user, domain, Mapping.forward(address));
        virtualUserTable.addMapping(user, domain, Mapping.forward(address2));

        virtualUserTable.removeMapping(user, domain, Mapping.forward(address));
        virtualUserTable.removeMapping(user, domain, Mapping.forward(address2));

        assertThat(virtualUserTable.getMappings(user, domain))
            .isEqualTo(MappingsImpl.empty());
    }

    @Test
    public void addGroupMappingShouldStore() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        virtualUserTable.addMapping(user, domain, Mapping.group(address));
        virtualUserTable.addMapping(user, domain, Mapping.group(address2));

        assertThat(virtualUserTable.getMappings(user, domain)).hasSize(2);
    }

    @Test
    public void removeGroupMappingShouldDelete() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        Domain domain = Domain.LOCALHOST;
        String address = "test@localhost2";
        String address2 = "test@james";

        virtualUserTable.addMapping(user, domain, Mapping.group(address));
        virtualUserTable.addMapping(user, domain, Mapping.group(address2));

        virtualUserTable.removeMapping(user, domain, Mapping.group(address));
        virtualUserTable.removeMapping(user, domain, Mapping.group(address2));

        assertThat(virtualUserTable.getMappings(user, domain))
            .isEqualTo(MappingsImpl.empty());
    }
}
