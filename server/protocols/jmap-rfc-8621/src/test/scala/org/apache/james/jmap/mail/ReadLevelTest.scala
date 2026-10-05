/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you May not use this file except in compliance   *
 * with the License.  You May obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.mail

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ReadLevelTest extends AnyWordSpec with Matchers {
  private val readLevelsByIncreasingCost: Seq[ReadLevel] = Seq(
    MetadataReadLevel,
    HeaderReadLevel,
    FastViewReadLevel,
    FastViewWithAttachmentsMetadataReadLevel,
    FullReadLevel)

  private val pairs: Seq[(ReadLevel, ReadLevel)] = for {
    readLevel1 <- readLevelsByIncreasingCost
    readLevel2 <- readLevelsByIncreasingCost
  } yield (readLevel1, readLevel2)

  private def mostExpensive(readLevel1: ReadLevel, readLevel2: ReadLevel): ReadLevel =
    Seq(readLevel1, readLevel2).maxBy(readLevelsByIncreasingCost.indexOf)

  "combine" should {
    "return the most expensive read level" in {
      pairs.foreach { case (readLevel1, readLevel2) =>
        ReadLevel.combine(readLevel1, readLevel2) must equal(mostExpensive(readLevel1, readLevel2))
      }
    }

    "be commutative" in {
      pairs.foreach { case (readLevel1, readLevel2) =>
        ReadLevel.combine(readLevel1, readLevel2) must equal(ReadLevel.combine(readLevel2, readLevel1))
      }
    }

    "support attachments followed by another property" in {
      ReadLevel.combine(FastViewWithAttachmentsMetadataReadLevel, MetadataReadLevel) must equal(FastViewWithAttachmentsMetadataReadLevel)
    }
  }
}
