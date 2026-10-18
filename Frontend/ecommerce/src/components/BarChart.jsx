/**
 * Dependency-free SVG bar chart. Each bar has a tooltip, and the same data is available to
 * screen readers as a visually hidden table.
 * data: [{ label: 'Oct 12', value: 1234 }]
 */
export default function BarChart({ data, title, format = (v) => v, height = 200, color = 'var(--ss-blue)' }) {
  const max = Math.max(1, ...data.map((d) => Number(d.value) || 0))
  const width = 100
  const gap = data.length > 40 ? 0.3 : 0.8
  const barWidth = width / data.length - gap
  const labelEvery = Math.ceil(data.length / 8)

  return (
    <figure className="ss-chart m-0">
      <svg viewBox={`0 0 ${width} 100`} preserveAspectRatio="none" style={{ height }} role="img" aria-label={title}>
        {[25, 50, 75].map((y) => (
          <line key={y} x1="0" x2={width} y1={y} y2={y} stroke="var(--ss-line)" strokeWidth="0.3" vectorEffect="non-scaling-stroke" />
        ))}
        {data.map((d, i) => {
          const h = ((Number(d.value) || 0) / max) * 96
          return (
            <rect key={d.label} x={i * (barWidth + gap) + gap / 2} y={100 - h} width={barWidth} height={Math.max(h, 0.4)} rx="0.6" fill={color}>
              <title>{`${d.label}: ${format(d.value)}`}</title>
            </rect>
          )
        })}
      </svg>
      <div className="ss-chart-labels">
        {data.map((d, i) => (
          <span key={d.label} style={{ visibility: i % labelEvery === 0 ? 'visible' : 'hidden' }}>
            {d.label}
          </span>
        ))}
      </div>
      <table className="visually-hidden">
        <caption>{title}</caption>
        <tbody>
          {data.map((d) => (
            <tr key={d.label}>
              <th scope="row">{d.label}</th>
              <td>{format(d.value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  )
}

/** Horizontal bars for ranked lists (top products, categories, sellers). */
export function RankedBars({ rows, format = (v) => v }) {
  const max = Math.max(1, ...rows.map((r) => Number(r.value) || 0))
  return (
    <ul className="ss-ranked">
      {rows.map((r) => (
        <li key={r.label}>
          <div className="d-flex justify-content-between small mb-1 gap-2">
            <span className="text-truncate">{r.label}</span>
            <strong className="text-nowrap">{format(r.value)}</strong>
          </div>
          <div className="ss-ranked-track" aria-hidden="true">
            <div style={{ width: `${((Number(r.value) || 0) / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ul>
  )
}
