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
package org.apache.james.jmap.mail

import java.util.function.BinaryOperator

object KeywordsCombiner {
  private val KEYWORD_TO_INTERSECT = Set(Keyword.DRAFT)
  private val KEYWORD_NOT_TO_UNION = KEYWORD_TO_INTERSECT
}

final case class KeywordsCombiner() extends BinaryOperator[Keywords] {
  override def apply(keywords: Keywords, keywords2: Keywords): Keywords =
    Keywords(keywords.getKeywords.union(keywords2.getKeywords).filterNot(KeywordsCombiner.KEYWORD_NOT_TO_UNION)
      .union(keywords.getKeywords.intersect(keywords2.getKeywords).filterNot(KeywordsCombiner.KEYWORD_TO_INTERSECT)))

  def union(set1: Set[Keyword], set2: Set[Keyword], exceptKeywords: List[Keyword]): Set[Keyword] =
    set1.union(set2).filter(!exceptKeywords.contains(_))

  def intersect(set1: Set[Keyword], set2: Set[Keyword], forKeywords: List[Keyword]): Set[Keyword] =
    set1.intersect(set2).filter(forKeywords.contains)
}