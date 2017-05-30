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
    public static final String BR_TAG = "br";
    public static final String UL_TAG = "ul";
    public static final String OL_TAG = "ol";
    public static final String LI_TAG = "li";
    public static final String P_TAG = "p";
    public static final String IMG_TAG = "img";
    public static final String ALT_TAG = "alt";

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
            if (element.tagName().equals(BR_TAG)) {
                return "\n";
            }
            if (element.tagName().equals(UL_TAG)) {
                return "\n\n";
            }
            if (element.tagName().equals(OL_TAG)) {
                return "\n\n";
            }
            if (element.tagName().equals(LI_TAG)) {
                return "\n - ";
            }
            if (element.tagName().equals(P_TAG)) {
                return "\n\n";
            }
            if (element.tagName().equals(IMG_TAG)) {
                return "[" + element.attributes().get(ALT_TAG) + "]";
            }
        }
        return "";
    }

    Stream<Node> flatten(Node base) {
        Position position = getPosition(base);
        Stream<Node> flatChildren = base.childNodes()
            .stream()
            .flatMap(this::flatten);
        switch (position) {
            case PREFIX:
                return Stream.concat(Stream.of(base), flatChildren);
            case SUFFIX:
                return Stream.concat(flatChildren, Stream.of(base));
            default:
                throw new RuntimeException("Unexpected POSITION for node element: " + position);
        }
    }

    private enum Position {
        PREFIX,
        SUFFIX
    }

    private Position getPosition(Node node) {
        if (node instanceof Element) {
            Element element = (Element) node;
            if (element.tagName().equals(LI_TAG)) {
                return Position.PREFIX;
            }
        }
        return Position.SUFFIX;
    }

}
