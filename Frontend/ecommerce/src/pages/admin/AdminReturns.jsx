import adminService from '../../services/adminService'
import ReturnsManager from '../../components/ReturnsManager'
import { PageHeader } from '../../components/ui'

export default function AdminReturns() {
  return (
    <>
      <PageHeader
        eyebrow="After-sales"
        title="Returns"
        subtitle="Return requests across every store. Sellers usually decide their own returns; step in here when a request needs escalation."
      />
      <ReturnsManager fetchPage={adminService.getReturns} decide={adminService.decideReturn} showStore />
    </>
  )
}
