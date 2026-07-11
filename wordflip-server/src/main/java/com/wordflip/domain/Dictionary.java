package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 内置词典目录（dictionaries）。
 */
@Entity
@Table(name = "dictionaries")
@Getter
@Setter
public class Dictionary {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('zh','en')")
    private DictionaryLocale locale = DictionaryLocale.zh;

    @Column(name = "license_note", length = 512)
    private String licenseNote;

    @Column(name = "is_builtin", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean builtin = true;

    @Column(name = "sort_order", nullable = false, columnDefinition = "INT UNSIGNED")
    private int sortOrder = 0;
}
