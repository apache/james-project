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

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static org.apache.james.webadmin.Constants.JSON_CONTENT_TYPE;
import static org.apache.james.webadmin.Constants.SEPARATOR;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.dlp.api.DLPConfigurationItem;
import org.apache.james.dlp.api.DLPConfigurationItem.Id;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.dlp.api.DLPRules;
import org.apache.james.dlp.api.DLPRules.DuplicateRulesIdsException;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.DLPConfigurationDTO;
import org.apache.james.webadmin.dto.DLPConfigurationItemDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import reactor.core.publisher.Mono;
import spark.HaltException;
import spark.Request;
import spark.Service;

public class DLPConfigurationRoutes implements Routes {

    public static final String BASE_PATH = "/dlp/rules";

    private static final String DOMAIN_NAME = ":senderDomain";
    private static final String SPECIFIC_DLP_RULE_DOMAIN = BASE_PATH + SEPARATOR + DOMAIN_NAME;

    private static final String RULE_ID_NAME = ":ruleId";
    private static final String RULE_SPECIFIC_PATH = SPECIFIC_DLP_RULE_DOMAIN + SEPARATOR + "rules" + SEPARATOR + RULE_ID_NAME;

    private final JsonTransformer jsonTransformer;
    private final DLPConfigurationStore dlpConfigurationStore;
    private final JsonExtractor<DLPConfigurationDTO> jsonExtractor;
    private final DomainList domainList;

    @Inject
    public DLPConfigurationRoutes(DLPConfigurationStore dlpConfigurationStore, DomainList domainList, JsonTransformer jsonTransformer) {
        this.dlpConfigurationStore = dlpConfigurationStore;
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(DLPConfigurationDTO.class);
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {

        defineStore(service);

        defineList(service);

        defineClear(service);

        defineFetch(service);
    }

    public void defineStore(Service service) {
        service.put(SPECIFIC_DLP_RULE_DOMAIN, (request, response) -> {
            Domain senderDomain = parseDomain(request);
            DLPConfigurationDTO dto = jsonExtractor.parse(request.body());

            DLPRules rules = constructRules(dto);

            dlpConfigurationStore.store(senderDomain, rules);

            return Responses.returnNoContent(response);
        });
    }

    private DLPRules constructRules(DLPConfigurationDTO dto) {
        try {
            return dto.toDLPConfiguration();
        } catch (DuplicateRulesIdsException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("'id' duplicates are not allowed in DLP rules")
                .haltError();
        }
    }

    public void defineList(Service service) {
        service.get(SPECIFIC_DLP_RULE_DOMAIN, (request, response) -> {
            Domain senderDomain = parseDomain(request);
            DLPRules dlpConfigurations = Mono.from(dlpConfigurationStore.list(senderDomain)).block();

            DLPConfigurationDTO dto = DLPConfigurationDTO.toDTO(dlpConfigurations);
            response.status(HttpStatus.OK_200);
            response.header(CONTENT_TYPE, JSON_CONTENT_TYPE);
            return dto;
        }, jsonTransformer);
    }

    public void defineClear(Service service) {
        service.delete(SPECIFIC_DLP_RULE_DOMAIN, (request, response) -> {
            Domain senderDomain = parseDomain(request);
            dlpConfigurationStore.clear(senderDomain);

            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }

    public void defineFetch(Service service) {
        service.get(RULE_SPECIFIC_PATH, (request, response) -> {
            Domain senderDomain = parseDomain(request);
            Id ruleId = DLPConfigurationItem.Id.of(request.params(RULE_ID_NAME));
            DLPConfigurationItem dlpConfigurationItem = dlpConfigurationStore
                .fetch(senderDomain, ruleId)
                .orElseThrow(() -> notFound("There is no rule '" + ruleId.asString() + "' for '" + senderDomain.asString() + "' managed by this James server"));

            DLPConfigurationItemDTO dto = DLPConfigurationItemDTO.toDTO(dlpConfigurationItem);
            response.status(HttpStatus.OK_200);
            response.header(CONTENT_TYPE, JSON_CONTENT_TYPE);
            return dto;
        }, jsonTransformer);
    }

    private Domain parseDomain(Request request) {
        String domainName = request.params(DOMAIN_NAME);
        try {
            Domain domain = Domain.of(domainName);
            validateDomainInList(domain);

            return domain;
        } catch (DomainListException e) {
            throw serverError(String.format("Cannot recognize domain: %s in domain list", domainName), e);
        }
    }

    private void validateDomainInList(Domain domain) throws DomainListException {
        if (!domainList.containsDomain(domain)) {
            throw notFound(String.format("'%s' is not managed by this James server", domain.name()));
        }
    }

    private HaltException serverError(String message, Exception e) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
            .type(ErrorType.SERVER_ERROR)
            .message(message)
            .cause(e)
            .haltError();
    }

    private HaltException notFound(String message) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorType.INVALID_ARGUMENT)
            .message(message)
            .haltError();
    }
}
