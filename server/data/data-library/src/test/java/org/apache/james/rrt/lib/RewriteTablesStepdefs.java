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

import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class RewriteTablesStepdefs {

    public AbstractRecipientRewriteTable rewriteTable;
    private Exception exception;

    @Given("store \"([^\"]*)\" regexp mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeRegexpMappingForUserAtDomain(String regexp, String user, String domain) throws Throwable {
        rewriteTable.addRegexMapping(user, Domain.of(domain), regexp);
    }

    @Given("store an invalid \"([^\"]*)\" regexp mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeInvalidRegexpMappingForUserAtDomain(String regexp, String user, String domain) {
        try {
            rewriteTable.addRegexMapping(user, Domain.of(domain), regexp);
        } catch (RecipientRewriteTableException e) {
            this.exception = e;
        }
    }

    @Given("store \"([^\"]*)\" address mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeAddressMappingForUserAtDomain(String address, String user, String domain) throws Throwable {
        rewriteTable.addAddressMapping(user, Domain.of(domain), address);
    }

    @Given("store \"([^\"]*)\" error mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeErrorMappingForUserAtDomain(String error, String user, String domain) throws Throwable {
        rewriteTable.addErrorMapping(user, Domain.of(domain), error);
    }

    @Given("store \"([^\"]*)\" address mapping as wildcard for domain \"([^\"]*)\"")
    public void storeAddressMappingAsWildcardAtDomain(String address, String domain) throws Throwable {
        storeAddressMappingForUserAtDomain(address, RecipientRewriteTable.WILDCARD, domain);
    }

    @Given("store \"([^\"]*)\" alias domain mapping for domain \"([^\"]*)\"")
    public void storeAliasDomainMappingForDomain(String aliasDomain, String domain) throws Throwable {
        rewriteTable.addAliasDomainMapping(Domain.of(aliasDomain), Domain.of(domain));
    }

    @Given("store \"([^\"]*)\" forward mapping for user \"([^\"]*)\" at domain \"([^\"]*)\"")
    public void storeForwardMappingForUserAtDomain(String address, String user, String domain) throws Throwable {
        rewriteTable.addForwardMapping(user, Domain.of(domain), address);
    }

    @Given("recursive mapping is disable")
    public void disableRecursiveMapping() {
        rewriteTable.setRecursiveMapping(false);
    }

    @Given("recursive mapping is enable")
    public void enableRecursiveMapping() {
        rewriteTable.setRecursiveMapping(true);
    }

    @When("user \"([^\"]*)\" at domain \"([^\"]*)\" removes a regexp mapping \"([^\"]*)\"")
    public void userAtDomainRemovesRegexpMapping(String user, String domain, String regexp) throws Throwable {
        rewriteTable.removeRegexMapping(user, Domain.of(domain), regexp);
    }

    @When("user \"([^\"]*)\" at domain \"([^\"]*)\" removes a address mapping \"([^\"]*)\"")
    public void userAtDomainRemovesAddressMapping(String user, String domain, String address) throws Throwable {
        rewriteTable.removeAddressMapping(user, Domain.of(domain), address);
    }

    @When("user \"([^\"]*)\" at domain \"([^\"]*)\" removes a error mapping \"([^\"]*)\"")
    public void userAtDomainRemovesErrorMapping(String user, String domain, String error) throws Throwable {
        rewriteTable.removeErrorMapping(user, Domain.of(domain), error);
    }

    @When("user \"([^\"]*)\" at domain \"([^\"]*)\" removes a forward mapping \"([^\"]*)\"")
    public void userAtDomainRemovesForwardMapping(String user, String domain, String address) throws Throwable {
        rewriteTable.removeForwardMapping(user, Domain.of(domain), address);
    }

    @When("wildcard address mapping \"([^\"]*)\" at domain \"([^\"]*)\" is removed")
    public void removeWildcardAddressMappingAtDomain(String address, String domain) throws Throwable {
        userAtDomainRemovesAddressMapping(RecipientRewriteTable.WILDCARD, domain, address);
    }

    @When("alias domain mapping \"([^\"]*)\" for \"([^\"]*)\" domain is removed")
    public void removeAliasDomainMappingForDomain(String aliasdomain, String domain) throws Throwable {
        rewriteTable.removeAliasDomainMapping(Domain.of(aliasdomain), Domain.of(domain));
    }

    @Then("mappings should be empty")
    public void assertMappingsIsEmpty() throws Throwable {
        assertThat(rewriteTable.getAllMappings()).isNullOrEmpty();
    }

    @Then("mappings for user \"([^\"]*)\" at domain \"([^\"]*)\" should be empty")
    public void assertMappingsIsEmpty(String user, String domain) throws Throwable {
        assertThat(rewriteTable.getMappings(user, Domain.of(domain))).isNullOrEmpty();
    }

    @Then("mappings for user \"([^\"]*)\" at domain \"([^\"]*)\" should contains only \"([^\"]*)\"")
    public void assertMappingsForUser(String user, String domain, List<String> mappings) throws Throwable {
        assertThat(rewriteTable.getMappings(user, Domain.of(domain)).asStrings()).containsOnlyElementsOf(mappings);
    }

    @Then("a \"([^\"]*)\" exception should have been thrown")
    public void assertException(String exceptionClass) throws Throwable {
        assertThat(exception.getClass().getSimpleName()).isEqualTo(exceptionClass);
    }

    @Then("retrieving mappings for user \"([^\"]*)\" at domain \"([^\"]*)\" should raise an ErrorMappingException with message \"([^\"]*)\"")
    public void retrievingMappingsForUserAtDomainShouldRaiseAnException(String user, String domain, String message) throws Exception {
        assertThatThrownBy(() -> rewriteTable.getMappings(user, Domain.of(domain)))
            .isInstanceOf(ErrorMappingException.class)
            .hasMessage(message);
    }
}
