

package org.apache.james.sieve.cassandra.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.sieverepository.api.ScriptSummary;

import com.google.common.base.Preconditions;

public class Script {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String content;
        private Optional<Boolean> isActive = Optional.empty();
        private Optional<Long> size = Optional.empty();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder copyOf(Script script) {
            this.name = script.getName();
            this.content = script.getContent();
            this.isActive = Optional.of(script.isActive());
            this.size = Optional.of(script.getSize());
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder size(long size) {
            this.size = Optional.of(size);
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = Optional.of(isActive);
            return this;
        }

        public Script build() {
            Preconditions.checkState(name != null);
            Preconditions.checkState(content != null);
            Preconditions.checkState(isActive.isPresent());

            return new Script(name,
                content,
                isActive.get(),
                size.orElse((long) content.getBytes(StandardCharsets.UTF_8).length));
        }

    }

    private final String name;
    private final String content;
    private final boolean isActive;
    private final long size;

    private Script(String name, String content, boolean isActive, long size) {
        this.name = name;
        this.content = content;
        this.isActive = isActive;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public boolean isActive() {
        return isActive;
    }

    public ScriptSummary toSummary() {
        return new ScriptSummary(name, isActive);
    }

    public long getSize() {
        return size;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Script) {
            Script that = (Script) o;

            return Objects.equals(this.name, that.name)
                && Objects.equals(this.content, that.content)
                && Objects.equals(this.isActive, that.isActive)
                && Objects.equals(this.size, that.size);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, content, isActive, size);
    }
}
