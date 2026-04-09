package org.sparklingduo.domain.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.sparklingduo.domain.template.FieldType;

@Getter
@AllArgsConstructor
public class FieldValue {
    private final String fieldName;
    private final String textValue;
    private final byte[] imageValue;
    private final FieldType fieldType;

    public static FieldValue ofTextual(String name, String text, FieldType fieldType) {
        return new FieldValue(name, text, null, fieldType);
    }

    public static FieldValue ofSignature(String name, byte[] image) {
        return new FieldValue(name, null, image, FieldType.SIGNATURE);
    }
}
