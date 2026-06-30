package com.example.golden_retriever_java.repository;

import com.example.golden_retriever_java.entity.ConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntity, String> {
    Optional<ConfigEntity> findByKey(String key);
}
