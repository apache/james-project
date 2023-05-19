/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.api.identity

import com.google.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.DeleteUserDataTaskStep
import org.apache.james.user.api.DeleteUserDataTaskStep.StepName
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

class IdentityUserDeletionTaskStep @Inject()(customIdentityDAO: CustomIdentityDAO) extends DeleteUserDataTaskStep {
  override val name: StepName = new StepName("IdentityUserDeletionTaskStep")

  override val priority: Int = 1

  override def deleteUserData(username: Username): Publisher[Void] = Mono.from(customIdentityDAO.delete(username)).`then`()
}
