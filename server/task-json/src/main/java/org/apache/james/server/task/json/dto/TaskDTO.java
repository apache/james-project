package org.apache.james.server.task.json.dto;

import org.apache.james.json.DTO;

public interface TaskDTO extends DTO {

    String getType();
}