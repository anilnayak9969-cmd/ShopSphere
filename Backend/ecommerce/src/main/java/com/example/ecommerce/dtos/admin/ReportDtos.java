package com.example.ecommerce.dtos.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.example.ecommerce.dtos.product.ProductResponse;

/** Shapes used by the seller and admin report pages. Every number comes from a database query. */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** One point of a daily chart. */
    public record DailyPoint(LocalDate date, long count, BigDecimal amount) {
    }

    /** A labelled total, e.g. a product, category or store with its units and revenue. */
    public record RankedRow(String label, long units, BigDecimal revenue) {
    }

    public record MonthlyCommission(int year, int month, long units, BigDecimal grossSales, BigDecimal commission,
                                    BigDecimal netEarnings) {
    }

    public record SellerReport(
            int days,
            List<DailyPoint> dailySales,
            List<RankedRow> topProducts,
            Map<String, Long> itemStatusCounts,
            BigDecimal grossSales,
            long unitsSold) {
    }

    public record CommissionSummary(
            BigDecimal commissionPercent,
            BigDecimal deliveredSales,
            BigDecimal totalCommission,
            BigDecimal netEarnings,
            BigDecimal pendingSettlement,
            List<MonthlyCommission> months) {
    }

    public record AdminReport(
            int days,
            List<DailyPoint> dailyOrders,
            Map<String, Long> orderStatusCounts,
            List<RankedRow> topCategories,
            List<RankedRow> topSellers,
            List<ProductResponse> lowStockProducts,
            BigDecimal grossMerchandiseValue,
            BigDecimal refundedTotal,
            BigDecimal estimatedCommission) {
    }
}
