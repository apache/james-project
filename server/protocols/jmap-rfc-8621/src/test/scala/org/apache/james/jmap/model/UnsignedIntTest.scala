/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.model

import org.scalatest.{FlatSpec, Matchers}

class UnsignedIntTest extends FlatSpec with Matchers {

  "apply" should "throw when negative value" in {
    the [IllegalArgumentException] thrownBy {
      UnsignedInt(-1)
    } should have message "requirement failed: value should be positive and less than 2^53"
  }

  "apply" should "throw when greater than max value" in {
    the [IllegalArgumentException] thrownBy {
      UnsignedInt(UnsignedInt.MAX_VALUE + 1)
    } should have message "requirement failed: value should be positive and less than 2^53"
  }

  "apply" should "not throw when zero value" in {
    noException should be thrownBy UnsignedInt(0)
  }

  "apply" should "not throw when positive value" in {
    noException should be thrownBy UnsignedInt(1)
  }

  "asLong" should "return max value when max value" in {
    UnsignedInt(UnsignedInt.MAX_VALUE)
      .asLong should equal(UnsignedInt.MAX_VALUE)
  }

  "fromOutbound" should "return min value when negative value" in {
    UnsignedInt.BOUND_SANITIZING_FACTORY
      .from(-1) should equal(UnsignedInt.ZERO)
  }

  "fromOutbound" should "return max value when greater than max value" in {
    UnsignedInt.BOUND_SANITIZING_FACTORY
      .from(UnsignedInt.MAX_VALUE + 1)
      .asLong should equal(UnsignedInt.MAX_VALUE)
  }
}
