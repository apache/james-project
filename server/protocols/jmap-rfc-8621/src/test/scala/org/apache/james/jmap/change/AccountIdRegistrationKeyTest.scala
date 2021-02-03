/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.change

import org.apache.james.jmap.api.model.AccountId
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

class AccountIdRegistrationKeyTest {
  private val ID: String = "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"

  private val FACTORY: Factory = Factory()

  private val ACCOUNT_ID_REGISTRATION_KEY: AccountIdRegistrationKey = AccountIdRegistrationKey(AccountId.fromString(ID))

  @Test
  def asStringShouldReturnSerializedAccountId(): Unit = {
    assertThat(ACCOUNT_ID_REGISTRATION_KEY.asString()).isEqualTo(ID)
  }

  @Test
  def fromStringShouldReturnCorrespondingRegistrationKey(): Unit = {
    assertThat(FACTORY.fromString(ID)).isEqualTo(ACCOUNT_ID_REGISTRATION_KEY)
  }

  @Test
  def fromStringShouldThrowOnNullValues(): Unit = {
    assertThatThrownBy(() => FACTORY.fromString(null)).isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def fromStringShouldThrowOnEmptyValues(): Unit = {
    assertThatThrownBy(() => FACTORY.fromString("")).isInstanceOf(classOf[IllegalArgumentException])
  }
}
