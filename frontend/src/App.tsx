// 앱 라우팅 루트
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Suspense, lazy } from 'react'

// 초기 로드에 항상 필요한 경량 페이지는 정적 import 유지
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import AuthCallbackPage from './pages/AuthCallbackPage'

// React Flow, 커뮤니티 에디터 등 무거운 페이지는 지연 로드
const MyPage = lazy(() => import('./pages/MyPage'))
const GraphPage = lazy(() => import('./pages/GraphPage'))
const GraphViewerPage = lazy(() => import('./pages/GraphViewerPage'))
const CommunityPage = lazy(() => import('./pages/CommunityPage'))
const MessagesPage = lazy(() => import('./pages/MessagesPage'))
const CommunityPostGraphPage = lazy(() => import('./pages/CommunityPostGraphPage'))
const DiffPage = lazy(() => import('./pages/DiffPage'))
const AdminPage = lazy(() => import('./pages/AdminPage'))
const PaymentSuccessPage = lazy(() => import('./pages/PaymentSuccessPage'))
const PaymentCancelPage = lazy(() => import('./pages/PaymentCancelPage'))
const ChangelogPage = lazy(() => import('./pages/ChangelogPage'))
const HowItWorksPage = lazy(() => import('./pages/HowItWorksPage'))
const EvolutionPage = lazy(() => import('./pages/EvolutionPage'))
const DogfoodingPage = lazy(() => import('./pages/DogfoodingPage'))
const TermsPage = lazy(() => import('./pages/TermsPage'))
const PrivacyPage = lazy(() => import('./pages/PrivacyPage'))
const ContactPage = lazy(() => import('./pages/ContactPage'))
const BookmarksPage = lazy(() => import('./pages/BookmarksPage'))
const UserProfilePage = lazy(() => import('./pages/UserProfilePage'))
const SettingsPage = lazy(() => import('./pages/SettingsPage'))
const DonatePage = lazy(() => import('./pages/DonatePage'))
const DonateSuccessPage = lazy(() => import('./pages/DonateSuccessPage'))
const DonateFailPage = lazy(() => import('./pages/DonateFailPage'))
const JoinCollaborationPage = lazy(() => import('./pages/JoinCollaborationPage'))
const TeamsPage = lazy(() => import('./pages/TeamsPage'))
const TeamPaymentSuccessPage = lazy(() => import('./pages/TeamPaymentSuccessPage'))

import CookieBanner from './components/CookieBanner'
import NoticeBanner from './components/NoticeBanner'
import ErrorToast from './components/ErrorToast'

// 앱 최상위 라우터 컴포넌트
export default function App() {

  return (
    <BrowserRouter>
      <Suspense fallback={null}>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/auth/callback" element={<AuthCallbackPage />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/dashboard" element={<Navigate to="/mypage" replace />} />
          <Route path="/projects/:projectId/graph" element={<GraphPage />} />
          <Route path="/share/:projectId" element={<GraphViewerPage />} />
          <Route path="/community" element={<CommunityPage />} />
          <Route path="/community/posts/:postId/graph" element={<CommunityPostGraphPage />} />
          <Route path="/community/posts/:postId/graph/:position" element={<CommunityPostGraphPage />} />
          <Route path="/payment/success" element={<PaymentSuccessPage />} />
          <Route path="/payment/team-success" element={<TeamPaymentSuccessPage />} />
          <Route path="/payment/cancel" element={<PaymentCancelPage />} />
          <Route path="/payment/fail" element={<PaymentCancelPage />} />
          <Route path="/changelog" element={<ChangelogPage />} />
          <Route path="/how-it-works" element={<HowItWorksPage />} />
          <Route path="/evolution" element={<EvolutionPage />} />
          <Route path="/dogfooding" element={<DogfoodingPage />} />
          <Route path="/projects/:projectId/diff" element={<DiffPage />} />
          <Route path="/terms" element={<TermsPage />} />
          <Route path="/privacy" element={<PrivacyPage />} />
          <Route path="/contact" element={<ContactPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/bookmarks" element={<BookmarksPage />} />
          <Route path="/users/:userId" element={<UserProfilePage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/donate" element={<DonatePage />} />
          <Route path="/donate/success" element={<DonateSuccessPage />} />
          <Route path="/donate/fail" element={<DonateFailPage />} />
          <Route path="/collab/join" element={<JoinCollaborationPage />} />
          <Route path="/collab/:inviteCode" element={<JoinCollaborationPage />} />
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/teams" element={<TeamsPage />} />
        </Routes>
      </Suspense>
      <NoticeBanner />
      <CookieBanner />
      <ErrorToast />
    </BrowserRouter>
  )
}
