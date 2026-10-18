const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js'

let loadingPromise = null

/** Loads Razorpay's checkout script once, the first time a customer pays. */
export function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(true)
  if (!loadingPromise) {
    loadingPromise = new Promise((resolve) => {
      const script = document.createElement('script')
      script.src = SCRIPT_URL
      script.async = true
      script.onload = () => resolve(true)
      script.onerror = () => {
        loadingPromise = null
        resolve(false)
      }
      document.body.appendChild(script)
    })
  }
  return loadingPromise
}
