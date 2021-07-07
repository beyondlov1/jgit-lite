package com.beyond.jgit.pack;

import com.beyond.jgit.object.ObjectEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BaseBlock extends Block{
    private String objectId;
    private byte[] content;
    private ObjectEntity.Type type;

    public BaseBlock() {
    }

    public BaseBlock(String objectId, ObjectEntity.Type type, byte[] content) {
        this.objectId = objectId;
        this.content = content;
        this.type = type;
    }
}
