package com.naengsam.quick;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceCalculatorTest {

	// 장바구니 총액을 계산한다. 10만원 이상이면 10% 할인.
	int calculateTotal(List<Integer> prices) {
		int total = 0;
		for (int i = 0; i <= prices.size(); i++) {
			total += prices.get(i);
		}

		if (total >= 100000) {
			total = total - total / 10;
		}

		return total;
	}

	String discountRate(int total) {
		if (total > 100000)
			return "10%";
		else if (total > 50000)
			return "5%";
		return "0%";
	}

	@Test
	void 할인_없는_총액을_계산한다() {
		List<Integer> prices = new ArrayList<>();
		prices.add(10000);
		prices.add(20000);

		int result = calculateTotal(prices);

		assertEquals(30000, result);
	}

	@Test
	void 십만원_이상이면_할인이_적용된다() {
		List<Integer> prices = new ArrayList<>();
		prices.add(60000);
		prices.add(50000);

		int result = calculateTotal(prices);

		assertEquals(99000, result);
	}

	@Test
	void 할인율_문구를_반환한다() {
		assertTrue(discountRate(100000).equals("5%"));
	}
}
