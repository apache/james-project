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

    private static final String USER = "test";
    private static final String ADDRESS = "test@localhost2";

    protected abstract AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    protected AbstractRecipientRewriteTable virtualUserTable;

    public void setUp() throws Exception {
        virtualUserTable = getRecipientRewriteTable();
    }

    public void tearDown() throws Exception {
        Map<MappingSource, Mappings> mappings = virtualUserTable.getAllMappings();

        if (mappings != null) {
            for (MappingSource key : virtualUserTable.getAllMappings().keySet()) {
                Mappings map = mappings.get(key);

                map.asStream()
                    .forEach(Throwing.consumer(mapping ->
                        virtualUserTable.removeMapping(key, mapping)));
            }
        }

        LifecycleUtil.dispose(virtualUserTable);
    }

    @Test
    public void testStoreAndGetMappings() throws Exception {
        Domain domain = Domain.of("test");
        virtualUserTable.addMapping(MappingSource.fromDomain(domain), Mapping.regex("prefix_.*:admin@test"));
        assertThat(virtualUserTable.getResolvedMappings("prefix_abc", domain)).isNotEmpty();
    }

    @Test
    public void testStoreAndRetrieveRegexMapping() throws Exception {
        Domain domain = Domain.LOCALHOST;
        MappingSource source = MappingSource.fromUser(USER, domain);
        // String regex = "(.*):{$1}@localhost";
        // String regex2 = "(.+):{$1}@test";
        String regex = "(.*)@localhost";
        String regex2 = "(.+)@test";
        String invalidRegex = ".*):";

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());

        virtualUserTable.addMapping(source, Mapping.regex(regex));
        virtualUserTable.addMapping(source, Mapping.regex(regex2));
        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("Two mappings").hasSize(2);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);
        virtualUserTable.removeMapping(source, Mapping.regex(regex));

        assertThatThrownBy(() -> virtualUserTable.addRegexMapping(source, invalidRegex))
            .describedAs("Invalid Mapping throw exception")
            .isInstanceOf(RecipientRewriteTableException.class);


        virtualUserTable.removeMapping(source, Mapping.regex(regex2));


        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isEmpty();
    }

    @Test
    public void getAllMappingsShouldListAllEntries() throws Exception {
        String user2 = "test2";
        String regex = "(.*)@localhost";
        String regex2 = "(.+)@test";

        MappingSource source1 = MappingSource.fromUser(USER, Domain.LOCALHOST);
        MappingSource source2 = MappingSource.fromUser(user2, Domain.LOCALHOST);

        virtualUserTable.addMapping(source1, Mapping.regex(regex));
        virtualUserTable.addMapping(source1, Mapping.regex(regex2));
        virtualUserTable.addMapping(source2, Mapping.address(USER + "@" + Domain.LOCALHOST.asString()));

        assertThat(virtualUserTable.getAllMappings())
            .describedAs("One mappingline")
            .containsOnly(
                Pair.of(source1, MappingsImpl.builder()
                    .add(Mapping.regex(regex))
                    .add(Mapping.regex(regex2))
                    .build()),
                Pair.of(source2, MappingsImpl.builder()
                    .add(Mapping.address(USER + "@" + Domain.LOCALHOST.asString()))
                    .build()));
    }

    @Test
    public void testStoreAndRetrieveAddressMapping() throws Exception {
        Domain domain = Domain.LOCALHOST;
        MappingSource source = MappingSource.fromUser(USER, domain);
        String address2 = "test@james";

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());

        virtualUserTable.addMapping(source, Mapping.address(ADDRESS));
        virtualUserTable.addMapping(source, Mapping.address(address2));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("Two mappings").hasSize(2);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);

        virtualUserTable.removeMapping(source, Mapping.address(ADDRESS));
        virtualUserTable.removeMapping(source, Mapping.address(address2));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isEmpty();
    }

    @Test
    public void testStoreAndRetrieveErrorMapping() throws Exception {
        Domain domain = Domain.LOCALHOST;
        MappingSource source = MappingSource.fromUser(USER, domain);
        String error = "bounce!";

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());

        virtualUserTable.addMapping(source, Mapping.error(error));
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);

        assertThatThrownBy(() ->
            virtualUserTable.getResolvedMappings(USER, domain))
            .describedAs("Exception thrown on to many mappings")
            .isInstanceOf(ErrorMappingException.class);

        virtualUserTable.removeMapping(source, Mapping.error(error));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isEmpty();
    }

    @Test
    public void testStoreAndRetrieveWildCardAddressMapping() throws Exception {
        String user2 = "test2";
        Domain domain = Domain.LOCALHOST;
        String address2 = "test@james";
        MappingSource source = MappingSource.fromUser(USER, domain);

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());

        virtualUserTable.addMapping(MappingSource.fromDomain(domain), Mapping.address(ADDRESS));
        virtualUserTable.addMapping(source, Mapping.address(address2));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("One mappings").hasSize(1);
        assertThat(virtualUserTable.getResolvedMappings(user2, domain)).describedAs("One mappings").hasSize(1);

        virtualUserTable.removeMapping(source, Mapping.address(address2));
        virtualUserTable.removeMapping(MappingSource.fromDomain(domain), Mapping.address(ADDRESS));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
        assertThat(virtualUserTable.getResolvedMappings(user2, domain)).describedAs("No mapping")
            .isEqualTo(MappingsImpl.empty());
    }

    @Test
    public void testRecursiveMapping() throws Exception {
        String user1 = "user1";
        String user2 = "user2";
        String user3 = "user3";
        Domain domain1 = Domain.of("domain1");
        Domain domain2 = Domain.of("domain2");
        Domain domain3 = Domain.of("domain3");
        MappingSource source1 = MappingSource.fromUser(user1, domain1);
        MappingSource source2 = MappingSource.fromUser(user2, domain2);
        MappingSource source3 = MappingSource.fromUser(user3, domain3);

        virtualUserTable.setRecursiveMapping(true);

        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isEmpty();

        virtualUserTable.addMapping(source1, Mapping.address(user2 + "@" + domain2.asString()));
        virtualUserTable.addMapping(source2, Mapping.address(user3 + "@" + domain3.asString()));
        assertThat(virtualUserTable.getResolvedMappings(user1, domain1)).containsOnly(Mapping.address(user3 + "@" + domain3.asString()));
        virtualUserTable.addMapping(source3, Mapping.address(user1 + "@" + domain1.asString()));

        assertThatThrownBy(() ->
            virtualUserTable.getResolvedMappings(user1, domain1))
            .describedAs("Exception thrown on to many mappings")
            .isInstanceOf(ErrorMappingException.class);

        // disable recursive mapping
        virtualUserTable.setRecursiveMapping(false);
        assertThat(virtualUserTable.getResolvedMappings(user1, domain1)).describedAs("Not recursive mapped").containsExactly(Mapping.address(user2 + "@" + domain2.asString()));
    }

    @Test
    public void testAliasDomainMapping() throws Exception {
        String domain = "realdomain";
        Domain aliasDomain = Domain.of("aliasdomain");
        String user = "user";
        String user2 = "user2";

        assertThat(virtualUserTable.getAllMappings()).describedAs("No mappings").isEmpty();

        virtualUserTable.addMapping(MappingSource.fromDomain(aliasDomain), Mapping.address(user2 + "@" + domain));
        virtualUserTable.addMapping(MappingSource.fromDomain(aliasDomain), Mapping.domain(Domain.of(domain)));

        assertThat(virtualUserTable.getResolvedMappings(user, aliasDomain))
            .describedAs("Domain mapped as first, Address mapped as second")
            .isEqualTo(MappingsImpl.builder()
                .add(Mapping.address(user + "@" + domain))
                .add(Mapping.address(user2 + "@" + domain))
                .build());

        virtualUserTable.removeMapping(MappingSource.fromDomain(aliasDomain), Mapping.address(user2 + "@" + domain));
        virtualUserTable.removeMapping(MappingSource.fromDomain(aliasDomain), Mapping.domain(Domain.of(domain)));
    }

    @Test
    public void addMappingShouldThrowWhenMappingAlreadyExists() throws Exception {
        Domain domain = Domain.LOCALHOST;
        MappingSource source = MappingSource.fromUser(USER, domain);

        expectedException.expect(RecipientRewriteTableException.class);

        virtualUserTable.addAddressMapping(source, ADDRESS);
        virtualUserTable.addAddressMapping(source, ADDRESS);
    }

    @Test
    public void addMappingShouldNotThrowWhenMappingAlreadyExistsWithAnOtherType() throws Exception {
        Domain domain = Domain.LOCALHOST;
        MappingSource source = MappingSource.fromUser(USER, domain);

        virtualUserTable.addMapping(source, Mapping.address(ADDRESS));
        virtualUserTable.addMapping(source, Mapping.regex(ADDRESS));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).hasSize(2);
    }

    @Test
    public void addForwardMappingShouldStore() throws Exception {
        Domain domain = Domain.LOCALHOST;
        String address2 = "test@james";
        MappingSource source = MappingSource.fromUser(USER, domain);

        virtualUserTable.addMapping(source, Mapping.forward(ADDRESS));
        virtualUserTable.addMapping(source, Mapping.forward(address2));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).hasSize(2);
    }

    @Test
    public void removeForwardMappingShouldDelete() throws Exception {
        Domain domain = Domain.LOCALHOST;
        String address2 = "test@james";
        MappingSource source = MappingSource.fromUser(USER, domain);

        virtualUserTable.addMapping(source, Mapping.forward(ADDRESS));
        virtualUserTable.addMapping(source, Mapping.forward(address2));

        virtualUserTable.removeMapping(source, Mapping.forward(ADDRESS));
        virtualUserTable.removeMapping(source, Mapping.forward(address2));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain))
            .isEqualTo(MappingsImpl.empty());
    }

    @Test
    public void addGroupMappingShouldStore() throws Exception {
        Domain domain = Domain.LOCALHOST;
        String address2 = "test@james";
        MappingSource source = MappingSource.fromUser(USER, domain);

        virtualUserTable.addMapping(source, Mapping.group(ADDRESS));
        virtualUserTable.addMapping(source, Mapping.group(address2));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain)).hasSize(2);
    }

    @Test
    public void removeGroupMappingShouldDelete() throws Exception {
        Domain domain = Domain.LOCALHOST;
        String address2 = "test@james";
        MappingSource source = MappingSource.fromUser(USER, domain);

        virtualUserTable.addMapping(source, Mapping.group(ADDRESS));
        virtualUserTable.addMapping(source, Mapping.group(address2));

        virtualUserTable.removeMapping(source, Mapping.group(ADDRESS));
        virtualUserTable.removeMapping(source, Mapping.group(address2));

        assertThat(virtualUserTable.getResolvedMappings(USER, domain))
            .isEqualTo(MappingsImpl.empty());
    }

    @Test
    public void listSourcesShouldReturnWhenHasMapping() throws Exception {
        MappingSource source = MappingSource.fromUser(USER, Domain.LOCALHOST);
        Mapping mapping = Mapping.group(ADDRESS);
        virtualUserTable.addMapping(source, mapping);

        assertThat(virtualUserTable.listSources(mapping)).contains(source);
    }

    @Test
    public void listSourcesShouldReturnWhenMultipleSourceMapping() throws Exception {
        MappingSource source = MappingSource.fromUser(USER, Domain.of("james"));
        MappingSource source2 = MappingSource.fromDomain(Domain.LOCALHOST);
        Mapping mapping = Mapping.group(ADDRESS);

        virtualUserTable.addMapping(source, mapping);
        virtualUserTable.addMapping(source2, mapping);

        assertThat(virtualUserTable.listSources(mapping)).contains(source, source2);
    }

    @Test
    public void listSourcesShouldReturnWhenHasForwardMapping() throws Exception {
        MappingSource source = MappingSource.fromUser(USER, Domain.LOCALHOST);
        Mapping mapping = Mapping.forward("forward");

        virtualUserTable.addMapping(source, mapping);

        assertThat(virtualUserTable.listSources(mapping)).contains(source);
    }

    @Test
    public void listSourcesShouldReturnWhenHasAddressMapping() throws Exception {
        MappingSource source = MappingSource.fromUser(USER, Domain.LOCALHOST);
        Mapping mapping = Mapping.address("address");

        virtualUserTable.addMapping(source, mapping);

        assertThat(virtualUserTable.listSources(mapping)).contains(source);
    }

    @Test
    public void listSourcesShouldThrowExceptionWhenHasRegexMapping() throws Exception {
        MappingSource source = MappingSource.fromUser(USER, Domain.LOCALHOST);
        Mapping mapping = Mapping.regex("regex");

        virtualUserTable.addMapping(source, mapping);

        assertThatThrownBy(() -> virtualUserTable.listSources(mapping))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void listSourcesShouldThrowExceptionWhenHasDomainMapping() throws Exception {
        MappingSource source = MappingSource.fromUser(USER, Domain.LOCALHOST);
        Mapping mapping = Mapping.domain(Domain.of("domain"));

        virtualUserTable.addMapping(source, mapping);

        assertThatThrownBy(() -> virtualUserTable.listSources(mapping))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void listSourcesShouldThrowExceptionWhenHasErrorMapping() throws Exception {
        MappingSource source = MappingSource.fromUser(USER, Domain.LOCALHOST);
        Mapping mapping = Mapping.error("error");

        virtualUserTable.addMapping(source, mapping);

        assertThatThrownBy(() -> virtualUserTable.listSources(mapping))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void listSourcesShouldReturnEmptyWhenMappingDoesNotExist() throws Exception {
        MappingSource source = MappingSource.fromUser(USER, Domain.LOCALHOST);
        Mapping domainMapping = Mapping.domain(Domain.of("domain"));
        Mapping groupMapping = Mapping.group("group");

        virtualUserTable.addMapping(source, domainMapping);

        assertThat(virtualUserTable.listSources(groupMapping)).isEmpty();
    }
}
