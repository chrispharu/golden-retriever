package com.example.golden_retriever_java.repository;

import com.example.golden_retriever_java.entity.DividendEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DividendRepository extends JpaRepository<DividendEntity, Long> {
    List<DividendEntity> findBySymbol(String symbol);
}
