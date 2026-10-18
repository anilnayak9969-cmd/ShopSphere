package com.example.ecommerce.dtos.delivery;

/** Live counts for the delivery partner's overview. */
public record DeliveryDashboardResponse(
        DeliveryPartnerResponse partner,
        long availablePickups,
        long activeDeliveries,
        long deliveredToday,
        long deliveredTotal) {
}
