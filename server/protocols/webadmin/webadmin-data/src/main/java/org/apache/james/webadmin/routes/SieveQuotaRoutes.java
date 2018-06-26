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

import static org.apache.james.webadmin.Constants.EMPTY_BODY;
import static org.apache.james.webadmin.Constants.SEPARATOR;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.sieverepository.api.SieveQuotaRepository;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Service;

@Api(tags = "SieveQuota")
@Path(SieveQuotaRoutes.ROOT_PATH)
@Produces("application/json")
public class SieveQuotaRoutes implements Routes {

    static final String ROOT_PATH = "/sieve/quota";
    public static final String DEFAULT_QUOTA_PATH = ROOT_PATH + SEPARATOR + "default";
    private static final String USER_ID = "userId";
    private static final String USER_SIEVE_QUOTA_PATH = Joiner.on(SEPARATOR).join(ROOT_PATH, "users", ":" + USER_ID);
    private static final String REQUESTED_SIZE = "requestedSize";
    private static final Logger LOGGER = LoggerFactory.getLogger(SieveQuotaRoutes.class);

    private final SieveQuotaRepository sieveQuotaRepository;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<Long> jsonExtractor;

    @Inject
    public SieveQuotaRoutes(SieveQuotaRepository sieveQuotaRepository, JsonTransformer jsonTransformer) {
        this.sieveQuotaRepository = sieveQuotaRepository;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(Long.class);
    }

    @Override
    public void define(Service service) {
        defineGetGlobalSieveQuota(service);
        defineUpdateGlobalSieveQuota(service);
        defineRemoveGlobalSieveQuota(service);

        defineGetPerUserSieveQuota(service);
        defineUpdatePerUserSieveQuota(service);
        defineRemovePerUserSieveQuota(service);
    }

    @GET
    @ApiOperation(value = "Reading global sieve quota size")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Long.class),
            @ApiResponse(code = 204, message = "Global sieve quota not set."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetGlobalSieveQuota(Service service) {
        service.get(DEFAULT_QUOTA_PATH, (request, response) -> {
            try {
                QuotaSize sieveQuota = sieveQuotaRepository.getDefaultQuota();
                response.status(HttpStatus.OK_200);
                return sieveQuota.asLong();
            } catch (QuotaNotFoundException e) {
                response.status(HttpStatus.NO_CONTENT_204);
                return EMPTY_BODY;
            }
        }, jsonTransformer);
    }

    @PUT
    @ApiOperation(value = "Update global sieve quota size")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "long", name = REQUESTED_SIZE, paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Long.class),
            @ApiResponse(code = 400, message = "The body is not a positive integer."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUpdateGlobalSieveQuota(Service service) {
        service.put(DEFAULT_QUOTA_PATH, (request, response) -> {
            try {
                QuotaSize requestedSize = extractRequestedQuotaSizeFromRequest(request);
                sieveQuotaRepository.setDefaultQuota(requestedSize);
                response.status(HttpStatus.NO_CONTENT_204);
                return Constants.EMPTY_BODY;
            } catch (JsonExtractException e) {
                LOGGER.info("Malformed JSON", e);
                throw ErrorResponder.builder()
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .message("Malformed JSON")
                    .cause(e)
                    .haltError();
            }
        }, jsonTransformer);
    }

    @DELETE
    @ApiOperation(value = "Removes global sieve quota")
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "Global sieve quota removed."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineRemoveGlobalSieveQuota(Service service) {
        service.delete(DEFAULT_QUOTA_PATH, (request, response) -> {
            try {
                sieveQuotaRepository.removeQuota();
            } catch (QuotaNotFoundException e) {
                // Do nothing
            }
            response.status(HttpStatus.NO_CONTENT_204);
            return Constants.EMPTY_BODY;
        });
    }

    @GET
    @Path(value = ROOT_PATH + "/{" + USER_ID + "}")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = USER_ID, paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Long.class),
            @ApiResponse(code = 204, message = "User sieve quota not set."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetPerUserSieveQuota(Service service) {
        service.get(USER_SIEVE_QUOTA_PATH, (request, response) -> {
            User userId = User.fromUsername(request.params(USER_ID));
            try {
                QuotaSize userQuota = sieveQuotaRepository.getQuota(userId);
                response.status(HttpStatus.OK_200);
                return userQuota.asLong();
            } catch (QuotaNotFoundException e) {
                response.status(HttpStatus.NO_CONTENT_204);
                return EMPTY_BODY;
            }
        }, jsonTransformer);
    }

    @PUT
    @Path(value = ROOT_PATH + "/{" + USER_ID + "}")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = USER_ID, paramType = "path"),
            @ApiImplicitParam(required = true, dataType = "long", name = REQUESTED_SIZE, paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "OK", response = Long.class),
            @ApiResponse(code = 400, message = "The body is not a positive integer."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUpdatePerUserSieveQuota(Service service) {
        service.put(USER_SIEVE_QUOTA_PATH, (request, response) -> {
            User userId = User.fromUsername(request.params(USER_ID));
            try {
                QuotaSize requestedSize = extractRequestedQuotaSizeFromRequest(request);
                sieveQuotaRepository.setQuota(userId, requestedSize);
                response.status(HttpStatus.NO_CONTENT_204);
            } catch (JsonExtractException e) {
                LOGGER.info("Malformed JSON", e);
                throw ErrorResponder.builder()
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .message("Malformed JSON")
                    .cause(e)
                    .haltError();
            }
            return Constants.EMPTY_BODY;
        }, jsonTransformer);
    }

    @DELETE
    @Path(value = ROOT_PATH + "/{" + USER_ID + "}")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = USER_ID, paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = 204, message = "User sieve quota removed."),
            @ApiResponse(code = 500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineRemovePerUserSieveQuota(Service service) {
        service.delete(USER_SIEVE_QUOTA_PATH, (request, response) -> {
            User userId = User.fromUsername(request.params(USER_ID));
            try {
                sieveQuotaRepository.removeQuota(userId);
            } catch (QuotaNotFoundException e) {
                // Do nothing
            }
            response.status(HttpStatus.NO_CONTENT_204);
            return Constants.EMPTY_BODY;
        });
    }

    private QuotaSize extractRequestedQuotaSizeFromRequest(Request request) throws JsonExtractException {
        Long requestedSize = jsonExtractor.parse(request.body());
        if (requestedSize < 0) {
            throw ErrorResponder.builder()
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Requested quota size have to be a positive integer")
                .haltError();
        }
        return QuotaSize.size(requestedSize);
    }
}
