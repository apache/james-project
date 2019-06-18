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

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.api.SameSourceAndDestinationException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.DomainAliasResponse;
import org.apache.james.webadmin.service.DomainAliasService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

@Api(tags = "Domains")
@Path(DomainsRoutes.DOMAINS)
@Produces("application/json")
public class DomainsRoutes implements Routes {

    public static final String DOMAINS = "/domains";
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainsRoutes.class);
    private static final String DOMAIN_NAME = ":domainName";
    private static final String SOURCE_DOMAIN = ":sourceDomain";
    private static final String DESTINATION_DOMAIN = ":destinationDomain";
    private static final String SPECIFIC_DOMAIN = DOMAINS + SEPARATOR + DOMAIN_NAME;
    private static final String ALIASES = "aliases";
    private static final String DOMAIN_ALIASES = SPECIFIC_DOMAIN + SEPARATOR + ALIASES;
    private static final String SPECIFIC_ALIAS = DOMAINS + SEPARATOR + DESTINATION_DOMAIN + SEPARATOR + ALIASES + SEPARATOR + SOURCE_DOMAIN;
    private static final int MAXIMUM_DOMAIN_SIZE = 256;

    private final DomainList domainList;
    private final DomainAliasService domainAliasService;
    private final JsonTransformer jsonTransformer;
    private Service service;

    @Inject
    DomainsRoutes(DomainList domainList, DomainAliasService domainAliasService, JsonTransformer jsonTransformer) {
        this.domainList = domainList;
        this.domainAliasService = domainAliasService;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return DOMAINS;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        // Domain endpoints
        defineGetDomains();
        defineDomainExists();
        defineAddDomain();
        defineDeleteDomain();

        // Domain aliases endpoints
        defineListAliases(service);
        defineAddAlias(service);
        defineRemoveAlias(service);
    }

    @DELETE
    @Path("/{domainName}")
    @ApiOperation(value = "Deleting a domain")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "domainName", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. Domain is removed."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteDomain() {
        service.delete(SPECIFIC_DOMAIN, this::removeDomain);
    }

    @PUT
    @Path("/{domainName}")
    @ApiOperation(value = "Creating new domain")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "domainName", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. New domain is created."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid request for domain creation"),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                message = "Internal server error - Something went bad on the server side.")
    })
    public void defineAddDomain() {
        service.put(SPECIFIC_DOMAIN, this::addDomain);
    }

    @GET
    @Path("/{domainName}")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "domainName", paramType = "path")
    })
    @ApiOperation(value = "Testing existence of a domain.")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The domain exists", response = String.class),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The domain does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDomainExists() {
        service.get(SPECIFIC_DOMAIN, this::exists);
    }

    @GET
    @ApiOperation(value = "Getting all domains")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK", response = List.class),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetDomains() {
        service.get(DOMAINS,
            (request, response) ->
                domainList.getDomains().stream().map(Domain::name).collect(Collectors.toList()),
            jsonTransformer);
    }

    @GET
    @Path("/{domainName}/aliases")
    @ApiOperation(value = "Getting all aliases for a domain")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "domainName", paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The domain does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public void defineListAliases(Service service) {
        service.get(DOMAIN_ALIASES, this::listDomainAliases, jsonTransformer);
    }

    @DELETE
    @Path("/{destinationDomain}/aliases/{sourceDomain}")
    @ApiOperation(value = "Remove an alias for a specific domain")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "sourceDomain", paramType = "path"),
        @ApiImplicitParam(required = true, dataType = "string", name = "destinationDomain", paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The domain does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public void defineRemoveAlias(Service service) {
        service.delete(SPECIFIC_ALIAS, this::removeDomainAlias, jsonTransformer);
    }

    @PUT
    @Path("/{destinationDomain}/aliases/{sourceDomain}")
    @ApiOperation(value = "Add an alias for a specific domain")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "sourceDomain", paramType = "path"),
        @ApiImplicitParam(required = true, dataType = "string", name = "destinationDomain", paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The domain does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public void defineAddAlias(Service service) {
        service.put(SPECIFIC_ALIAS, this::addDomainAlias, jsonTransformer);
    }

    private String removeDomain(Request request, Response response) throws RecipientRewriteTableException {
        try {
            Domain domain = checkValidDomain(request.params(DOMAIN_NAME));
            domainList.removeDomain(domain);

            domainAliasService.removeCorrespondingDomainAliases(domain);
        } catch (DomainListException e) {
            LOGGER.info("{} did not exists", request.params(DOMAIN_NAME));
        }
        return Responses.returnNoContent(response);
    }

    private String addDomain(Request request, Response response) {
        Domain domain = checkValidDomain(request.params(DOMAIN_NAME));
        try {
            addDomain(domain);
            return Responses.returnNoContent(response);
        } catch (DomainListException e) {
            LOGGER.info("{} already exists", domain);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NO_CONTENT_204)
                .type(ErrorType.INVALID_ARGUMENT)
                .message(domain.name() + " already exists")
                .cause(e)
                .haltError();
        } catch (IllegalArgumentException e) {
            LOGGER.info("Invalid request for domain creation");
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid request for domain creation " + domain.name())
                .cause(e)
                .haltError();
        }
    }

    private Domain checkValidDomain(String domainName) {
        try {
            return Domain.of(domainName);
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid request for domain creation " + domainName)
                .cause(e)
                .haltError();
        }
    }

    private void addDomain(Domain domain) throws DomainListException {
        Preconditions.checkArgument(domain.name().length() < MAXIMUM_DOMAIN_SIZE);
        domainList.addDomain(domain);
    }

    private String exists(Request request, Response response) throws DomainListException {
        Domain domain = checkValidDomain(request.params(DOMAIN_NAME));

        if (!domainList.containsDomain(domain)) {
            throw domainNotFound(domain);
        } else {
            return Responses.returnNoContent(response);
        }
    }

    private ImmutableSet<DomainAliasResponse> listDomainAliases(Request request, Response response) throws DomainListException, RecipientRewriteTableException {
        Domain domain = checkValidDomain(request.params(DOMAIN_NAME));

        if (!domainAliasService.hasAliases(domain)) {
            throw domainHasNoAliases(domain);
        } else {
            return domainAliasService.listDomainAliases(domain);
        }
    }

    private String addDomainAlias(Request request, Response response) throws DomainListException, RecipientRewriteTableException {
        Domain sourceDomain = checkValidDomain(request.params(SOURCE_DOMAIN));
        Domain destinationDomain = checkValidDomain(request.params(DESTINATION_DOMAIN));

        try {
            domainAliasService.addDomainAlias(sourceDomain, destinationDomain);
            return Responses.returnNoContent(response);
        } catch (DomainAliasService.DomainNotFound e) {
            throw domainNotFound(e.getDomain());
        } catch (SameSourceAndDestinationException e) {
            throw sameSourceAndDestination(sourceDomain);
        }
    }

    private String removeDomainAlias(Request request, Response response) throws DomainListException, RecipientRewriteTableException {
        Domain sourceDomain = checkValidDomain(request.params(SOURCE_DOMAIN));
        Domain destinationDomain = checkValidDomain(request.params(DESTINATION_DOMAIN));

        try {
            domainAliasService.removeDomainAlias(sourceDomain, destinationDomain);
            return Responses.returnNoContent(response);
        } catch (DomainAliasService.DomainNotFound e) {
            throw domainNotFound(e.getDomain());
        } catch (SameSourceAndDestinationException e) {
            throw sameSourceAndDestination(sourceDomain);
        }
    }

    private HaltException domainNotFound(Domain domain) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorType.INVALID_ARGUMENT)
            .message("The domain list does not contain: " + domain.name())
            .haltError();
    }

    private HaltException domainHasNoAliases(Domain domain) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorType.INVALID_ARGUMENT)
            .message("The following domain is not in the domain list and has no registered local aliases: " + domain.name())
            .haltError();
    }

    private HaltException sameSourceAndDestination(Domain sameDomain) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorType.INVALID_ARGUMENT)
            .message("Source domain and destination domain can not have same value(" + sameDomain.name() + ")")
            .haltError();
    }
}
