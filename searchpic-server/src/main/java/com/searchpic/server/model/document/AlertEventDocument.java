package com.searchpic.server.model.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

@Data
@Document(indexName = "searchpic_alert_events")
public class AlertEventDocument {

    @Id
    private String id; // Internal ES Document ID

    @Field(type = FieldType.Keyword, index = false)
    private String eventId; // Original Event UUID passed by user

    @Field(type = FieldType.Keyword)
    private String tenantId; // Absolute isolation key

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String cameraId;

    @Field(type = FieldType.Date)
    private Long timestamp; // Epoch milliseconds

    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;

    // Standard English analyzer for BM25 Sparse Search
    @Field(type = FieldType.Text, analyzer = "english") 
    private String entitiesText;

    // 768-dim Dense Vector for k-NN Scene Caption Search
    @Field(type = FieldType.Dense_Vector, dims = 768, similarity = "cosine", index = true)
    private List<Float> captionVector;
}
