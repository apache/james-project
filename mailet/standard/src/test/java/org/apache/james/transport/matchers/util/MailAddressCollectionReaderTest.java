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

package org.apache.james.transport.matchers.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.matchers.utils.MailAddressCollectionReader;
import org.junit.jupiter.api.Test;

class MailAddressCollectionReaderTest {

    @Test
    void readShouldThrowOnNullInput() {
        assertThatThrownBy(() -> MailAddressCollectionReader.read(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readShouldThrowOnEmptyInput() {
        assertThatThrownBy(() -> MailAddressCollectionReader.read(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readShouldThrowOnInvalidEmail() {
        assertThatThrownBy(() -> MailAddressCollectionReader.read("not_valid"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void readShouldThrowOnInvalidEmailOnSecondPosition() {
        assertThatThrownBy(() -> MailAddressCollectionReader.read("valid@apache.org, not_valid"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void readShouldParseOneEmail() throws Exception {
        MailAddress mailAddress = new MailAddress("valid@apache.org");

        assertThat(MailAddressCollectionReader.read(mailAddress.toString()))
            .containsExactly(Optional.of(mailAddress));
    }

    @Test
    void readShouldParseNullSender() {
        assertThat(MailAddressCollectionReader.read("<>"))
            .containsExactly(Optional.empty());
    }

    @Test
    void readShouldParseTwoEmailSeparatedByComaOnly() throws Exception {
        MailAddress mailAddress1 = new MailAddress("valid@apache.org");
        MailAddress mailAddress2 = new MailAddress("bis@apache.org");

        assertThat(MailAddressCollectionReader.read(mailAddress1.toString() + "," + mailAddress2.toString()))
            .containsExactly(Optional.of(mailAddress1), Optional.of(mailAddress2));
    }

    @Test
    void readShouldParseTwoEmailSeparatedBySpaceOnly() throws Exception {
        MailAddress mailAddress1 = new MailAddress("valid@apache.org");
        MailAddress mailAddress2 = new MailAddress("bis@apache.org");

        assertThat(MailAddressCollectionReader.read(mailAddress1.toString() + " " + mailAddress2.toString()))
            .containsExactly(Optional.of(mailAddress1), Optional.of(mailAddress2));
    }

    @Test
    void readShouldParseTwoEmailSeparatedByTabOnly() throws Exception {
        MailAddress mailAddress1 = new MailAddress("valid@apache.org");
        MailAddress mailAddress2 = new MailAddress("bis@apache.org");

        assertThat(MailAddressCollectionReader.read(mailAddress1.toString() + "\t" + mailAddress2.toString()))
            .containsExactly(Optional.of(mailAddress1), Optional.of(mailAddress2));
    }


    @Test
    void readShouldParseTwoEmailSeparatorsCombination() throws Exception {
        MailAddress mailAddress1 = new MailAddress("valid@apache.org");
        MailAddress mailAddress2 = new MailAddress("bis@apache.org");

        assertThat(MailAddressCollectionReader.read(mailAddress1.toString() + ",\t  \t,\t \t " + mailAddress2.toString()))
            .containsExactly(Optional.of(mailAddress1), Optional.of(mailAddress2));
    }

    @Test
    void readShouldRemoveDuplicates() throws Exception {
        MailAddress mailAddress = new MailAddress("valid@apache.org");

        assertThat(MailAddressCollectionReader.read(mailAddress.toString() + ", " + mailAddress.toString()))
            .containsExactly(Optional.of(mailAddress));
    }


}
