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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class XMLRecipientRewriteTableTest extends AbstractRecipientRewriteTableTest {

    private final BaseHierarchicalConfiguration defaultConfiguration = new BaseHierarchicalConfiguration();

    @Override
    @Before
    public void setUp() throws Exception {
        defaultConfiguration.setListDelimiterHandler(new DisabledListDelimiterHandler());
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() {
        return new XMLRecipientRewriteTable();
    }

    @Test
    @Ignore("addMapping doesn't handle checking for domain existence in this test implementation")
    @Override
    public void addAddressMappingShouldThrowWhenSourceDomainIsNotInDomainList() {
    }

    @Test
    @Ignore("addMapping doesn't handle checking for duplicate in this test implementation")
    @Override
    public void addMappingShouldThrowWhenMappingAlreadyExists() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void testStoreAndGetMappings() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void testStoreAndRetrieveRegexMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void getAllMappingsShouldListAllEntries() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void testStoreAndRetrieveAddressMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void testStoreAndRetrieveErrorMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void testStoreAndRetrieveWildCardAddressMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void testNonRecursiveMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void testAliasDomainMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void addMappingShouldNotThrowWhenMappingAlreadyExistsWithAnOtherType() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void addForwardMappingShouldStore() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void removeForwardMappingShouldDelete() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void addGroupMappingShouldStore() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void removeGroupMappingShouldDelete() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void addAliasMappingShouldStore() {

    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void removeAliasMappingShouldDelete() {

    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnWhenHasMapping() {

    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnWhenMultipleSourceMapping() {

    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnWhenHasForwardMapping() {

    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnAliasMappings() {

    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnWhenHasAddressMapping() {

    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldThrowExceptionWhenHasRegexMapping() {

    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldHandleDomainMapping() {

    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldHandleDomainSource() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldHandleDomainSources() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldThrowExceptionWhenHasErrorMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnEmptyWhenMappingDoesNotExist() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void getSourcesForTypeShouldReturnEmptyWhenNoMatchingMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void getSourcesForTypeShouldReturnMatchingMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void getSourcesForTypeShouldNotReturnDuplicatedSources() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void getSourcesForTypeShouldReturnSortedStream() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void getMappingsForTypeShouldReturnEmptyWhenNoMatchingMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void getMappingsForTypeShouldReturnMatchingMapping() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void getMappingsForTypeShouldNotReturnDuplicatedDestinations() {
    }

    @Test
    @Ignore("XMLRecipientRewriteTable is read only")
    public void getMappingsForTypeShouldReturnSortedStream() {
    }
}
