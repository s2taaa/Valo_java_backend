package com.iuh.ChatAppValo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.iuh.ChatAppValo.entity.enumEntity.MessageStatus;
import com.iuh.ChatAppValo.entity.enumEntity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@CompoundIndexes(@CompoundIndex(background = true, def = "{conversationId: 1, sendAt: -1}"))    // tạo index kết hợp conversationId và sendAt
public class Message {
    @Id
    private String id;

    private String conversationId;
    private String senderId;

    @CreatedDate
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private Date sendAt;

    private MessageType messageType;
    private String content;
    private MessageStatus messageStatus;
    private String replyId; // phản hồi tin nhắn của người dùng có userId là replyId
    private List<Reaction> reactions;
    private boolean pin;
}
