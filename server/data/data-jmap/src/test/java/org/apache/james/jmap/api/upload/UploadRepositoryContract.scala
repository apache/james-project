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
 import java.time.{Clock, Duration}
 import java.util.UUID

 import org.apache.commons.io.IOUtils
 import org.apache.james.core.Username
 import org.apache.james.jmap.api.model.Size.sanitizeSize
 import org.apache.james.jmap.api.model.{Upload, UploadId, UploadMetaData, UploadNotFoundException}
 import org.apache.james.jmap.api.upload.UploadRepositoryContract.{CONTENT_TYPE, DATA_STRING, USER}
 import org.apache.james.mailbox.model.ContentType
 import org.apache.james.utils.UpdatableTickingClock
 import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
 import org.assertj.core.groups.Tuple.tuple
 import org.junit.jupiter.api.Test
 import reactor.core.scala.publisher.{SFlux, SMono}

 import scala.jdk.CollectionConverters._

 object UploadRepositoryContract {
   private lazy val CONTENT_TYPE: ContentType = ContentType
     .of("text/html")
   private lazy val DATA_STRING: String = "123321"
   private lazy val USER: Username = Username.of("Bob")
 }

 trait UploadRepositoryContract {

   def randomUploadId(): UploadId = UploadId.from(UUID.randomUUID())

   def testee: UploadRepository

   def clock: UpdatableTickingClock

   def data(): InputStream = IOUtils.toInputStream(DATA_STRING, StandardCharsets.UTF_8)

   @Test
   def uploadShouldSuccess(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId

     assertThat(SMono.fromPublisher(testee.retrieve(uploadId, USER)).block())
       .isNotNull
   }

   @Test
   def uploadShouldReturnDifferentIdWhenDifferentData(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId

     assertThat(uploadId)
       .isNotEqualTo(SMono.fromPublisher(testee.upload(IOUtils.toInputStream("abcxyz", StandardCharsets.UTF_8), CONTENT_TYPE, USER)).block())
   }

   @Test
   def uploadSameContentShouldReturnDifferentId(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId

     assertThat(uploadId)
       .isNotEqualTo(SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block())
   }

   @Test
   def retrieveShouldSuccess(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId
     val actualUpload: Upload = SMono.fromPublisher(testee.retrieve(uploadId, USER)).block()

     assertThat(actualUpload.uploadId)
       .isEqualTo(uploadId)
     assertThat(actualUpload.contentType)
       .isEqualTo(CONTENT_TYPE)
     assertThat(actualUpload.size)
       .isEqualTo(sanitizeSize(DATA_STRING.length))
     assertThat(actualUpload.content.apply().readAllBytes())
       .isEqualTo(DATA_STRING.getBytes)
     assertThat(actualUpload.uploadDate)
       .isNotNull
   }

   @Test
   def retrieveShouldThrowWhenUploadIdIsNotExist(): Unit = {
     assertThatThrownBy(() => SMono.fromPublisher(testee.retrieve(randomUploadId(), USER)).block())
       .isInstanceOf(classOf[UploadNotFoundException])
   }

   @Test
   def retrieveShouldThrowWhenUserIsNotOwnerOfUpload(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId

     assertThatThrownBy(() => SMono.fromPublisher(testee.retrieve(uploadId, Username.of("Alice"))).block())
       .isInstanceOf(classOf[UploadNotFoundException])
   }

   @Test
   def listUploadsShouldReturnEmptyWhenNoUpload(): Unit = {
     assertThat(SFlux.fromPublisher(testee.listUploads(USER)).collectSeq().block().asJava)
       .isEmpty()
   }

   @Test
   def listUploadsShouldReturnUserUploads(): Unit = {
     val contentType1: ContentType = ContentType
       .of("text/html")
     val contentType2: ContentType = ContentType
       .of("json")

     val data1: InputStream = IOUtils.toInputStream("123321", StandardCharsets.UTF_8)
     val data2: InputStream = IOUtils.toInputStream("t2", StandardCharsets.UTF_8)

     val uploadId1: UploadId = SMono.fromPublisher(testee.upload(data1, contentType1, USER)).block().uploadId
     val uploadId2: UploadId = SMono.fromPublisher(testee.upload(data2, contentType2, USER)).block().uploadId

     val uploadMetaDataList: Seq[UploadMetaData] = SFlux.fromPublisher(testee.listUploads(USER)).collectSeq().block();

     assertThat(uploadMetaDataList.asJava)
       .extracting("uploadId", "contentType", "size")
       .containsExactlyInAnyOrder(tuple(uploadId1, contentType1, 6L),
         tuple(uploadId2, contentType2, 2L))

     assertThat(uploadMetaDataList.asJava)
       .extracting("blobId", "uploadDate")
       .doesNotContainNull()
   }

   @Test
   def listUploadsShouldReturnTheSameUploadDateAsUpload(): Unit = {
     val contentType1: ContentType = ContentType
       .of("text/html")
     val data1: InputStream = IOUtils.toInputStream("123321", StandardCharsets.UTF_8)

     val upload: UploadMetaData = SMono.fromPublisher(testee.upload(data1, contentType1, USER)).block()

     val listUploadsResult: UploadMetaData = SFlux.fromPublisher(testee.listUploads(USER)).blockFirst().get;

     assertThat(upload.uploadDate).isEqualTo(listUploadsResult.uploadDate)
   }

   @Test
   def listUploadShouldNotReturnEntryOfAnotherUser(): Unit = {
      val data1: InputStream = IOUtils.toInputStream("123321", StandardCharsets.UTF_8)
      val data2: InputStream = IOUtils.toInputStream("t2", StandardCharsets.UTF_8)

      val uploadId1: UploadId = SMono.fromPublisher(testee.upload(data1, CONTENT_TYPE, USER)).block().uploadId
      val uploadId2: UploadId = SMono.fromPublisher(testee.upload(data2, CONTENT_TYPE, Username.of("Alice"))).block().uploadId

      val uploadMetaDataList: Seq[UploadMetaData] = SFlux.fromPublisher(testee.listUploads(USER)).collectSeq().block();

      assertThat(uploadMetaDataList.asJava)
        .extracting("uploadId")
        .containsExactlyInAnyOrder(uploadId1)
   }

   @Test
   def deleteShouldRemoveUpload(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId

     SMono.fromPublisher(testee.delete(uploadId, USER)).block()
     assertThatThrownBy(() => SMono.fromPublisher(testee.retrieve(uploadId, USER)).block())
       .isInstanceOf(classOf[UploadNotFoundException])
   }

   @Test
   def deleteShouldNotThrowWhenUploadIdIsNotExist(): Unit = {
     assertThatCode(() => SMono.fromPublisher(testee.delete(randomUploadId(), USER)).block())
       .doesNotThrowAnyException()
   }

   @Test
   def deleteShouldNotRemoveUploadOfAnotherUser(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId
     SMono.fromPublisher(testee.delete(uploadId, Username.of("Alice"))).block()
     assertThat(SMono.fromPublisher(testee.retrieve(uploadId, USER)).block())
       .isNotNull
   }

   @Test
   def deleteShouldReturnTrueWhenRowExists(): Unit = {
     val uploadId: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId

     assertThat(SMono.fromPublisher(testee.delete(uploadId, USER)).block()).isTrue
   }

   @Test
   def deleteShouldReturnFalseWhenRowDoesNotExist(): Unit = {
     val uploadIdOfAlice: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, Username.of("Alice"))).block().uploadId
     assertThat(SMono.fromPublisher(testee.delete(uploadIdOfAlice, Username.of("Bob"))).block()).isFalse
   }

   def deleteByUploadDateBeforeShouldRemoveExpiredUploads(): Unit = {
     val uploadId1: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId
     clock.setInstant(clock.instant().plus(8, java.time.temporal.ChronoUnit.DAYS))
     val uploadId2: UploadId = SMono.fromPublisher(testee.upload(data(), CONTENT_TYPE, USER)).block().uploadId

     SMono(testee.deleteByUploadDateBefore(Duration.ofDays(7))).block();

     assertThatThrownBy(() => SMono.fromPublisher(testee.retrieve(uploadId1, USER)).block())
       .isInstanceOf(classOf[UploadNotFoundException])
     assertThat(SMono.fromPublisher(testee.retrieve(uploadId2, USER)).block())
       .isNotNull
   }

 }
