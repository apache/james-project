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

public class OidcTokenFixture {

    public static final String PRIVATE_KEY_BASE64 = "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCroSIEhNYajXzC\n" +
        "gsn+xetgjqYc/SaihaHCIjWra2xMbkyl42BITRmjBFGbUxThMEg5YvaBXC1XQeib\n" +
        "auW7gJnBZs8S54K5FMyjgUXOKbjHqPRxE76vUaIYkAZoeufAnXosfDf/XUZTTKE2\n" +
        "yxyZJhdfgU/RSEpN19joXfskIQWmIXlMKkIG9lGqj7eIcyomdlHHuYxb9owqU+lP\n" +
        "JlLUBO8MDnogOOAXmC3yoSwePnXLL+6NLgLp7iQgr2xR58f9zfTJY9CiYuENBQPS\n" +
        "ZHIbI3/Oj9JBuT1LiFCCHI0Nv9kmOl+hnJKM14GtUU+2ltQfLwMr0dZY6oSy/WQA\n" +
        "LJ8TutCdAgMBAAECggEADuJ73If3uJhOOmFU6uVX10kv02JKHDy6LWfBn1MC8rL7\n" +
        "L0TjuAmFwGtSt4WSUUBady2jws7PeNqUtYvlDiimTQ1hRpqw7ePFpqWCvBFGvvwi\n" +
        "bP8hIbvS8s3k3GFSYZRWwO5p/dnlTDqgXavqWEbjLot8tGIQXaVrYKWr7vN1NbXb\n" +
        "k5GmH+Ljq07Ac2voDMzfoqX8o+XPNCcaO7ndFC7oFn85ixx1PnwUmKJBau7q8YBh\n" +
        "4ixfe1cJq+Tyeg3oFtBPHqpTEC+7bIoxsLo35aYfwYJpYI7p4fdgJx38U1/22Arv\n" +
        "GQuEp61PGE7DBlV1Np4EBNtXm00+lLClwhtljNKo2QKBgQDcZhjzbdlqflR0fo5B\n" +
        "JUwmams8B3FTi7fv9mJZmLWeFYF7Jr2GUG31QkJLSoHpsaaYotgOMkapbDdVp7Lp\n" +
        "57oaZLIpCTjLrUVeoMgUUB1zd68Kq//oZJFE8n/ms3osuGUaBCNmRkGwTbyT2HT4\n" +
        "DrwQXek8V9noz3Oiok2qS4BOtQKBgQDHWlR5+2E6LIQ9GMOXtUNq55Ypx9DImubz\n" +
        "CZomm6ntCI2Oo6JcyXstrZiVlAAGFbdCgBWhfsF0vXjNwVQyuBIqxQQXY4e31bRx\n" +
        "O0x1Zn3qwWoL1YiT6IS/wgGWrGnPEKSFM8e3A8e+lg2KX/w/n5EV9t47jB+Z/SIq\n" +
        "Adtp1T9DSQKBgAHljwlpRJm6BJgMDsVYwNMDz3Bwz/TcGvUhta73tXhqzvZ0WuUx\n" +
        "BAE8VL25Im0Ubk6Z+CoHHLyQhdli3BNPPzbC7xWTUr3N++7Yi2BBYD+CJCt/V3B0\n" +
        "jRt+ysL9gGuqGpSivHtA14fg42KSVk9cMRoi9MLkLqfmQSSBKRHyHGSRAoGAfyCi\n" +
        "mHtvQErdNZ0SNi+4w3bV8uTixtrJhplL/WztSyRWKW0+gA9YhwOaN2D/NuIoULcf\n" +
        "lDIiKlEdtZChIgryQuYKuuOUy+3zOPZIxuFKUSdwegV9KF1yMlsE6lIe05ZYZD1m\n" +
        "EdbOTUKhdenKEcSvICOjCrRL/sZHQCSZCH+d7UkCgYAbGniqH/pp73sGd9NZyVT/\n" +
        "HJdOH5dfBfS9sBBJ1f0/pySJLKcArOXS9BMIFueOq4EIc+7hKDCQuqeyhpYZ6UCe\n" +
        "C9h0QNig49qGI/UEtlNrIlydHyPinTa1fDqu99EuRHG0d4RuONW45tmZAY7mGIbf\n" +
        "PRhJhwOHZT9xO+uPrtQIAw==\n";
    public static final String PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n" +
        PRIVATE_KEY_BASE64 +
        "-----END PRIVATE KEY-----";

