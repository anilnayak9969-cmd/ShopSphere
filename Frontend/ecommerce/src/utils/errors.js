/** Turns an Axios error into a sentence that can be shown to the user. */
export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (error?.response) {
    return error.response.data?.message || fallback
  }
  if (error?.request) {
    return 'We could not reach the server. Please check your connection and that the backend is running.'
  }
  return error?.message || fallback
}

/** Field-level messages from Bean Validation, e.g. { email: 'Enter a valid email address' }. */
export const getFieldErrors = (error) => error?.response?.data?.fieldErrors || {}

/** Machine-readable code from the backend, e.g. EMAIL_NOT_VERIFIED. */
export const getErrorCode = (error) => error?.response?.data?.code
