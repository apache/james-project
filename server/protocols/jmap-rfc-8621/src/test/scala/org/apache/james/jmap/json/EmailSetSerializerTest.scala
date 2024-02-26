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

import org.apache.james.jmap.json.EmailSetSerializerTest.SERIALIZER
import org.apache.james.jmap.mail.EmailCreationRequest
import org.apache.james.mailbox.model.{TestId, TestMessageId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsResult, Json}

object EmailSetSerializerTest {
  val SERIALIZER: EmailSetSerializer = new EmailSetSerializer(new TestMessageId.Factory, new TestId.Factory)
}

class EmailSetSerializerTest extends AnyWordSpec with Matchers {

  "Deserialize EmailSetRequest" should {
    "Request should be success" in {
      val jsResult: JsResult[EmailCreationRequest] = SERIALIZER.deserializeCreationRequest(
        Json.parse(
          """{
            |    "mailboxIds": {
            |        "1": true
            |    },
            |    "keywords": {
            |        "$draft": true,
            |        "$seen": true
            |    },
            |    "subject": "draft 1",
            |    "from": [
            |        {
            |            "name": "Van Tung TRAN",
            |            "email": "vttran@linagora.com"
            |        }
            |    ],
            |    "to": [
            |        {
            |            "name": null,
            |            "email": "bt"
            |        }
            |    ],
            |    "cc": [],
            |    "bcc": [],
            |    "replyTo": [
            |        {
            |            "name": null,
            |            "email": "vttran@linagora.com"
            |        }
            |    ],
            |    "htmlBody": [
            |        {
            |            "partId": "951c3960-d139-11ee-843e-b70023541167",
            |            "type": "text/html"
            |        }
            |    ],
            |    "bodyValues": {
            |        "951c3960-d139-11ee-843e-b70023541167": {
            |            "value": "<div><br>xin chao</div>",
            |            "isEncodingProblem": false,
            |            "isTruncated": false
            |        }
            |    },
            |    "header:User-Agent:asText": "Team-Mail/0.11.3 Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
            |}""".stripMargin))

      assert(jsResult.isSuccess)
    }
  }
}
