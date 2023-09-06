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

import static org.apache.james.webadmin.routes.MailboxesRoutes.TASK_PARAMETER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.RunningOptions;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasTask;
import org.apache.james.quota.search.Limit;
import org.apache.james.quota.search.Offset;
import org.apache.james.quota.search.QuotaBoundary;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.QuotaDTO;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;
import org.apache.james.webadmin.service.UserQuotaService;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.JsonTransformerModule;
import org.apache.james.webadmin.utils.ParametersExtractor;
import org.apache.james.webadmin.utils.Responses;
import org.apache.james.webadmin.validation.QuotaDTOValidator;
import org.apache.james.webadmin.validation.Quotas;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import spark.Request;
import spark.Route;
import spark.Service;

public class UserQuotaRoutes implements Routes {

    public static final String USER_QUOTAS_OPERATIONS_INJECTION_KEY = "userQuotasOperations";

    public static class RecomputeCurrentQuotasRequestToTask extends TaskFromRequestRegistry.TaskRegistration {
        private static final String USERS_PER_SECOND = "usersPerSecond";
        private static final String QUOTA_COMPONENT = "quotaComponent";
        private static final ImmutableMap<String, QuotaComponent> QUOTA_COMPONENT_MAP = ImmutableMap.of(QuotaComponent.MAILBOX.getValue(), QuotaComponent.MAILBOX,
                QuotaComponent.SIEVE.getValue(), QuotaComponent.SIEVE,
                QuotaComponent.JMAP_UPLOADS.getValue(), QuotaComponent.JMAP_UPLOADS);

        @Inject
        public RecomputeCurrentQuotasRequestToTask(RecomputeCurrentQuotasService service) {
            super(RECOMPUTE_CURRENT_QUOTAS, request -> new RecomputeCurrentQuotasTask(service,parseRunningOptions(request)));
        }

        private static RunningOptions parseRunningOptions(Request request) {
            return RunningOptions.of(intQueryParameter(request).orElse(RunningOptions.DEFAULT_USERS_PER_SECOND), getQuotaComponent(request));
        }

        private static Optional<Integer> intQueryParameter(Request request) {
            try {
                return Optional.ofNullable(request.queryParams(USERS_PER_SECOND))
                    .map(Integer::parseInt);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(String.format("Illegal value supplied for query parameter '%s', expecting a " +
                    "strictly positive optional integer", USERS_PER_SECOND), e);
            }
        }

        private static List<QuotaComponent> getQuotaComponent(Request request) {
            List<QuotaComponent> quotaComponents = new ArrayList<>();
            String[] quotaComponentStrings = request.queryParamsValues(QUOTA_COMPONENT);
            if (Objects.isNull(quotaComponentStrings) || quotaComponentStrings.length == 0) {
                return ImmutableList.of();
            }
            for (String quotaComponentString : quotaComponentStrings) {
                QuotaComponent quotaComponent = QUOTA_COMPONENT_MAP.get(quotaComponentString);
                if (Objects.isNull(quotaComponent)) {
                    throw new IllegalArgumentException(String.format("Illegal value supplied for query parameter '%s' with value '%s', expecting existing " +
                        "quota components", QUOTA_COMPONENT, quotaComponentString));
                }
                quotaComponents.add(quotaComponent);
            }

            return ImmutableList.copyOf(quotaComponents);
        }
    }

    private static final TaskRegistrationKey RECOMPUTE_CURRENT_QUOTAS = TaskRegistrationKey.of("RecomputeCurrentQuotas");
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
    private final QuotaDTOValidator quotaDTOValidator;
    private final TaskManager taskManager;
    private final Set<TaskFromRequestRegistry.TaskRegistration> usersQuotasTaskRegistration;
    private Service service;

