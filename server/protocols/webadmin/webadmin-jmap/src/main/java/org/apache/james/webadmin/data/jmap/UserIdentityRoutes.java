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

package org.apache.james.webadmin.data.jmap;

import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.apache.james.webadmin.data.jmap.dto.UserIdentity.UserIdentityUpsert;
import static spark.Spark.halt;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.identity.IdentityNotFoundException;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.model.IdentityId;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.data.jmap.dto.UserIdentity;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.ParametersExtractor;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class UserIdentityRoutes implements Routes {
    public static final String USERS = "/users";
    public static final String IDENTITIES = "identities";
    private static final String USER_NAME = ":userName";
    private static final String IDENTITY_ID = ":identityId";
    public static final String USERS_IDENTITY_BASE_PATH = USERS + SEPARATOR + USER_NAME + SEPARATOR + IDENTITIES;

    private Service service;
    private final IdentityRepository identityRepository;
    private final JsonTransformer jsonTransformer;
    private final ObjectMapper jsonDeserialize;

    @Inject
    public UserIdentityRoutes(IdentityRepository identityRepository,
                              JsonTransformer jsonTransformer) {
        this.identityRepository = identityRepository;
        this.jsonTransformer = jsonTransformer;
        this.jsonDeserialize =  new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new GuavaModule());
        this.jsonDeserialize.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String getBasePath() {
        return USERS;
    }

    @Override
    public void define(Service service) {
        this.service = service;
        getUserIdentities();
        createUserIdentity();
        updateUserIdentity();
    }

    public void getUserIdentities() {
        service.get(USERS_IDENTITY_BASE_PATH, this::listIdentities, jsonTransformer);
    }

    public void createUserIdentity() {
        service.post(USERS_IDENTITY_BASE_PATH, this::createIdentity);
    }

    public void updateUserIdentity() {
        service.put(USERS_IDENTITY_BASE_PATH + SEPARATOR + IDENTITY_ID, this::updateIdentity);
    }

    private List<UserIdentity> listIdentities(Request request, Response response) {
        Username username = extractUsername(request);
        Optional<Boolean> defaultFilter = ParametersExtractor.extractBoolean(request, "default");

        List<UserIdentity> identities = Flux.from(identityRepository.list(username))
            .map(UserIdentity::from)
            .collectList()
            .block();

        return defaultFilter
            .filter(FunctionalUtils.identityPredicate())
            .map(queryDefault -> getDefaultIdentity(identities)
                .map(List::of)
                .orElseThrow(() -> throw404("Default identity can not be found")))
            .orElse(identities);
    }

    private HaltException createIdentity(Request request, Response response) {
        Username username = extractUsername(request);
        try {
            UserIdentityUpsert creationRequest = jsonDeserialize.readValue(request.body(), UserIdentityUpsert.class);
            Mono.from(identityRepository.save(username, creationRequest.asCreationRequest())).block();
            return halt(HttpStatus.CREATED_201);
        } catch (AddressException | JsonProcessingException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("JSON payload of the request is not valid")
                .cause(e)
                .haltError();
        }
    }

    private HaltException updateIdentity(Request request, Response response) {
        Username username = extractUsername(request);
        IdentityId identityId = Optional.ofNullable(request.params(IDENTITY_ID))
            .map(UUID::fromString)
            .map(IdentityId::new)
            .orElseThrow(() -> new IllegalArgumentException("Can not parse identityId"));
        try {
            UserIdentityUpsert updateRequest = jsonDeserialize.readValue(request.body(), UserIdentityUpsert.class);
            Mono.from(identityRepository.update(username, identityId, updateRequest.asUpdateRequest())).block();
            return halt(HttpStatus.NO_CONTENT_204);
        } catch (JsonProcessingException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("JSON payload of the request is not valid")
                .cause(e)
                .haltError();
        } catch (IdentityNotFoundException notFoundException) {
            throw throw404(String.format("IdentityId '%s' can not be found", identityId.id().toString()));
        }
    }

    private Optional<UserIdentity> getDefaultIdentity(List<UserIdentity> identities) {
        return identities.stream()
            .filter(UserIdentity::getMayDelete)
            .min(Comparator.comparing(UserIdentity::getSortOrder));
    }

    private HaltException throw404(String message) {
        throw ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .message(message)
            .haltError();
    }

    private Username extractUsername(Request request) {
        return Username.of(request.params(USER_NAME));
    }

}
