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

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.ValuePatch;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationPatch;
import org.apache.james.vacation.api.VacationService;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.VacationDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import spark.Request;
import spark.Response;
import spark.Service;

public class VacationRoutes implements Routes {

    public static final String VACATION = "/vacation";
    private static final String USER_NAME = ":userName";

    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<VacationDTO> jsonExtractor;

    private final VacationService vacationService;
    private final UsersRepository usersRepository;

    @Inject
    public VacationRoutes(VacationService vacationService,
                          UsersRepository usersRepository,
                          JsonTransformer jsonTransformer) {
        this.vacationService = vacationService;
        this.usersRepository = usersRepository;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(VacationDTO.class, new JavaTimeModule());
    }

    @Override
    public String getBasePath() {
        return VACATION;
    }

    @Override
    public void define(Service service) {
        service.get(VACATION + SEPARATOR + USER_NAME, this::getVacation, jsonTransformer);
        service.post(VACATION + SEPARATOR + USER_NAME, this::updateVacation);
        service.delete(VACATION + SEPARATOR + USER_NAME, this::deleteVacation);
    }

    public VacationDTO getVacation(Request request, Response response) {
        testUserExists(request);
        AccountId accountId = AccountId.fromString(request.params(USER_NAME));
        Vacation vacation = vacationService.retrieveVacation(accountId).block();
        return VacationDTO.from(vacation);
    }

    public String updateVacation(Request request, Response response) throws JsonExtractException {
        testUserExists(request);
        AccountId accountId = AccountId.fromString(request.params(USER_NAME));
        VacationDTO vacationDto = jsonExtractor.parse(request.body());
        VacationPatch vacationPatch = VacationPatch.builder()
            .subject(updateOrKeep(vacationDto.getSubject()))
            .textBody(updateOrKeep(vacationDto.getTextBody()))
            .htmlBody(updateOrKeep(vacationDto.getHtmlBody()))
            .fromDate(updateOrKeep(vacationDto.getFromDate()))
            .toDate(updateOrKeep(vacationDto.getToDate()))
            .isEnabled(updateOrKeep(vacationDto.getEnabled()))
            .build();
        vacationService.modifyVacation(accountId, vacationPatch).block();
        return Responses.returnNoContent(response);
    }

    public String deleteVacation(Request request, Response response) {
        testUserExists(request);
        AccountId accountId = AccountId.fromString(request.params(USER_NAME));
        VacationPatch vacationPatch = VacationPatch.builder()
            .isEnabled(false)
            .subject(ValuePatch.remove())
            .textBody(ValuePatch.remove())
            .htmlBody(ValuePatch.remove())
            .fromDate(ValuePatch.remove())
            .toDate(ValuePatch.remove())
            .build();
        vacationService.modifyVacation(accountId, vacationPatch).block();
        return Responses.returnNoContent(response);
    }

    private void testUserExists(Request request) {
        Username username = Username.of(request.params(USER_NAME));
        if (!isExistingUser(username)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("The user '" + username.asString() + "' does not exist")
                .haltError();
        }
    }

    private boolean isExistingUser(Username username) {
        try {
            return usersRepository.contains(username);
        } catch (UsersRepositoryException e) {
            return false;
        }
    }

    private static <T> ValuePatch<T> updateOrKeep(Optional<T> opt) {
        return opt.map(ValuePatch::modifyTo).orElse(ValuePatch.keep());
    }
}
