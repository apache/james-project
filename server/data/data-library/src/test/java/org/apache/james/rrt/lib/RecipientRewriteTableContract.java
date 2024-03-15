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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.rrt.api.LoopDetectedException;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.SourceDomainIsNotInDomainListException;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

/**
 * The abstract test for the virtual user table. Contains tests related to
 * simple, regexp, wildcard, error,... Extend this and instantiate the needed
 * virtualUserTable implementation.
 */
public interface RecipientRewriteTableContract {

    String USER = "test";
    String ADDRESS = "test@localhost2";
    String ADDRESS_2 = "test@james";
    Domain SUPPORTED_DOMAIN = Domain.LOCALHOST;
    Domain DOMAIN_2 = Domain.of("adomain.tld");
    MappingSource SOURCE = MappingSource.fromUser(USER, SUPPORTED_DOMAIN);
    Domain NOT_SUPPORTED_DOMAIN = Domain.of("notAManagedDomain");
    MappingSource SOURCE_WITH_DOMAIN_NOT_IN_DOMAIN_LIST = MappingSource.fromUser(USER, NOT_SUPPORTED_DOMAIN);

    void createRecipientRewriteTable() throws Exception;

    AbstractRecipientRewriteTable virtualUserTable();

    default void setUp() throws Exception {
        setRecursiveRecipientRewriteTable();
    }

    private void setRecursiveRecipientRewriteTable() throws Exception {
        setNotConfiguredRecipientRewriteTable();
        virtualUserTable().setConfiguration(new RecipientRewriteTableConfiguration(true, 10));
    }

    private void setNonRecursiveRecipientRewriteTable() throws Exception {
        setNotConfiguredRecipientRewriteTable();
        virtualUserTable().setConfiguration(new RecipientRewriteTableConfiguration(false, 0));
    }

    private void setNotConfiguredRecipientRewriteTable() throws Exception {
        createRecipientRewriteTable();

        SimpleDomainList domainList = new SimpleDomainList();
        domainList.addDomain(SUPPORTED_DOMAIN);
        domainList.addDomain(DOMAIN_2);
        virtualUserTable().setDomainList(domainList);
    }

    default void tearDown() throws Exception {
        Map<MappingSource, Mappings> mappings = virtualUserTable().getAllMappings();

        if (mappings != null) {
            for (MappingSource key : virtualUserTable().getAllMappings().keySet()) {
                Mappings map = mappings.get(key);

                map.asStream()
                    .forEach(Throwing.consumer(mapping ->
                        virtualUserTable().removeMapping(key, mapping)));
            }
        }

        LifecycleUtil.dispose(virtualUserTable());
    }

    @Test
    default void testStoreAndGetMappings() throws Exception {
        Domain domain = Domain.of("test");
        virtualUserTable().addMapping(MappingSource.fromDomain(domain), Mapping.regex("prefix_.*:admin@test"));
        assertThat(virtualUserTable().getResolvedMappings("prefix_abc", domain)).isNotEmpty();
    }

    @Test
    default void testQuotedLocalPart() {
        assertThatCode(() -> virtualUserTable().getResolvedMappings("\"a@b\"", Domain.of("test"))).doesNotThrowAnyException();
    }

