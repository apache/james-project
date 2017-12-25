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

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
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
    protected final static int REGEX_TYPE = 0;
    protected final static int ERROR_TYPE = 1;
    protected final static int ADDRESS_TYPE = 2;
    protected final static int ALIASDOMAIN_TYPE = 3;

    public void setUp() throws Exception {
        virtualUserTable = getRecipientRewriteTable();
    }

    public void tearDown() throws Exception {

        Map<String, Mappings> mappings = virtualUserTable.getAllMappings();

        if (mappings != null) {

            for (String key : virtualUserTable.getAllMappings().keySet()) {
                String args[] = key.split("@");

                Mappings map = mappings.get(key);

                for (String aMap : map.asStrings()) {
                    try {
                        removeMapping(args[0], args[1], aMap);
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
        String domain = "test";
        String regex = "prefix_.*:admin@test";
        addMapping(user, domain, regex, REGEX_TYPE);
        assertThat(virtualUserTable.getMappings("prefix_abc", domain)).isNotEmpty();
    }

    @Test
    public void testStoreAndRetrieveRegexMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        String domain = "localhost";
        // String regex = "(.*):{$1}@localhost";
        // String regex2 = "(.+):{$1}@test";
        String regex = "(.*)@localhost";
        String regex2 = "(.+)@test";
        String invalidRegex = ".*):";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();

        addMapping(user, domain, regex, REGEX_TYPE);
        addMapping(user, domain, regex2, REGEX_TYPE);
        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("Two mappings").hasSize(2);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);
        removeMapping(user, domain, regex, REGEX_TYPE);

        assertThatThrownBy(() -> virtualUserTable.addRegexMapping(user, domain, invalidRegex))
            .describedAs("Invalid Mapping throw exception")
            .isInstanceOf(RecipientRewriteTableException.class);


        removeMapping(user, domain, regex2, REGEX_TYPE);


        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isNull();
    }

    @Test
    public void testStoreAndRetrieveAddressMapping() throws ErrorMappingException, RecipientRewriteTableException {

        String user = "test";
        String domain = "localhost";
        String address = "test@localhost2";
        String address2 = "test@james";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();

        addMapping(user, domain, address, ADDRESS_TYPE);
        addMapping(user, domain, address2, ADDRESS_TYPE);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("Two mappings").hasSize(2);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);

        removeMapping(user, domain, address, ADDRESS_TYPE);

            /*
             * TEMPORARILY REMOVE JDBC specific test String invalidAddress=
             * ".*@localhost2:"; boolean catched = false; if (virtualUserTable
             * instanceof JDBCRecipientRewriteTable) { try {
             * assertTrue("Added virtual mapping", addMapping(user, domain,
             * invalidAddress, ADDRESS_TYPE)); } catch (InvalidMappingException
             * e) { catched = true; }
             * assertTrue("Invalid Mapping throw exception" , catched); }
             */


        removeMapping(user, domain, address2, ADDRESS_TYPE);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isNull();
    }

    @Test
    public void testStoreAndRetrieveErrorMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        String domain = "localhost";
        String error = "bounce!";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();

        addMapping(user, domain, error, ERROR_TYPE);
        assertThat(virtualUserTable.getAllMappings()).describedAs("One mappingline").hasSize(1);

        assertThatThrownBy(() ->
            virtualUserTable.getMappings(user, domain))
            .describedAs("Exception thrown on to many mappings")
            .isInstanceOf(ErrorMappingException.class);

        removeMapping(user, domain, error, ERROR_TYPE);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();
        assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isNull();
    }

    @Test
    public void testStoreAndRetrieveWildCardAddressMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user = "test";
        String user2 = "test2";
        String domain = "localhost";
        String address = "test@localhost2";
        String address2 = "test@james";

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();

        addMapping(RecipientRewriteTable.WILDCARD, domain, address, ADDRESS_TYPE);
        addMapping(user, domain, address2, ADDRESS_TYPE);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("One mappings").hasSize(1);
        assertThat(virtualUserTable.getMappings(user2, domain)).describedAs("One mappings").hasSize(1);

        removeMapping(user, domain, address2, ADDRESS_TYPE);
        removeMapping(RecipientRewriteTable.WILDCARD, domain, address, ADDRESS_TYPE);

        assertThat(virtualUserTable.getMappings(user, domain)).describedAs("No mapping").isNull();
        assertThat(virtualUserTable.getMappings(user2, domain)).describedAs("No mapping").isNull();
    }

    @Test
    public void testRecursiveMapping() throws ErrorMappingException, RecipientRewriteTableException {
        String user1 = "user1";
        String user2 = "user2";
        String user3 = "user3";
        String domain1 = "domain1";
        String domain2 = "domain2";
        String domain3 = "domain3";

        virtualUserTable.setRecursiveMapping(true);

            assertThat(virtualUserTable.getAllMappings()).describedAs("No mapping").isNull();

            addMapping(user1, domain1, user2 + "@" + domain2, ADDRESS_TYPE);
            addMapping(user2, domain2, user3 + "@" + domain3, ADDRESS_TYPE);
            assertThat(virtualUserTable.getMappings(user1, domain1)).containsOnly(MappingImpl.address(user3 + "@" + domain3));
            addMapping(user3, domain3, user1 + "@" + domain1, ADDRESS_TYPE);
            
            assertThatThrownBy(() ->
                virtualUserTable.getMappings(user1, domain1))
                .describedAs("Exception thrown on to many mappings")
                .isInstanceOf(ErrorMappingException.class);

            // disable recursive mapping
            virtualUserTable.setRecursiveMapping(false);
            assertThat(virtualUserTable.getMappings(user1, domain1)).describedAs("Not recursive mapped").containsExactly(MappingImpl.address(user2 + "@" + domain2));
    }

    @Test
    public void testAliasDomainMapping() throws ErrorMappingException, RecipientRewriteTableException {

        String domain = "realdomain";
        String aliasDomain = "aliasdomain";
        String user = "user";
        String user2 = "user2";

        assertThat(virtualUserTable.getAllMappings()).describedAs("No mappings").isNull();

        addMapping(RecipientRewriteTable.WILDCARD, aliasDomain, user2 + "@" + domain, ADDRESS_TYPE);
        addMapping(RecipientRewriteTable.WILDCARD, aliasDomain, domain, ALIASDOMAIN_TYPE);

        assertThat(virtualUserTable.getMappings(user, aliasDomain))
            .describedAs("Domain mapped as first, Address mapped as second")
            .containsExactly(MappingImpl.address(user + "@" + domain), MappingImpl.address(user2 + "@" + domain));

        removeMapping(RecipientRewriteTable.WILDCARD, aliasDomain, user2 + "@" + domain, ADDRESS_TYPE);

        removeMapping(RecipientRewriteTable.WILDCARD, aliasDomain, domain, ALIASDOMAIN_TYPE);
    }

    @Test
    public void sortMappingsShouldReturnEmptyWhenEmpty() {
        assertThat(AbstractRecipientRewriteTable.sortMappings(MappingsImpl.empty())).isEmpty();
    }

    @Test
    public void sortMappingsShouldReturnSameStringWhenSingleDomainAlias() {
        String singleDomainAlias = RecipientRewriteTable.ALIASDOMAIN_PREFIX + "first";
        assertThat(AbstractRecipientRewriteTable.sortMappings(MappingsImpl.fromRawString(singleDomainAlias))).containsExactly(MappingImpl.domain("first"));
    }
     
    @Test
    public void sortMappingsShouldReturnSameStringWhenTwoDomainAliases() {
        MappingsImpl mappings = MappingsImpl.builder()
                .add(RecipientRewriteTable.ALIASDOMAIN_PREFIX + "first")
                .add(RecipientRewriteTable.ALIASDOMAIN_PREFIX + "second")
                .build();
        assertThat(AbstractRecipientRewriteTable.sortMappings(mappings)).isEqualTo(mappings);
    }
    
    @Test
    public void sortMappingsShouldPutDomainAliasFirstWhenVariousMappings() {
        String regexMapping = RecipientRewriteTable.REGEX_PREFIX + "first";
        String domainMapping = RecipientRewriteTable.ALIASDOMAIN_PREFIX + "second";
        MappingsImpl mappings = MappingsImpl.builder()
                .add(regexMapping)
                .add(domainMapping)
                .build();
        assertThat(AbstractRecipientRewriteTable.sortMappings(mappings))
                .isEqualTo(MappingsImpl.builder()
                        .add(domainMapping)
                        .add(regexMapping)
                        .build());
    }

    @Test
    public void addMappingShouldThrowWhenMappingAlreadyExists() throws Exception {
        String user = "test";
        String domain = "localhost";
        String address = "test@localhost2";

        expectedException.expect(RecipientRewriteTableException.class);

        addMapping(user, domain, address, ADDRESS_TYPE);
        addMapping(user, domain, address, ADDRESS_TYPE);
    }

    @Test
    public void addMappingShouldNotThrowWhenMappingAlreadyExistsWithAnOtherType() throws Exception {
        String user = "test";
        String domain = "localhost";
        String address = "test@localhost2";

        addMapping(user, domain, address, ADDRESS_TYPE);
        addMapping(user, domain, address, REGEX_TYPE);

        assertThat(virtualUserTable.getMappings(user, domain)).hasSize(2);
    }

    protected abstract AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception;

    protected abstract void addMapping(String user, String domain, String mapping, int type) throws
            RecipientRewriteTableException;

    protected abstract void removeMapping(String user, String domain, String mapping, int type) throws
            RecipientRewriteTableException;

    private void removeMapping(String user, String domain, String rawMapping) throws RecipientRewriteTableException {
        if (rawMapping.startsWith(RecipientRewriteTable.ERROR_PREFIX)) {
            removeMapping(user, domain, rawMapping.substring(RecipientRewriteTable.ERROR_PREFIX.length()), ERROR_TYPE);
        } else if (rawMapping.startsWith(RecipientRewriteTable.REGEX_PREFIX)) {
            removeMapping(user, domain, rawMapping.substring(RecipientRewriteTable.REGEX_PREFIX.length()), REGEX_TYPE);
        } else if (rawMapping.startsWith(RecipientRewriteTable.ALIASDOMAIN_PREFIX)) {
            removeMapping(user, domain, rawMapping.substring(RecipientRewriteTable.ALIASDOMAIN_PREFIX.length()),
                    ALIASDOMAIN_TYPE);
        } else {
            removeMapping(user, domain, rawMapping, ADDRESS_TYPE);
        }
    }
}
