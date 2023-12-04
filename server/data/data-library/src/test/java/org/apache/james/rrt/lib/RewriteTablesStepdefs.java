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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class RewriteTablesStepdefs {

    private Supplier<AbstractRecipientRewriteTable> recipientRewriteTableSupplier;
    private AbstractRecipientRewriteTable rewriteTable;
    private Exception exception;

    public void setUp(Supplier<AbstractRecipientRewriteTable> recipientRewriteTableSupplier) {
        this.recipientRewriteTableSupplier = recipientRewriteTableSupplier;
        this.rewriteTable = this.recipientRewriteTableSupplier.get();
        this.rewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
    }

    @Given("store {string} regexp mapping for user {string} at domain {string}")
    public void storeRegexpMappingForUserAtDomain(String regexp, String user, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.addRegexMapping(source, regexp);
    }

    @Given("store an invalid {string} regexp mapping for user {string} at domain {string}")
    public void storeInvalidRegexpMappingForUserAtDomain(String regexp, String user, String domain) {
        try {
            MappingSource source = MappingSource.fromUser(user, domain);
            rewriteTable.addRegexMapping(source, regexp);
        } catch (RecipientRewriteTableException e) {
            this.exception = e;
        }
    }

    @Given("store {string} address mapping for user {string} at domain {string}")
    public void storeAddressMappingForUserAtDomain(String address, String user, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        storeAddressMappingForUserAtDomain(address, source);
    }

    private void storeAddressMappingForUserAtDomain(String address, MappingSource source) throws RecipientRewriteTableException {
        rewriteTable.addAddressMapping(source, address);
    }

    @Given("store {string} error mapping for user {string} at domain {string}")
    public void storeErrorMappingForUserAtDomain(String error, String user, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.addErrorMapping(source, error);
    }

    @Given("store {string} address mapping as wildcard for domain {string}")
    public void storeAddressMappingAsWildcardAtDomain(String address, String domain) throws Throwable {
        storeAddressMappingForUserAtDomain(address, MappingSource.fromDomain(Domain.of(domain)));
    }

    @Given("store {string} domain mapping for domain {string}")
    public void storeDomainMappingForDomain(String aliasDomain, String domain) throws Throwable {
        rewriteTable.addDomainMapping(MappingSource.fromDomain(Domain.of(aliasDomain)), Domain.of(domain));
    }

    @Given("store {string} domain alias for domain {string}")
    public void storeDomainAliasMappingForDomain(String aliasDomain, String domain) throws Throwable {
        rewriteTable.addDomainAliasMapping(MappingSource.fromDomain(Domain.of(aliasDomain)), Domain.of(domain));
    }

    @Given("store {string} forward mapping for user {string} at domain {string}")
    public void storeForwardMappingForUserAtDomain(String address, String user, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.addForwardMapping(source, address);
    }

    @Given("store user {string} alias mapping for alias {string} at domain {string}")
    public void storeAliasMappingForUserAtDomain(String user, String address, String domain) throws Throwable {
        MappingSource source = MappingSource.fromUser(address, domain);
        rewriteTable.addAliasMapping(source, user);
    }

    @Given("store {string} group mapping for user {string} at domain {string}")
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

    @When("user {string} at domain {string} removes a regexp mapping {string}")
    public void userAtDomainRemovesRegexpMapping(String user, String domain, String regexp) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeRegexMapping(source, regexp);
    }

    @When("user {string} at domain {string} removes a address mapping {string}")
    public void userAtDomainRemovesAddressMapping(String user, String domain, String address) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        userAtDomainRemovesAddressMapping(address, source);
    }

    private void userAtDomainRemovesAddressMapping(String address, MappingSource source) throws RecipientRewriteTableException {
        rewriteTable.removeAddressMapping(source, address);
    }

    @When("user {string} at domain {string} removes a error mapping {string}")
    public void userAtDomainRemovesErrorMapping(String user, String domain, String error) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeErrorMapping(source, error);
    }

    @When("user {string} at domain {string} removes a forward mapping {string}")
    public void userAtDomainRemovesForwardMapping(String user, String domain, String address) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeForwardMapping(source, address);
    }

    @When("alias {string} at domain {string} removes an alias mapping {string}")
    public void userAtDomainRemovesAliasMapping(String user, String domain, String address) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeAliasMapping(source, address);
    }

    @When("user {string} at domain {string} removes a group mapping {string}")
    public void userAtDomainRemovesGroupMapping(String user, String domain, String address) throws Throwable {
        MappingSource source = MappingSource.fromUser(user, domain);
        rewriteTable.removeGroupMapping(source, address);
    }

    @When("wildcard address mapping {string} at domain {string} is removed")
    public void removeWildcardAddressMappingAtDomain(String address, String domain) throws Throwable {
        userAtDomainRemovesAddressMapping(address, MappingSource.fromDomain(Domain.of(domain)));
    }

    @When("domain mapping {string} for {string} domain is removed")
    public void removeDomainMappingForDomain(String aliasdomain, String domain) throws Throwable {
        rewriteTable.removeDomainMapping(MappingSource.fromDomain(Domain.of(aliasdomain)), Domain.of(domain));
    }

    @When("domain alias {string} for {string} domain is removed")
    public void removeDomainAliasForDomain(String aliasdomain, String domain) throws Throwable {
        rewriteTable.removeDomainAliasMapping(MappingSource.fromDomain(Domain.of(aliasdomain)), Domain.of(domain));
    }

    @Then("mappings should be empty")
    public void assertMappingsIsEmpty() throws Throwable {
        assertThat(rewriteTable.getAllMappings()).isNullOrEmpty();
    }

    @Then("mappings for user {string} at domain {string} should be empty")
    public void assertMappingsIsEmpty(String user, String domain) throws Throwable {
        assertThat(rewriteTable.getResolvedMappings(user, Domain.of(domain))).isNullOrEmpty();
    }

    @Then("mappings for user {string} at domain {string} should contain only {string}")
    public void assertMappingsForUser(String user, String domain, String mappings) throws Throwable {
        assertThat(rewriteTable.getResolvedMappings(user, Domain.of(domain)).asStrings()).containsOnly(asList(mappings).toArray(new String[0]));
    }

    @Then("mappings for alias {string} at domain {string} should contain only {string}")
    public void assertMappingsForAlias(String alias, String domain, String mappings) throws Throwable {
        assertThat(rewriteTable.getResolvedMappings(alias, Domain.of(domain)).asStrings()).containsOnly(asList(mappings).toArray(new String[0]));
    }

    private List<String> asList(String value) {
        return Splitter.on(',')
            .trimResults()
            .splitToStream(value)
            .map(s -> {
                if (s.startsWith("\"")) {
                    return s.substring(1);
                }
                return s;
            })
            .map(s -> {
                if (s.endsWith("\"")) {
                    return s.substring(0, s.length() - 1);
                }
                return s;
            })
            .collect(ImmutableList.toImmutableList());
    }

    @Then("a {string} exception should have been thrown")
    public void assertException(String exceptionClass) throws Throwable {
        assertThat(exception.getClass().getSimpleName()).isEqualTo(exceptionClass);
    }

    @Then("retrieving mappings for user {string} at domain {string} should raise an ErrorMappingException with message {string}")
    public void retrievingMappingsForUserAtDomainShouldRaiseAnException(String user, String domain, String message) throws Exception {
        assertThatThrownBy(() -> rewriteTable.getResolvedMappings(user, Domain.of(domain)))
            .isInstanceOf(ErrorMappingException.class)
            .hasMessage(message);
    }
}
