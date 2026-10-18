import { useState } from 'react'
import { Link } from 'react-router-dom'
import publicService from '../../services/publicService'
import { useAuth } from '../../context/AuthContext'
import { Field } from '../../components/FormControls'
import { Alert } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { EMAIL_RE } from '../../utils/validators'

export default function Contact() {
  const { user } = useAuth()
  const [form, setForm] = useState({ name: user?.fullName || '', email: user?.email || '', subject: '', message: '' })
  const [errors, setErrors] = useState({})
  const [status, setStatus] = useState(null) // { ok, text }
  const [sending, setSending] = useState(false)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const submit = async (event) => {
    event.preventDefault()
    const found = {}
    if (!form.name.trim()) found.name = 'Name is required'
    if (!EMAIL_RE.test(form.email.trim())) found.email = 'Enter a valid email address'
    if (!form.subject.trim()) found.subject = 'Subject is required'
    if (form.message.trim().length < 10) found.message = 'Message must be at least 10 characters'
    setErrors(found)
    setStatus(null)
    if (Object.keys(found).length) return
    setSending(true)
    try {
      const response = await publicService.sendContactMessage({
        name: form.name.trim(),
        email: form.email.trim(),
        subject: form.subject.trim(),
        message: form.message.trim(),
      })
      setStatus({ ok: true, text: response.message })
      setForm((f) => ({ ...f, subject: '', message: '' }))
    } catch (err) {
      setErrors(getFieldErrors(err))
      setStatus({ ok: false, text: getErrorMessage(err) })
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="container ss-page" style={{ maxWidth: 1000 }}>
      <span className="ss-eyebrow">Customer support</span>
      <h1 className="mt-1 mb-4">Contact us</h1>
      <div className="row g-4">
        <div className="col-lg-7">
          <form className="ss-card ss-card-pad d-grid gap-3" onSubmit={submit} noValidate>
            {status && <Alert variant={status.ok ? 'success' : 'danger'}>{status.text}</Alert>}
            <div className="row g-3">
              <div className="col-md-6">
                <Field id="contact-name" label="Your name" value={form.name} onChange={set('name')} error={errors.name} maxLength={100} />
              </div>
              <div className="col-md-6">
                <Field id="contact-email" type="email" label="Email" value={form.email} onChange={set('email')} error={errors.email} maxLength={150} />
              </div>
            </div>
            <Field id="contact-subject" label="Subject" value={form.subject} onChange={set('subject')} error={errors.subject} maxLength={150} />
            <Field
              id="contact-message"
              as="textarea"
              rows={5}
              label="Message"
              value={form.message}
              onChange={set('message')}
              error={errors.message}
              maxLength={2000}
              hint={`${form.message.length}/2000`}
            />
            <div>
              <button type="submit" className="btn btn-ink" disabled={sending}>
                {sending && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
                Send message
              </button>
            </div>
          </form>
        </div>
        <div className="col-lg-5">
          <div className="ss-card ss-card-pad">
            <h2 className="h5">Before you write</h2>
            <ul className="list-unstyled d-grid gap-3 mb-0 mt-3">
              <li className="d-flex gap-2">
                <i className="bi bi-bag-check text-primary" aria-hidden="true" />
                <span>
                  For order updates, open <Link to="/account/orders">My orders</Link> — every status change and
                  tracking number is shown there.
                </span>
              </li>
              <li className="d-flex gap-2">
                <i className="bi bi-arrow-counterclockwise text-primary" aria-hidden="true" />
                <span>
                  Returns can be requested from the order page. See <Link to="/policies/returns">Returns &amp; refunds</Link>.
                </span>
              </li>
              <li className="d-flex gap-2">
                <i className="bi bi-question-circle text-primary" aria-hidden="true" />
                <span>
                  Quick answers are in our <Link to="/help">FAQs</Link>.
                </span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  )
}