    @Inject
    public UserQuotaRoutes(UsersRepository usersRepository,
                           UserQuotaService userQuotaService,
                           JsonTransformer jsonTransformer,
                           Set<JsonTransformerModule> modules,
                           TaskManager taskManager,
                           @Named(USER_QUOTAS_OPERATIONS_INJECTION_KEY) Set<TaskFromRequestRegistry.TaskRegistration> usersQuotasTaskRegistration) {
        this.usersRepository = usersRepository;
        this.userQuotaService = userQuotaService;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(QuotaDTO.class, modules.stream().map(JsonTransformerModule::asJacksonModule).collect(Collectors.toList()));
        this.quotaDTOValidator = new QuotaDTOValidator();
        this.taskManager = taskManager;
        this.usersQuotasTaskRegistration = usersQuotasTaskRegistration;
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
        definePostUsersQuota();
        definePostUsersQuota()
            .ifPresent(route -> service.post(USERS_QUOTA_ENDPOINT, route, jsonTransformer));
    }

    public Optional<Route> definePostUsersQuota() {
        return TaskFromRequestRegistry.builder()
            .parameterName(TASK_PARAMETER)
            .registrations(usersQuotasTaskRegistration)
            .buildAsRouteOptional(taskManager);
    }

    public void defineUpdateQuota() {
        service.put(QUOTA_ENDPOINT, ((request, response) -> {
            try {
                Username username = checkUserExist(request);
                QuotaDTO quotaDTO = jsonExtractor.parse(request.body());
                ValidatedQuotaDTO validatedQuotaDTO = quotaDTOValidator.validatedQuotaDTO(quotaDTO);
                userQuotaService.defineQuota(username, validatedQuotaDTO);
                return Responses.returnNoContent(response);
            } catch (IllegalArgumentException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("Quota should be positive or unlimited (-1)")
                    .cause(e)
                    .haltError();
            }
        }));
    }

    public void defineGetQuota() {
        service.get(QUOTA_ENDPOINT, (request, response) -> {
            Username username = checkUserExist(request);
            return userQuotaService.getQuota(username);
        }, jsonTransformer);
    }

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

    public void defineDeleteQuotaSize() {
        service.delete(SIZE_ENDPOINT, (request, response) -> {
            Username username = checkUserExist(request);
            userQuotaService.deleteMaxSizeQuota(username);
            return Responses.returnNoContent(response);
        });
    }

    public void defineUpdateQuotaSize() {
        service.put(SIZE_ENDPOINT, (request, response) -> {
            Username username = checkUserExist(request);
            QuotaSizeLimit quotaSize = Quotas.quotaSize(request.body());
            userQuotaService.defineMaxSizeQuota(username, quotaSize);
            return Responses.returnNoContent(response);
        });
    }

    public void defineGetQuotaSize() {
        service.get(SIZE_ENDPOINT, (request, response) -> {
            Username username = checkUserExist(request);
            Optional<QuotaSizeLimit> maxSizeQuota = userQuotaService.getMaxSizeQuota(username);
            if (maxSizeQuota.isPresent()) {
                return maxSizeQuota;
            }
            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }

    public void defineDeleteQuotaCount() {
        service.delete(COUNT_ENDPOINT, (request, response) -> {
            Username username = checkUserExist(request);
            userQuotaService.deleteMaxCountQuota(username);
            return Responses.returnNoContent(response);
        });
    }

    public void defineUpdateQuotaCount() {
        service.put(COUNT_ENDPOINT, (request, response) -> {
            Username username = checkUserExist(request);
            QuotaCountLimit quotaCount = Quotas.quotaCount(request.body());
            userQuotaService.defineMaxCountQuota(username, quotaCount);
            return Responses.returnNoContent(response);
        });
    }

    public void defineGetQuotaCount() {
        service.get(COUNT_ENDPOINT, (request, response) -> {
            Username username = checkUserExist(request);
            Optional<QuotaCountLimit> maxCountQuota = userQuotaService.getMaxCountQuota(username);
            if (maxCountQuota.isPresent()) {
                return maxCountQuota;
            }
            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }

    private Username checkUserExist(Request request) throws UsersRepositoryException {
        Username username = Username.of(request.params(USER));

        if (!usersRepository.contains(username)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorType.NOT_FOUND)
                .message("User not found")
                .haltError();
        }
        return username;
    }
}
