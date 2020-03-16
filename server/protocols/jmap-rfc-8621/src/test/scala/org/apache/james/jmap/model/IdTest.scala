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

class IdTest extends FlatSpec with Matchers {

  private val INVALID_CHARACTERS = List("\"", "(", ")", ",", ":", ";", "<", ">", "@", "[", "\\", "]", " ")

  "apply" should "throw when null value" in {
    the [IllegalArgumentException] thrownBy {
      Id(null)
    } should have message "requirement failed: value cannot be null"
  }

  "apply" should "throw when empty value" in {
    the [IllegalArgumentException] thrownBy {
      Id("")
    } should have message "requirement failed: value cannot be empty"
  }

  "apply" should "throw when too long value" in {
    val idWith256Chars = "a" * 256
    the [IllegalArgumentException] thrownBy {
      Id(idWith256Chars)
    } should have message "requirement failed: value length cannot exceed 255 characters"
  }

  "apply" should "throw when invalid value" in {
    INVALID_CHARACTERS.foreach { invalidChar =>
      the [IllegalArgumentException] thrownBy {
        Id(invalidChar)
      } should have message
        "requirement failed: value should contains only 'URL and Filename Safe' base64 alphabet characters, see Section 5 of [@!RFC4648]"
    }
  }
}