    @Test
    default void notConfiguredResolutionShouldThrow() throws Exception {
        setNotConfiguredRecipientRewriteTable();
        assertThatCode(() -> virtualUserTable().getResolvedMappings(USER, Domain.LOCALHOST))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    default void configuringTwiceShouldThrow() {
        assertThatCode(() -> virtualUserTable().setConfiguration(new RecipientRewriteTableConfiguration(true, 10)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    default void testStoreAndRetrieveRegexMapping() throws Exception {
        String regex = "(.*)@localhost";
        String regex2 = "(.+)@test";
        String invalidRegex = ".*):";

        Mapping mappingRegex = Mapping.regex(regex);
        Mapping mappingRegex2 = Mapping.regex(regex2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).describedAs("No mapping").isEmpty();

        virtualUserTable().addMapping(SOURCE, mappingRegex);
        virtualUserTable().addMapping(SOURCE, mappingRegex2);
        assertThat(virtualUserTable().getStoredMappings(SOURCE)).describedAs("Two mappings")
            .containsOnly(mappingRegex, mappingRegex2);
        assertThat(virtualUserTable().getAllMappings()).describedAs("One mappingline").hasSize(1);
        virtualUserTable().removeMapping(SOURCE, mappingRegex);

        assertThatThrownBy(() -> virtualUserTable().addRegexMapping(SOURCE, invalidRegex))
            .describedAs("Invalid Mapping throw exception")
            .isInstanceOf(RecipientRewriteTableException.class);
        
        virtualUserTable().removeMapping(SOURCE, mappingRegex2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).describedAs("No mapping").isEmpty();
        assertThat(virtualUserTable().getAllMappings()).describedAs("No mapping").isEmpty();
    }

    @Test
    default void getAllMappingsShouldListAllEntries() throws Exception {
        String user2 = "test2";
        String regex = "(.*)@localhost";
        String regex2 = "(.+)@test";

        Mapping mappingAddress = Mapping.address(USER + "@" + Domain.LOCALHOST.asString());
        Mapping mappingRegex = Mapping.regex(regex);
        Mapping mappingRegex2 = Mapping.regex(regex2);
        MappingSource source2 = MappingSource.fromUser(user2, Domain.LOCALHOST);

        virtualUserTable().addMapping(SOURCE, mappingRegex);
        virtualUserTable().addMapping(SOURCE, mappingRegex2);
        virtualUserTable().addMapping(source2, mappingAddress);

        assertThat(virtualUserTable().getAllMappings())
            .describedAs("One mappingline")
            .containsOnly(
                Pair.of(SOURCE, MappingsImpl.builder()
                    .add(mappingRegex)
                    .add(mappingRegex2)
                    .build()),
                Pair.of(source2, MappingsImpl.builder()
                    .add(mappingAddress)
                    .build()));
    }

    @Test
    default void testStoreAndRetrieveAddressMapping() throws Exception {
        Mapping mappingAddress = Mapping.address(ADDRESS);
        Mapping mappingAddress2 = Mapping.address(ADDRESS_2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).describedAs("No mapping").isEmpty();

        virtualUserTable().addMapping(SOURCE, mappingAddress);
        virtualUserTable().addMapping(SOURCE, mappingAddress2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).describedAs("Two mappings")
            .containsOnly(mappingAddress, mappingAddress2);
        assertThat(virtualUserTable().getAllMappings()).describedAs("One mappingline").hasSize(1);

        virtualUserTable().removeMapping(SOURCE, mappingAddress);
        virtualUserTable().removeMapping(SOURCE, mappingAddress2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).describedAs("No mapping").isEmpty();
        assertThat(virtualUserTable().getAllMappings()).describedAs("No mapping").isEmpty();
    }

    @Test
    default void testStoreAndRetrieveErrorMapping() throws Exception {
        String error = "bounce!";

        assertThat(virtualUserTable().getResolvedMappings(USER, Domain.LOCALHOST)).describedAs("No mapping").isEmpty();

        virtualUserTable().addMapping(SOURCE, Mapping.error(error));
        assertThat(virtualUserTable().getAllMappings()).describedAs("One mappingline").hasSize(1);

        assertThatThrownBy(() ->
            virtualUserTable().getResolvedMappings(USER, Domain.LOCALHOST))
            .describedAs("Exception thrown on to many mappings")
            .isInstanceOf(ErrorMappingException.class);

        virtualUserTable().removeMapping(SOURCE, Mapping.error(error));

        assertThat(virtualUserTable().getResolvedMappings(USER, Domain.LOCALHOST)).describedAs("No mapping").isEmpty();
        assertThat(virtualUserTable().getAllMappings()).describedAs("No mapping").isEmpty();
    }

    @Test
    default void testStoreAndRetrieveWildCardAddressMapping() throws Exception {
        String user2 = "test2";

        Mapping mappingAddress = Mapping.address(ADDRESS);
        Mapping mappingAddress2 = Mapping.address(ADDRESS_2);

        assertThat(virtualUserTable().getResolvedMappings(USER, Domain.LOCALHOST)).describedAs("No mapping").isEmpty();

        virtualUserTable().addMapping(MappingSource.fromDomain(Domain.LOCALHOST), mappingAddress);
        virtualUserTable().addMapping(SOURCE, mappingAddress2);

        assertThat(virtualUserTable().getResolvedMappings(USER, Domain.LOCALHOST)).describedAs("One mappings")
            .containsOnly(mappingAddress2);
        assertThat(virtualUserTable().getResolvedMappings(user2, Domain.LOCALHOST)).describedAs("One mappings")
            .containsOnly(mappingAddress);

        virtualUserTable().removeMapping(SOURCE, mappingAddress2);
        virtualUserTable().removeMapping(MappingSource.fromDomain(Domain.LOCALHOST), mappingAddress);

        assertThat(virtualUserTable().getResolvedMappings(USER, Domain.LOCALHOST)).describedAs("No mapping").isEmpty();
        assertThat(virtualUserTable().getResolvedMappings(user2, Domain.LOCALHOST)).describedAs("No mapping").isEmpty();
    }

    @Test
    default void testNonRecursiveMapping() throws Exception {
        String user1 = "user1";
        String user2 = "user2";
        String user3 = "user3";
        Domain domain1 = Domain.of("domain1");
        Domain domain2 = Domain.of("domain2");
        Domain domain3 = Domain.of("domain3");
        MappingSource source1 = MappingSource.fromUser(user1, domain1);
        MappingSource source2 = MappingSource.fromUser(user2, domain2);

        setNonRecursiveRecipientRewriteTable();

        assertThat(virtualUserTable().getAllMappings()).describedAs("No mapping").isEmpty();

        virtualUserTable().addMapping(source1, Mapping.address(user2 + "@" + domain2.asString()));
        virtualUserTable().addMapping(source2, Mapping.address(user3 + "@" + domain3.asString()));
        assertThatThrownBy(() ->
            virtualUserTable().getResolvedMappings(user1, domain1))
            .describedAs("Exception thrown on too many mappings")
            .isInstanceOf(ErrorMappingException.class);
    }

    @Test
    default void testAliasDomainMapping() throws Exception {
        String domain = "realdomain";
        Domain aliasDomain = Domain.of("aliasdomain");
        String user = "user";
        String user2 = "user2";

        Mapping mappingAddress = Mapping.address(user2 + "@" + domain);
        Mapping mappingDomain = Mapping.domain(Domain.of(domain));

        assertThat(virtualUserTable().getAllMappings()).describedAs("No mappings").isEmpty();

        virtualUserTable().addMapping(MappingSource.fromDomain(aliasDomain), mappingAddress);
        virtualUserTable().addMapping(MappingSource.fromDomain(aliasDomain), mappingDomain);

        assertThat(virtualUserTable().getResolvedMappings(user, aliasDomain))
            .describedAs("Domain mapped as first, Address mapped as second")
            .isEqualTo(MappingsImpl.builder()
                .add(Mapping.address(user + "@" + domain))
                .add(mappingAddress)
                .build());

        virtualUserTable().removeMapping(MappingSource.fromDomain(aliasDomain), mappingAddress);
        virtualUserTable().removeMapping(MappingSource.fromDomain(aliasDomain), mappingDomain);
    }

    @Test
    default void addMappingShouldThrowWhenMappingAlreadyExists() throws Exception {
        virtualUserTable().addAddressMapping(SOURCE, ADDRESS);

        assertThatThrownBy(() -> virtualUserTable().addAddressMapping(SOURCE, ADDRESS))
            .isInstanceOf(RecipientRewriteTableException.class);
    }

    @Test
    default void addMappingShouldNotThrowWhenMappingAlreadyExistsWithAnOtherType() throws Exception {
        Mapping mappingAddress = Mapping.address(ADDRESS);
        Mapping mappingRegex = Mapping.regex(ADDRESS);

        virtualUserTable().addMapping(SOURCE, mappingAddress);
        virtualUserTable().addMapping(SOURCE, mappingRegex);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).containsOnly(mappingAddress, mappingRegex);
    }

    @Test
    default void addForwardMappingShouldStore() throws Exception {
        Mapping mappingForward = Mapping.forward(ADDRESS);
        Mapping mappingForward2 = Mapping.forward(ADDRESS_2);

        virtualUserTable().addMapping(SOURCE, mappingForward);
        virtualUserTable().addMapping(SOURCE, mappingForward2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).containsOnly(mappingForward, mappingForward2);
    }

    @Test
    default void removeForwardMappingShouldDelete() throws Exception {
        Mapping mappingForward = Mapping.forward(ADDRESS);
        Mapping mappingForward2 = Mapping.forward(ADDRESS_2);
        MappingSource source = MappingSource.fromUser(USER, Domain.LOCALHOST);

        virtualUserTable().addMapping(source, mappingForward);
        virtualUserTable().addMapping(source, mappingForward2);

        virtualUserTable().removeMapping(source, mappingForward);
        virtualUserTable().removeMapping(source, mappingForward2);

        assertThat(virtualUserTable().getStoredMappings(source)).isEmpty();
    }

    @Test
    default void addGroupMappingShouldStore() throws Exception {
        Mapping mappingGroup = Mapping.group(ADDRESS);
        Mapping mappingGroup2 = Mapping.group(ADDRESS_2);

        virtualUserTable().addMapping(SOURCE, mappingGroup);
        virtualUserTable().addMapping(SOURCE, mappingGroup2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).containsOnly(mappingGroup, mappingGroup2);
    }

    @Test
    default void removeGroupMappingShouldDelete() throws Exception {
        Mapping mappingGroup = Mapping.group(ADDRESS);
        Mapping mappingGroup2 = Mapping.group(ADDRESS_2);

        virtualUserTable().addMapping(SOURCE, mappingGroup);
        virtualUserTable().addMapping(SOURCE, mappingGroup2);

        virtualUserTable().removeMapping(SOURCE, mappingGroup);
        virtualUserTable().removeMapping(SOURCE, mappingGroup2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).isEmpty();
    }

    @Test
    default void addAliasMappingShouldStore() throws Exception {
        Mapping mappingAlias = Mapping.alias(ADDRESS);
        Mapping mappingAlias2 = Mapping.alias(ADDRESS_2);

        virtualUserTable().addMapping(SOURCE, mappingAlias);
        virtualUserTable().addMapping(SOURCE, mappingAlias2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).containsOnly(mappingAlias, mappingAlias2);
    }

    @Test
    default void removeAliasMappingShouldDelete() throws Exception {
        Mapping mappingAlias = Mapping.alias(ADDRESS);
        Mapping mappingAlias2 = Mapping.alias(ADDRESS_2);

        virtualUserTable().addMapping(SOURCE, mappingAlias);
        virtualUserTable().addMapping(SOURCE, mappingAlias2);

        virtualUserTable().removeMapping(SOURCE, mappingAlias);
        virtualUserTable().removeMapping(SOURCE, mappingAlias2);

        assertThat(virtualUserTable().getStoredMappings(SOURCE)).isEmpty();
    }

    @Test
    default void getUserDomainMappingShouldBeEmptyByDefault() throws Exception {
        assertThat(virtualUserTable().getStoredMappings(SOURCE)).isEmpty();
    }

    @Test
    default void listSourcesShouldReturnWhenHasMapping() throws Exception {
        Mapping mapping = Mapping.group(ADDRESS);
        virtualUserTable().addMapping(SOURCE, mapping);

        assertThat(virtualUserTable().listSources(mapping)).contains(SOURCE);
    }

    @Test
    default void listSourcesShouldReturnWhenMultipleSourceMapping() throws Exception {
        MappingSource source = MappingSource.fromUser(USER, Domain.of("james"));
        MappingSource source2 = MappingSource.fromDomain(Domain.LOCALHOST);
        Mapping mapping = Mapping.group(ADDRESS);

        virtualUserTable().addMapping(source, mapping);
        virtualUserTable().addMapping(source2, mapping);

        assertThat(virtualUserTable().listSources(mapping)).contains(source, source2);
    }

    @Test
    default void listSourcesShouldReturnWhenHasForwardMapping() throws Exception {
        Mapping mapping = Mapping.forward("forward");

        virtualUserTable().addMapping(SOURCE, mapping);

        assertThat(virtualUserTable().listSources(mapping)).contains(SOURCE);
    }

    @Test
    default void listSourcesShouldReturnAliasMappings() throws Exception {
        Mapping mapping = Mapping.alias("alias");

        virtualUserTable().addMapping(SOURCE, mapping);

        assertThat(virtualUserTable().listSources(mapping)).contains(SOURCE);
    }

    @Test
    default void listSourcesShouldReturnWhenHasAddressMapping() throws Exception {
        Mapping mapping = Mapping.address("address");

        virtualUserTable().addMapping(SOURCE, mapping);

        assertThat(virtualUserTable().listSources(mapping)).contains(SOURCE);
    }

    @Test
    default void listSourcesShouldThrowExceptionWhenHasRegexMapping() throws Exception {
        Mapping mapping = Mapping.regex("regex");

        virtualUserTable().addMapping(SOURCE, mapping);

        assertThatThrownBy(() -> virtualUserTable().listSources(mapping))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void listSourcesShouldHandleDomainMapping() throws Exception {
        Mapping mapping = Mapping.domain(Domain.of("domain"));

        virtualUserTable().addMapping(SOURCE, mapping);

        assertThat(virtualUserTable().listSources(mapping))
            .containsExactly(SOURCE);
    }

    @Test
    default void listSourcesShouldReturnEmptyWhenNoDomainAlias() throws Exception {
        Mapping mapping = Mapping.domain(Domain.of("domain"));

        assertThat(virtualUserTable().listSources(mapping)).isEmpty();
    }

    @Test
    default void listSourcesShouldHandleDomainSource() throws Exception {
        Mapping mapping = Mapping.domain(Domain.of("domain"));

        MappingSource source = MappingSource.fromDomain(Domain.of("source.org"));
        virtualUserTable().addMapping(source, mapping);

        assertThat(virtualUserTable().listSources(mapping))
            .containsExactly(source);
    }

    @Test
    default void listSourcesShouldHandleDomainSources() throws Exception {
        Mapping mapping = Mapping.domain(Domain.of("domain"));

        MappingSource source1 = MappingSource.fromDomain(Domain.of("source1.org"));
        MappingSource source2 = MappingSource.fromDomain(Domain.of("source2.org"));
        virtualUserTable().addMapping(source1, mapping);
        virtualUserTable().addMapping(source2, mapping);

        assertThat(virtualUserTable().listSources(mapping))
            .containsExactlyInAnyOrder(source1, source2);
    }

    @Test
    default void listSourcesShouldThrowExceptionWhenHasErrorMapping() throws Exception {
        Mapping mapping = Mapping.error("error");

        virtualUserTable().addMapping(SOURCE, mapping);

        assertThatThrownBy(() -> virtualUserTable().listSources(mapping))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void listSourcesShouldReturnEmptyWhenMappingDoesNotExist() throws Exception {
        Mapping domainMapping = Mapping.domain(Domain.of("domain"));
        Mapping groupMapping = Mapping.group("group");

        virtualUserTable().addMapping(SOURCE, domainMapping);

        assertThat(virtualUserTable().listSources(groupMapping)).isEmpty();
    }

    @Test
    default void getSourcesForTypeShouldReturnEmptyWhenNoMapping() throws Exception {
        assertThat(virtualUserTable().getSourcesForType(Mapping.Type.Alias)).isEmpty();
    }

    @Test
    default void getSourcesForTypeShouldReturnEmptyWhenNoMatchingMapping() throws Exception {
        virtualUserTable().addForwardMapping(SOURCE, ADDRESS);

        assertThat(virtualUserTable().getSourcesForType(Mapping.Type.Alias)).isEmpty();
    }

    @Test
    default void getSourcesForTypeShouldReturnMatchingMapping() throws Exception {
        virtualUserTable().addAliasMapping(SOURCE, ADDRESS);

        assertThat(virtualUserTable().getSourcesForType(Mapping.Type.Alias)).containsOnly(SOURCE);
    }

    @Test
    default void getSourcesForTypeShouldNotReturnDuplicatedSources() throws Exception {
        virtualUserTable().addAliasMapping(SOURCE, ADDRESS);
        virtualUserTable().addAliasMapping(SOURCE, ADDRESS_2);

        assertThat(virtualUserTable().getSourcesForType(Mapping.Type.Alias)).containsExactly(SOURCE);
    }

    @Test
    default void getSourcesForTypeShouldReturnSortedStream() throws Exception {
        MappingSource source1 = MappingSource.fromUser("alice", Domain.LOCALHOST);
        MappingSource source2 = MappingSource.fromUser("bob", Domain.LOCALHOST);
        MappingSource source3 = MappingSource.fromUser("cedric", Domain.LOCALHOST);

        virtualUserTable().addAliasMapping(source1, ADDRESS);
        virtualUserTable().addAliasMapping(source3, ADDRESS);
        virtualUserTable().addAliasMapping(source2, ADDRESS);

        assertThat(virtualUserTable().getSourcesForType(Mapping.Type.Alias))
            .containsExactly(source1, source2, source3);
    }

    @Test
    default void getMappingsForTypeShouldReturnEmptyWhenNoMapping() throws Exception {
        assertThat(virtualUserTable().getMappingsForType(Mapping.Type.Alias)).isEmpty();
    }

    @Test
    default void getMappingsForTypeShouldReturnEmptyWhenNoMatchingMapping() throws Exception {
        virtualUserTable().addForwardMapping(SOURCE, ADDRESS);

        assertThat(virtualUserTable().getMappingsForType(Mapping.Type.Alias)).isEmpty();
    }

    @Test
    default void getMappingsForTypeShouldReturnMatchingMapping() throws Exception {
        virtualUserTable().addAliasMapping(SOURCE, ADDRESS);

        assertThat(virtualUserTable().getMappingsForType(Mapping.Type.Alias)).containsOnly(Mapping.alias(ADDRESS));
    }

    @Test
    default void getMappingsForTypeShouldNotReturnDuplicatedDestinations() throws Exception {
        MappingSource source2 = MappingSource.fromUser("bob", Domain.LOCALHOST);

        virtualUserTable().addAliasMapping(SOURCE, ADDRESS);
        virtualUserTable().addAliasMapping(source2, ADDRESS);

        assertThat(virtualUserTable().getMappingsForType(Mapping.Type.Alias)).containsExactly(Mapping.alias(ADDRESS));
    }

    @Test
    default void getMappingsForTypeShouldReturnSortedStream() throws Exception {
        String address1 = "alice@domain.com";
        String address2 = "bob@domain.com";
        String address3 = "cedric@domain.com";
        Mapping mapping1 = Mapping.alias(address1);
        Mapping mapping2 = Mapping.alias(address2);
        Mapping mapping3 = Mapping.alias(address3);

        virtualUserTable().addAliasMapping(SOURCE, address1);
        virtualUserTable().addAliasMapping(SOURCE, address3);
        virtualUserTable().addAliasMapping(SOURCE, address2);

        assertThat(virtualUserTable().getMappingsForType(Mapping.Type.Alias))
            .containsExactly(mapping1, mapping2, mapping3);
    }

    @Test
    default void addRegexMappingShouldThrowWhenSourceDomainIsNotInDomainList() {
        assertThatThrownBy(() -> virtualUserTable().addRegexMapping(SOURCE_WITH_DOMAIN_NOT_IN_DOMAIN_LIST, ".*@localhost"))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class);
    }

    @Test
    default void addAddressMappingShouldThrowWhenSourceDomainIsNotInDomainList() {
        assertThatThrownBy(() -> virtualUserTable().addAddressMapping(SOURCE_WITH_DOMAIN_NOT_IN_DOMAIN_LIST, ADDRESS))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class);
    }

    @Test
    default void addErrorMappingShouldThrowWhenSourceDomainIsNotInDomainList() {
        assertThatThrownBy(() -> virtualUserTable().addErrorMapping(SOURCE_WITH_DOMAIN_NOT_IN_DOMAIN_LIST, "error"))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class);
    }

    @Test
    default void addDomainMappingShouldThrowWhenSourceDomainIsNotInDomainList() {
        assertThatThrownBy(() -> virtualUserTable().addDomainMapping(SOURCE_WITH_DOMAIN_NOT_IN_DOMAIN_LIST, SUPPORTED_DOMAIN))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class);
    }

    @Test
    default void addDomainAliasShouldThrowWhenSourceDomainIsNotInDomainList() {
        assertThatThrownBy(() -> virtualUserTable().addDomainAliasMapping(SOURCE_WITH_DOMAIN_NOT_IN_DOMAIN_LIST, SUPPORTED_DOMAIN))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class);
    }

