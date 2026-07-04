package com.fintechwave.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceResponse {
    private List<AccountBalanceDTO> accounts;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private boolean isBalanced;
}
