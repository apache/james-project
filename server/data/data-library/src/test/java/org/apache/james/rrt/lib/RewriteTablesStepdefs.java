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

import java.util.List;
import java.util.function.Supplier;

import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTableException;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class RewriteTablesStepdefs {

    private Supplier<AbstractRecipientRewriteTable> recipientRewriteTableSupplier;
    private AbstractRecipientRewriteTable rewriteTable;
    private Exception exception;

    public void setUp(Supplier<AbstractRecipientRewriteTable> recipientRewriteTableSupplier) {
        this.recipientRewriteTableSupplier = recipientRewriteTableSupplier;
        this.rewriteTable = this.recipientRewriteTableSupplier.get();
        this.rewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
    }

    @Given("store \"([^\"]*)\" regexp mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeRegexpMappingForUserAtDomain(String regexp, String user, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.addRegexMapping(source, regexp);
    }

    @Given("store an invalid \"([^\"]*)\" regexp mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeInvalidRegexpMappingForUserAtDomain(String regexp, String user, String domain) {
        try {
            MappingSource source = MappingSource.fromUser(user, domain);
            rewriteTable.addRegexMapping(source, regexp);
        } catch (RecipientRewriteTableException e) {
            this.exception = e;
        }
    }

    @Given("store \"([^\"]*)\" address mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeAddressMappingForUserAtDomain(String address, String user, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        storeAddressMappingForUserAtDomain(address, source);
    }

    private void storeAddressMappingForUserAtDomain(String address, MappingSource source) throws RecipientRewriteTableException {
        rewriteTable.addAddressMapping(source, address);
    }

    @Given("store \"([^\"]*)\" error mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeErrorMappingForUserAtDomain(String error, String user, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.addErrorMapping(source, error);
    }

    @Given("store \"([^\"]*)\" address mapping as wildcard for domain \"([^\"]*)\"")
    public void storeAddressMappingAsWildcardAtDomain(String address, String domain) throws Throwable {
        storeAddressMappingForUserAtDomain(address, MappingSource.fromDomain(Domain.of(domain)));
    }

    @Given("store \"([^\"]*)\" domain mapping for domain \"([^\"]*)\"")
    public void storeDomainMappingForDomain(String aliasDomain, String domain) throws Throwable {
        rewriteTable.addDomainMapping(MappingSource.fromDomain(Domain.of(aliasDomain)), Domain.of(domain));
    }

    @Given("store \"([^\"]*)\" domain alias for domain \"([^\"]*)\"")
    public void storeDomainAliasMappingForDomain(String aliasDomain, String domain) throws Throwable {
        rewriteTable.addDomainAliasMapping(MappingSource.fromDomain(Domain.of(aliasDomain)), Domain.of(domain));
    }

    @Given("store \"([^\"]*)\" forward mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeForwardMappingForUserAtDomain(String address, String user, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.addForwardMapping(source, address);
    }

    @Given("store user \"([^\"]*)\" alias mapping for alias \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeAliasMappingForUserAtDomain(String user, String address, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(address, domain);
        rewriteTable.addAliasMapping(source, user);
    }

    @Given("store \"([^\"]*)\" group mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeGroupMappingForUserAtDomain(String address, String user, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.addGroupMapping(source, address);
    }

    @Given("recursive mapping is disable")
    public void disableRecursiveMapping() {
        this.rewriteTable = this.recipientRewriteTableSupplier.get();
        this.rewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DISABLED);
    }

    @Given("recursive mapping is enable")
    public void enableRecursiveMapping() {
        // default case
    }

    @When("user \"([^\"]*)\" at domain \"([^\"]*)\" removes a regexp mapping \"([^\"]*)\"")
    public void userAtDomainRemovesRegexpMapping(String user, String domain, String regexp) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeRegexMapping(source, regexp);
    }

    @When("user \"([^\"]*)\" at domain \"([^\"]*)\" removes a address mapping \"([^\"]*)\"")
    public void userAtDomainRemovesAddressMapping(String user, String domain, String address) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        userAtDomainRemovesAddressMapping(address, source);
    }

    private void userAtDomainRemovesAddressMapping(String address, MappingSource source) throws RecipientRewriteTableException {
        rewriteTable.removeAddressMapping(source, address);
    }

    @When("user \"([^\"]*)\" at domain \"([^\"]*)\" removes a error mapping \"([^\"]*)\"")
    public void userAtDomainRemovesErrorMapping(String user, String domain, String error) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeErrorMapping(source, error);
    }

    @When("user \"([^\"]*)\" at domain \"([^\"]*)\" removes a forward mapping \"([^\"]*)\"")
    public void userAtDomainRemovesForwardMapping(String user, String domain, String address) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeForwardMapping(source, address);
    }

    @When("alias \"([^\"]*)\" at domain \"([^\"]*)\" removes an alias mapping \"([^\"]*)\"")
    public void userAtDomainRemovesAliasMapping(String user, String domain, String address) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeAliasMapping(source, address);
    }

    @When("user \"([^\"]*)\" at domain \"([^\"]*)\" removes a group mapping \"([^\"]*)\"")
    public void userAtDomainRemovesGroupMapping(String user, String domain, String address) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeGroupMapping(source, address);
    }

    @When("wildcard address mapping \"([^\"]*)\" at domain \"([^\"]*)\" is removed")
    public void removeWildcardAddressMappingAtDomain(String address, String domain) throws Throwable {
        userAtDomainRemovesAddressMapping(address, MappingSource.fromDomain(Domain.of(domain)));
    }

    @When("domain mapping \"([^\"]*)\" for \"([^\"]*)\" domain is removed")
    public void removeDomainMappingForDomain(String aliasdomain, String domain) throws Throwable {
        rewriteTable.removeDomainMapping(MappingSource.fromDomain(Domain.of(aliasdomain)), Domain.of(domain));
    }

    @When("domain alias \"([^\"]*)\" for \"([^\"]*)\" domain is removed")
    public void removeDomainAliasForDomain(String aliasdomain, String domain) throws Throwable {
        rewriteTable.removeDomainAliasMapping(MappingSource.fromDomain(Domain.of(aliasdomain)), Domain.of(domain));
    }

    @Then("mappings should be empty")
    public void assertMappingsIsEmpty() throws Throwable {
        assertThat(rewriteTable.getAllMappings()).isNullOrEmpty();
    }

    @Then("mappings for user \"([^\"]*)\" at domain \"([^\"]*)\" should be empty")
    public void assertMappingsIsEmpty(String user, String domain) throws Throwable {
        assertThat(rewriteTable.getResolvedMappings(user, Domain.of(domain))).isNullOrEmpty();
    }

    @Then("mappings for user \"([^\"]*)\" at domain \"([^\"]*)\" should contain only \"([^\"]*)\"")
    public void assertMappingsForUser(String user, String domain, List<String> mappings) throws Throwable {
        assertThat(rewriteTable.getResolvedMappings(user, Domain.of(domain)).asStrings()).containsOnly(mappings.toArray(new String[0]));
    }

    @Then("mappings for alias \"([^\"]*)\" at domain \"([^\"]*)\" should contain only \"([^\"]*)\"")
    public void assertMappingsForAlias(String alias, String domain, List<String> mappings) throws Throwable {
        assertThat(rewriteTable.getResolvedMappings(alias, Domain.of(domain)).asStrings()).containsOnly(mappings.toArray(new String[0]));
    }

    @Then("a \"([^\"]*)\" exception should have been thrown")
    public void assertException(String exceptionClass) throws Throwable {
        assertThat(exception.getClass().getSimpleName()).isEqualTo(exceptionClass);
    }

    @Then("retrieving mappings for user \"([^\"]*)\" at domain \"([^\"]*)\" should raise an ErrorMappingException with message \"([^\"]*)\"")
    public void retrievingMappingsForUserAtDomainShouldRaiseAnException(String user, String domain, String message) throws Exception {
        assertThatThrownBy(() -> rewriteTable.getResolvedMappings(user, Domain.of(domain)))
            .isInstanceOf(ErrorMappingException.class)
            .hasMessage(message);
    }
}
