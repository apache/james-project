package org.apache.james.webadmin.request;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BodyPartProps {

    @JsonCreator
    public BodyPartProps(
            @JsonProperty("type") String type,
            @JsonProperty("filename") String filename,
            @JsonProperty("content") String content,
            @JsonProperty("encoding") String encoding,
            @JsonProperty("disposition") String disposition,
            @JsonProperty("headers") Map<String, String> headers) {

        Optional.ofNullable(type).ifPresent(this::setType);
        this.filename = filename;
        Optional.ofNullable(content).ifPresent(this::setContent);
        this.encoding = encoding;
        this.disposition = disposition;
        Optional.ofNullable(headers).ifPresent(this::setHeaders);
    }

    private String type = "text/plain";

    private String filename;

    private String content;

    private String encoding;

    private String disposition;

    private Map<String, String> headers = new HashMap<>();

    public String getType() {
        return this.type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getFilename() {
        return this.filename;
    }

    public void setFilename(final String filename) {
        this.filename = filename;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public String getEncoding() {
        return this.encoding;
    }

    public void setEncoding(final String encoding) {
        this.encoding = encoding;
    }

    public String getDisposition() {
        return this.disposition;
    }

    public void setDisposition(final String disposition) {
        this.disposition = disposition;
    }

    public Map<String, String> getHeaders() {
        return this.headers;
    }

    public void setHeaders(final Map<String, String> headers) {
        this.headers = headers;
    }

}
