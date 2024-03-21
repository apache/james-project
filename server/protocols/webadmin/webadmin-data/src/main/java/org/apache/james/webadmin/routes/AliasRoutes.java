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

package org.apache.james.webadmin.routes;

import static org.apache.james.webadmin.Constants.SEPARATOR;
import static spark.Spark.halt;

import java.util.Comparator;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.LoopDetectedException;
import org.apache.james.rrt.api.MappingAlreadyExistsException;
import org.apache.james.rrt.api.MappingConflictException;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.SameSourceAndDestinationException;
import org.apache.james.rrt.api.SourceDomainIsNotInDomainListException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.AliasSourcesResponse;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class AliasRoutes implements Routes {

    public static final String ROOT_PATH = "address/aliases";

    private static final String ALIAS_DESTINATION_ADDRESS = "aliasDestinationAddress";
    private static final String ALIAS_ADDRESS_PATH = ROOT_PATH + SEPARATOR + ":" + ALIAS_DESTINATION_ADDRESS;
    private static final String ALIAS_SOURCE_ADDRESS = "aliasSourceAddress";
    private static final String USER_IN_ALIAS_SOURCES_ADDRESSES_PATH = ALIAS_ADDRESS_PATH + SEPARATOR +
        "sources" + SEPARATOR + ":" + ALIAS_SOURCE_ADDRESS;
    private static final String ADDRESS_TYPE = "alias";

    private final DomainList domainList;
    private final JsonTransformer jsonTransformer;
    private final RecipientRewriteTable recipientRewriteTable;

    @Inject
    @VisibleForTesting
    AliasRoutes(RecipientRewriteTable recipientRewriteTable, DomainList domainList, JsonTransformer jsonTransformer) {
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
        this.recipientRewriteTable = recipientRewriteTable;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(ROOT_PATH, this::listAddressesWithAliases, jsonTransformer);
        service.get(ALIAS_ADDRESS_PATH, this::listAliasesOfAddress, jsonTransformer);
        service.put(USER_IN_ALIAS_SOURCES_ADDRESSES_PATH, this::addAlias);
        service.delete(USER_IN_ALIAS_SOURCES_ADDRESSES_PATH, this::deleteAlias);
    }

    public ImmutableSet<String> listAddressesWithAliases(Request request, Response response) throws RecipientRewriteTableException {
        return recipientRewriteTable.getMappingsForType(Mapping.Type.Alias)
            .flatMap(mapping -> mapping.asMailAddress().stream())
            .map(MailAddress::asString)
            .collect(ImmutableSortedSet.toImmutableSortedSet(String::compareTo));
    }

    public HaltException addAlias(Request request, Response response) throws Exception {
        MailAddress aliasSourceAddress = MailAddressParser.parseMailAddress(request.params(ALIAS_SOURCE_ADDRESS), ADDRESS_TYPE);
        MailAddress destinationAddress = MailAddressParser.parseMailAddress(request.params(ALIAS_DESTINATION_ADDRESS), ADDRESS_TYPE);
        ensureDomainIsSupported(destinationAddress.getDomain());
        MappingSource source = MappingSource.fromUser(Username.fromMailAddress(aliasSourceAddress));
        addAlias(source, destinationAddress);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    private void addAlias(MappingSource source, MailAddress aliasSourceAddress) throws RecipientRewriteTableException {
        try {
            recipientRewriteTable.addAliasMapping(source, aliasSourceAddress.asString());
        } catch (MappingAlreadyExistsException e) {
            // ignore
        } catch (SameSourceAndDestinationException | SourceDomainIsNotInDomainListException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .haltError();
        } catch (LoopDetectedException | MappingConflictException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.CONFLICT_409)
                .type(ErrorResponder.ErrorType.WRONG_STATE)
                .message(e.getMessage())
                .haltError();
        }
    }

    private void ensureDomainIsSupported(Domain domain) throws DomainListException {
        if (!domainList.containsDomain(domain)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Domain in the destination is not managed by the DomainList")
                .haltError();
        }
    }

    public HaltException deleteAlias(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress destinationAddress = MailAddressParser.parseMailAddress(request.params(ALIAS_DESTINATION_ADDRESS), ADDRESS_TYPE);
        MailAddress aliasToBeRemoved = MailAddressParser.parseMailAddress(request.params(ALIAS_SOURCE_ADDRESS), ADDRESS_TYPE);
        MappingSource source = MappingSource.fromMailAddress(aliasToBeRemoved);
        recipientRewriteTable.removeAliasMapping(source, destinationAddress.asString());
        return halt(HttpStatus.NO_CONTENT_204);
    }

    public ImmutableSet<AliasSourcesResponse> listAliasesOfAddress(Request request, Response response) throws RecipientRewriteTableException {
        MailAddress destinationAddress = MailAddressParser.parseMailAddress(request.params(ALIAS_DESTINATION_ADDRESS), ADDRESS_TYPE);

        return recipientRewriteTable.listSources(Mapping.alias(destinationAddress.asString()))
            .sorted(Comparator.comparing(MappingSource::asMailAddressString))
            .map(AliasSourcesResponse::new)
            .collect(ImmutableSet.toImmutableSet());
    }
}
