package org.sparklingduo.domain.template;

public enum FieldType {
    TEXT,
    NUMERIC,
    DATE,
    PHONE,
    HANDWRITTEN, // это рукописный текст, возвращаемый объект - string
    SIGNATURE, // подпись, возвращаемый объект - byte[]
    ANCHOR // закос под будущее...
}
