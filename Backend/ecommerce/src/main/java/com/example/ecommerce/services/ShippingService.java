package com.example.ecommerce.services;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.QuoteRequest;
import com.example.ecommerce.dtos.shipping.ShippingDtos.QuoteResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.ShipmentResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.SummaryResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.TrackingResponse;
import com.example.ecommerce.dtos.shipping.ShippingDtos.UpdateRequest;
import com.example.ecommerce.enums.ShippingStatus;

/** Shipping Management module: cost calculation, dashboard, tracking and shipping updates. */
public interface ShippingService {

    /** Standard and express prices for the customer's cart delivered to an address. */
    QuoteResponse quote(Long customerId, QuoteRequest request);

    PageResponse<ShipmentResponse> list(ShippingStatus status, String search, int page, int size);

    SummaryResponse summary();

    ShipmentResponse get(Long orderId);

    /** Admin updates courier, tracking number and status. A pending order can be shipped here. */
    ShipmentResponse update(Long orderId, UpdateRequest request);

    /** Customer tracks one of their own shipments by tracking number. */
    TrackingResponse track(Long customerId, String trackingNumber);
}
