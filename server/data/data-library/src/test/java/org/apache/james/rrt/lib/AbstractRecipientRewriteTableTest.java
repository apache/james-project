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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The abstract test for the virtual user table. Contains tests related to
 * simple, regexp, wildcard, error,... Extend this and instanciate the needed
 * virtualUserTable implementation.
 */
public abstract class AbstractRecipientRewriteTableTest {

    protected AbstractRecipientRewriteTable virtualUserTable;
    protected final static int REGEX_TYPE = 0;
    protected final static int ERROR_TYPE = 1;
    protected final static int ADDRESS_TYPE = 2;
    protected final static int ALIASDOMAIN_TYPE = 3;

    @Before
    public void setUp() throws Exception {
        virtualUserTable = getRecipientRewriteTable();
    }

    @After
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
    public void testStoreAndRetrieveRegexMapping() throws
            org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException, RecipientRewriteTableException {

        String user = "test";
        String domain = "localhost";
        // String regex = "(.*):{$1}@localhost";
        // String regex2 = "(.+):{$1}@test";
        String regex = "(.*)@localhost";
        String regex2 = "(.+)@test";
        String invalidRegex = ".*):";
        boolean catched = false;

        try {

            assertNull("No mapping", virtualUserTable.getMappings(user, domain));

            assertTrue("Added virtual mapping", addMapping(user, domain, regex, REGEX_TYPE));
            assertTrue("Added virtual mapping", addMapping(user, domain, regex2, REGEX_TYPE));
            assertEquals("Two mappings", virtualUserTable.getMappings(user, domain).size(), 2);
            assertEquals("One mappingline", virtualUserTable.getAllMappings().size(), 1);

            assertTrue("remove virtual mapping", removeMapping(user, domain, regex, REGEX_TYPE));

            try {
                virtualUserTable.addRegexMapping(user, domain, invalidRegex);
            } catch (RecipientRewriteTableException e) {
                catched = true;
            }
            assertTrue("Invalid Mapping throw exception", catched);

            assertTrue("remove virtual mapping", removeMapping(user, domain, regex2, REGEX_TYPE));

            assertNull("No mapping", virtualUserTable.getMappings(user, domain));

            assertNull("No mappings", virtualUserTable.getAllMappings());

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            fail("Storing failed");
        }

    }

    @Test
    public void testStoreAndRetrieveAddressMapping() throws ErrorMappingException, RecipientRewriteTableException {

        String user = "test";
        String domain = "localhost";
        String address = "test@localhost2";
        String address2 = "test@james";

        try {

            assertNull("No mapping", virtualUserTable.getMappings(user, domain));

            assertTrue("Added virtual mapping", addMapping(user, domain, address, ADDRESS_TYPE));
            assertTrue("Added virtual mapping", addMapping(user, domain, address2, ADDRESS_TYPE));

            assertEquals("Two mappings", virtualUserTable.getMappings(user, domain).size(), 2);
            assertEquals("One mappingline", virtualUserTable.getAllMappings().size(), 1);

            assertTrue("remove virtual mapping", removeMapping(user, domain, address, ADDRESS_TYPE));

            /*
             * TEMPORARILY REMOVE JDBC specific test String invalidAddress=
             * ".*@localhost2:"; boolean catched = false; if (virtualUserTable
             * instanceof JDBCRecipientRewriteTable) { try {
             * assertTrue("Added virtual mapping", addMapping(user, domain,
             * invalidAddress, ADDRESS_TYPE)); } catch (InvalidMappingException
             * e) { catched = true; }
             * assertTrue("Invalid Mapping throw exception" , catched); }
             */

            assertTrue("remove virtual mapping", removeMapping(user, domain, address2, ADDRESS_TYPE));

            assertNull("No mapping", virtualUserTable.getMappings(user, domain));
            assertNull("No mappings", virtualUserTable.getAllMappings());

        } catch (IllegalArgumentException e) {
            fail("Storing failed");
        }

    }

    @Test
    public void testStoreAndRetrieveErrorMapping() throws ErrorMappingException, RecipientRewriteTableException {

        String user = "test";
        String domain = "localhost";
        String error = "bounce!";
        boolean catched = false;

        try {

            assertNull("No mapping", virtualUserTable.getMappings(user, domain));

            assertTrue("Added virtual mapping", addMapping(user, domain, error, ERROR_TYPE));
            assertEquals("One mappingline", virtualUserTable.getAllMappings().size(), 1);

            try {
                virtualUserTable.getMappings(user, domain);
            } catch (ErrorMappingException e) {
                catched = true;
            }
            assertTrue("Error Mapping throw exception", catched);

            assertTrue("remove virtual mapping", removeMapping(user, domain, error, ERROR_TYPE));
            assertNull("No mapping", virtualUserTable.getMappings(user, domain));
            assertNull("No mappings", virtualUserTable.getAllMappings());

        } catch (IllegalArgumentException e) {
            fail("Storing failed");
        }

    }

    @Test
    public void testStoreAndRetrieveWildCardAddressMapping() throws ErrorMappingException,
            RecipientRewriteTableException {

        String user = "test";
        String user2 = "test2";
        String domain = "localhost";
        String address = "test@localhost2";
        String address2 = "test@james";

        try {

            assertNull("No mapping", virtualUserTable.getMappings(user, domain));

            assertTrue("Added virtual mapping",
                    addMapping(RecipientRewriteTable.WILDCARD, domain, address, ADDRESS_TYPE));
            assertTrue("Added virtual mapping", addMapping(user, domain, address2, ADDRESS_TYPE));

            assertEquals("One mappings", 1, virtualUserTable.getMappings(user, domain).size());
            assertEquals("One mappings", 1, virtualUserTable.getMappings(user2, domain).size());

            assertTrue("remove virtual mapping", removeMapping(user, domain, address2, ADDRESS_TYPE));
            assertTrue("remove virtual mapping", removeMapping(RecipientRewriteTable.WILDCARD, domain, address,
                    ADDRESS_TYPE));
            assertNull("No mapping", virtualUserTable.getMappings(user, domain));
            assertNull("No mapping", virtualUserTable.getMappings(user2, domain));

        } catch (IllegalArgumentException e) {
            fail("Storing failed");
        }

    }

