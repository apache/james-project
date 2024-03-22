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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.SourceDomainIsNotInDomainListException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class DomainMappingsRoutes implements Routes {
    public static final String DOMAIN_MAPPINGS = "/domainMappings";
    private static final String FROM_DOMAIN = "fromDomain";
    private static final String SPECIFIC_MAPPING = DOMAIN_MAPPINGS + SEPARATOR + ":" + FROM_DOMAIN;

    private final RecipientRewriteTable recipientRewriteTable;
    private final JsonTransformer jsonTransformer;

    @Inject
    @VisibleForTesting
    DomainMappingsRoutes(final RecipientRewriteTable recipientRewriteTable, final JsonTransformer jsonTransformer) {
        this.recipientRewriteTable = recipientRewriteTable;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return DOMAIN_MAPPINGS;
    }

    @Override
    public void define(final Service service) {
        service.get(DOMAIN_MAPPINGS, this::getAllMappings, jsonTransformer);
        service.get(SPECIFIC_MAPPING, this::getMapping, jsonTransformer);
        service.put(SPECIFIC_MAPPING, this::addDomainMapping);
        service.delete(SPECIFIC_MAPPING, this::removeDomainMapping);
    }

    public HaltException addDomainMapping(Request request, Response response) throws RecipientRewriteTableException {
        MappingSource mappingSource = mappingSourceFrom(request);
        Domain destinationDomain = extractDomain(request.body());
        addAliasDomainMapping(mappingSource, destinationDomain);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    private void addAliasDomainMapping(MappingSource source, Domain destinationDomain) throws RecipientRewriteTableException {
        try {
            recipientRewriteTable.addDomainAliasMapping(source, destinationDomain);
        } catch (SourceDomainIsNotInDomainListException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .haltError();
        }
    }

    public HaltException removeDomainMapping(Request request, Response response) throws RecipientRewriteTableException {
        MappingSource mappingSource = mappingSourceFrom(request);
        Domain destinationDomain = extractDomain(request.body());

        recipientRewriteTable.removeDomainMapping(mappingSource, destinationDomain);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    public Map<String, List<String>> getAllMappings(Request request, Response response) throws RecipientRewriteTableException {
        return recipientRewriteTable.getAllMappings()
            .entrySet()
            .stream()
            .filter(mappingsEntry -> mappingsEntry.getValue().contains(Mapping.Type.Domain))
            .collect(ImmutableMap.toImmutableMap(
                mappingsEntry -> mappingsEntry.getKey().getFixedDomain(),
                mappingsEntry -> toDomainList(mappingsEntry.getValue())
            ));
    }

    public List<String> getMapping(Request request, Response response) throws RecipientRewriteTableException {
        MappingSource mappingSource = mappingSourceFrom(request);

        return Optional.of(recipientRewriteTable.getStoredMappings(mappingSource).select(Mapping.Type.Domain))
            .filter(mappings -> mappings.contains(Mapping.Type.Domain))
            .map(this::toDomainList)
            .orElseThrow(() -> ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message(String.format("Cannot find mappings for %s", mappingSource.getFixedDomain()))
                .haltError());
    }

    private MappingSource mappingSourceFrom(final Request request) {
        Domain fromDomain = extractDomain(request.params(FROM_DOMAIN));
        return MappingSource.fromDomain(fromDomain);
    }

    private Domain extractDomain(String domainAsString) {
        try {
            return Domain.of(domainAsString.trim());
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(String.format("The domain %s is invalid.", domainAsString))
                .cause(e)
                .haltError();
        }
    }

    private List<String> toDomainList(Mappings mappings) {
        return mappings
            .select(Mapping.Type.Domain)
            .asStream()
            .map(Mapping::asString)
            .map(Mapping.Type.Domain::withoutPrefix)
            .collect(ImmutableList.toImmutableList());
    }
}
