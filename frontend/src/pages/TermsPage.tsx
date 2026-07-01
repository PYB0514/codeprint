// 이용약관 페이지
import { useNavigate } from 'react-router-dom'
import Footer from '../components/Footer'

// 이용약관 렌더링
export default function TermsPage() {
  const navigate = useNavigate()

  return (
    <div className="app-page min-h-screen flex flex-col bg-gray-950 text-white">

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
        <h1 className="text-2xl font-bold mb-2">이용약관</h1>
        <p className="text-gray-500 text-sm mb-10">최종 업데이트: 2026년 6월 12일</p>

        <Section title="제1조 (목적)">
          본 약관은 Codeprint(이하 "서비스")가 제공하는 GitHub 레포지토리 분석 및 시각화 서비스의
          이용 조건 및 절차, 이용자와 서비스 간의 권리·의무 관계를 규정함을 목적으로 합니다.
        </Section>

        <Section title="제2조 (정의)">
          <ul className="list-disc list-inside space-y-1 text-gray-400">
            <li>"서비스"란 Codeprint가 제공하는 코드 분석 및 그래프 시각화 플랫폼을 의미합니다.</li>
            <li>"이용자"란 본 약관에 동의하고 서비스를 이용하는 개인을 의미합니다.</li>
            <li>"콘텐츠"란 이용자가 서비스를 통해 생성·업로드·게시하는 모든 자료를 의미합니다.</li>
          </ul>
        </Section>

        <Section title="제3조 (이용 자격)">
          서비스는 GitHub 계정을 보유한 누구나 이용할 수 있습니다. 만 14세 미만의 경우 법정대리인의 동의가
          필요합니다. 서비스 이용 시 본 약관에 동의한 것으로 간주합니다.
        </Section>

        <Section title="제4조 (서비스 내용)">
          <ul className="list-disc list-inside space-y-1 text-gray-400">
            <li><strong className="text-white">무료 플랜:</strong> 비공개 프로젝트 무제한, 팀 협업 최대 5명</li>
            <li><strong className="text-white">Desktop 라이센스:</strong> AI 기능, 그래프 버전 히스토리 (개인 1석 또는 팀 N석, 좌석당 월정액)</li>
          </ul>
          <p className="mt-3 text-gray-400">
            서비스는 사전 고지 없이 기능을 추가·변경·중단할 수 있으며, 서비스 안정성을 위한 일시적 중단이
            발생할 수 있습니다.
          </p>
        </Section>

        <Section title="제5조 (이용자 의무)">
          이용자는 다음 행위를 하여서는 안 됩니다.
          <ul className="list-disc list-inside space-y-1 mt-2 text-gray-400">
            <li>타인의 계정 또는 개인정보를 도용하는 행위</li>
            <li>서비스의 안정적 운영을 방해하는 행위 (과도한 API 요청, 자동화 크롤링 등)</li>
            <li>타인의 저작권·지식재산권을 침해하는 콘텐츠 게시</li>
            <li>불법·음란·혐오 등 법령에 위반되는 콘텐츠 게시</li>
            <li>서비스를 이용하여 상업적 목적으로 제3자에게 유료 서비스를 제공하는 행위</li>
          </ul>
        </Section>

        <Section title="제6조 (분석 정확도 및 면책)">
          서비스의 코드 분석은 정적 분석 기반으로 약 85~90%의 정확도를 제공합니다. 동적 호출,
          런타임 의존성 등은 감지되지 않을 수 있습니다. 분석 결과는 참고용 "자동 초안"이며,
          이를 근거로 한 판단·결정에 대한 책임은 이용자에게 있습니다.
        </Section>

        <Section title="제7조 (지식재산권)">
          서비스 및 서비스가 자체 생성한 그래프 UI·아이콘·디자인의 지식재산권은 Codeprint에 귀속됩니다.
          이용자가 분석에 사용한 코드의 저작권은 해당 저작권자에게 있으며, 서비스는 분석 목적 외로
          해당 코드를 저장·사용하지 않습니다.
        </Section>

        <Section title="제8조 (개인정보 처리)">
          이용자의 개인정보는{' '}
          <button
            onClick={() => navigate('/privacy')}
            className="underline text-gray-400 hover:text-white transition-colors"
          >
            개인정보처리방침
          </button>
          에 따라 처리됩니다.
        </Section>

        <Section title="제9조 (서비스 중단 및 종료)">
          서비스가 영구 종료되는 경우, 이용자에게 30일 이전 공지합니다. Desktop 라이센스 유료 이용자에게는
          잔여 기간에 대한 환불 정책을 별도로 안내합니다.
        </Section>

        <Section title="제10조 (약관 변경)">
          약관을 변경하는 경우 변경 내용과 시행일을 서비스 공지사항 또는 이메일로 7일 이전에 안내합니다.
          변경 후 서비스를 계속 이용하면 변경된 약관에 동의한 것으로 간주합니다.
        </Section>

        <Section title="제11조 (준거법 및 관할)">
          본 약관은 대한민국 법률에 따라 해석되며, 서비스와 이용자 간 분쟁은 서울중앙지방법원을
          전속 관할 법원으로 합니다.
        </Section>

        <Section title="문의">
          <button
            onClick={() => navigate('/contact')}
            className="underline text-gray-400 hover:text-white transition-colors"
          >
            문의하기 페이지
          </button>
          를 통해 연락 주세요.
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