    @Test
    public void testRecursiveMapping() throws ErrorMappingException, RecipientRewriteTableException {

        String user1 = "user1";
        String user2 = "user2";
        String user3 = "user3";
        String domain1 = "domain1";
        String domain2 = "domain2";
        String domain3 = "domain3";
        boolean exception1 = false;

        virtualUserTable.setRecursiveMapping(true);

        try {
            assertNull("No mappings", virtualUserTable.getAllMappings());

            assertTrue("Add mapping", addMapping(user1, domain1, user2 + "@" + domain2, ADDRESS_TYPE));
            assertTrue("Add mapping", addMapping(user2, domain2, user3 + "@" + domain3, ADDRESS_TYPE));
            assertEquals("Recursive mapped", virtualUserTable.getMappings(user1, domain1).iterator().next(),
                    MappingImpl.of(user3 + "@" + domain3));

            assertTrue("Add mapping", addMapping(user3, domain3, user1 + "@" + domain1, ADDRESS_TYPE));
            try {
                virtualUserTable.getMappings(user1, domain1);
            } catch (ErrorMappingException e) {
                exception1 = true;
            }
            assertTrue("Exception thrown on to many mappings", exception1);

            // disable recursive mapping
            virtualUserTable.setRecursiveMapping(false);
            assertEquals("Not recursive mapped", virtualUserTable.getMappings(user1, domain1).iterator().next(),
                    MappingImpl.of(user2 + "@" + domain2));

        } catch (IllegalArgumentException e) {
            fail("Storing failed");
        }
    }

    @Test
    public void testAliasDomainMapping() throws ErrorMappingException, RecipientRewriteTableException {

        String domain = "realdomain";
        String aliasDomain = "aliasdomain";
        String user = "user";
        String user2 = "user2";

        assertNull("No mappings", virtualUserTable.getAllMappings());

        try {

            assertTrue("Add mapping", addMapping(RecipientRewriteTable.WILDCARD, aliasDomain, user2 + "@" + domain,
                    ADDRESS_TYPE));
            assertTrue("Add aliasDomain mapping", addMapping(RecipientRewriteTable.WILDCARD, aliasDomain, domain,
                    ALIASDOMAIN_TYPE));

            Iterator<String> mappings = virtualUserTable.getMappings(user, aliasDomain).asStrings().iterator();
            assertEquals("Domain mapped as first ", mappings.next(), user + "@" + domain);
            assertEquals("Address mapped as second ", mappings.next(), user2 + "@" + domain);

            assertTrue("Remove mapping", removeMapping(RecipientRewriteTable.WILDCARD, aliasDomain, user2 + "@" + domain,
                    ADDRESS_TYPE));
            assertTrue("Remove aliasDomain mapping", removeMapping(RecipientRewriteTable.WILDCARD, aliasDomain, domain,
                    ALIASDOMAIN_TYPE));

        } catch (IllegalArgumentException e) {
            fail("Storing failed");
        }

    }
    
    @Test
    public void sortMappingsShouldReturnNullWhenNull() {
        assertNull(AbstractRecipientRewriteTable.sortMappings(null));
    }

    @Test
    public void sortMappingsShouldReturnEmptyWhenEmpty() {
        assertEquals("", AbstractRecipientRewriteTable.sortMappings(""));
    }

    @Test
    public void sortMappingsShouldReturnSameStringWhenSingleDomainAlias() {
        String singleDomainAlias = RecipientRewriteTable.ALIASDOMAIN_PREFIX + "first";
        assertEquals(singleDomainAlias, AbstractRecipientRewriteTable.sortMappings(singleDomainAlias));
    }
     
    @Test
    public void sortMappingsShouldReturnSameStringWhenTwoDomainAliases() {
        String firstAliasMapping = RecipientRewriteTable.ALIASDOMAIN_PREFIX + "first";
        String secondAliasMapping = RecipientRewriteTable.ALIASDOMAIN_PREFIX + "second";
        String mappings = RecipientRewriteTableUtil.CollectionToMapping(Arrays.asList(firstAliasMapping, secondAliasMapping));
        assertEquals(mappings, AbstractRecipientRewriteTable.sortMappings(mappings));
    }
    
    @Test
    public void sortMappingsShouldPutDomainAliasFirstWhenVariousMappings() {
        String regexMapping = RecipientRewriteTable.REGEX_PREFIX + "first";
        String domainMapping = RecipientRewriteTable.ALIASDOMAIN_PREFIX + "second";
        String inputMappings = RecipientRewriteTableUtil.CollectionToMapping(Arrays.asList(regexMapping, domainMapping));
        String expectedMappings = RecipientRewriteTableUtil.CollectionToMapping(Arrays.asList(domainMapping, regexMapping));
        assertEquals(expectedMappings, AbstractRecipientRewriteTable.sortMappings(inputMappings));
    }


    protected abstract AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception;

    protected abstract boolean addMapping(String user, String domain, String mapping, int type) throws
            RecipientRewriteTableException;

    protected abstract boolean removeMapping(String user, String domain, String mapping, int type) throws
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
