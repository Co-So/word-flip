package com.wordflip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 义项例句；MVP 可空。
 */
@Entity
@Table(name = "dict_examples")
@Getter
@Setter
public class DictExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sense_id", nullable = false)
    private Long senseId;

    @Column(nullable = false, length = 512)
    private String en;

    @Column(length = 512)
    private String cn;

    @Column(name = "sort_order", nullable = false, columnDefinition = "INT UNSIGNED")
    private int sortOrder = 0;
}
