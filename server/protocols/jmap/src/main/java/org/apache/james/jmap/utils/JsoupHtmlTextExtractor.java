/****************************************************************
 O * Licensed to the Apache Software Foundation (ASF) under one   *
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

package org.apache.james.jmap.utils;

import java.util.Optional;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsoupHtmlTextExtractor implements HtmlTextExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsoupHtmlTextExtractor.class);

    @Override
    public String toPlainText(String html) {
        try {
            Document document = Jsoup.parse(html);

            Element body = Optional.ofNullable(document.body()).orElse(document);

            return flatten(body)
                .map(this::convertNodeToText)
                .reduce("", (s1, s2) -> s1 + s2);
        } catch (Exception e) {
            LOGGER.warn("Failed extracting text from html", e);
            return html;
        }
    }

    private String convertNodeToText(Node node) {
        if (node instanceof TextNode) {
            TextNode textNode = (TextNode) node;
            return textNode.getWholeText();
        }
        if (node instanceof Element) {
            Element element = (Element) node;
            if (element.tagName().equals("br")) {
                return "\n";
            }
            if (element.tagName().equals("p")) {
                return "\n\n";
            }
        }
        return "";
    }

    Stream<Node> flatten(Node base) {
        return Stream.concat(
            base.childNodes()
                .stream()
                .flatMap(this::flatten),
            Stream.of(base));
    }

}
