package com.fintechwave.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceDTO {
    private String accountCode;
    private String accountName;
    private String accountType;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal currentBalance;
}