    public static final String PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n" +
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAq6EiBITWGo18woLJ/sXr\n" +
        "YI6mHP0mooWhwiI1q2tsTG5MpeNgSE0ZowRRm1MU4TBIOWL2gVwtV0Hom2rlu4CZ\n" +
        "wWbPEueCuRTMo4FFzim4x6j0cRO+r1GiGJAGaHrnwJ16LHw3/11GU0yhNsscmSYX\n" +
        "X4FP0UhKTdfY6F37JCEFpiF5TCpCBvZRqo+3iHMqJnZRx7mMW/aMKlPpTyZS1ATv\n" +
        "DA56IDjgF5gt8qEsHj51yy/ujS4C6e4kIK9sUefH/c30yWPQomLhDQUD0mRyGyN/\n" +
        "zo/SQbk9S4hQghyNDb/ZJjpfoZySjNeBrVFPtpbUHy8DK9HWWOqEsv1kACyfE7rQ\n" +
        "nQIDAQAB\n" +
        "-----END PUBLIC KEY-----";

    public static final String KEY_KID = "w80Ps5Iasn-aGWmw2TrxDiNcahpH2sXz5pqdhAl9HXc";
    public static final String JWKS_RESPONSE = "{" +
        "    \"keys\": [" +
        "        {" +
        "            \"kid\": \"" + KEY_KID + "\"," +
        "            \"kty\": \"RSA\"," +
        "            \"alg\": \"RS256\"," +
        "            \"use\": \"sig\"," +
        "            \"n\": \"q6EiBITWGo18woLJ_sXrYI6mHP0mooWhwiI1q2tsTG5MpeNgSE0ZowRRm1MU4TBIOWL2gVwtV0Hom2rlu4CZwWbPEueCuRTMo4FFzim4x6j0cRO-r1GiGJAGaHrnwJ16LHw3_11GU0yhNsscmSYXX4FP0UhKTdfY6F37JCEFpiF5TCpCBvZRqo-3iHMqJnZRx7mMW_aMKlPpTyZS1ATvDA56IDjgF5gt8qEsHj51yy_ujS4C6e4kIK9sUefH_c30yWPQomLhDQUD0mRyGyN_zo_SQbk9S4hQghyNDb_ZJjpfoZySjNeBrVFPtpbUHy8DK9HWWOqEsv1kACyfE7rQnQ\"," +
        "            \"e\": \"AQAB\"," +
        "            \"x5c\": [" +
        "                \"MIICmzCCAYMCBgF91kYdyzANBgkqhkiG9w0BAQsFADARMQ8wDQYDVQQDDAZyZWFsbTEwHhcNMjExMjIwMDUxNTU5WhcNMzExMjIwMDUxNzM5WjARMQ8wDQYDVQQDDAZyZWFsbTEwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCroSIEhNYajXzCgsn+xetgjqYc/SaihaHCIjWra2xMbkyl42BITRmjBFGbUxThMEg5YvaBXC1XQeibauW7gJnBZs8S54K5FMyjgUXOKbjHqPRxE76vUaIYkAZoeufAnXosfDf/XUZTTKE2yxyZJhdfgU/RSEpN19joXfskIQWmIXlMKkIG9lGqj7eIcyomdlHHuYxb9owqU+lPJlLUBO8MDnogOOAXmC3yoSwePnXLL+6NLgLp7iQgr2xR58f9zfTJY9CiYuENBQPSZHIbI3/Oj9JBuT1LiFCCHI0Nv9kmOl+hnJKM14GtUU+2ltQfLwMr0dZY6oSy/WQALJ8TutCdAgMBAAEwDQYJKoZIhvcNAQELBQADggEBADbwilJj3iLRyuypfYakEv42L5RDrwgImjmXvaX77Bjacr9IjaEyRAVZI7UGu62qN0lV3DxOFwdhsCoyXtufiz1nuvsuZ1M/M/RSe9iJOitQQVkS+OcDayN/GSeHZD7p8V+eY6rtvdNrxINVcuAxPYL+QLbXD5yctaOxs+HfDK9bDYyedpEbtjGnyTzioKHimM7W3PBYGpFfdAhiAcckyd+lfjBfEkjDlJBqzPgdkvTa+tZrR2kA1/QGVBuOpScHI7OlXQnuTXCJqwp8l6lI4umsosjlWw28EShDJQ6SDiNUqtpcbpAVc818PTqO1pdH269i9nqujHkPmdPOddV4nWI=\"" +
        "            ]," +
        "            \"x5t\": \"_LzkYFjU_do-qUGGVHGwwFNKaDQ\"," +
        "            \"x5t#S256\": \"uK879e7BK9sCySdxp16FIW0M7y1-pIvbISgJo6elcHk\"" +
        "        }" +
        "    ]" +
        "}";

    public static final String USERINFO_RESPONSE = "{" +
        "    \"sub\": \"a0d03864-12f7-4f0b-b732-699c27eff3e7\"," +
        "    \"email_verified\": false," +
        "    \"name\": \"User name 1\"," +
        "    \"preferred_username\": \"james\"," +
        "    \"email_address\": \"user@domain.org\"," +
        "    \"email\": \"user@domain.org\"" +
        "}";

