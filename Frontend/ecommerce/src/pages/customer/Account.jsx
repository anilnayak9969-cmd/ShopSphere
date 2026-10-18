import { useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import authService from '../../services/authService'
import { Field, PasswordInput } from '../../components/FormControls'
import { Alert, StatusBadge } from '../../components/ui'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { formatDate, labelize } from '../../utils/format'
import { PASSWORD_MESSAGE, PASSWORD_RE, PHONE_RE } from '../../utils/validators'

export default function Account() {
  const { user, refreshUser } = useAuth()
  const toast = useToast()
  const isSeller = user.role === 'SELLER'

  const [profile, setProfile] = useState({ fullName: user.fullName, phone: user.phone || '', storeName: user.storeName || '' })
  const [profileErrors, setProfileErrors] = useState({})
  const [savingProfile, setSavingProfile] = useState(false)

  const [passwords, setPasswords] = useState({ current: '', next: '', confirm: '' })
  const [passwordErrors, setPasswordErrors] = useState({})
  const [savingPassword, setSavingPassword] = useState(false)

  const saveProfile = async (event) => {
    event.preventDefault()
    const errors = {}
    if (profile.fullName.trim().length < 2) errors.fullName = 'Enter your full name'
    if (!PHONE_RE.test(profile.phone)) errors.phone = 'Enter a valid 10-digit mobile number'
    if (isSeller && profile.storeName.trim().length < 2) errors.storeName = 'Enter your store name'
    setProfileErrors(errors)
    if (Object.keys(errors).length) return
    setSavingProfile(true)
    try {
      await authService.updateProfile({
        fullName: profile.fullName.trim(),
        phone: profile.phone,
        storeName: isSeller ? profile.storeName.trim() : null,
      })
      await refreshUser()
      toast.success('Your profile has been updated.')
    } catch (err) {
      setProfileErrors(getFieldErrors(err))
      toast.error(getErrorMessage(err))
    } finally {
      setSavingProfile(false)
    }
  }

  const savePassword = async (event) => {
    event.preventDefault()
    const errors = {}
    if (!passwords.current) errors.current = 'Enter your current password'
    if (!PASSWORD_RE.test(passwords.next)) errors.next = PASSWORD_MESSAGE
    if (passwords.confirm !== passwords.next) errors.confirm = 'Passwords do not match'
    setPasswordErrors(errors)
    if (Object.keys(errors).length) return
    setSavingPassword(true)
    try {
      const response = await authService.changePassword(passwords.current, passwords.next)
      toast.success(response.message)
      setPasswords({ current: '', next: '', confirm: '' })
    } catch (err) {
      const code = err.response?.data?.code
      setPasswordErrors(code === 'WRONG_PASSWORD' ? { current: getErrorMessage(err) } : { next: getErrorMessage(err) })
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <>
      <span className="ss-eyebrow">My account</span>
      <h1 className="mt-1 mb-4" style={{ fontSize: 'clamp(2.2rem, 4vw, 3rem)' }}>
        Hello, {user.fullName.split(' ')[0]}
      </h1>

      {isSeller && user.sellerStatus !== 'APPROVED' && (
        <Alert variant="warning" className="mb-4">
          Your store is <strong>{labelize(user.sellerStatus)}</strong>. You can list products once an administrator approves it.
        </Alert>
      )}

      <form className="ss-card ss-card-pad mb-4" onSubmit={saveProfile} noValidate>
        <div className="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-3">
          <div>
            <h2 className="h3 mb-1">Personal details</h2>
            <p className="small text-muted mb-0">Member since {formatDate(user.createdAt)}</p>
          </div>
          <div className="d-flex gap-2">
            <span className="ss-pill ss-pill-gold">{labelize(user.role)}</span>
            {user.sellerStatus && <StatusBadge status={user.sellerStatus} />}
          </div>
        </div>
        <div className="row g-3">
          <div className="col-md-6">
            <Field id="fullName" label="Full name" value={profile.fullName} maxLength={100} error={profileErrors.fullName}
              onChange={(e) => setProfile((p) => ({ ...p, fullName: e.target.value }))} />
          </div>
          <div className="col-md-6">
            <Field id="phone" label="Mobile number" value={profile.phone} inputMode="numeric" maxLength={10} error={profileErrors.phone}
              onChange={(e) => setProfile((p) => ({ ...p, phone: e.target.value }))} />
          </div>
          <div className="col-md-6">
            <Field id="email" label="Email" value={user.email} disabled hint={user.emailVerified ? 'Verified — email cannot be changed' : undefined} />
          </div>
          {isSeller && (
            <div className="col-md-6">
              <Field id="storeName" label="Store name" value={profile.storeName} maxLength={120} error={profileErrors.storeName}
                onChange={(e) => setProfile((p) => ({ ...p, storeName: e.target.value }))} />
            </div>
          )}
        </div>
        <button type="submit" className="btn btn-ink mt-4" disabled={savingProfile}>
          {savingProfile && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
          Save changes
        </button>
      </form>

      <form className="ss-card ss-card-pad" onSubmit={savePassword} noValidate>
        <h2 className="h3 mb-1">Password</h2>
        <p className="small text-muted mb-3">Use at least 8 characters with upper and lower case letters and a number.</p>
        <div className="row g-3">
          <div className="col-md-4">
            <PasswordInput id="currentPassword" label="Current password" value={passwords.current} error={passwordErrors.current}
              onChange={(e) => setPasswords((p) => ({ ...p, current: e.target.value }))} />
          </div>
          <div className="col-md-4">
            <PasswordInput id="newPassword" label="New password" autoComplete="new-password" value={passwords.next} error={passwordErrors.next}
              onChange={(e) => setPasswords((p) => ({ ...p, next: e.target.value }))} />
          </div>
          <div className="col-md-4">
            <PasswordInput id="confirmPassword" label="Confirm new password" autoComplete="new-password" value={passwords.confirm} error={passwordErrors.confirm}
              onChange={(e) => setPasswords((p) => ({ ...p, confirm: e.target.value }))} />
          </div>
        </div>
        <button type="submit" className="btn btn-outline-ink mt-4" disabled={savingPassword}>
          {savingPassword && <span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />}
          Change password
        </button>
      </form>
    </>
  )
}
