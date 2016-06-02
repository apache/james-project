

package org.apache.james.jmap.utils;

import java.io.ByteArrayInputStream;

import javax.inject.Inject;

import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailboxBasedHtmlTextExtractor implements HtmlTextExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxBasedHtmlTextExtractor.class);

    private final TextExtractor textExtractor;

    @Inject
    public MailboxBasedHtmlTextExtractor(TextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    @Override
    public String toPlainText(String html) {
        try {
            return textExtractor.extractContent(new ByteArrayInputStream(html.getBytes()), "text/html", "").getTextualContent();
        } catch (Exception e) {
            LOGGER.warn("Error extracting text from HTML", e);
            return html;
        }
    }
}
