import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap-icons/font/bootstrap-icons.css'
import './App.css'
import './styles/features.css'
import './styles/components.css'
import './styles/motion.css'

import { AuthProvider } from './context/AuthContext'
import { LiveProvider } from './context/LiveContext'
import { CartProvider } from './context/CartContext'
import { ToastProvider } from './context/ToastContext'
import { WishlistProvider } from './context/WishlistContext'
import ScrollToTop from './components/ScrollToTop'
import { PageLoader } from './components/ui'
import ProtectedRoute, { GuestRoute } from './routes/ProtectedRoute'

import MainLayout from './layouts/MainLayout'
import AccountLayout from './layouts/AccountLayout'
// Seller studio and admin console are loaded only when opened, keeping the storefront bundle small.
const SellerLayout = lazy(() => import('./layouts/SellerLayout'))
const AdminLayout = lazy(() => import('./layouts/AdminLayout'))
const DeliveryLayout = lazy(() => import('./layouts/DeliveryLayout'))
const DeliveryDashboard = lazy(() => import('./pages/delivery/DeliveryDashboard'))
const DeliveryProfile = lazy(() => import('./pages/delivery/DeliveryProfile'))

import Home from './pages/Home'
import Products from './pages/Products'
import ProductDetails from './pages/ProductDetails'
import Cart from './pages/Cart'
import Checkout from './pages/Checkout'
import OrderConfirmation from './pages/OrderConfirmation'
import Help from './pages/Help'
import NotFound from './pages/NotFound'
import About from './pages/info/About'
import Contact from './pages/info/Contact'
import Policy from './pages/info/Policy'

import Login from './pages/auth/Login'
import Register from './pages/auth/Register'
import VerifyEmail from './pages/auth/VerifyEmail'
import ForgotPassword from './pages/auth/ForgotPassword'
import ResetPassword from './pages/auth/ResetPassword'

import Account from './pages/customer/Account'
import Orders from './pages/customer/Orders'
import OrderDetails from './pages/customer/OrderDetails'
import Wishlist from './pages/customer/Wishlist'
import Addresses from './pages/customer/Addresses'
import Returns from './pages/customer/Returns'
import Payments from './pages/customer/Payments'
import MyReviews from './pages/customer/MyReviews'
import Notifications from './pages/customer/Notifications'

const SellerDashboard = lazy(() => import('./pages/seller/SellerDashboard'))
const SellerProducts = lazy(() => import('./pages/seller/SellerProducts'))
const SellerProductForm = lazy(() => import('./pages/seller/SellerProductForm'))
const SellerOrders = lazy(() => import('./pages/seller/SellerOrders'))
const SellerInventory = lazy(() => import('./pages/seller/SellerInventory'))
const SellerReturns = lazy(() => import('./pages/seller/SellerReturns'))
const SellerSales = lazy(() => import('./pages/seller/SellerSales'))
const SellerCommission = lazy(() => import('./pages/seller/SellerCommission'))
const SellerStoreProfile = lazy(() => import('./pages/seller/SellerStoreProfile'))

const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'))
const AdminCategories = lazy(() => import('./pages/admin/AdminCategories'))
const AdminSellers = lazy(() => import('./pages/admin/AdminSellers'))
const AdminProducts = lazy(() => import('./pages/admin/AdminProducts'))
const AdminOrders = lazy(() => import('./pages/admin/AdminOrders'))
const AdminCoupons = lazy(() => import('./pages/admin/AdminCoupons'))
const AdminReviews = lazy(() => import('./pages/admin/AdminReviews'))
const AdminUsers = lazy(() => import('./pages/admin/AdminUsers'))
const AdminPayments = lazy(() => import('./pages/admin/AdminPayments'))
const AdminReturns = lazy(() => import('./pages/admin/AdminReturns'))
const AdminReports = lazy(() => import('./pages/admin/AdminReports'))
const AdminSettings = lazy(() => import('./pages/admin/AdminSettings'))
const AdminMessages = lazy(() => import('./pages/admin/AdminMessages'))
const AdminDeliveryPartners = lazy(() => import('./pages/admin/AdminDeliveryPartners'))

