package org.sparklingduo.domain.job;

import jakarta.persistence.*;
import lombok.*;
import org.sparklingduo.domain.template.FieldType;

import java.util.UUID;

@Entity
@Table(name = "recognition_results")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class RecognitionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private RecognitionJob job;

    @Column(nullable = false, name = "field_name")
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "field_type")
    private FieldType fieldType;

    @Column(columnDefinition = "TEXT", name = "text_value")
    private String textValue;

    @Column(name = "image_storage_key")
    private String imageStorageKey;

    public static RecognitionResult ofText(String fieldName, FieldType fieldType, String text) {
        return RecognitionResult.builder()
                .fieldName(fieldName)
                .fieldType(fieldType)
                .textValue(text)
                .build();
    }

    public static RecognitionResult ofSignature(String fieldName, String storageKey) {
        return RecognitionResult.builder()
                .fieldName(fieldName)
                .fieldType(FieldType.SIGNATURE)
                .imageStorageKey(storageKey)
                .build();
    }
}