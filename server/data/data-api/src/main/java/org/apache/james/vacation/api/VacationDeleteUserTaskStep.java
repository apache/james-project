/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.vacation.api;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.util.ValuePatch;
import org.reactivestreams.Publisher;

public class VacationDeleteUserTaskStep implements DeleteUserDataTaskStep {
    private final VacationRepository vacationRepository;

    @Inject
    public VacationDeleteUserTaskStep(VacationRepository vacationRepository) {
        this.vacationRepository = vacationRepository;
    }

    @Override
    public StepName name() {
        return new StepName("VacationDeleteUserTaskStep");
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public Publisher<Void> deleteUserData(Username username) {
        return vacationRepository.modifyVacation(AccountId.fromUsername(username),
            VacationPatch.builder()
                .isEnabled(false)
                .subject(ValuePatch.remove())
                .fromDate(ValuePatch.remove())
                .htmlBody(ValuePatch.remove())
                .textBody(ValuePatch.remove())
                .toDate(ValuePatch.remove())
                .build());
    }
}
