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

import com.github.fge.lambdas.consumers.ThrowingBiConsumer;
import io.swagger.annotations.*;
import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.github.steveash.guavate.Guavate.toImmutableList;
import static com.github.steveash.guavate.Guavate.toImmutableMap;
import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.routes.DomainMappingsRoutes.DOMAIN_MAPPINGS;
import static spark.Spark.halt;

@Api(tags = "Domain Mappings")
@Path(DOMAIN_MAPPINGS)
@Produces("application/json")
public class DomainMappingsRoutes implements Routes {
    static final String DOMAIN_MAPPINGS = "/domainMappings";
    private static final String FROM_DOMAIN = "fromDomain";
    private static final String SPECIFIC_MAPPING_PATH = SEPARATOR + "/{" + FROM_DOMAIN + "}";
    private static final String SPECIFIC_MAPPING = DOMAIN_MAPPINGS + SEPARATOR + ":" + FROM_DOMAIN;

    private final RecipientRewriteTable recipientRewriteTable;
    private final JsonTransformer jsonTransformer;

    @Inject
    public DomainMappingsRoutes(final RecipientRewriteTable recipientRewriteTable, final JsonTransformer jsonTransformer) {
        this.recipientRewriteTable = recipientRewriteTable;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public void define(final Service service) {
        service.get(DOMAIN_MAPPINGS, this::get, jsonTransformer);
        service.put(SPECIFIC_MAPPING, this::addDomainMapping);
        service.delete(SPECIFIC_MAPPING, this::removeDomainMapping);
    }

    @PUT
    @Path(SPECIFIC_MAPPING_PATH)
    @ApiOperation(value = "Creating domain mapping between source and destination domains.")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = FROM_DOMAIN, paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "Ok"),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Domain name is invalid"),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                    message = "Internal server error - Something went bad on the server side.")
    })
    public HaltException addDomainMapping(Request request, Response response) {
        doMapping(request, recipientRewriteTable::addAliasDomainMapping);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    @DELETE
    @Path(SPECIFIC_MAPPING_PATH)
    @ApiOperation(value = "Removes domain mapping between source and destination domains.")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = FROM_DOMAIN, paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "Ok"),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Domain name is invalid"),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                    message = "Internal server error - Something went bad on the server side.")
    })
    public HaltException removeDomainMapping(Request request, Response response) {
        doMapping(request, recipientRewriteTable::removeAliasDomainMapping);
        return halt(HttpStatus.NO_CONTENT_204);
    }

    @GET
    @Path(DOMAIN_MAPPINGS)
    @ApiOperation(value = "Lists all domain mappings.")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "Domain mappings.", responseContainer = "Map"),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                    message = "Internal server error - Something went bad on the server side.")
    })
    public Map<String, List<String>> get(Request request, Response response) throws RecipientRewriteTableException {
        return recipientRewriteTable.getAllMappings()
                .entrySet()
                .stream()
                .collect(toImmutableMap(e -> e.getKey().getFixedDomain(),
                        e -> e.getValue()
                                .select(Mapping.Type.Domain)
                                .asStream()
                                .map(Mapping::asString)
                                .map(Mapping.Type.Domain::withoutPrefix)
                                .collect(toImmutableList())
                ));
    }

    private void doMapping(final Request request, final ThrowingBiConsumer<MappingSource, Domain> op) {
        MappingSource fromDomain = createDomainOrThrow()
                .andThen(MappingSource::fromDomain)
                .apply(request.params(FROM_DOMAIN));

        Domain toDomain = createDomainOrThrow().apply(request.body());

        op.accept(fromDomain, toDomain);
    }

    private Function<String, Domain> createDomainOrThrow() {
        return candidate -> {
            try {
                return Domain.of(candidate.trim());
            } catch (IllegalArgumentException e) {
                throw ErrorResponder.builder()
                        .statusCode(HttpStatus.BAD_REQUEST_400)
                        .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                        .message(String.format("The domain %s is invalid.", candidate))
                        .cause(e)
                        .haltError();
            }
        };
    }
}
