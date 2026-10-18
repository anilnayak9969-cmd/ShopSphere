package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.order.CheckoutRequest;
import com.example.ecommerce.dtos.order.CheckoutResponse;
import com.example.ecommerce.dtos.order.OrderResponse;
import com.example.ecommerce.dtos.order.OrderResponse.PaymentInfo;
import com.example.ecommerce.dtos.order.PaymentFailedRequest;
import com.example.ecommerce.dtos.order.VerifyPaymentRequest;
import com.example.ecommerce.entites.Cart;
import com.example.ecommerce.entites.CartItem;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.Payment;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CartRepository;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.PaymentRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.CheckoutService;
import com.example.ecommerce.services.CouponService;
import com.example.ecommerce.services.CouponService.AppliedCoupon;
import com.example.ecommerce.services.EmailService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OrderSupport;
import com.example.ecommerce.services.PricingPolicy;
import com.example.ecommerce.services.RazorpayGateway;
import com.example.ecommerce.services.RefundService;
import com.example.ecommerce.services.SettingsService;
import com.razorpay.RazorpayException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private static final String ORDER_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RazorpayGateway razorpayGateway;
    private final EmailService emailService;
    private final CouponService couponService;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final OrderSupport orderSupport;
    private final RefundService refundService;

    @Override
    @Transactional
    public CheckoutResponse createCheckout(Long userId, CheckoutRequest request) {
        User customer = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null || cart.getItems().isEmpty()) {
            throw ApiException.badRequest("CART_EMPTY", "Your cart is empty.");
        }
        PlatformSettings settings = settingsService.get();
        PaymentMethod method = request.method();

        CustomerOrder order = new CustomerOrder();
        order.setCustomer(customer);
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentMethod(method);
        order.setShippingName(request.shippingName().trim());
        order.setShippingPhone(request.shippingPhone());
        order.setAddressLine1(request.addressLine1().trim());
        order.setAddressLine2(request.addressLine2() == null || request.addressLine2().isBlank()
                ? null : request.addressLine2().trim());
        order.setCity(request.city().trim());
        order.setState(request.state().trim());
        order.setPincode(request.pincode());

        // Re-check every item and take prices from the database, not from the browser.
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            if (!ProductServiceImpl.isPubliclyVisible(product) || product.getStockQuantity() < cartItem.getQuantity()) {
                throw ApiException.conflict("CART_CHANGED", product.getProductName()
                        + " is no longer available in the requested quantity. Please review your cart.");
            }
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setSeller(product.getSeller());
            item.setProductName(product.getProductName());
            item.setStoreName(product.getSeller().getStoreName());
            item.setImageUrl(product.getImageUrl());
            item.setUnitPrice(product.getSellingPrice());
            item.setQuantity(cartItem.getQuantity());
            item.setLineTotal(PricingPolicy.lineTotal(product.getSellingPrice(), cartItem.getQuantity()));
            item.setItemStatus(OrderStatus.PENDING_PAYMENT);
            order.addItem(item);
            subtotal = subtotal.add(item.getLineTotal());
        }

        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            AppliedCoupon applied = couponService.resolve(userId, request.couponCode(), subtotal);
            discount = applied.discount();
            order.setCouponCode(applied.coupon().getCode());
        }
        BigDecimal afterDiscount = subtotal.subtract(discount);
        BigDecimal shipping = PricingPolicy.shippingFor(afterDiscount, settings);
        order.setSubtotal(subtotal);
        order.setDiscountAmount(discount);
        order.setShippingFee(shipping);
        order.setTotalAmount(afterDiscount.add(shipping));

        long amountInPaise = PricingPolicy.toPaise(order.getTotalAmount());
        if (amountInPaise < 100) {
            throw ApiException.badRequest("AMOUNT_TOO_LOW", "The order total must be at least ₹1.");
        }

        if (method == PaymentMethod.COD) {
            return placeCashOnDelivery(customer, order, settings, amountInPaise);
        }

        orderRepository.save(order);
        String razorpayOrderId;
        try {
            razorpayOrderId = razorpayGateway.createOrder(amountInPaise, order.getOrderNumber());
        } catch (RazorpayException ex) {
            log.error("Razorpay order creation failed for {}: {}", order.getOrderNumber(), ex.getMessage());
            // Throwing rolls back the order we just saved.
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                    "The payment service is unavailable right now. Please try again.");
        }

        Payment payment = newPayment(order, PaymentMethod.RAZORPAY, PaymentStatus.CREATED);
        payment.setRazorpayOrderId(razorpayOrderId);
        paymentRepository.save(payment);

        return new CheckoutResponse(order.getOrderId(), order.getOrderNumber(), PaymentMethod.RAZORPAY,
                razorpayOrderId, amountInPaise, "INR", razorpayGateway.getKeyId(), customer.getFullName(),
                customer.getEmail(), customer.getPhone());
    }

    /** COD orders are placed immediately (stock reserved now); the money is collected on delivery. */
    private CheckoutResponse placeCashOnDelivery(User customer, CustomerOrder order, PlatformSettings settings,
                                                 long amountInPaise) {
        if (!settings.isCodEnabled()) {
            throw ApiException.badRequest("COD_DISABLED", "Cash on Delivery is not available right now. Please pay online.");
        }
        if (order.getTotalAmount().compareTo(settings.getCodMaxAmount()) > 0) {
            throw ApiException.badRequest("COD_LIMIT", "Cash on Delivery is available for orders up to ₹"
                    + settings.getCodMaxAmount().stripTrailingZeros().toPlainString() + ". Please pay online.");
        }
        orderRepository.save(order);
        paymentRepository.save(newPayment(order, PaymentMethod.COD, PaymentStatus.PENDING));
        if (!placeOrder(order)) {
            // Throwing rolls back the whole checkout, including the order row.
            throw ApiException.conflict("OUT_OF_STOCK", "An item in your cart just sold out. Please review your cart.");
        }
        return new CheckoutResponse(order.getOrderId(), order.getOrderNumber(), PaymentMethod.COD, null,
                amountInPaise, "INR", null, customer.getFullName(), customer.getEmail(), customer.getPhone());
    }

    /**
     * noRollbackFor: when the signature is invalid we still want the FAILED status saved,
     * even though an error is returned to the browser.
     */
    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public OrderResponse verifyPayment(Long userId, VerifyPaymentRequest request) {
        Payment payment = paymentRepository.lockByRazorpayOrderId(request.razorpayOrderId())
                .filter(p -> p.getOrder().getCustomer().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Payment not found."));

        // Idempotent: verifying an already-completed payment returns the same order without
        // deducting stock again (this also covers the webhook having completed it first).
        if (isFinal(payment)) {
            return OrderResponse.full(payment.getOrder(), info(payment));
        }
        if (!razorpayGateway.isSignatureValid(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Signature verification failed");
            throw ApiException.badRequest("PAYMENT_VERIFICATION_FAILED",
                    "We could not verify this payment. If money was debited, it will be refunded by Razorpay.");
        }
        completePayment(payment, request.razorpayPaymentId());
        return OrderResponse.full(payment.getOrder(), info(payment));
    }

    @Override
    @Transactional
    public void recordPaymentFailure(Long userId, PaymentFailedRequest request) {
        Payment payment = paymentRepository.findByRazorpayOrderId(request.razorpayOrderId())
                .filter(p -> p.getOrder().getCustomer().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Payment not found."));
        markFailed(payment, request.reason() == null ? "Payment was not completed" : request.reason());
    }

    @Override
    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (!razorpayGateway.isWebhookConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_DISABLED",
                    "Webhooks are not configured on this server.");
        }
        if (!razorpayGateway.isWebhookSignatureValid(rawBody, signature)) {
            throw ApiException.badRequest("INVALID_SIGNATURE", "Webhook signature verification failed.");
        }
        JSONObject entity;
        String event;
        try {
            JSONObject json = new JSONObject(rawBody);
            event = json.getString("event");
            entity = json.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        } catch (JSONException ex) {
            log.info("Ignoring webhook without a payment entity");
            return;
        }
        String razorpayOrderId = entity.optString("order_id", null);
        if (razorpayOrderId == null) {
            return;
        }
        Payment payment = paymentRepository.lockByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (payment == null) {
            log.warn("Webhook {} for unknown Razorpay order {}", event, razorpayOrderId);
            return;
        }
        switch (event) {
            case "payment.captured" -> {
                if (!isFinal(payment)) {
                    log.info("Completing order {} from webhook", payment.getOrder().getOrderNumber());
                    completePayment(payment, entity.getString("id"));
                }
            }
            case "payment.failed" -> markFailed(payment, entity.optString("error_description", "Payment failed"));
            default -> log.debug("Ignoring Razorpay webhook event {}", event);
        }
    }

    // --------------------------------------------------------------- helpers

    /** Shared by the browser verification and the webhook. */
    private void completePayment(Payment payment, String razorpayPaymentId) {
        CustomerOrder order = payment.getOrder();
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setFailureReason(null);
        order.setPaidAt(LocalDateTime.now());

        if (!placeOrder(order)) {
            // Rare race: someone bought the last unit while this customer was paying. Refund them.
            order.getItems().forEach(i -> i.setItemStatus(OrderStatus.CANCELLED));
            order.setStatus(OrderStatus.CANCELLED);
            order.setStatusNote("An item sold out while you were paying. Your payment is being refunded.");
            refundService.refund(order, order.getTotalAmount(), true);
        }
    }

    /**
     * Reserves stock and turns a pending order into a PLACED one: records the coupon use, clears
     * the purchased items from the cart, emails the customer and notifies each seller.
     * Returns false (and changes nothing) if any item no longer has enough stock.
     */
    private boolean placeOrder(CustomerOrder order) {
        if (!orderSupport.reserveStock(order.getItems())) {
            return false;
        }
        order.getItems().forEach(i -> i.setItemStatus(OrderStatus.PLACED));
        order.setStatus(OrderStatus.PLACED);
        order.setStatusNote(null);
        couponService.recordRedemption(order);

        Set<Long> productIds = order.getItems().stream().map(i -> i.getProduct().getProductId())
                .collect(Collectors.toSet());
        cartRepository.findByUserId(order.getCustomer().getId())
                .ifPresent(cart -> cart.getItems().removeIf(i -> productIds.contains(i.getProduct().getProductId())));

        notificationService.notify(order.getCustomer(), "Order placed",
                "Your order " + order.getOrderNumber() + " has been placed"
                        + (order.getPaymentMethod() == PaymentMethod.COD ? " (pay on delivery)." : "."),
                "/account/orders/" + order.getOrderId());
        Map<User, Integer> unitsBySeller = new LinkedHashMap<>();
        order.getItems().forEach(i -> unitsBySeller.merge(i.getSeller(), i.getQuantity(), Integer::sum));
        unitsBySeller.forEach((seller, units) -> notificationService.notify(seller, "New order " + order.getOrderNumber(),
                units + " unit(s) to confirm and pack for " + order.getShippingName() + ", " + order.getCity() + ".",
                "/seller/orders"));
        emailService.sendOrderConfirmation(order);
        return true;
    }

    private static Payment newPayment(CustomerOrder order, PaymentMethod method, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(method);
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency("INR");
        payment.setStatus(status);
        return payment;
    }

    private static PaymentInfo info(Payment p) {
        return new PaymentInfo(p.getStatus(), p.getRefundedAmount());
    }

    private static boolean isFinal(Payment payment) {
        return payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED;
    }

    private static void markFailed(Payment payment, String reason) {
        if (payment.getStatus() == PaymentStatus.CREATED || payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
        }
    }

    /** e.g. SS251016-7K2QXM : date for humans, random part so numbers cannot be guessed. */
    static String generateOrderNumber() {
        StringBuilder sb = new StringBuilder("SS").append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")))
                .append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(ORDER_CHARS.charAt(RANDOM.nextInt(ORDER_CHARS.length())));
        }
        return sb.toString();
    }
}
