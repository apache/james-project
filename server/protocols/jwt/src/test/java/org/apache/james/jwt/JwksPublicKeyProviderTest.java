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

package org.apache.james.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class JwksPublicKeyProviderTest {
    private static final String JWKS_URI_PATH = "/auth/realms/realm1/protocol/openid-connect/certs";
    private static final String JWKS_JSON = "{" +
        "    \"keys\": [" +
        "        {" +
        "            \"kid\": \"QgjozGKW0m9NV3Gw-AdYbjyZsBd6VATHCkY8bcraewA\"," +
        "            \"kty\": \"RSA\"," +
        "            \"alg\": \"RS256\"," +
        "            \"use\": \"enc\"," +
        "            \"n\": \"jWqrqziNh5xNCXNBDIAETru3FNaqlDUAMCdcuvu8CJMm39JekugbKiPEQ7iwSEntTZ-7yVWj83nwbnc5-pVltw-6hqxY8rtCR7V0Ncfh9wqET2FlKDCbaY9qQkQuVklQ-5FuNMX5-VNUjw8O3QuJmoJWL9Yd4-tHbqd4d6TfK5qz1XtcfIPU9YNbpfMQXtlLWEkBDaCm5TTxGUdU44sULSCDILLubn5kV2PoRTwnYp7snzcpT7m7BbUSTNV451T_9TFaY-E6A_iiTHgLt0ugFrLkpfi8ilifz9NXEqy_UMNrUrBd827bv4LQYJnNyN3K6eCce1A0aMQ5OwGsIc9VNw\"," +
        "            \"e\": \"AQAB\"," +
        "            \"x5c\": [" +
        "                \"MIICmzCCAYMCBgF9ub7JPDANBgkqhkiG9w0BAQsFADARMQ8wDQYDVQQDDAZtYXN0ZXIwHhcNMjExMjE0MTYxODQ4WhcNMzExMjE0MTYyMDI4WjARMQ8wDQYDVQQDDAZtYXN0ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCNaqurOI2HnE0Jc0EMgAROu7cU1qqUNQAwJ1y6+7wIkybf0l6S6BsqI8RDuLBISe1Nn7vJVaPzefBudzn6lWW3D7qGrFjyu0JHtXQ1x+H3CoRPYWUoMJtpj2pCRC5WSVD7kW40xfn5U1SPDw7dC4maglYv1h3j60dup3h3pN8rmrPVe1x8g9T1g1ul8xBe2UtYSQENoKblNPEZR1TjixQtIIMgsu5ufmRXY+hFPCdinuyfNylPubsFtRJM1XjnVP/1MVpj4ToD+KJMeAu3S6AWsuSl+LyKWJ/P01cSrL9Qw2tSsF3zbtu/gtBgmc3I3crp4Jx7UDRoxDk7Aawhz1U3AgMBAAEwDQYJKoZIhvcNAQELBQADggEBAAE4liV2QX/3zDDHY4KGjTssQn+LUfVRJUIMOfnumzTNozYaHgA56G5uIobS7JsW6nfk6U4WJPb0zMnMDF+sngXPivjnZk1ojT7iBAwgPVdzKLudWXJUGh3ws6I5tLaP1VPPG3Vo2GPr305/+iYmJ4QSjWxWRX4Z//Y4mlw91D1GwyVzb2q5YrzKexyGa+ZcSRxt8hD3Ml9ygc5173CTXl/EUsz7ltnrxQ8ddH7inZMTxNrMyhKcr2GeJd98QIWXroeknDzc+AhgVMXzI+Ykz+lNJRN/BSrPZN7azxL9MczhY5IG7AAIuhodNdWMHRmIJMVxe7A7szeyfwA6OQT/LHA=\"" +
        "            ]," +
        "            \"x5t\": \"JVzmary1u-6-h_ntUBkTMa1WoBU\"," +
        "            \"x5t#S256\": \"Y92JgcHrW-5dePL0vZKMvfiIHSUJfjDuRfVi4yPnl_M\"" +
        "        }," +
        "        {" +
        "            \"kid\": \"wu-9VZEr0gHF986PYPVzvU-5IP1q26EzzQVK_sjG29Q\"," +
        "            \"kty\": \"RSA\"," +
        "            \"alg\": \"RS256\"," +
        "            \"use\": \"sig\"," +
        "            \"n\": \"n9fdJJ87sjLcHKK4NigVJk0-bnvUimpf5ErZeEcJkqCDjP9I42xByRfYQ8a0Ob2v3x-nVF8cIwucCNbREpPnWnSX7sJkWdqVQocyUpC7qgc9sT8A899YrgImSxQ0ZR4CDlcXyMfBcZTRa8a6eUBdge5W5xHzIdtEIjgAos8bf4IZ94Js_yec4rV5dYOf3J56-XBxvg9Xm6ChC7u6hwgYH9_6R8k5W6ziYehKgqqZ7Ygs_1ynqU5htwozPqcppfzp2GjHFVwbJi0mpRXKOBz7KjCoymPihC_TdLAik2PQcZcIoEa7XD8yqh-qK2VUwV5UVyvSFqRJgiqEeXFSMYt3LQ\"," +
        "            \"e\": \"AQAB\"," +
        "            \"x5c\": [" +
        "                \"MIICmzCCAYMCBgF9ub7ItDANBgkqhkiG9w0BAQsFADARMQ8wDQYDVQQDDAZtYXN0ZXIwHhcNMjExMjE0MTYxODQ3WhcNMzExMjE0MTYyMDI3WjARMQ8wDQYDVQQDDAZtYXN0ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCf190knzuyMtwcorg2KBUmTT5ue9SKal/kStl4RwmSoIOM/0jjbEHJF9hDxrQ5va/fH6dUXxwjC5wI1tESk+dadJfuwmRZ2pVChzJSkLuqBz2xPwDz31iuAiZLFDRlHgIOVxfIx8FxlNFrxrp5QF2B7lbnEfMh20QiOACizxt/ghn3gmz/J5zitXl1g5/cnnr5cHG+D1eboKELu7qHCBgf3/pHyTlbrOJh6EqCqpntiCz/XKepTmG3CjM+pyml/OnYaMcVXBsmLSalFco4HPsqMKjKY+KEL9N0sCKTY9BxlwigRrtcPzKqH6orZVTBXlRXK9IWpEmCKoR5cVIxi3ctAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAIEb/zH5YIMVL3gM/w8DnVNhFTBeZ1GUaz5uWsXmlc3aC6Lshl5lJYH27gy41bIwvQspDPsHw9h69VnN2I5aoVomJ4re94LxorxC7767mINUnwrwClhyNO1bdZWi6zeKp+pwVhBymazeXL5SV2ngBrsFZrQ7XtfhxDqRy69gFJQwJXwhD/qfhgBj+jcsYGFpvooGGLoOpc5f6gzkTv1u99NqwWSj7E4yfrcBjlrQafUdsD4nD4cSNi2Lqd7dRmCvhgIjzFOuBpEqVSgoQIwlja9Gc8hA6DVcxvZiE6q2sMzjp8Orxgl3UqqqR+Pb0ITqfM5OQLZF+nCIhryF/ZCChVM=\"" +
        "            ]," +
        "            \"x5t\": \"paqnI4t1-GuJz5PUqtTgfLjXcqw\"," +
        "            \"x5t#S256\": \"S3QfJleq8olDM3dXMb2GI2jGM_Ntdt040JOANmEGEh8\"" +
        "        }" +
        "    ]" +
        "}";
    
    ClientAndServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = ClientAndServer.startClientAndServer(0);
        mockServer
            .when(HttpRequest.request().withPath(JWKS_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type","application/json")
                .withBody(JWKS_JSON, StandardCharsets.UTF_8));
    }

    private URL getJwksURL() {
        try {
            return new URI(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), JWKS_URI_PATH)).toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getShouldSuccessWhenKeyProvided() {
        PublicKeyProvider testee = JwksPublicKeyProvider.of(getJwksURL(), "wu-9VZEr0gHF986PYPVzvU-5IP1q26EzzQVK_sjG29Q");
        List<PublicKey> publicKeys = testee.get();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(publicKeys).hasSize(1);
            softly.assertThat(publicKeys.get(0)).isInstanceOf(RSAPublicKey.class);
        });
    }

    @Test
    void getShouldReturnEmptyWhenKeyNotProvided() {
        PublicKeyProvider testee = JwksPublicKeyProvider.of(getJwksURL(), "notfound");
        assertThat(testee.get()).isEmpty();
    }

    @Test
    void getShouldFailWhenBadJwksURL() throws MalformedURLException, URISyntaxException {
        mockServer
            .when(HttpRequest.request().withPath("/invalid"))
            .respond(HttpResponse.response().withStatusCode(200)
                .withBody("invalid body", StandardCharsets.UTF_8));

        PublicKeyProvider testee = JwksPublicKeyProvider.of(new URI(String.format("http://127.0.0.1:%s/invalid", mockServer.getLocalPort())).toURL(),
            "wu-9VZEr0gHF986PYPVzvU-5IP1q26EzzQVK_sjG29Q");
        assertThatThrownBy(testee::get)
            .isInstanceOf(MissingOrInvalidKeyException.class);
    }

    @Test
    void getShouldReturnAllPublicKeyWhenKidNoProvided() {
        PublicKeyProvider testee = JwksPublicKeyProvider.of(getJwksURL());
        assertThat(testee.get())
            .hasSize(2);
    }

}
