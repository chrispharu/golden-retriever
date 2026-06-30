package com.example.golden_retriever_java.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigEntity {

    @Id
    @Column(name = "config_key", length = 50)
    private String key;

    @Column(name = "config_value", length = 255)
    private String value;

    @Column(name = "description")
    private String description;
}
