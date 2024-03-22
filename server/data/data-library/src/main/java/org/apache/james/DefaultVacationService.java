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

package org.apache.james;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.RecipientId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationPatch;
import org.apache.james.vacation.api.VacationRepository;
import org.apache.james.vacation.api.VacationService;

import reactor.core.publisher.Mono;

public class DefaultVacationService implements VacationService {

    private final VacationRepository vacationRepository;
    private final NotificationRegistry notificationRegistry;

    @Inject
    public DefaultVacationService(VacationRepository vacationRepository, NotificationRegistry notificationRegistry) {
        this.vacationRepository = vacationRepository;
        this.notificationRegistry = notificationRegistry;
    }

    @Override
    public Mono<Void> modifyVacation(AccountId accountId, VacationPatch vacationPatch) {
        return vacationRepository.modifyVacation(accountId, vacationPatch)
            .then(notificationRegistry.flush(accountId));
    }

    @Override
    public Mono<Vacation> retrieveVacation(AccountId accountId) {
        return vacationRepository.retrieveVacation(accountId);
    }

    @Override
    public Mono<Void> registerNotification(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate) {
        return notificationRegistry.register(accountId, recipientId, expiryDate);
    }

    @Override
    public Mono<Boolean> isNotificationRegistered(AccountId accountId, RecipientId recipientId) {
        return notificationRegistry.isRegistered(accountId, recipientId);
    }

    @Override
    public Mono<Void> flushNotifications(AccountId accountId) {
        return notificationRegistry.flush(accountId);
    }
}
