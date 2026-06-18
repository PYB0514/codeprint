// 배포 환경(Railway Linux)에서 tree-sitter native .so 로드 가능 여부를 시작 시 로그로 검증하는 프로브 (spike)
package com.codeprint.tools.treesitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterJava;

// TREESITTER_PROBE=true 일 때만 동작 — 모든 프로필에서 의도적으로 켤 때만 실행되게 게이팅
@Component
@Profile("!test")
public class TreeSitterStartupProbe implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterStartupProbe.class);

    // 환경변수 게이트가 켜져 있으면 native 로드를 시도하고 결과를 로그에 남긴다
    @Override
    public void run(ApplicationArguments args) {
        if (!"true".equalsIgnoreCase(System.getenv("TREESITTER_PROBE"))) {
            return;
        }
        String platform = System.getProperty("os.name") + " / " + System.getProperty("os.arch");
        log.info("[treesitter-probe] 시작 — 플랫폼: {}", platform);
        try {
            long t0 = System.nanoTime();
            TSParser parser = new TSParser();
            parser.setLanguage(new TreeSitterJava());
            TSTree tree = parser.parseString(null, "class A { void m(){ n(); } void n(){} }");
            String root = tree.getRootNode().getType();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.info("[treesitter-probe] ✅ native 로드 성공 — 루트 노드: {}, {}ms", root, ms);
        } catch (Throwable t) {
            log.error("[treesitter-probe] ❌ native 로드 실패 — {}: {}", t.getClass().getName(), t.getMessage(), t);
        }
    }
}
