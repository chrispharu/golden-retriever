package com.example.golden_retriever_java.repository;

import com.example.golden_retriever_java.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {
    List<InventoryEntity> findBySymbol(String symbol);
    void deleteBySymbol(String symbol);
}
