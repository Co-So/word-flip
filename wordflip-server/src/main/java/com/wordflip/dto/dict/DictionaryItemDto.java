package com.wordflip.dto.dict;

import com.wordflip.domain.Dictionary;
import com.wordflip.domain.DictionaryLocale;

/**
 * 词典目录项，对齐 openapi DictionaryItem。
 */
public record DictionaryItemDto(
        String id,
        String name,
        DictionaryLocale locale,
        String licenseNote,
        boolean builtin,
        int sortOrder
) {
    public static DictionaryItemDto from(Dictionary d) {
        return new DictionaryItemDto(
                d.getId(),
                d.getName(),
                d.getLocale(),
                d.getLicenseNote(),
                d.isBuiltin(),
                d.getSortOrder()
        );
    }
}