    public static final String INTROSPECTION_RESPONSE = "{" +
        "    \"exp\": 1669719841," +
        "    \"iat\": 1669719541," +
        "    \"aud\": \"account\"," +
        "    \"sub\": \"a0d03864-12f7-4f0b-b732-699c27eff3e7\"," +
        "    \"typ\": \"Bearer\"," +
        "    \"session_state\": \"42799d76-be33-4f24-bcec-fc0dbb5d126d\"," +
        "    \"preferred_username\": \"james\"," +
        "    \"email_address\": \"user@domain.org\"," +
        "    \"email\": \"user@domain.org\"," +
        "    \"scope\": \"profile email\"," +
        "    \"sid\": \"42799d76-be33-4f24-bcec-fc0dbb5d126d\"," +
        "    \"client_id\": \"james-thunderbird\"," +
        "    \"username\": \"user1\"," +
        "    \"active\": true" +
        "}";

    public static final String CLAIM = "email_address";
    //  "email_address": "user@domain.org"
    public static final String VALID_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Inc4MFBzNUlhc24tYUdXbXcyVHJ4RGlOY2FocEgyc1h6NXBxZGhBbDlIWGMifQ.eyJleHAiOjM5Mzk1MDYxNjcsImlhdCI6MTYzOTUwNTg2NywiYXV0aF90aW1lIjozNjM5NTA1ODQxLCJqdGkiOiJjMjQ5ZTBkNi1jY2JiLTRmZDAtODI5Yi04OTM1MjczN2YzZGIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvYXV0aC9yZWFsbXMvcmVhbG0xIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6IjIwNDUyNzFiLWMxYmItNDJiOC1hMTkwLThlYWI1MmYzYmEwOSIsInR5cCI6IkJlYXJlciIsImF6cCI6ImFjY291bnQtY29uc29sZSIsIm5vbmNlIjoiNWUyOGJjNTAtODE5NS00NjM3LThmMWEtYWUzNWFlYTk0NTc1Iiwic2Vzc2lvbl9zdGF0ZSI6ImMxYzI3MmYwLWMwMjAtNGZmMC1hMzYwLTQ3MGJlYWVlNWUwMCIsImFjciI6IjAiLCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIl19fSwic2NvcGUiOiJvcGVuaWQgZW1haWwgcHJvZmlsZSIsInNpZCI6ImMxYzI3MmYwLWMwMjAtNGZmMC1hMzYwLTQ3MGJlYWVlNWUwMCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoiamFtZXMiLCJlbWFpbF9hZGRyZXNzIjoidXNlckBkb21haW4ub3JnIn0.bqHsX3yngXwXyVW7LenKzHbdqZy1AmCjE3QWrp7Y1sd_zcQEu5WABwLIOAzrXiNFeGwyww8taGJBdYa0KTBCY6MYkAHAEa1vyyO1LfJgr3cIfQT6WCf3g2BJqHRjUsqNgT_Sit9druMRke01m1V0EmzqIdLLHp8Vl-u4R3JSDx1bsQ1w3WCRlcgr_k3EJ7jNiuNnklCH8_o59y4c7Rzdpl-Y8tcA07nGjeJ_7qPgNZX6lgwvr0EhpQpbVDHXwQlp2NDzkWwBLJR0-V50Q0a-L0QD69wqeEaqi1xaRAfx2Gwn2FgCgMUWzKeW_qkEBP0tnN-pzl7j31EOnmKhshlOtw";
    public static final String VALID_TOKEN_HAS_NOT_KID = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjM5Mzk1MDYxNjcsImlhdCI6MTYzOTUwNTg2NywiYXV0aF90aW1lIjozNjM5NTA1ODQxLCJqdGkiOiJjMjQ5ZTBkNi1jY2JiLTRmZDAtODI5Yi04OTM1MjczN2YzZGIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvYXV0aC9yZWFsbXMvcmVhbG0xIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6IjIwNDUyNzFiLWMxYmItNDJiOC1hMTkwLThlYWI1MmYzYmEwOSIsInR5cCI6IkJlYXJlciIsImF6cCI6ImFjY291bnQtY29uc29sZSIsIm5vbmNlIjoiNWUyOGJjNTAtODE5NS00NjM3LThmMWEtYWUzNWFlYTk0NTc1Iiwic2Vzc2lvbl9zdGF0ZSI6ImMxYzI3MmYwLWMwMjAtNGZmMC1hMzYwLTQ3MGJlYWVlNWUwMCIsImFjciI6IjAiLCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIl19fSwic2NvcGUiOiJvcGVuaWQgZW1haWwgcHJvZmlsZSIsInNpZCI6ImMxYzI3MmYwLWMwMjAtNGZmMC1hMzYwLTQ3MGJlYWVlNWUwMCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoiamFtZXMiLCJlbWFpbF9hZGRyZXNzIjoidXNlckBkb21haW4ub3JnIn0.GR0Xi0de9_G_PyX4f3oj-_VWAIiae0UAOvFJZT3Jy3hqh2gFxC83PmCNKYXMVg8VXfdEHJRqjF4-swqVRGJGGlrz7C-0-sBh4geoh5HIPw4nSfQsdr2NS9IBPFurJjBJqf2u0VM9lZdvRnameFGZasSv0Ob6tnm4oLcL3MfFK5AO9NQslrV7RUCPgjF6B7FFoimvXp1dPYfL_6L_yQeyscroIWxmkcheXSA-yRf5jdmn3MTFfpvrBi-VT8HEueJSkk5HjU7PlMUesaZG07B98Q4eN8CmsKhQNDf__DMCRuVhUstcNbWXk0z_loEHARjnBDTl74cm6yVLI2mMYtrHkg";
    public static final String VALID_TOKEN_HAS_NOT_FOUND_KID = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6Im5vdEZvdW5kIn0.eyJleHAiOjM5Mzk1MDYxNjcsImlhdCI6MTYzOTUwNTg2NywiYXV0aF90aW1lIjozNjM5NTA1ODQxLCJqdGkiOiJjMjQ5ZTBkNi1jY2JiLTRmZDAtODI5Yi04OTM1MjczN2YzZGIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvYXV0aC9yZWFsbXMvcmVhbG0xIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6IjIwNDUyNzFiLWMxYmItNDJiOC1hMTkwLThlYWI1MmYzYmEwOSIsInR5cCI6IkJlYXJlciIsImF6cCI6ImFjY291bnQtY29uc29sZSIsIm5vbmNlIjoiNWUyOGJjNTAtODE5NS00NjM3LThmMWEtYWUzNWFlYTk0NTc1Iiwic2Vzc2lvbl9zdGF0ZSI6ImMxYzI3MmYwLWMwMjAtNGZmMC1hMzYwLTQ3MGJlYWVlNWUwMCIsImFjciI6IjAiLCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIl19fSwic2NvcGUiOiJvcGVuaWQgZW1haWwgcHJvZmlsZSIsInNpZCI6ImMxYzI3MmYwLWMwMjAtNGZmMC1hMzYwLTQ3MGJlYWVlNWUwMCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoiamFtZXMiLCJlbWFpbF9hZGRyZXNzIjoidXNlckBkb21haW4ub3JnIn0.dgcqfAhyUxw1nLgqojWjrFzxjJaJX-xGpb2kMPe_3fbTBauXndI5y1CMyxvG9yA3BevijqdUOZ5s6oLAJc_1qQ45KYf7Oh3jiNpw3CcDk4cLnap5NbdsiDHM10HrJl7qbaUVa1-YljloGMk6qbYRjM_UKYyfRDHbqkPnMhyGQuG_4oSjuQMOXhCDvXUSfjpP20efQxFoZA7A5MDPd0YXs2UxGR1Hg6POW9zNZkH4XQms0SXfxY87tnt7ETN11t9xB3i1XrYOjts7rwRfnu3eXTcFQQhWd14hm9b-_DwMisfvPNrIAHIrY_dCvmOe87ekHL-5VYMaB8x5g_gjQUaUsw";
    public static final String INVALID_TOKEN = "eyJhbGciOiJSUzI1NiIsImFhYSI6MjUxfQ.eyJpc3MiOiJEaW5vQ2hpZXNhLmdpdGh1Yi5pbyIsInN1YiI6ImFzYSIsImF1ZCI6Im1pbmciLCJpYXQiOjE2Mzk2Mzk2OTUsImV4cCI6MTYzOTY0MDI5NX0.DyZDvrR62NSranW6NRsBG9s18jKrqmYR6LwwOmHNmEsWak0ijPRc3ROMMCB-v-ckDEOn_EUcaQ-YifcFzIxikqZ8TQg3wodjxfA7iTcbl-_5OKfaK4_AHUN6hvQ7DQE9EpdzPKFaH5mupWW4NCghuGRjTTz9iP0qkWmtPluSMtjmVg_62Cf2Y7RIWeLvOdEvLbUDuu_-kd1IsVReuHaoqfwejxVFMqxotre_M5G9fcXrllis4tUwY8dqUBBy9pYC3wRFaT7KTFBjtg2_CLCtg8vw9Nu1XAJwuVQmmHaVBkEX4FVgrIu0KwPWbuk1Dg9mgbAVI9osePL2QvTmqSobbQ";

}