function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <LiveProvider>
          <CartProvider>
            <WishlistProvider>
              <ScrollToTop />
              <Suspense fallback={<PageLoader />}>
                <Routes>
                  {/* Sign-in / sign-up: separate full-screen pages without the store header */}
                  <Route element={<GuestRoute />}>
                    <Route path="login" element={<Login />} />
                    <Route path="register" element={<Register />} />
                    <Route path="verify-email" element={<VerifyEmail />} />
                    <Route path="forgot-password" element={<ForgotPassword />} />
                    <Route path="reset-password" element={<ResetPassword />} />
                  </Route>

                  {/* Storefront */}
                  <Route element={<MainLayout />}>
                    <Route index element={<Home />} />
                    <Route path="products" element={<Products />} />
                    <Route path="products/:id" element={<ProductDetails />} />
                    <Route path="help" element={<Help />} />
                    <Route path="about" element={<About />} />
                    <Route path="contact" element={<Contact />} />
                    <Route path="policies/:slug" element={<Policy />} />

                    {/* My account (any signed-in user; some tabs are customer-only) */}
                    <Route element={<ProtectedRoute />}>
                      <Route path="account" element={<AccountLayout />}>
                        <Route index element={<Account />} />
                        <Route path="notifications" element={<Notifications />} />
                        <Route element={<ProtectedRoute roles={['CUSTOMER']} />}>
                          <Route path="orders" element={<Orders />} />
                          <Route path="returns" element={<Returns />} />
                          <Route path="payments" element={<Payments />} />
                          <Route path="wishlist" element={<Wishlist />} />
                          <Route path="addresses" element={<Addresses />} />
                          <Route path="reviews" element={<MyReviews />} />
                        </Route>
                      </Route>
                    </Route>

                    <Route element={<ProtectedRoute roles={['CUSTOMER']} />}>
                      <Route path="cart" element={<Cart />} />
                      <Route path="checkout" element={<Checkout />} />
                      <Route path="order-confirmation/:id" element={<OrderConfirmation />} />
                      <Route path="account/orders/:id" element={<OrderDetails />} />
                    </Route>

                    <Route path="*" element={<NotFound />} />
                  </Route>

                  {/* Seller studio */}
                  <Route
                    path="seller"
                    element={
                      <ProtectedRoute roles={['SELLER']}>
                        <SellerLayout />
                      </ProtectedRoute>
                    }
                  >
                    <Route index element={<SellerDashboard />} />
                    <Route path="products" element={<SellerProducts />} />
                    <Route path="products/new" element={<SellerProductForm />} />
                    <Route path="products/:id/edit" element={<SellerProductForm />} />
                    <Route path="inventory" element={<SellerInventory />} />
                    <Route path="orders" element={<SellerOrders />} />
                    <Route path="returns" element={<SellerReturns />} />
                    <Route path="sales" element={<SellerSales />} />
                    <Route path="commission" element={<SellerCommission />} />
                    <Route path="profile" element={<SellerStoreProfile />} />
                    <Route path="notifications" element={<Notifications />} />
                  </Route>

                  {/* Delivery partner area */}
                  <Route
                    path="delivery"
                    element={
                      <ProtectedRoute roles={['DELIVERY_PARTNER']}>
                        <DeliveryLayout />
                      </ProtectedRoute>
                    }
                  >
                    <Route index element={<DeliveryDashboard />} />
                    <Route path="profile" element={<DeliveryProfile />} />
                    <Route path="notifications" element={<Notifications />} />
                  </Route>

                  {/* Admin console */}
                  <Route
                    path="admin"
                    element={
                      <ProtectedRoute roles={['ADMIN']}>
                        <AdminLayout />
                      </ProtectedRoute>
                    }
                  >
                    <Route index element={<AdminDashboard />} />
                    <Route path="users" element={<AdminUsers />} />
                    <Route path="delivery-partners" element={<AdminDeliveryPartners />} />
                    <Route path="categories" element={<AdminCategories />} />
                    <Route path="sellers" element={<AdminSellers />} />
                    <Route path="products" element={<AdminProducts />} />
                    <Route path="orders" element={<AdminOrders />} />
                    <Route path="coupons" element={<AdminCoupons />} />
                    <Route path="payments" element={<AdminPayments />} />
                    <Route path="returns" element={<AdminReturns />} />
                    <Route path="reviews" element={<AdminReviews />} />
                    <Route path="reports" element={<AdminReports />} />
                    <Route path="settings" element={<AdminSettings />} />
                    <Route path="messages" element={<AdminMessages />} />
                    <Route path="notifications" element={<Notifications />} />
                  </Route>
                </Routes>
              </Suspense>
            </WishlistProvider>
          </CartProvider>
          </LiveProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}

export default App






