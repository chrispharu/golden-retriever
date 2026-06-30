package com.example.golden_retriever_java;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GoldenRetrieverJavaApplicationTests {

	@Test
	void contextLoads() {
	}

	@Autowired
	private com.example.golden_retriever_java.repository.InventoryRepository inventoryRepository;

	@Test
	void testInventoryDecimalPrecision() {
		com.example.golden_retriever_java.entity.InventoryEntity entity = com.example.golden_retriever_java.entity.InventoryEntity.builder()
				.symbol("TEST.TW")
				.name("測試股票")
				.price(new java.math.BigDecimal("123.4567"))
				.shares(new java.math.BigDecimal("10.8888"))
				.buyDate("2026-05-06")
				.exchangeRate(new java.math.BigDecimal("1.0000"))
				.build();

		com.example.golden_retriever_java.entity.InventoryEntity saved = inventoryRepository.save(entity);
		com.example.golden_retriever_java.entity.InventoryEntity retrieved = inventoryRepository.findById(saved.getId()).orElseThrow();

		org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("123.4567").compareTo(retrieved.getPrice()));
		org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("10.8888").compareTo(retrieved.getShares()));

		inventoryRepository.delete(retrieved);
	}

}
