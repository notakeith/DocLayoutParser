package org.sparklingduo.infrastructure.llm;

import org.sparklingduo.domain.template.FieldType;

public final class FieldTypePromptProvider {

    private FieldTypePromptProvider() {}

    public static String buildInput(String rawText, FieldType type, String fieldName) {
        String hint = contextHint(type, fieldName);
        if (hint.isBlank()) {
            return rawText;
        }
        return hint + "\n\n" + rawText;
    }

    public static boolean requiresCorrection(FieldType type) {
        return switch (type) {
            case SIGNATURE, ANCHOR -> false;
            default -> true;
        };
    }

    private static String contextHint(FieldType type, String fieldName) {
        String name = fieldName != null && !fieldName.isBlank()
                ? " «" + fieldName + "»"
                : "";

        return switch (type) {
            case TEXT -> """
                    [Поле%s: произвольный текст. Исправь ошибки OCR. \
                    Верни только исправленный текст без пояснений.]""".formatted(name);

            case HANDWRITTEN -> """
                    [Поле%s: рукописный текст. Исправь ошибки распознавания рукописи — \
                    перепутанные буквы, пропуски, слипшиеся слова. \
                    Верни только исправленный текст без пояснений.]""".formatted(name);

            case NUMERIC -> """
                    [Поле%s: числовое значение. Извлеки число, убери лишние символы и буквы, \
                    исправь замены цифр (О→0, З→3, l→1). \
                    Верни только число без пояснений.]""".formatted(name);

            case DATE -> """
                    [Поле%s: дата. Исправь ошибки OCR и приведи к формату ДД.ММ.ГГГГ. \
                    Исправь замены символов (О→0, З→3). \
                    Верни только дату без пояснений.]""".formatted(name);

            case PHONE -> """
                    [Поле%s: номер телефона или позывной. Исправь ошибки OCR, \
                    сохрани оригинальный формат. \
                    Верни только исправленный номер без пояснений.]""".formatted(name);

            case SIGNATURE, ANCHOR -> "";
            case TABLE -> """
                    [Поле%s: таблица. Текст распознан OCR построчно, строки разделены переносами. \
                    Преобразуй в JSON-массив объектов — каждая строка данных = один объект. \
                    Имена ключей возьми из первой строки (заголовки). \
                    Исправь OCR-ошибки в значениях ячеек. \
                    Верни ТОЛЬКО валидный JSON-массив, без пояснений и без markdown-блоков.]""".formatted(name);
        };
    }
}