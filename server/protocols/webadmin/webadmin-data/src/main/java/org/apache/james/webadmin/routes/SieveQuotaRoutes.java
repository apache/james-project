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

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.sieverepository.api.SieveQuotaRepository;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Joiner;

import spark.Request;
import spark.Service;

public class SieveQuotaRoutes implements Routes {

    public static final String ROOT_PATH = "/sieve/quota";
    public static final String DEFAULT_QUOTA_PATH = ROOT_PATH + SEPARATOR + "default";
    private static final String USER_ID = "userId";
    private static final String USER_SIEVE_QUOTA_PATH = Joiner.on(SEPARATOR).join(ROOT_PATH, "users", ":" + USER_ID);

    private final SieveQuotaRepository sieveQuotaRepository;
    private final UsersRepository usersRepository;
    private final JsonTransformer jsonTransformer;

    @Inject
    public SieveQuotaRoutes(SieveQuotaRepository sieveQuotaRepository, UsersRepository usersRepository, JsonTransformer jsonTransformer) {
        this.sieveQuotaRepository = sieveQuotaRepository;
        this.usersRepository = usersRepository;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
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

    public void defineGetGlobalSieveQuota(Service service) {
        service.get(DEFAULT_QUOTA_PATH, (request, response) -> {
            try {
                QuotaSizeLimit sieveQuota = sieveQuotaRepository.getDefaultQuota();
                response.status(HttpStatus.OK_200);
                return sieveQuota.asLong();
            } catch (QuotaNotFoundException e) {
                return Responses.returnNoContent(response);
            }
        }, jsonTransformer);
    }

    public void defineUpdateGlobalSieveQuota(Service service) {
        service.put(DEFAULT_QUOTA_PATH, (request, response) -> {
            QuotaSizeLimit requestedSize = extractRequestedQuotaSizeFromRequest(request);
            sieveQuotaRepository.setDefaultQuota(requestedSize);
            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }

    public void defineRemoveGlobalSieveQuota(Service service) {
        service.delete(DEFAULT_QUOTA_PATH, (request, response) -> {
            try {
                sieveQuotaRepository.removeQuota();
            } catch (QuotaNotFoundException e) {
                // Do nothing
            }
            return Responses.returnNoContent(response);
        });
    }

    public void defineGetPerUserSieveQuota(Service service) {
        service.get(USER_SIEVE_QUOTA_PATH, (request, response) -> {
            Username userId = getUsername(request.params(USER_ID));
            try {
                QuotaSizeLimit userQuota = sieveQuotaRepository.getQuota(userId);
                response.status(HttpStatus.OK_200);
                return userQuota.asLong();
            } catch (QuotaNotFoundException e) {
                return Responses.returnNoContent(response);
            }
        }, jsonTransformer);
    }

    public void defineUpdatePerUserSieveQuota(Service service) {
        service.put(USER_SIEVE_QUOTA_PATH, (request, response) -> {
            Username userId = getUsername(request.params(USER_ID));
            QuotaSizeLimit requestedSize = extractRequestedQuotaSizeFromRequest(request);
            sieveQuotaRepository.setQuota(userId, requestedSize);
            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }

    public void defineRemovePerUserSieveQuota(Service service) {
        service.delete(USER_SIEVE_QUOTA_PATH, (request, response) -> {
            Username usernameId = getUsername(request.params(USER_ID));
            try {
                sieveQuotaRepository.removeQuota(usernameId);
            } catch (QuotaNotFoundException e) {
                // Do nothing
            }
            return Responses.returnNoContent(response);
        });
    }

    private QuotaSizeLimit extractRequestedQuotaSizeFromRequest(Request request) {
        long requestedSize = extractNumberFromRequestBody(request);
        if (requestedSize < 0) {
            throw ErrorResponder.builder()
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Requested quota size have to be a positive integer")
                .haltError();
        }
        return QuotaSizeLimit.size(requestedSize);
    }

    private long extractNumberFromRequestBody(Request request) {
        String body = request.body();
        try {
            return Long.parseLong(body);
        } catch (NumberFormatException e) {
            throw ErrorResponder.builder()
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("unrecognized integer number '%s'", body)
                .haltError();
        }
    }

    private Username getUsername(String usernameParameter) throws UsersRepositoryException {
        Username username = Username.of(usernameParameter);
        if (!usersRepository.contains(username)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("User %s does not exist", username)
                .haltError();
        }
        return username;
    }
}
