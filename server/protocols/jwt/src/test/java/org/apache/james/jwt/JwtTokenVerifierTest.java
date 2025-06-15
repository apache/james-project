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

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class JwtTokenVerifierTest {

    private static final String PUBLIC_PEM_KEY = "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtlChO/nlVP27MpdkG0Bh\n" +
            "16XrMRf6M4NeyGa7j5+1UKm42IKUf3lM28oe82MqIIRyvskPc11NuzSor8HmvH8H\n" +
            "lhDs5DyJtx2qp35AT0zCqfwlaDnlDc/QDlZv1CoRZGpQk1Inyh6SbZwYpxxwh0fi\n" +
            "+d/4RpE3LBVo8wgOaXPylOlHxsDizfkL8QwXItyakBfMO6jWQRrj7/9WDhGf4Hi+\n" +
            "GQur1tPGZDl9mvCoRHjFrD5M/yypIPlfMGWFVEvV5jClNMLAQ9bYFuOc7H1fEWw6\n" +
            "U1LZUUbJW9/CH45YXz82CYqkrfbnQxqRb2iVbVjs/sHopHd1NTiCfUtwvcYJiBVj\n" +
            "kwIDAQAB\n" +
            "-----END PUBLIC KEY-----";
    public static final String PUBLIC_PEM_KEY_2 =
        "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCVnxAOpup/rtGzn+xUaBRFSe34\n" +
            "H7YyiM6YBD1bh5rkoi9pB6fvs1vDlXzBmR0Zl6kn3g+2ChW0lqMkmv73Y2Lv3WZK\n" +
            "NZ3DUR3lfBFbvYGQyFyib+e4MY1yWkj3sumMl1wdUB4lKLHLIRv9X1xCqvbSHEtq\n" +
            "zoZF4vgBYx0VmuJslwIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    public static final String PRIVATE_PEM_KEY_EC = // use to generate tokens for future tests
            "-----BEGIN EC PRIVATE KEY-----\n" +
            "MHcCAQEEIL3oeBhM7D1DttaWfGUOsurEuFG1XcN1JDLijqTOcTuqoAoGCCqGSM49\n" +
            "AwEHoUQDQgAE35E+lPVu46nAdTySey4ilUxO76RGG62SxqAdZb8B57t1ShzKC8U8\n" +
            "qG+azeT9edpTao7dSRmgK3V83GAPAI88Ag==\n" +
            "-----END EC PRIVATE KEY-----\n";
    public static final String PUBLIC_PEM_KEY_EC =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE35E+lPVu46nAdTySey4ilUxO76RG\n" +
            "G62SxqAdZb8B57t1ShzKC8U8qG+azeT9edpTao7dSRmgK3V83GAPAI88Ag==\n" +
            "-----END PUBLIC KEY-----\n";

    private static final String VALID_TOKEN_WITHOUT_ADMIN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.T04BTk" +
            "LXkJj24coSZkK13RfG25lpvmSl2MJ7N10KpBk9_-95EGYZdog-BDAn3PJzqVw52z-Bwjh4VOj1-j7cURu0cT4jXehhUrlCxS4n7QHZD" +
            "N_bsEYGu7KzjWTpTsUiHe-rN7izXVFxDGG1TGwlmBCBnPW-EFCf9ylUsJi0r2BKNdaaPRfMIrHptH1zJBkkUziWpBN1RNLjmvlAUf49" +
            "t1Tbv21ZqYM5Ht2vrhJWczFbuC-TD-8zJkXhjTmA1GVgomIX5dx1cH-dZX1wANNmshUJGHgepWlPU-5VIYxPEhb219RMLJIELMY2qN" +
            "OR8Q31ydinyqzXvCSzVJOf6T60-w";

    private static final String VALID_TOKEN_ADMIN_TRUE = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbkBvcGVuL" +
        "XBhYXMub3JnIiwiYWRtaW4iOnRydWUsImlhdCI6MTQ4OTAzODQzOH0.rgxCkdWEa-92a4R-72a9Z49k4LRvQDShgci5Y7qWRUP9IGJCK-lMkrHF" +
        "4H0a6L87BYppxVW701zaZ6dNxRMvHnjLBBWnPsC2B0rkkr2hEL2zfz7sb-iNGV-J4ICx97t8-TfQ5rz3VOX0FwdusPL_rJtmlGEGRivPkR6_aBe1" +
        "kQnvMlwpqF_3ox58EUqYJk6lK_6rjKEV3Xfre31IMpuQUy6c7TKc95sL2-13cknelTierBEmZ00RzTtv9SHIEfzZTfaUK2Wm0PvnQjmU2nIdEvU" +
        "EqE-jrM3yYXcQzoO-YTQnEhdl-iqbCfmEpYkl2Bx3eIq7gRxxnr7BPsX6HrCB0w";

    private static final String VALID_TOKEN_ADMIN_FALSE = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbkBvcGVu" +
        "LXBhYXMub3JnIiwiYWRtaW4iOmZhbHNlLCJpYXQiOjE0ODkwNDA4Njd9.reQc3DiVvbQHF08oW1qOUyDJyv3tfzDNk8jhVZequiCdOI9vXnRlOe" +
        "-yDYktd4WT8MYhqY7MgS-wR0vO9jZFv8ZCgd_MkKCvCO0HmMjP5iQPZ0kqGkgWUH7X123tfR38MfbCVAdPDba-K3MfkogV1xvDhlkPScFr_6MxE" +
        "xtedOK2JnQZn7t9sUzSrcyjWverm7gZkPptkIVoS8TsEeMMME5vFXe_nqkEG69q3kuBUm_33tbR5oNS0ZGZKlG9r41lHBjyf9J1xN4UYV8n866d" +
        "a7RPPCzshIWUtO0q9T2umWTnp-6OnOdBCkndrZmRR6pPxsD5YL0_77Wq8KT_5__fGA";

    private static final String VALID_TOKEN_NO_EXPIRATION = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW" +
        "1wbGUuY29tIiwiYWRtaW4iOnRydWV9.YSIWBGBcNv0-9ztJI2toAY5c6lgUo445l4C4smDlwVxIqe7N3lmgpwvq1BPI7NQFtNrJ6M2ZzMTK" +
        "DUr1SlV0fQ";

    private static final String VALID_TOKEN_EXPIRED = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUu" +
        "Y29tIiwiYWRtaW4iOnRydWUsImV4cCI6OTQ2Njg0ODAwfQ.j12z_REiEdMAgmZT4_XTZ-bsn8Yh-61f2ibraLU-7Pp9JTKPsmr63Ah5c5Zn" +
        "gnqVwBKE6wuNPNnIh3E3GqA5Yg";

    private static final String VALID_TOKEN_NOT_EXPIRED = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1w" +
        "bGUuY29tIiwiYWRtaW4iOnRydWUsImV4cCI6NDA3MDkwODgwMH0.BNa7bF8twlRycfz_8K8IPLd-0kNPAIe1DozKtibX6JR1PIpaHlRJT1U" +
        "NdNc7Xw-qFJrLt8aepNN7Bu7C-EjKJQ";

    // Generated on https://jwt.io/
    private static final String TOKEN_NONE_ALGORITHM = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwi" +
        "bmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.2XijNOVI9LXP9nWf-oj2SEWWNlcwmxzlQNGK1WdaWcQ";
    private static final String TOKEN_NONE_ALGORITHM_NO_SIGNATURE = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwi" +
        "bmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.";
    private JwtTokenVerifier sut;
    private JwtTokenVerifier sutEc;

    @BeforeAll
    static void init() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @BeforeEach
    void setup() {
        DefaultPublicKeyProvider pubKeyProvider = new DefaultPublicKeyProvider(new JwtConfiguration(ImmutableList.of(PUBLIC_PEM_KEY)), new PublicKeyReader());
        sut = new JwtTokenVerifier(pubKeyProvider);

        pubKeyProvider = new DefaultPublicKeyProvider(new JwtConfiguration(ImmutableList.of(PUBLIC_PEM_KEY_EC)), new PublicKeyReader());
        sutEc = new JwtTokenVerifier(pubKeyProvider);
    }

    @Test
    void shouldReturnTrueOnValidSignature() {
        assertThat(sut.verifyAndExtractLogin(VALID_TOKEN_WITHOUT_ADMIN)).isPresent();
    }

    @Test
    void verifyAndExtractClaimShouldAllowExtractingClaim() {
        assertThat(sut.verifyAndExtractClaim(VALID_TOKEN_WITHOUT_ADMIN, "name", String.class))
            .contains("John Doe");
    }

    @Test
    void verifyAndExtractClaimShouldReturnEmptyWhenBadDatatype() {
        assertThat(sut.verifyAndExtractClaim(VALID_TOKEN_WITHOUT_ADMIN, "name", Integer.class))
            .isEmpty();
    }

    @Test
    void verifyAndExtractClaimShouldReturnEmptyWhenNotFound() {
        assertThat(sut.verifyAndExtractClaim(VALID_TOKEN_WITHOUT_ADMIN, "notFound", String.class))
            .isEmpty();
    }


    @Test
    void verifyAndExtractClaimShouldReturnEmptyWhenNoneAlgorithm() {
        assertThat(sut.verifyAndExtractClaim(TOKEN_NONE_ALGORITHM, "name", String.class))
            .isEmpty();
    }

    @Test
    void verifyAndExtractLoginShouldReturnEmptyOnMismatchingSigningKey() {
        String invalidToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.Pd6t82" +
            "tPL3EZdkeYxw_DV2KimE1U2FvuLHmfR_mimJ5US3JFU4J2Gd94O7rwpSTGN1B9h-_lsTebo4ua4xHsTtmczZ9xa8a_kWKaSkqFjNFa" +
            "Fp6zcoD6ivCu03SlRqsQzSRHXo6TKbnqOt9D6Y2rNa3C4igSwoS0jUE4BgpXbc0";

        assertThat(sut.verifyAndExtractClaim(invalidToken, "name", String.class)).isEmpty();
    }

    @Test
    void shouldReturnTrueOnValidSignatureWithMultipleKeys() {
        DefaultPublicKeyProvider pubKeyProvider = new DefaultPublicKeyProvider(new JwtConfiguration(ImmutableList.of(PUBLIC_PEM_KEY_2, PUBLIC_PEM_KEY)), new PublicKeyReader());
        JwtTokenVerifier sut = new JwtTokenVerifier(pubKeyProvider);
        assertThat(sut.verifyAndExtractLogin(VALID_TOKEN_WITHOUT_ADMIN)).isPresent();
    }

    @Test
    void shouldReturnFalseOnMismatchingSigningKey() {
        String invalidToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.Pd6t82" +
                "tPL3EZdkeYxw_DV2KimE1U2FvuLHmfR_mimJ5US3JFU4J2Gd94O7rwpSTGN1B9h-_lsTebo4ua4xHsTtmczZ9xa8a_kWKaSkqFjNFa" +
                "Fp6zcoD6ivCu03SlRqsQzSRHXo6TKbnqOt9D6Y2rNa3C4igSwoS0jUE4BgpXbc0";

        assertThat(sut.verifyAndExtractLogin(invalidToken)).isEmpty();
    }

    @Test
    void shouldReturnFalseOnMismatchingSigningKeyWithMultipleKeys() {
        String invalidToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.Pd6t82" +
                "tPL3EZdkeYxw_DV2KimE1U2FvuLHmfR_mimJ5US3JFU4J2Gd94O7rwpSTGN1B9h-_lsTebo4ua4xHsTtmczZ9xa8a_kWKaSkqFjNFa" +
                "Fp6zcoD6ivCu03SlRqsQzSRHXo6TKbnqOt9D6Y2rNa3C4igSwoS0jUE4BgpXbc0";

        DefaultPublicKeyProvider pubKeyProvider = new DefaultPublicKeyProvider(new JwtConfiguration(ImmutableList.of(PUBLIC_PEM_KEY_2, PUBLIC_PEM_KEY)), new PublicKeyReader());
        JwtTokenVerifier sut = new JwtTokenVerifier(pubKeyProvider);

        assertThat(sut.verifyAndExtractLogin(invalidToken)).isEmpty();
    }

    @Test
    void verifyShouldReturnFalseWhenSubjectIsNull() {
        String tokenWithNullSubject = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOm51bGwsIm5hbWUiOiJKb2huIERvZSJ9.EB" +
                "_1grWDy_kFelXs3AQeiP13ay4eG_134dWB9XPRSeWsuPs8Mz2UY-VHDxLGD-fAqv-xKXr4QFEnS7iZkdpe0tPLNSwIjqeqkC6KqQln" +
                "oC1okqWVWBDOcf7Acp1Jzp_cFTUhL5LkHvZDsyCdq5T9OOVVkzO4A9RrzIUsTrYPtRCBuYJ3ggR33cKpw191PulPGNH70rZqpUfDXe" +
                "VPY3q15vWzZH9O9IJzB2KdHRMPxl2luRjzDbh4DLp56NhZuLX_2a9UAlmbV8MQX4Z_04ybhAYrcBfxR3MgJyr0jlxSibqSbXrkXuo-" +
                "PyybfZCIhK_qXUlO5OS6sO7AQhKZO9p0MQ";

        assertThat(sut.verifyAndExtractLogin(tokenWithNullSubject)).isEmpty();
    }
    
    @Test
    void verifyShouldReturnFalseWhenEmptySubject() {
        String tokenWithEmptySubject = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIiLCJuYW1lIjoiSm9obiBEb2UifQ.UdY" +
                "s2PPzFCegUYspoDCnlJR_bJm8_z1InOv4v3tq8SJETQUarOXlhb_n6y6ujVvmJiSx9dI24Hc3Czi3RGUOXbnBDj1WPfd0aVSiUSqZr" +
                "MCHBt5vjCYqAseDaP3w4aiiFb6EV3tteJFeBLZx8XlKPYxlzRLLUADDyDSQvrFBBPxfsvCETZovKdo9ofIN64o-yx23ss63yE6oIOd" +
                "zJZ1Id40KSR2d7l3kIQJPLKUWJDnro5RAh4DOGOWNSq0JSbMhk7Zn3cXIBUpv3R8p79tui1UQpzwHMC0e6OSuWEDNQHtq-Cz85u8GG" +
                "sUSbogmgObA_BimNtUq_Q1p0SGtIYBXmQ";


        assertThat(sut.verifyAndExtractLogin(tokenWithEmptySubject)).isEmpty();
    }

    @Test
    void verifyShouldNotAcceptNoneAlgorithm() {
        assertThat(sut.verifyAndExtractLogin(TOKEN_NONE_ALGORITHM)).isEmpty();
    }

    @Test
    void verifyShouldNotAcceptNoneAlgorithmWithoutSignature() {
        assertThat(sut.verifyAndExtractLogin(TOKEN_NONE_ALGORITHM_NO_SIGNATURE)).isEmpty();
    }

    @Test
    void shouldReturnUserLoginFromValidToken() {
        assertThat(sut.verifyAndExtractLogin(VALID_TOKEN_WITHOUT_ADMIN)).contains("1234567890");
    }

    @Test
    void hasAttributeShouldReturnFalseOnNoneAlgorithm() throws Exception {
        boolean authorized = sut.hasAttribute("admin", true, TOKEN_NONE_ALGORITHM);

        assertThat(authorized).isFalse();
    }

    @Test
    void hasAttributeShouldReturnFalseOnNoneAlgorithmWithoutSignature() throws Exception {
        boolean authorized = sut.hasAttribute("admin", true, TOKEN_NONE_ALGORITHM_NO_SIGNATURE);

        assertThat(authorized).isFalse();
    }

    @Test
    void hasAttributeShouldReturnTrueIfClaimValid() throws Exception {
        boolean authorized = sut.hasAttribute("admin", true, VALID_TOKEN_ADMIN_TRUE);

        assertThat(authorized).isTrue();
    }

    @Test
    void extractLoginShouldWorkWithAdminClaim() {
        assertThat(sut.verifyAndExtractLogin(VALID_TOKEN_ADMIN_TRUE)).contains("admin@open-paas.org");
    }

    @Test
    void hasAttributeShouldThrowIfClaimInvalid() throws Exception {
        boolean authorized = sut.hasAttribute("admin", true, VALID_TOKEN_ADMIN_FALSE);

        assertThat(authorized).isFalse();
    }

    @Test
    void verifyShouldWorkIfNoExpiration() {
        boolean authorized = sutEc.verifyAndExtractLogin(VALID_TOKEN_NO_EXPIRATION).isPresent();

        assertThat(authorized).isTrue();
    }

    @Test
    void verifyShouldWorkIfNotExpired() {
        boolean authorized = sutEc.verifyAndExtractLogin(VALID_TOKEN_NOT_EXPIRED).isPresent();

        assertThat(authorized).isTrue();
    }

    @Test
    void verifyShouldFailIfExpired() {
        boolean authorized = sutEc.verifyAndExtractLogin(VALID_TOKEN_EXPIRED).isPresent();

        assertThat(authorized).isFalse();
    }

}
