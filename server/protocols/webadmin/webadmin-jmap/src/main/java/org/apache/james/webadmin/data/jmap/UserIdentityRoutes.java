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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.data.jmap.dto.UserIdentity;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.ParametersExtractor;
import org.eclipse.jetty.http.HttpStatus;

import reactor.core.publisher.Flux;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class UserIdentityRoutes implements Routes {
    public static final String USERS = "/users";
    public static final String IDENTITIES = "identities";
    private static final String USER_NAME = ":userName";
    private Service service;
    private final IdentityRepository identityRepository;
    private final JsonTransformer jsonTransformer;

    @Inject
    public UserIdentityRoutes(IdentityRepository identityRepository,
                              JsonTransformer jsonTransformer) {
        this.identityRepository = identityRepository;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return USERS;
    }

    @Override
    public void define(Service service) {
        this.service = service;
        getUserIdentities();
    }

    public void getUserIdentities() {
        service.get(USERS + SEPARATOR + USER_NAME + SEPARATOR + IDENTITIES, this::listIdentities, jsonTransformer);
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
