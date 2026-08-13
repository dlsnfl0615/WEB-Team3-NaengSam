package com.naengsam.quick.domain.boormi.dto;

import java.time.YearMonth;

public record MonthlySavingDto(YearMonth month, long savedAmount) {
}
