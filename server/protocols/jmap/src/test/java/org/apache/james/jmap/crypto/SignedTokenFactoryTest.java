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

package org.apache.james.jmap.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.james.jmap.FixedDateZonedDateTimeProvider;
import org.junit.Before;
import org.junit.Test;

public class SignedTokenFactoryTest {

    private static final String EXPIRATION_DATE_STRING = "2011-12-03T10:15:30+01:00";
    private static final ZonedDateTime DATE = ZonedDateTime.parse(EXPIRATION_DATE_STRING, DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    private SignedTokenFactory toKenFactory;
    private FixedDateZonedDateTimeProvider zonedDateTimeProvider;

    @Before
    public void setUp() throws Exception {
        JamesSignatureHandler signatureHandler = new JamesSignatureHandlerProvider().provide();
        zonedDateTimeProvider = new FixedDateZonedDateTimeProvider();
        toKenFactory = new SignedTokenFactory(signatureHandler, zonedDateTimeProvider);
    }

    @Test
    public void generateContinuationTokenShouldThrowWhenUsernameIsNull() throws Exception {
        assertThatThrownBy(() -> toKenFactory.generateContinuationToken(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void generateContinuationTokenShouldHaveTheRightOutPut() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        assertThat(toKenFactory.generateContinuationToken("user").serialize())
            .isEqualTo("user_2011-12-03T10:30:30+01:00_eOvOqTmV3dPrhIkbuQSj2sno3YJMxWl6J1sH1JhwYcaNgMX9twm98/WSF9uyDkvJgvBxFokDr53AbxQ3DsJysB2dAzCC0tUM4u8ZMvl/hQrFXhVCdpVMyHRvixKCxnHsVXAr9g3WMn2vbIVq5i3HPgA6/p9FB1+N4WA06B8ueoCrdxT2w1ITEm8p+QZvje3n1F344SgrqgIYqvt0yUvzxnB24f3ccjAKidlBj4wZkcXgUTMbZ7MdnCbDGbp10+tgJqxiv1S0rXZMeJLJ+vBt5TyqEhsJUmUQ84qctlB4yR5FS+ncbAOyZAxs2dWsHqiQjedb3IR77N7CASzqO2mmVw==");
    }

    @Test
    public void generateAttachmentAccessTokenShouldThrowWhenUsernameIsNull() throws Exception {
        assertThatThrownBy(() -> toKenFactory.generateAttachmentAccessToken(null, "blobId"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void generateAttachmentAccessTokenShouldThrowWhenBlobIdIsNull() throws Exception {
        assertThatThrownBy(() -> toKenFactory.generateAttachmentAccessToken("username", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void generateAttachmentAccessTokenShouldHaveTheRightOutPut() throws Exception {
        zonedDateTimeProvider.setFixedDateTime(DATE);
        assertThat(toKenFactory.generateAttachmentAccessToken("user", "blobId").serialize())
            .isEqualTo("user_2011-12-03T10:20:30+01:00_UAmjTzvmmIwvE1Yw54tE7jC1Q2nCJ1l3XX1703kYmLIeOZe7fNSLM6V8CzPFEvZ+Y4H+UD4UTkNHbmgcPbxesITnby+UfT/tIiTppJhXJvtTxSoTy9vuAJrW9/kJh6CruqtSM+BUEkLKuuzJySmvDkaHSaXwot4egGXaJ9yHgjEh2PT3uA0O0JjRNB2x8oa370fFSZsT2QgXrqeqHWWO1j6IrAf4UcyhvjNkJBK9TVNubfqGKuCZ4dz2Rm/CUvp13CpzUoVqBS1nJ1VaIw94L2rX8RkAMTlV7AXKB3kPiBX7MdGp2NBiAUlYlOLjflYl8plnv/QrRCmfGxnsvv4WVQ==");
    }

}
