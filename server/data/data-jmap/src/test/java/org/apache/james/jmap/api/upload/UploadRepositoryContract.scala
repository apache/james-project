 /***************************************************************
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

 package org.apache.james.jmap.api.upload

 import java.io.InputStream
 import java.nio.charset.StandardCharsets
 import java.util.UUID

 import org.apache.commons.io.IOUtils
 import org.apache.james.core.Username
 import org.apache.james.jmap.api.model.Size.sanitizeSize
 import org.apache.james.jmap.api.model.{Upload, UploadId, UploadNotFoundException}
 import org.apache.james.jmap.api.upload.UploadRepositoryContract.{CONTENT_TYPE, DATA_STRING, USER}
 import org.apache.james.mailbox.model.ContentType
 import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
 import org.junit.jupiter.api.Test
 import reactor.core.scala.publisher.SMono

 object UploadRepositoryContract {
   private lazy val CONTENT_TYPE: ContentType = ContentType
     .of("text/html")
   private lazy val DATA_STRING: String = "123321"
   private lazy val USER: Username = Username.of("Bob")
 }

 trait UploadRepositoryContract {

   def testee: UploadRepository

   def data(): InputStream = IOUtils.toInputStream(DATA_STRING, StandardCharsets.UTF_8)

   @Test
   def uploadShouldSuccess(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block()

     assertThat(SMono.fromPublisher(testee.retrieve(uploadId, USER)).block())
       .isNotNull
   }

   @Test
   def uploadShouldReturnDifferentIdWhenDifferentData(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block()

     assertThat(uploadId)
       .isNotEqualTo(SMono.fromPublisher(testee.upload(IOUtils.toInputStream("abcxyz", StandardCharsets.UTF_8), CONTENT_TYPE, USER)).block())
   }

   @Test
   def uploadSameContentShouldReturnDifferentId(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block()

     assertThat(uploadId)
       .isNotEqualTo(SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block())
   }

   @Test
   def retrieveShouldSuccess(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block()
     val actualUpload: Upload = SMono.fromPublisher(testee.retrieve(uploadId, USER)).block()

     assertThat(actualUpload.uploadId)
       .isEqualTo(uploadId)
     assertThat(actualUpload.contentType)
       .isEqualTo(CONTENT_TYPE)
     assertThat(actualUpload.size)
       .isEqualTo(sanitizeSize(DATA_STRING.length))
     assertThat(actualUpload.content.apply().readAllBytes())
       .isEqualTo(DATA_STRING.getBytes)
   }

   @Test
   def retrieveShouldThrowWhenUploadIdIsNotExist(): Unit = {
     assertThatThrownBy(() => SMono.fromPublisher(testee.retrieve(UploadId.from(UUID.randomUUID()), USER)).block())
       .isInstanceOf(classOf[UploadNotFoundException])
   }

   @Test
   def retrieveShouldThrowWhenUserIsNotOwnerOfUpload(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block()

     assertThatThrownBy(() => SMono.fromPublisher(testee.retrieve(uploadId, Username.of("Alice"))).block())
       .isInstanceOf(classOf[UploadNotFoundException])
   }

 }
