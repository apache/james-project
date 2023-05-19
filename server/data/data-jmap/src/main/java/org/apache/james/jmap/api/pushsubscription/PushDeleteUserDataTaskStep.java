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

package org.apache.james.jmap.api.pushsubscription;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.reactivestreams.Publisher;

public class PushDeleteUserDataTaskStep implements DeleteUserDataTaskStep {
    private final PushSubscriptionRepository pushSubscriptionRepository;

    @Inject
    public PushDeleteUserDataTaskStep(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    @Override
    public StepName name() {
        return new StepName("PushDeleteUserDataTaskStep");
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public Publisher<Void> deleteUserData(Username username) {
        return pushSubscriptionRepository.delete(username);
    }
}
