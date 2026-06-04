// 앱 라우팅 루트
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import AuthCallbackPage from './pages/AuthCallbackPage'
import DashboardPage from './pages/DashboardPage'
import GraphPage from './pages/GraphPage'
import ShareGraphPage from './pages/ShareGraphPage'
import CommunityPage from './pages/CommunityPage'
import CommunityPostGraphPage from './pages/CommunityPostGraphPage'
import PaymentSuccessPage from './pages/PaymentSuccessPage'
import PaymentCancelPage from './pages/PaymentCancelPage'
import ChangelogPage from './pages/ChangelogPage'
import DiffPage from './pages/DiffPage'

// 앱 최상위 라우터 컴포넌트
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/auth/callback" element={<AuthCallbackPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/projects/:projectId/graph" element={<GraphPage />} />
        <Route path="/share/:projectId" element={<ShareGraphPage />} />
        <Route path="/community" element={<CommunityPage />} />
        <Route path="/community/posts/:postId/graph" element={<CommunityPostGraphPage />} />
        <Route path="/payment/success" element={<PaymentSuccessPage />} />
        <Route path="/payment/cancel" element={<PaymentCancelPage />} />
        <Route path="/changelog" element={<ChangelogPage />} />
        <Route path="/projects/:projectId/diff" element={<DiffPage />} />
      </Routes>
    </BrowserRouter>
  )
}