    @Test
    default void addForwardMappingShouldThrowWhenSourceDomainIsNotInDomainList() {
        assertThatThrownBy(() -> virtualUserTable().addForwardMapping(SOURCE_WITH_DOMAIN_NOT_IN_DOMAIN_LIST, ADDRESS))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class);
    }

    @Test
    default void addGroupMappingShouldThrowWhenSourceDomainIsNotInDomainList() {
        assertThatThrownBy(() -> virtualUserTable().addGroupMapping(SOURCE_WITH_DOMAIN_NOT_IN_DOMAIN_LIST, ADDRESS))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class);
    }

    @Test
    default void addAliasMappingShouldThrowWhenDomainIsNotInDomainList() {
        assertThatThrownBy(() -> virtualUserTable().addAliasMapping(SOURCE_WITH_DOMAIN_NOT_IN_DOMAIN_LIST, ADDRESS))
            .isInstanceOf(SourceDomainIsNotInDomainListException.class);
    }

    @Test
    default void addAliasMappingShouldDetectLoops() throws Exception {
        String address1 = "alice@localhost";

        virtualUserTable().addAliasMapping(SOURCE, address1);

        assertThatThrownBy(() -> virtualUserTable().addAliasMapping(
            MappingSource.fromMailAddress(new MailAddress(address1)),
            SOURCE.asString()))
            .isInstanceOf(LoopDetectedException.class);
    }

    @Test
    default void addAddressMappingShouldDetectLoops() throws Exception {
        String address1 = "alice@localhost";

        virtualUserTable().addAddressMapping(SOURCE, address1);

        assertThatThrownBy(() -> virtualUserTable().addAddressMapping(
            MappingSource.fromMailAddress(new MailAddress(address1)),
            SOURCE.asString()))
            .isInstanceOf(LoopDetectedException.class);
    }

    @Test
    default void addGroupMappingShouldDetectLoops() throws Exception {
        String address1 = "alice@localhost";

        virtualUserTable().addGroupMapping(SOURCE, address1);

        assertThatThrownBy(() -> virtualUserTable().addGroupMapping(
            MappingSource.fromMailAddress(new MailAddress(address1)),
            SOURCE.asString()))
            .isInstanceOf(LoopDetectedException.class);
    }

    @Test
    default void addForwardMappingShouldDetectLoops() throws Exception {
        String address1 = "alice@localhost";

        virtualUserTable().addForwardMapping(SOURCE, address1);

        assertThatThrownBy(() -> virtualUserTable().addForwardMapping(
            MappingSource.fromMailAddress(new MailAddress(address1)),
            SOURCE.asString()))
            .isInstanceOf(LoopDetectedException.class);
    }

    @Test
    default void heterogeneousLoopsShouldBeDetected() throws Exception {
        String address1 = "alice@localhost";

        virtualUserTable().addForwardMapping(SOURCE, address1);

        assertThatThrownBy(() -> virtualUserTable().addGroupMapping(
            MappingSource.fromMailAddress(new MailAddress(address1)),
            SOURCE.asString()))
            .isInstanceOf(LoopDetectedException.class);
    }

    @Test
    default void longLoopsShouldBeDetected() throws Exception {
        String address1 = "alice@localhost";
        String address2 = "bob@localhost";

        virtualUserTable().addForwardMapping(SOURCE, address1);
        virtualUserTable().addForwardMapping(MappingSource.parse(address1), address2);

        assertThatThrownBy(() -> virtualUserTable().addGroupMapping(
            MappingSource.fromMailAddress(new MailAddress(address2)),
            SOURCE.asString()))
            .isInstanceOf(LoopDetectedException.class);
    }

    @Test
    default void domainMappingShouldBeHandledAsPartOfLoopDetection() throws Exception {
        String address1 = "alice@localhost";

        virtualUserTable().addForwardMapping(SOURCE, address1);
        virtualUserTable().addDomainMapping(MappingSource.fromDomain(Domain.LOCALHOST), DOMAIN_2);

        assertThatThrownBy(() -> virtualUserTable().addGroupMapping(
            MappingSource.fromMailAddress(new MailAddress("alice@adomain.tld")),
            SOURCE.asString()))
            .isInstanceOf(LoopDetectedException.class);
    }
}
