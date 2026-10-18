import { useRef, useState } from 'react'
import uploadService, { ACCEPTED_IMAGE_TYPES, MAX_UPLOAD_BYTES } from '../services/uploadService'
import { getErrorMessage } from '../utils/errors'
import { IMAGE_URL_RE } from '../utils/validators'
import { SafeImage } from './ui'

/**
 * Image field: upload a JPG/PNG/WEBP (stored securely by the backend) or paste an image link.
 * The value is either "/api/images/<id>.jpg" (uploaded) or an http(s) URL.
 */
export default function ImageUpload({ id, label, value, onChange, error, hint, compact = false }) {
  const inputRef = useRef(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState(null)

  const pick = () => inputRef.current?.click()

  const onFile = async (event) => {
    const file = event.target.files?.[0]
    event.target.value = '' // allow choosing the same file again
    if (!file) return
    if (!ACCEPTED_IMAGE_TYPES.split(',').includes(file.type)) {
      setUploadError('Choose a JPG, PNG or WEBP image.')
      return
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setUploadError('Images must be 5 MB or smaller.')
      return
    }
    setUploading(true)
    setUploadError(null)
    try {
      const result = await uploadService.uploadImage(file)
      onChange(result.url)
    } catch (err) {
      setUploadError(getErrorMessage(err))
    } finally {
      setUploading(false)
    }
  }

  const shownError = uploadError || error
  const hasPreview = value && (value.startsWith('/api/images/') || IMAGE_URL_RE.test(value))

  return (
    <div>
      {label && (
        <label htmlFor={id} className="form-label">
          {label}
        </label>
      )}
      <div className="d-flex gap-2 align-items-start">
        {!compact && (
          <div className="ss-thumb" style={{ width: 64, height: 64 }}>
            {hasPreview ? <SafeImage key={value} src={value} alt="Selected image preview" /> : <div className="ss-img-fallback"><i className="bi bi-image" /></div>}
          </div>
        )}
        <div className="flex-grow-1 min-w-0">
          <div className="d-flex gap-2">
            <input
              id={id}
              type="text"
              className={`form-control ${shownError ? 'is-invalid' : ''}`}
              placeholder="Upload an image or paste a link (https://…)"
              value={value || ''}
              onChange={(e) => onChange(e.target.value)}
              aria-describedby={shownError ? `${id}-error` : hint ? `${id}-hint` : undefined}
            />
            <button type="button" className="btn btn-ghost text-nowrap" onClick={pick} disabled={uploading}>
              {uploading ? (
                <span className="spinner-border spinner-border-sm" aria-label="Uploading" />
              ) : (
                <>
                  <i className="bi bi-upload me-1" aria-hidden="true" />
                  Upload
                </>
              )}
            </button>
          </div>
          <input ref={inputRef} type="file" accept={ACCEPTED_IMAGE_TYPES} className="d-none" onChange={onFile} aria-hidden="true" tabIndex={-1} />
          {shownError ? (
            <div id={`${id}-error`} className="invalid-feedback d-block">
              {shownError}
            </div>
          ) : (
            hint && (
              <div id={`${id}-hint`} className="form-text">
                {hint}
              </div>
            )
          )}
        </div>
      </div>
    </div>
  )
}
