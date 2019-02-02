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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.Domain;
import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.quota.search.Limit;
import org.apache.james.quota.search.Offset;
import org.apache.james.quota.search.QuotaBoundary;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.QuotaDTO;
import org.apache.james.webadmin.dto.QuotaDetailsDTO;
import org.apache.james.webadmin.service.UserQuotaService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.JsonTransformerModule;
import org.apache.james.webadmin.utils.ParametersExtractor;
import org.apache.james.webadmin.validation.Quotas;
import org.eclipse.jetty.http.HttpStatus;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Service;

@Api(tags = "UserQuota")
@Path(UserQuotaRoutes.USERS_QUOTA_ENDPOINT)
@Produces("application/json")
public class UserQuotaRoutes implements Routes {

    private static final String USER = "user";
    private static final String MIN_OCCUPATION_RATIO = "minOccupationRatio";
    private static final String MAX_OCCUPATION_RATIO = "maxOccupationRatio";
    private static final String DOMAIN = "domain";
    public static final String USERS_QUOTA_ENDPOINT = "/quota/users";
    private static final String QUOTA_ENDPOINT = USERS_QUOTA_ENDPOINT + "/:" + USER;
    private static final String COUNT_ENDPOINT = QUOTA_ENDPOINT + "/count";
    private static final String SIZE_ENDPOINT = QUOTA_ENDPOINT + "/size";

    private final UsersRepository usersRepository;
    private final UserQuotaService userQuotaService;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<QuotaDTO> jsonExtractor;
    private Service service;

    @Inject
    public UserQuotaRoutes(UsersRepository usersRepository, UserQuotaService userQuotaService, JsonTransformer jsonTransformer, Set<JsonTransformerModule> modules) {
        this.usersRepository = usersRepository;
        this.userQuotaService = userQuotaService;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(QuotaDTO.class, modules.stream().map(JsonTransformerModule::asJacksonModule).collect(Collectors.toList()));
    }

    @Override
    public String getBasePath() {
        return USERS_QUOTA_ENDPOINT;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        defineGetQuotaCount();
        defineDeleteQuotaCount();
        defineUpdateQuotaCount();

        defineGetQuotaSize();
        defineDeleteQuotaSize();
        defineUpdateQuotaSize();

        defineGetQuota();
        defineUpdateQuota();

        defineGetUsersQuota();
    }

    @PUT
    @ApiOperation(value = "Updating count and size at the same time")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "org.apache.james.webadmin.dto.QuotaDTO", paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The value has been updated."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The body is not a positive integer or not unlimited value (-1)."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.CONFLICT_409, message = "The requested restriction can't be enforced right now."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUpdateQuota() {
        service.put(QUOTA_ENDPOINT, ((request, response) -> {
            User user = checkUserExist(request);
            QuotaDTO quotaDTO = parseQuotaDTO(request);
            userQuotaService.defineQuota(user, quotaDTO);
            response.status(HttpStatus.NO_CONTENT_204);
            return response;
        }));
    }

