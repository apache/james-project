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

package org.apache.james.mailrepository.blob;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.Protocol;
import org.junit.jupiter.api.Test;

class MailRepositoryBlobIdFactoryTest {
    static String MAIL_REPOSITORY_PATH = "/var/mail/error";

    BlobId.Factory blobIdFactory = new PlainBlobId.Factory();
    MailRepositoryUrl url = MailRepositoryUrl.fromPathAndProtocol(
            new Protocol("blob"),
            MailRepositoryPath.from(MAIL_REPOSITORY_PATH)
    );

    @Test
    void ofShouldRelocateBlobIdUnderTheMailRepositoryPath() {
        var id = "0c222abb-d115-4a88-9fbe-e65e951301f6";
        MailRepositoryBlobIdFactory factory = new MailRepositoryBlobIdFactory(blobIdFactory, url);

        BlobId actual = factory.of(id);

        assertThat(actual.asString()).isEqualTo(MAIL_REPOSITORY_PATH + "/" + id);
    }

    @Test
    void parseShouldNotRelocateBlobIdUnderTheMailRepositoryPath() {
        var id = MAIL_REPOSITORY_PATH + "/" + "0c222abb-d115-4a88-9fbe-e65e951301f6";
        MailRepositoryBlobIdFactory factory = new MailRepositoryBlobIdFactory(blobIdFactory, url);

        BlobId actual = factory.parse(id);

        assertThat(actual.asString()).isEqualTo(id);
    }


}