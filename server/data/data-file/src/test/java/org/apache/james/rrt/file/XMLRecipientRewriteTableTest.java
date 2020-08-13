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
import org.apache.james.rrt.lib.RecipientRewriteTableContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class XMLRecipientRewriteTableTest implements RecipientRewriteTableContract {

    final BaseHierarchicalConfiguration defaultConfiguration = new BaseHierarchicalConfiguration();

    AbstractRecipientRewriteTable recipientRewriteTable;

    @BeforeEach
    void setup() throws Exception {
        defaultConfiguration.setListDelimiterHandler(new DisabledListDelimiterHandler());
        setUp();
    }

    @AfterEach
    void teardown() throws Exception {
        tearDown();
    }

    @Override
    public void createRecipientRewriteTable() {
        recipientRewriteTable = new XMLRecipientRewriteTable();
    }

    @Override
    public AbstractRecipientRewriteTable virtualUserTable() {
        return recipientRewriteTable;
    }

    @Test
    @Disabled("addMapping doesn't handle checking for domain existence in this test implementation")
    @Override
    public void addAddressMappingShouldThrowWhenSourceDomainIsNotInDomainList() {
    }

    @Test
    @Disabled("addMapping doesn't handle checking for duplicate in this test implementation")
    @Override
    public void addMappingShouldThrowWhenMappingAlreadyExists() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void testStoreAndGetMappings() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void testStoreAndRetrieveRegexMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void getAllMappingsShouldListAllEntries() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void testStoreAndRetrieveAddressMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void testStoreAndRetrieveErrorMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void testStoreAndRetrieveWildCardAddressMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void testNonRecursiveMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void testAliasDomainMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void addMappingShouldNotThrowWhenMappingAlreadyExistsWithAnOtherType() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void addForwardMappingShouldStore() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void removeForwardMappingShouldDelete() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void addGroupMappingShouldStore() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void removeGroupMappingShouldDelete() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void addAliasMappingShouldStore() {

    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void removeAliasMappingShouldDelete() {

    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnWhenHasMapping() {

    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnWhenMultipleSourceMapping() {

    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnWhenHasForwardMapping() {

    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnAliasMappings() {

    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnWhenHasAddressMapping() {

    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldThrowExceptionWhenHasRegexMapping() {

    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldHandleDomainMapping() {

    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldHandleDomainSource() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldHandleDomainSources() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldThrowExceptionWhenHasErrorMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void listSourcesShouldReturnEmptyWhenMappingDoesNotExist() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void getSourcesForTypeShouldReturnEmptyWhenNoMatchingMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void getSourcesForTypeShouldReturnMatchingMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void getSourcesForTypeShouldNotReturnDuplicatedSources() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void getSourcesForTypeShouldReturnSortedStream() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void getMappingsForTypeShouldReturnEmptyWhenNoMatchingMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void getMappingsForTypeShouldReturnMatchingMapping() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void getMappingsForTypeShouldNotReturnDuplicatedDestinations() {
    }

    @Test
    @Disabled("XMLRecipientRewriteTable is read only")
    public void getMappingsForTypeShouldReturnSortedStream() {
    }
}