    @GET
    @ApiOperation(
        value = "Reading count and size at the same time",
        notes = "If there is no limitation for count and/or size, the returned value will be -1"
    )
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = QuotaDetailsDTO.class),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuota() {
        service.get(QUOTA_ENDPOINT, (request, response) -> {
            User user = checkUserExist(request);
            return userQuotaService.getQuota(user);
        }, jsonTransformer);
    }

    @GET
    @ApiOperation(
        value = "Reading count and size at the same time",
        notes = "If there is no limitation for count and/or size, the returned value will be -1"
    )
    @ApiImplicitParams({
        @ApiImplicitParam(
                required = false,
                name = "minOccuptationRatio",
                paramType = "query parameter",
                dataType = "Double",
                example = "?minOccuptationRatio=0.8",
                value = "If present, filter the users with occupation ratio lesser than this value."),
        @ApiImplicitParam(
                required = false,
                name = "maxOccupationRatio",
                paramType = "query parameter",
                dataType = "Double",
                example = "?maxOccupationRatio=0.99",
                value = "If present, filter the users with occupation ratio greater than this value."),
        @ApiImplicitParam(
                required = false,
                paramType = "query parameter",
                name = "limit",
                dataType = "Integer",
                example = "?limit=100",
                value = "If present, fixes the maximal number of key returned in that call. Must be more than zero if specified."),
        @ApiImplicitParam(
                required = false,
                name = "offset",
                paramType = "query parameter",
                dataType = "Integer",
                example = "?offset=100",
                value = "If present, skips the given number of key in the output."),
        @ApiImplicitParam(
                required = false,
                name = "domain",
                paramType = "query parameter",
                dataType = "String",
                example = "?domain=james.org",
                value = "If present, filter the users by this domain.")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = QuotaDetailsDTO.class),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Validation issues with parameters"),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetUsersQuota() {
        service.get(USERS_QUOTA_ENDPOINT, (request, response) -> {
            QuotaQuery quotaQuery = QuotaQuery.builder()
                .lessThan(extractQuotaBoundary(request, MAX_OCCUPATION_RATIO))
                .moreThan(extractQuotaBoundary(request, MIN_OCCUPATION_RATIO))
                .hasDomain(extractDomain(request, DOMAIN))
                .withLimit(extractLimit(request))
                .withOffset(extractOffset(request))
                .build();

            return userQuotaService.getUsersQuota(quotaQuery);
        }, jsonTransformer);
    }

    public Optional<Domain> extractDomain(Request request, String parameterName) {
        return Optional.ofNullable(request.queryParams(parameterName)).map(Domain::of);
    }

    public Optional<QuotaBoundary> extractQuotaBoundary(Request request, String parameterName) {
        return ParametersExtractor.extractPositiveDouble(request, parameterName)
            .map(QuotaBoundary::new);
    }

    public Limit extractLimit(Request request) {
        return ParametersExtractor.extractLimit(request)
            .getLimit()
            .map(Limit::of)
            .orElse(Limit.unlimited());
    }

    public Offset extractOffset(Request request) {
        return Offset.of(ParametersExtractor.extractOffset(request)
            .getOffset());
    }

    @DELETE
    @Path("/size")
    @ApiOperation(value = "Removing per user mail size limitation by updating to unlimited value")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The value is updated to unlimited value."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteQuotaSize() {
        service.delete(SIZE_ENDPOINT, (request, response) -> {
            User user = checkUserExist(request);
            userQuotaService.deleteMaxSizeQuota(user);
            response.status(HttpStatus.NO_CONTENT_204);
            return response;
        });
    }

    @PUT
    @Path("/size")
    @ApiOperation(value = "Updating per user mail size limitation")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "integer", paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The value has been updated."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The body is not a positive integer nor -1."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.CONFLICT_409, message = "The requested restriction can't be enforced right now."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUpdateQuotaSize() {
        service.put(SIZE_ENDPOINT, (request, response) -> {
            User user = checkUserExist(request);
            QuotaSize quotaSize = Quotas.quotaSize(request.body());
            userQuotaService.defineMaxSizeQuota(user, quotaSize);
            response.status(HttpStatus.NO_CONTENT_204);
            return response;
        });
    }

    @GET
    @Path("/size")
    @ApiOperation(value = "Reading per user mail size limitation")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = Long.class),
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "No value defined"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuotaSize() {
        service.get(SIZE_ENDPOINT, (request, response) -> {
            User user = checkUserExist(request);
            Optional<QuotaSize> maxSizeQuota = userQuotaService.getMaxSizeQuota(user);
            if (maxSizeQuota.isPresent()) {
                return maxSizeQuota;
            }
            response.status(HttpStatus.NO_CONTENT_204);
            return null;
        }, jsonTransformer);
    }

    @DELETE
    @Path("/count")
    @ApiOperation(value = "Removing per user mail count limitation by updating to unlimited value")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The value is updated to unlimited value."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineDeleteQuotaCount() {
        service.delete(COUNT_ENDPOINT, (request, response) -> {
            User user = checkUserExist(request);
            userQuotaService.deleteMaxCountQuota(user);
            response.status(HttpStatus.NO_CONTENT_204);
            return response;
        });
    }

    @PUT
    @Path("/count")
    @ApiOperation(value = "Updating per user mail count limitation")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "integer", paramType = "body")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. The value has been updated."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "The body is not a positive integer nor -1."),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.CONFLICT_409, message = "The requested restriction can't be enforced right now."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineUpdateQuotaCount() {
        service.put(COUNT_ENDPOINT, (request, response) -> {
            User user = checkUserExist(request);
            QuotaCount quotaCount = Quotas.quotaCount(request.body());
            userQuotaService.defineMaxCountQuota(user, quotaCount);
            response.status(HttpStatus.NO_CONTENT_204);
            return response;
        });
    }

    @GET
    @Path("/count")
    @ApiOperation(value = "Reading per user mail count limitation")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = Long.class),
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "No value defined"),
            @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The user name does not exist."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetQuotaCount() {
        service.get(COUNT_ENDPOINT, (request, response) -> {
            User user = checkUserExist(request);
            Optional<QuotaCount> maxCountQuota = userQuotaService.getMaxCountQuota(user);
            if (maxCountQuota.isPresent()) {
                return maxCountQuota;
            }
            response.status(HttpStatus.NO_CONTENT_204);
            return null;
        }, jsonTransformer);
    }

    private User checkUserExist(Request request) throws UsersRepositoryException, UnsupportedEncodingException {
        String user = URLDecoder.decode(request.params(USER),
            StandardCharsets.UTF_8.displayName());

        if (!usersRepository.contains(user)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorType.NOT_FOUND)
                .message("User not found")
                .haltError();
        }
        return User.fromUsername(user);
    }

    private QuotaDTO parseQuotaDTO(Request request) {
        try {
            return jsonExtractor.parse(request.body());
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Quota should be positive or unlimited (-1)")
                .cause(e)
                .haltError();
        } catch (JsonExtractException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Malformed JSON input")
                .cause(e)
                .haltError();
        }
    }

}
