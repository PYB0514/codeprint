// 개인정보처리방침 페이지
import { useNavigate } from 'react-router-dom'
import Footer from '../components/Footer'

export default function PrivacyPage() {
  const navigate = useNavigate()

  return (
    <div className="min-h-screen flex flex-col bg-gray-950 text-white">

      {/* 헤더 */}
      <header className="flex items-center justify-between px-8 py-5 border-b border-gray-800">
        <button
          onClick={() => navigate('/')}
          className="font-bold text-lg tracking-tight hover:text-gray-300 transition-colors"
        >
          Codeprint
        </button>
      </header>

      {/* 본문 */}
      <main className="flex-1 max-w-3xl mx-auto w-full px-6 py-12">
        <h1 className="text-2xl font-bold mb-2">개인정보처리방침</h1>
        <p className="text-gray-500 text-sm mb-10">최종 업데이트: 2025년 1월 1일</p>

        <Section title="1. 수집하는 개인정보 항목">
          서비스는 GitHub OAuth 로그인을 통해 다음 정보를 수집합니다.
          <ul className="list-disc list-inside space-y-1 mt-2">
            <li>GitHub 사용자명 (login)</li>
            <li>이메일 주소</li>
            <li>프로필 이미지 URL</li>
            <li>GitHub 고유 ID</li>
          </ul>
          <p className="mt-3">
            분석 기능 사용 시 GitHub 레포지토리 메타데이터(레포명, 브랜치 목록, 파일 구조)를
            일시적으로 처리합니다. 원본 코드 파일은 분석 후 즉시 삭제되며 서버에 영구 저장하지 않습니다.
          </p>
        </Section>

        <Section title="2. 수집 목적">
          <ul className="list-disc list-inside space-y-1">
            <li>본인 인증 및 서비스 로그인</li>
            <li>프로필 표시 및 사용자 식별</li>
            <li>프로젝트·분석 결과 저장 및 조회</li>
            <li>커뮤니티 기능 (게시글 작성자 표시)</li>
            <li>서비스 개선 및 이용 통계 분석</li>
          </ul>
        </Section>

        <Section title="3. 보유 및 이용 기간">
          개인정보는 회원 탈퇴 시까지 보유하며, 탈퇴 즉시 파기합니다. 단, 법령에 의한 보존 의무가
          있는 경우 해당 기간 동안 보관합니다.
          <ul className="list-disc list-inside space-y-1 mt-2">
            <li>전자상거래 거래 기록: 5년 (전자상거래법)</li>
            <li>소비자 불만·분쟁 처리 기록: 3년 (전자상거래법)</li>
          </ul>
        </Section>

        <Section title="4. 제3자 제공">
          서비스는 이용자의 개인정보를 원칙적으로 외부에 제공하지 않습니다. 단, 다음의 경우 예외입니다.
          <ul className="list-disc list-inside space-y-1 mt-2">
            <li>이용자의 사전 동의가 있는 경우</li>
            <li>법령에 의해 제공이 요구되는 경우</li>
            <li>서비스 운영을 위한 인프라 파트너 (Railway, Vercel, AWS S3 — 데이터 처리 목적에 한함)</li>
          </ul>
        </Section>

        <Section title="5. 쿠키 사용">
          서비스는 로그인 상태 유지(JWT 토큰)를 위해 localStorage를 사용하며, 서비스 이용 통계를 위해
          분석 쿠키를 사용할 수 있습니다. 쿠키 동의 배너를 통해 비필수 쿠키 수집을 거부할 수 있습니다.
          거부 시 로그인 유지 등 일부 기능이 제한될 수 있습니다.
        </Section>

        <Section title="6. 이용자 권리">
          이용자는 언제든지 다음 권리를 행사할 수 있습니다.
          <ul className="list-disc list-inside space-y-1 mt-2">
            <li>개인정보 열람·수정 요청</li>
            <li>개인정보 삭제(탈퇴) 요청</li>
            <li>개인정보 처리 정지 요청</li>
          </ul>
          <p className="mt-3">
            요청은{' '}
            <button
              onClick={() => navigate('/contact')}
              className="underline text-gray-400 hover:text-white transition-colors"
            >
              문의하기
            </button>
            를 통해 접수하며, 10영업일 이내 처리합니다.
          </p>
        </Section>

        <Section title="7. 미성년자 보호">
          만 14세 미만 아동의 개인정보는 법정대리인의 동의 없이 수집하지 않습니다. 만 14세 미만임을
          인지한 경우 즉시 해당 정보를 파기합니다.
        </Section>

        <Section title="8. 개인정보 보호 책임자">
          <p>개인정보 처리 관련 문의, 불만 처리, 피해 구제 등에 관한 사항은 아래로 문의하세요.</p>
          <ul className="list-none mt-2 space-y-1">
            <li>이메일: <a href="mailto:qkrdydqls999@gmail.com" className="underline text-gray-400 hover:text-white transition-colors">qkrdydqls999@gmail.com</a></li>
          </ul>
        </Section>

        <Section title="9. 방침 변경">
          본 방침이 변경되는 경우, 변경 내용과 시행일을 서비스 공지사항을 통해 사전 안내합니다.
        </Section>
      </main>

      <Footer />
    </div>
  )
}

// 섹션 블록 헬퍼 컴포넌트
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-8">
      <h2 className="text-base font-semibold mb-3 text-white">{title}</h2>
      <div className="text-gray-400 text-sm leading-relaxed">{children}</div>
    </section>
  )
}
