package org.apache.james.server.task.json;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.task.Task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;

public class TaskDeserializer {

    private final ObjectMapper objectMapper;

    public interface Factory {
        Task create(JsonNode parameters);
    }

    public static class Type {
        public static Type of(String typeName) {
            return new Type(typeName);
        }

        private final String typeName;

        private Type(String typeName) {
            this.typeName = typeName;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Type) {
                Type that = (Type) o;

                return Objects.equals(this.typeName, that.typeName);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeName);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("typeName", typeName)
                    .toString();
        }
    }

    private final Map<Type, Factory> registry;

    public TaskDeserializer(Map<Type, Factory> registry) {
        this.registry = registry;
        objectMapper = new ObjectMapper();
    }

    public Task deserialize(String taskAsString) throws IOException {
        JsonNode taskAsJson = objectMapper.readTree(taskAsString);
        JsonNode parameters = taskAsJson.get("parameters");

        return getFactory(taskAsJson).create(parameters);
    }

    private Factory getFactory(JsonNode taskAsJson) {
        Type type = Optional.ofNullable(taskAsJson.get("type"))
            .map(JsonNode::asText)
            .map(Type::of)
            .orElseThrow(() -> new InvalidTaskTypeException());
        return Optional.ofNullable(registry.get(type))
            .orElseThrow(() -> new UnsupportedTypeException(type));
    }
}
