package org.apache.james.json;

public interface DTO<T> {
    T toDomainObject();
}
