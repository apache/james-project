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

package org.apache.james.mailbox.tika;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.tika.TikaTextExtractor.ContentAndMetadataDeserializer;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

class TikaTextExtractorTest {

    TextExtractor textExtractor;

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @BeforeEach
    void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(), new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
    }

    @Test
    void textualContentShouldReturnEmptyWhenInputStreamIsEmpty() throws Exception {
        assertThat(textExtractor.extractContent(IOUtils.toInputStream("", StandardCharsets.UTF_8), ContentType.of("text/plain"))
            .getTextualContent())
            .contains("");
    }

    @Test
    void textTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/Text.txt");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("text/plain")).getTextualContent())
            .isPresent()
            .asString()
            .contains("This is some awesome text text.");
    }

    @Test
    void textMicrosoftWorldTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/writter.docx");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream,
                ContentType.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .getTextualContent())
            .isPresent()
            .asString()
            .contains("This is an awesome document on libroffice writter !");
    }

    @Test
    void textOdtTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/writter.odt");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("application/vnd.oasis.opendocument.text"))
                .getTextualContent())
            .isPresent()
            .asString()
            .contains("This is an awesome document on libroffice writter !");
    }

    @Test
    void documentWithBadDeclaredMetadataShouldBeWellHandled() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/fake.txt");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("application/vnd.oasis.opendocument.text"))
                .getTextualContent())
            .isPresent()
            .asString()
            .contains("This is an awesome document on libroffice writter !");
    }
    
    @Test
    void slidePowerPointTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/slides.pptx");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")).getTextualContent())
            .isPresent()
            .asString()
            .contains("James is awesome")
            .contains("It manages attachments so well !");
    }

    @Test
    void slideOdpTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/slides.odp");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("application/vnd.oasis.opendocument.presentation"))
                .getTextualContent())
            .isPresent()
            .asString()
            .contains("James is awesome")
            .contains("It manages attachments so well !");
    }
    
    @Test
    void pdfTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/PDF.pdf");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("application/pdf"))
                .getTextualContent())
            .isPresent()
            .asString()
            .contains("This is an awesome document on libroffice writter !");
    }
    
    @Test
    void odsTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/calc.ods");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("application/vnd.oasis.opendocument.spreadsheet"))
                .getTextualContent())
            .isPresent()
            .asString()
            .contains("This is an aesome LibreOffice document !");
    }
    
    @Test
    void excelTest() throws Exception {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream("documents/calc.xlsx");
        assertThat(inputStream).isNotNull();
        assertThat(textExtractor.extractContent(inputStream, ContentType.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .getTextualContent())
            .isPresent()
            .asString()
            .contains("Feuille1")
            .contains("This is an aesome LibreOffice document !");
    }

    @Test
    void deserializerShouldNotThrowWhenMoreThanOneNode() throws Exception {
        TikaTextExtractor textExtractor = new TikaTextExtractor(
            new RecordingMetricFactory(),
            (inputStream, contentType) -> Optional.of(new ByteArrayInputStream(("[{\"X-TIKA:content\": \"This is an awesome LibreOffice document !\"}, " +
                                                            "{\"Chroma BlackIsZero\": \"true\"}]")
                                                        .getBytes(StandardCharsets.UTF_8))));

        InputStream inputStream = null;
        textExtractor.extractContent(inputStream, ContentType.of("text/plain"));
    }

    @Test
    void deserializerShouldTakeFirstNodeWhenSeveral() throws Exception {
        String expectedExtractedContent = "content A";
        TikaTextExtractor textExtractor = new TikaTextExtractor(
            new RecordingMetricFactory(),
            (inputStream, contentType) -> Optional.of(new ByteArrayInputStream(("[{\"X-TIKA:content\": \"" + expectedExtractedContent + "\"}, " +
                                                            "{\"X-TIKA:content\": \"content B\"}]")
                                                        .getBytes(StandardCharsets.UTF_8))));

        InputStream inputStream = new ByteArrayInputStream("toto".getBytes(StandardCharsets.UTF_8));
        ParsedContent parsedContent = textExtractor.extractContent(inputStream,
            ContentType.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        assertThat(parsedContent.getTextualContent()).contains(expectedExtractedContent);
    }

    @Test
    void deserializerShouldThrowWhenNodeIsNotAnObject() {
        TikaTextExtractor textExtractor = new TikaTextExtractor(
            new RecordingMetricFactory(),
            (inputStream, contentType) -> Optional.of(new ByteArrayInputStream("[\"value1\"]"
                                                        .getBytes(StandardCharsets.UTF_8))));

        InputStream inputStream = new ByteArrayInputStream("toto".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> textExtractor.extractContent(inputStream,
                ContentType.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The element should be a Json object");
    }

    @Test
    void asListOfStringShouldReturnASingletonWhenOneElement() {
        ContentAndMetadataDeserializer deserializer = new TikaTextExtractor.ContentAndMetadataDeserializer();
        List<String> listOfString = deserializer.asListOfString(TextNode.valueOf("text"));
        
        assertThat(listOfString).containsOnly("text");
    }

    @Test
    void asListOfStringShouldReturnAListWhenMultipleElements() {
        ArrayNode jsonArray = new ArrayNode(JsonNodeFactory.instance)
            .add("first")
            .add("second")
            .add("third");

        ContentAndMetadataDeserializer deserializer = new TikaTextExtractor.ContentAndMetadataDeserializer();
        List<String> listOfString = deserializer.asListOfString(jsonArray);
        
        assertThat(listOfString).containsOnly("first", "second", "third");
    }
}
