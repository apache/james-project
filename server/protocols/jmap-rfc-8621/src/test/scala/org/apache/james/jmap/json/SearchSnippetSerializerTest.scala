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

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.jmap.core.{AccountId, Id}
import org.apache.james.jmap.highlight.{SearchSnippet, SearchSnippetGetRequest, SearchSnippetGetResponse}
import org.apache.james.jmap.json.SearchSnippetSerializerTest.{messageIdFactory, testee}
import org.apache.james.jmap.mail.{FilterCondition, FilterOperator, UnparsedEmailId}
import org.apache.james.mailbox.model.{TestId, TestMessageId}
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsResult, Json}

import scala.jdk.CollectionConverters._

object SearchSnippetSerializerTest {
  val messageIdFactory = new TestMessageId.Factory
  val emailQuerySerializer: EmailQuerySerializer = new EmailQuerySerializer(new TestId.Factory)
  val testee: SearchSnippetSerializer = new SearchSnippetSerializer(emailQuerySerializer)
}

class SearchSnippetSerializerTest extends AnyWordSpec with Matchers {

  "Deserialize SearchSnippetGetRequest" should {
    "success when filter is FilterCondition" in {
      val jsResult: JsResult[SearchSnippetGetRequest] = testee.deserializeSearchSnippetGetRequest(
        Json.parse(
          """{
            |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
            |    "filter": {
            |        "inMailbox": "1",
            |        "text": "test"
            |    },
            |    "emailIds": ["1", "2"]
            |}""".stripMargin))

      assert(jsResult.isSuccess)
      val request: SearchSnippetGetRequest = jsResult.get
      assertSoftly(softly => {
        softly.assertThat(request.accountId.id.value).isEqualTo("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6")
        softly.assertThat(request.filter.isDefined).isTrue
        softly.assertThat(request.emailIds
          .map(_.id.value).asJava).containsExactly("1", "2")
      })

      assertThat(request.filter.get).isInstanceOf(classOf[FilterCondition])
    }

    "success when filter is FilterOperator" in {
      val jsResult: JsResult[SearchSnippetGetRequest] = testee.deserializeSearchSnippetGetRequest(
        Json.parse(
          """{
            |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
            |    "filter": {
            |    "operator": "AND",
            |    "conditions": [
            |      { "hasKeyword": "custom" }, { "hasKeyword": "another_custom" }
            |     ]
            |    },
            |    "emailIds": ["1", "2"]
            |}""".stripMargin))

      assert(jsResult.isSuccess)
      val request: SearchSnippetGetRequest = jsResult.get
      assertSoftly(softly => {
        softly.assertThat(request.accountId.id.value).isEqualTo("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6")
        softly.assertThat(request.filter.isDefined).isTrue
        softly.assertThat(request.emailIds
          .map(_.id.value).asJava).containsExactly("1", "2")
      })
      assertThat(request.filter.get).isInstanceOf(classOf[FilterOperator])
    }
  }


  "Serialize SearchSnippetGetResponse should" should {
    "success" in {
      val response: SearchSnippetGetResponse = SearchSnippetGetResponse(
        accountId = AccountId(Id.validate("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6").toOption.get),
        list = List(SearchSnippet(messageIdFactory.fromString("1"), Some("subject <mark>test</mark>"), Some("preview <mark>test</mark> preview")),
          SearchSnippet(messageIdFactory.fromString("2"))),
        notFound = List(UnparsedEmailId(Id.validate("123").toOption.get)))

      assertThatJson(Json.stringify(testee.serialize(response))).isEqualTo(
        """{
          |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |    "list": [
          |        {
          |            "emailId": "1",
          |            "subject": "subject <mark>test</mark>",
          |            "preview": "preview <mark>test</mark> preview"
          |        },
          |        {
          |            "emailId": "2",
          |            "subject": null,
          |            "preview": null
          |        }
          |    ],
          |    "notFound":[ "123" ]
          |}""".stripMargin)
    }
  }
}
