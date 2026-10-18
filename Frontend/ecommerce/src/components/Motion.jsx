import { useCallback, useEffect, useRef, useState } from 'react'

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/** Calls onVisible once, the first time the element scrolls into view. */
function useOnScreen(ref, onVisible) {
  useEffect(() => {
    const el = ref.current
    if (!el) return undefined
    // Already on screen when mounted (or no observer support): show immediately, never leave content hidden.
    const rect = el.getBoundingClientRect()
    if (!('IntersectionObserver' in window) || (rect.top < window.innerHeight && rect.bottom > 0)) {
      onVisible()
      return undefined
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          onVisible()
          observer.disconnect()
        }
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.08 },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [ref, onVisible])
}

/**
 * Fades and slides its content up when it scrolls into view. Content is always rendered (so it is
 * crawlable and readable without JavaScript); only the CSS state changes. Respects reduced motion.
 */
export function Reveal({ children, delay = 0, as: Tag = 'div', className = '', ...rest }) {
  const ref = useRef(null)
  const [visible, setVisible] = useState(prefersReducedMotion)
  useOnScreen(ref, useCallback(() => setVisible(true), []))
  return (
    <Tag ref={ref} className={`ss-reveal ${visible ? 'is-visible' : ''} ${className}`} style={{ '--ss-reveal-delay': `${delay}ms` }} {...rest}>
      {children}
    </Tag>
  )
}

/**
 * Counts from 0 up to `value` — straight away (above-the-fold numbers) or, with `onView`, when scrolled into
 * view. Shows the final number immediately with reduced motion; screen readers always get the final value.
 */
export function CountUp({ value, duration = 1200, onView = false, format = (n) => n.toLocaleString('en-IN') }) {
  const ref = useRef(null)
  const reduced = prefersReducedMotion()
  const [shown, setShown] = useState(0)
  const [seen, setSeen] = useState(false)
  useOnScreen(ref, useCallback(() => setSeen(true), []))
  const started = !onView || seen

  useEffect(() => {
    if (!started || reduced) return undefined
    // A timer (not requestAnimationFrame) so the count also completes in background tabs and previews.
    const start = Date.now()
    const timer = setInterval(() => {
      const t = Math.min(1, (Date.now() - start) / duration)
      setShown(Math.round(value * (1 - Math.pow(1 - t, 3))))
      if (t === 1) clearInterval(timer)
    }, 30)
    return () => clearInterval(timer)
  }, [started, reduced, value, duration])

  return (
    <span ref={ref} aria-label={format(value)}>
      <span aria-hidden="true">{format(reduced ? value : shown)}</span>
    </span>
  )
}

/** Cycles through words with a slide animation; screen readers hear the first word only. */
export function RotatingWord({ words, interval = 2600 }) {
  const [index, setIndex] = useState(0)
  useEffect(() => {
    if (prefersReducedMotion() || words.length < 2) return undefined
    const timer = setInterval(() => setIndex((i) => (i + 1) % words.length), interval)
    return () => clearInterval(timer)
  }, [words.length, interval])
  return (
    <span className="ss-rotator">
      <span className="visually-hidden">{words[0]}</span>
      <em key={index} className="ss-rotator-word" aria-hidden="true">
        {words[index]}
      </em>
    </span>
  )
}
