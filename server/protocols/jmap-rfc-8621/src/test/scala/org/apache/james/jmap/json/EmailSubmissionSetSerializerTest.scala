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

package org.apache.james.jmap.json

import org.apache.james.mailbox.model.TestMessageId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class EmailSubmissionSetSerializerTest extends AnyWordSpec with Matchers {
  val serializer = new EmailSubmissionSetSerializer(new TestMessageId.Factory)

  "Deserialize EmailSubmissionSetSerializerTest" should {
    var json = Json.parse(
      """{
        |    "emailId": "1",
        |    "envelope": {
        |        "mailFrom": {
        |            "email": "bob@domain.tld",
        |            "parameters": {
        |                "holdFor": "86400"
        |            }
        |        },
        |        "rcptTo": [
        |            {
        |                "email": "andre@domain.tld",
        |                "parameters": null
        |            }
        |        ]
        |    }
        |}""".stripMargin)
    "Request should be success" in {
      serializer.emailSubmissionCreationRequestReads.reads(json)
    }
  }

    "Deserialize EmailSubmissionSetSerializerTest when holdfor is null" should {
      var json = Json.parse(
        """{
          |    "emailId": "1",
          |    "envelope": {
          |        "mailFrom": {
          |            "email": "bob@domain.tld",
          |            "parameters": {
          |                "holdFor": null
          |            }
          |        },
          |        "rcptTo": [
          |            {
          |                "email": "andre@domain.tld",
          |                "parameters": null
          |            }
          |        ]
          |    }
          |}""".stripMargin)
      "not fail" in {
        assert(serializer.emailSubmissionCreationRequestReads.reads(json).isSuccess)
      }
    }
}
