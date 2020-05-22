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
package org.apache.james.jmap.rfc8621.contract

import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.authentication.NoAuthScheme
import io.restassured.http.Header
import org.apache.http.HttpStatus._
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.JWTAuthenticationContract._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object JWTAuthenticationContract {
  private val USER_TOKEN: String =
    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGRvbWFpbi50bGQifQ.U-dUPv6OU6KO5N7CooHUfMkCd" +
      "FJHx2F3H4fm7Q79g1BPfBSkifPj5xyVlZ0JwEGXypC4zBw9ay3l4DxzX7D_6p1Hx_ihXsoLx1Ca-WUo44x-XRSpPfgxiZjHCJkGBLMV3RZlA" +
      "jip-d18mxkcX3JGplX_sCQkFisduAOAHuKSUg9wI6VBgUQi_0B35FYv6tP_bD6eFtvaAUN9QyXXh8UQjEp8CO12lRz6enfLx_V6BG_fEMkee" +
      "6vRqdEqx_F9OF3eWTe1giMp_JhQ7_l1OXXtbd4TndVvTeuVy4irPbsRc-M8x_-qTDpFp6saRRsyOcFspxPp5n3yIhEK7B3UZiseXw"

  private val UNKNOWN_USER_TOKEN: String =
    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.T04BTk" +
      "LXkJj24coSZkK13RfG25lpvmSl2MJ7N10KpBk9_-95EGYZdog-BDAn3PJzqVw52z-Bwjh4VOj1-j7cURu0cT4jXehhUrlCxS4n7QHZD" +
      "N_bsEYGu7KzjWTpTsUiHe-rN7izXVFxDGG1TGwlmBCBnPW-EFCf9ylUsJi0r2BKNdaaPRfMIrHptH1zJBkkUziWpBN1RNLjmvlAUf49" +
      "t1Tbv21ZqYM5Ht2vrhJWczFbuC-TD-8zJkXhjTmA1GVgomIX5dx1cH-dZX1wANNmshUJGHgepWlPU-5VIYxPEhb219RMLJIELMY2qN" +
      "OR8Q31ydinyqzXvCSzVJOf6T60-w"

  private val INVALID_JWT_TOKEN: String =
    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbkBvcGVu" +
      "thispartiscompletelywrong.reQc3DiVvbQHF08oW1qOUyDJyv3tfzDNk8jhVZequiCdOI9vXnRlOe" +
      "-yDYktd4WT8MYhqY7MgS-wR0vO9jZFv8ZCgd_MkKCvCO0HmMjP5iQPZ0kqGkgWUH7X123tfR38MfbCVAdPDba-K3MfkogV1xvDhlkPScFr_6MxE" +
      "xtedOK2JnQZn7t9sUzSrcyjWverm7gZkPptkIVoS8TsEeMMME5vFXe_nqkEG69q3kuBUm_33tbR5oNS0ZGZKlG9r41lHBjyf9J1xN4UYV8n866d" +
      "a7RPPCzshIWUtO0q9T2umWTnp-6OnOdBCkndrZmRR6pPxsD5YL0_77Wq8KT_5__fGA"

  private val USER: Username = Username.fromLocalPartWithDomain("user", DOMAIN)
  private val USER_PASSWORD: String = "user"
}

trait JWTAuthenticationContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(USER.asString(), USER_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(new NoAuthScheme)
      .build
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def getMustReturnEndpointsWhenValidJwtAuthorizationHeader(): Unit = {
    `given`
      .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Bearer $USER_TOKEN")))
      .body(ECHO_REQUEST_OBJECT)
    .when
      .post()
    .`then`
      .statusCode(SC_OK)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMustReturnOKWhenValidUnknownUserJwtAuthorizationHeader(): Unit = {
    val authHeader: Header = new Header(AUTHORIZATION_HEADER, s"Bearer $UNKNOWN_USER_TOKEN")
    `given`()
      .headers(getHeadersWith(authHeader))
      .body(ECHO_REQUEST_OBJECT)
    .when()
      .post()
    .then
      .statusCode(SC_UNAUTHORIZED)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getMustReturnBadCredentialsWhenInvalidJwtAuthorizationHeader(): Unit = {
    `given`
      .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Bearer $INVALID_JWT_TOKEN")))
      .body(ECHO_REQUEST_OBJECT)
    .when
      .post()
    .`then`
      .statusCode(SC_UNAUTHORIZED)
  }
}
