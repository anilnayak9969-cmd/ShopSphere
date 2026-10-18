// Remembers the coupon code chosen in the cart for checkout (this tab only).
// It is just a code — the server re-validates it and calculates the discount at checkout.
const KEY = 'shopsphere.couponCode'

export function getSavedCoupon() {
  try {
    return sessionStorage.getItem(KEY) || ''
  } catch {
    return ''
  }
}

export function saveCoupon(code) {
  try {
    if (code) sessionStorage.setItem(KEY, code)
    else sessionStorage.removeItem(KEY)
  } catch {
    /* storage unavailable — the customer can re-enter the code at checkout */
  }
}
