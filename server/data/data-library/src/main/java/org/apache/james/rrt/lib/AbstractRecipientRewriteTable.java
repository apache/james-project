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

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.Domain;
import org.apache.james.core.User;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public abstract class AbstractRecipientRewriteTable implements RecipientRewriteTable, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRecipientRewriteTable.class);

    // The maximum mappings which will process before throwing exception
    private int mappingLimit = 10;

    private boolean recursive = true;

    private DomainList domainList;

    @Inject
    public void setDomainList(DomainList domainList) {
        this.domainList = domainList;
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        setRecursiveMapping(config.getBoolean("recursiveMapping", true));
        try {
            setMappingLimit(config.getInt("mappingLimit", 10));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(e.getMessage());
        }
        doConfigure(config);
    }

    /**
     * Override to handle config
     */
    protected void doConfigure(HierarchicalConfiguration conf) throws ConfigurationException {
    }

    public void setRecursiveMapping(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * Set the mappingLimit
     * 
     * @param mappingLimit
     *            the mappingLimit
     * @throws IllegalArgumentException
     *             get thrown if mappingLimit smaller then 1 is used
     */
    public void setMappingLimit(int mappingLimit) throws IllegalArgumentException {
        if (mappingLimit < 1) {
            throw new IllegalArgumentException("The minimum mappingLimit is 1");
        }
        this.mappingLimit = mappingLimit;
    }

    @Override
    public Mappings getMappings(String user, Domain domain) throws ErrorMappingException, RecipientRewriteTableException {
        return getMappings(User.fromLocalPartWithDomain(user, domain), mappingLimit);
    }

    private Mappings getMappings(User user, int mappingLimit) throws ErrorMappingException, RecipientRewriteTableException {

        // We have to much mappings throw ErrorMappingException to avoid
        // infinity loop
        if (mappingLimit == 0) {
            throw new ErrorMappingException("554 Too many mappings to process");
        }

        Mappings targetMappings = mapAddress(user.getLocalPart(), user.getDomainPart().get());


        try {
            return MappingsImpl.fromMappings(
                targetMappings.asStream()
                    .flatMap(Throwing.function((Mapping target) -> convertAndRecurseMapping(user, target, mappingLimit)).sneakyThrow()));
        } catch (SkipMappingProcessingException e) {
            return MappingsImpl.empty();
        }
    }

    private Stream<Mapping> convertAndRecurseMapping(User originalUser, Mapping associatedMapping, int remainingLoops) throws ErrorMappingException, RecipientRewriteTableException, SkipMappingProcessingException, AddressException {

        Function<User, Stream<Mapping>> convertAndRecurseMapping =
            Throwing
                .function((User rewrittenUser) -> convertAndRecurseMapping(associatedMapping, originalUser, rewrittenUser, remainingLoops))
                .sneakyThrow();

        return associatedMapping.rewriteUser(originalUser)
            .map(rewrittenUser -> rewrittenUser.withDefaultDomainFromUser(originalUser))
            .map(convertAndRecurseMapping)
            .orElse(Stream.empty());
    }

    private Stream<Mapping> convertAndRecurseMapping(Mapping mapping, User originalUser, User rewrittenUser, int remainingLoops) throws ErrorMappingException, RecipientRewriteTableException {
        LOGGER.debug("Valid virtual user mapping {} to {}", originalUser, rewrittenUser);

        Stream<Mapping> nonRecursiveResult = Stream.of(toMapping(rewrittenUser, mapping.getType()));
        if (!recursive) {
            return nonRecursiveResult;
        }

        // Check if the returned mapping is the same as the input. If so we need to handle identity to avoid loops.
        if (originalUser.equals(rewrittenUser)) {
            return mapping.handleIdentity(nonRecursiveResult);
        } else {
            return recurseMapping(nonRecursiveResult, rewrittenUser, remainingLoops);
        }
    }

    private Stream<Mapping> recurseMapping(Stream<Mapping> nonRecursiveResult, User targetUser, int remainingLoops) throws ErrorMappingException, RecipientRewriteTableException {
        Mappings childMappings = getMappings(targetUser, remainingLoops - 1);

        if (childMappings.isEmpty()) {
            return nonRecursiveResult;
        } else {
            return childMappings.asStream();
        }
    }

    private Mapping toMapping(User rewrittenUser, Type type) {
        switch (type) {
            case Forward:
            case Group:
                return Mapping.of(type, rewrittenUser.asString());
            case Regex:
            case Domain:
            case Error:
            case Address:
                return Mapping.address(rewrittenUser.asString());
        }
        throw new IllegalArgumentException("unhandled enum type");
    }

    @Override
    public void addRegexMapping(MappingSource source, String regex) throws RecipientRewriteTableException {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new RecipientRewriteTableException("Invalid regex: " + regex, e);
        }

        Mapping mapping = Mapping.regex(regex);
        checkDuplicateMapping(source, mapping);
        LOGGER.info("Add regex mapping => {} for source {}", regex, source.asString());
        addMapping(source, mapping);
    }

    @Override
    public void removeRegexMapping(MappingSource source, String regex) throws RecipientRewriteTableException {
        LOGGER.info("Remove regex mapping => {} for source: {}", regex, source.asString());
        removeMapping(source, Mapping.regex(regex));
    }

    @Override
    public void addAddressMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        Mapping mapping = Mapping.address(address)
            .appendDomainFromThrowingSupplierIfNone(this::defaultDomain);

        checkHasValidAddress(mapping);
        checkDuplicateMapping(source, mapping);

        LOGGER.info("Add address mapping => {} for source: {}", mapping, source.asString());
        addMapping(source, mapping);
    }

    private Domain defaultDomain() throws RecipientRewriteTableException {
        try {
            return domainList.getDefaultDomain();
        } catch (DomainListException e) {
            throw new RecipientRewriteTableException("Unable to retrieve default domain", e);
        }
    }

    private void checkHasValidAddress(Mapping mapping) throws RecipientRewriteTableException {
        if (!mapping.asMailAddress().isPresent()) {
            throw new RecipientRewriteTableException("Invalid emailAddress: " + mapping);
        }
    }

    @Override
    public void removeAddressMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        Mapping mapping = Mapping.address(address)
            .appendDomainFromThrowingSupplierIfNone(this::defaultDomain);

        LOGGER.info("Remove address mapping => {} for source: {}", mapping, source.asString());
        removeMapping(source, mapping);
    }

    @Override
    public void addErrorMapping(MappingSource source, String error) throws RecipientRewriteTableException {
        Mapping mapping = Mapping.error(error);

        checkDuplicateMapping(source, mapping);
        LOGGER.info("Add error mapping => {} for source: {}", error, source.asString());
        addMapping(source, mapping);

    }

    @Override
    public void removeErrorMapping(MappingSource source, String error) throws RecipientRewriteTableException {
        LOGGER.info("Remove error mapping => {} for source: {}", error, source.asString());
        removeMapping(source, Mapping.error(error));
    }

    @Override
    public void addAliasDomainMapping(MappingSource source, Domain realDomain) throws RecipientRewriteTableException {
        LOGGER.info("Add domain mapping: {} => {}", source.asDomain().map(Domain::asString).orElse("null"), realDomain);
        addMapping(source, Mapping.domain(realDomain));
    }

    @Override
    public void removeAliasDomainMapping(MappingSource source, Domain realDomain) throws RecipientRewriteTableException {
        LOGGER.info("Remove domain mapping: {} => {}", source.asDomain().map(Domain::asString).orElse("null"), realDomain);
        removeMapping(source, Mapping.domain(realDomain));
    }

    @Override
    public void addForwardMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        Mapping mapping = Mapping.forward(address)
            .appendDomainFromThrowingSupplierIfNone(this::defaultDomain);

        checkHasValidAddress(mapping);
        checkDuplicateMapping(source, mapping);

        LOGGER.info("Add forward mapping => {} for source: {}", mapping, source.asString());
        addMapping(source, mapping);
    }

    @Override
    public void removeForwardMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        Mapping mapping = Mapping.forward(address)
            .appendDomainFromThrowingSupplierIfNone(this::defaultDomain);

        LOGGER.info("Remove forward mapping => {} for source: {}", mapping, source.asString());
        removeMapping(source, mapping);
    }

    @Override
    public void addGroupMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        Mapping mapping = Mapping.group(address)
            .appendDomainFromThrowingSupplierIfNone(this::defaultDomain);

        checkHasValidAddress(mapping);
        checkDuplicateMapping(source, mapping);

        LOGGER.info("Add forward mapping => {} for source: {}", mapping, source.asString());
        addMapping(source, mapping);
    }

    @Override
    public void removeGroupMapping(MappingSource source, String address) throws RecipientRewriteTableException {
        Mapping mapping = Mapping.group(address)
            .appendDomainFromThrowingSupplierIfNone(this::defaultDomain);

        LOGGER.info("Remove forward mapping => {} for source: {}", mapping, source.asString());
        removeMapping(source, mapping);
    }

    /**
     * Return a Map which holds all Mappings
     * 
     * @return Map
     */
    public abstract Map<MappingSource, Mappings> getAllMappings() throws RecipientRewriteTableException;

    /**
     * This method must return stored Mappings for the given user.
     * It must never return null but throw RecipientRewriteTableException on errors and return an empty Mappings
     * object if no mapping is found.
     */
    protected abstract Mappings mapAddress(String user, Domain domain) throws RecipientRewriteTableException;

    private void checkDuplicateMapping(MappingSource source, Mapping mapping) throws RecipientRewriteTableException {
        Mappings mappings = getUserDomainMappings(source);
        if (mappings != null && mappings.contains(mapping)) {
            throw new RecipientRewriteTableException("Mapping " + mapping + " for " + source.asString() + " already exist!");
        }
    }

}
