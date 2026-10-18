import { useCallback, useEffect, useState } from 'react'
import { getErrorMessage } from '../utils/errors'

/**
 * Loads one page of a backend list (PageResponse) and reloads when `deps` change.
 * fetchPage(page) must return a promise of { content, page, totalPages, ... }.
 */
export default function usePagedList(fetchPage, deps = []) {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const depsKey = JSON.stringify(deps)

  // Reset to the first page whenever a filter changes.
  const [lastDeps, setLastDeps] = useState(depsKey)
  if (lastDeps !== depsKey) {
    setLastDeps(depsKey)
    setPage(0)
  }

  const reload = useCallback(() => {
    setError(null)
    fetchPage(page)
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)))
    // fetchPage is recreated each render; depsKey captures what actually matters.
  }, [page, depsKey]) // eslint-disable-line

  useEffect(() => {
    reload()
  }, [reload])

  return { page, setPage, result, error, reload, setResult }
}
