package org.apache.james.server.task.json.dto;

import org.apache.james.json.DTO;
import org.apache.james.task.Task;

public interface TaskDTO<T extends Task> extends DTO<T> {

    T toTask();

    String getType();

    default T toDomainObject() {
        return toTask();
    }
}