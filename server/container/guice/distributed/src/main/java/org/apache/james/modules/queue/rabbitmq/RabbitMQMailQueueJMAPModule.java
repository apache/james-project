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

package org.apache.james.modules.queue.rabbitmq;

import org.apache.james.jmap.http.rfc8621.InjectionKeys;
import org.apache.james.jmap.method.EmailSubmissionSetValidation;
import org.apache.james.jmap.method.RejectDelayEmailSubmissionSetValidation;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

/**
 * The RabbitMQ mail queue ignores enqueue delays: JMAP delayed sends (holdFor / holdUntil) would be
 * delivered right away. To be combined with the RabbitMQ mail queue whenever JMAP is enabled.
 */
public class RabbitMQMailQueueJMAPModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), EmailSubmissionSetValidation.class, Names.named(InjectionKeys.COMPOSED_EMAIL_SUBMISSION_SET_VALIDATIONS))
            .addBinding()
            .toInstance(new RejectDelayEmailSubmissionSetValidation());
    }
}
