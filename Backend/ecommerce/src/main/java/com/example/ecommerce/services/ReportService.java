package com.example.ecommerce.services;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.admin.ReportDtos.AdminReport;
import com.example.ecommerce.dtos.admin.ReportDtos.CommissionSummary;
import com.example.ecommerce.dtos.admin.ReportDtos.DailyPoint;
import com.example.ecommerce.dtos.admin.ReportDtos.MonthlyCommission;
import com.example.ecommerce.dtos.admin.ReportDtos.RankedRow;
import com.example.ecommerce.dtos.admin.ReportDtos.SellerReport;
import com.example.ecommerce.dtos.product.ProductResponse;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.repositories.OrderItemRepository;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.services.impl.OrderServiceImpl;

import lombok.RequiredArgsConstructor;

/** Seller and admin reports. Days without sales are filled with zero so charts have no gaps. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final SettingsService settingsService;

    @Transactional(readOnly = true)
    public SellerReport sellerReport(Long sellerId, int days) {
        int range = clampDays(days);
        LocalDate from = LocalDate.now().minusDays(range - 1L);
        Map<LocalDate, DailyPoint> byDay = new TreeMap<>();
        for (Object[] row : orderItemRepository.sellerDailySales(sellerId, OrderServiceImpl.SOLD_ITEM_STATUSES,
                from.atStartOfDay())) {
            LocalDate date = toDate(row[0]);
            byDay.put(date, new DailyPoint(date, ((Number) row[2]).longValue(), money(row[1])));
        }
        List<RankedRow> top = orderItemRepository.sellerTopProducts(sellerId, OrderServiceImpl.SOLD_ITEM_STATUSES,
                PageRequest.of(0, 5)).stream().map(ReportService::ranked).toList();
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Object[] row : orderItemRepository.sellerStatusCounts(sellerId)) {
            statusCounts.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        Object[] totals = orderItemRepository.sellerTotals(sellerId, OrderServiceImpl.SOLD_ITEM_STATUSES).get(0);
        return new SellerReport(range, fillDays(from, range, byDay), top, statusCounts, money(totals[1]),
                ((Number) totals[0]).longValue());
    }

    /** Commission is charged on delivered items only; everything else is "pending settlement". */
    @Transactional(readOnly = true)
    public CommissionSummary commission(Long sellerId) {
        BigDecimal percent = settingsService.get().getCommissionPercent();
        List<MonthlyCommission> months = new ArrayList<>();
        BigDecimal delivered = BigDecimal.ZERO;
        for (Object[] row : orderItemRepository.sellerMonthlyDelivered(sellerId)) {
            BigDecimal gross = money(row[2]);
            BigDecimal fee = PricingPolicy.commission(gross, percent);
            months.add(new MonthlyCommission(((Number) row[0]).intValue(), ((Number) row[1]).intValue(),
                    ((Number) row[3]).longValue(), gross, fee, gross.subtract(fee)));
            delivered = delivered.add(gross);
        }
        BigDecimal totalFee = PricingPolicy.commission(delivered, percent);
        EnumSet<OrderStatus> undelivered = EnumSet.copyOf(OrderServiceImpl.SOLD_ITEM_STATUSES);
        undelivered.remove(OrderStatus.DELIVERED);
        undelivered.remove(OrderStatus.RETURN_REQUESTED);
        BigDecimal pending = money(orderItemRepository.sellerTotals(sellerId, undelivered).get(0)[1]);
        return new CommissionSummary(percent, delivered, totalFee, delivered.subtract(totalFee), pending, months);
    }

    @Transactional(readOnly = true)
    public AdminReport adminReport(int days) {
        int range = clampDays(days);
        LocalDate from = LocalDate.now().minusDays(range - 1L);
        EnumSet<OrderStatus> placed = EnumSet.complementOf(EnumSet.of(OrderStatus.PENDING_PAYMENT));
        Map<LocalDate, DailyPoint> byDay = new TreeMap<>();
        for (Object[] row : orderRepository.dailyOrders(placed, from.atStartOfDay())) {
            LocalDate date = toDate(row[0]);
            byDay.put(date, new DailyPoint(date, ((Number) row[1]).longValue(), money(row[2])));
        }
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Object[] row : orderRepository.statusCounts()) {
            statusCounts.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        List<RankedRow> categories = orderItemRepository.categorySales(OrderServiceImpl.SOLD_ITEM_STATUSES).stream()
                .limit(6).map(ReportService::ranked).toList();
        List<RankedRow> sellers = orderItemRepository.sellerSales(OrderServiceImpl.SOLD_ITEM_STATUSES,
                PageRequest.of(0, 5)).stream().map(ReportService::ranked).toList();
        PlatformSettings settings = settingsService.get();
        List<ProductResponse> lowStock = productRepository
                .findTop10ByActiveTrueAndStockQuantityLessThanEqualOrderByStockQuantityAsc(settings.getLowStockThreshold())
                .stream().map(ProductResponse::from).toList();
        BigDecimal gmv = categories.isEmpty() ? BigDecimal.ZERO
                : orderItemRepository.categorySales(OrderServiceImpl.SOLD_ITEM_STATUSES).stream()
                .map(r -> money(r[2])).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveredSales = orderItemRepository.categorySales(EnumSet.of(OrderStatus.DELIVERED)).stream()
                .map(r -> money(r[2])).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AdminReport(range, fillDays(from, range, byDay), statusCounts, categories, sellers, lowStock, gmv,
                paymentRepository.sumRefunded(), PricingPolicy.commission(deliveredSales, settings.getCommissionPercent()));
    }

    @Transactional(readOnly = true)
    public BigDecimal revenue() {
        return paymentRepository.sumAmountByStatus(PaymentStatus.SUCCESS).subtract(paymentRepository.sumRefunded());
    }

    private static List<DailyPoint> fillDays(LocalDate from, int days, Map<LocalDate, DailyPoint> byDay) {
        List<DailyPoint> points = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            points.add(byDay.getOrDefault(d, new DailyPoint(d, 0, BigDecimal.ZERO)));
        }
        return points;
    }

    private static RankedRow ranked(Object[] row) {
        return new RankedRow(String.valueOf(row[0]), ((Number) row[1]).longValue(), money(row[2]));
    }

    private static LocalDate toDate(Object value) {
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof Date d) {
            return d.toLocalDate();
        }
        if (value instanceof LocalDateTime dt) {
            return dt.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    }

    private static int clampDays(int days) {
        return Math.min(Math.max(days, 7), 90);
    }
}
