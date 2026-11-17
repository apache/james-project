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

package org.apache.james.transport.mailets.jsieve;

import org.apache.james.core.MailAddress;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

/**
 * The addresses the server knows to belong to the user a message is delivered to (RFC 5230 section 4.5), for
 * instance the aliases mapped to them.
 */
@FunctionalInterface
public interface UserAddresses {
    UserAddresses NONE = recipient -> Flux.empty();

    Publisher<MailAddress> of(MailAddress recipient);
}
