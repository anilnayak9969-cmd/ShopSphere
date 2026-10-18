import sellerService from '../../services/sellerService'
import ReturnsManager from '../../components/ReturnsManager'
import { PageHeader } from '../../components/ui'

export default function SellerReturns() {
  return (
    <>
      <PageHeader
        eyebrow="After-sales"
        title="Returns"
        subtitle="Customers can ask to return delivered items within the return window. Approving refunds the item and restocks it."
      />
      <ReturnsManager fetchPage={sellerService.getReturns} decide={sellerService.decideReturn} />
    </>
  )
}
