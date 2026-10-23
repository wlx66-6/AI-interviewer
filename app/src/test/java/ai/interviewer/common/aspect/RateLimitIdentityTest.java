package ai.interviewer.common.aspect;

import ai.interviewer.common.config.RateLimitProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("限流身份解析")
class RateLimitIdentityTest {

    private final RateLimitProperties properties = new RateLimitProperties();

    private final RateLimitAspect aspect = new RateLimitAspect(null, properties);

    @Nested
    @DisplayName("客户端地址")
    class ClientIp {

        @Test
        @DisplayName("没有可信代理时不读 X-Forwarded-For")
        void shouldIgnoreForwardedHeaderByDefault() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.0.2.10");
            request.addHeader("X-Forwarded-For", "203.0.113.7");

            // X-Forwarded-For 是客户端能自己写的头，直接采信等于把限流桶的钥匙交给对方
            assertThat(aspect.resolveClientIp(request)).isEqualTo("192.0.2.10");
        }

        @Test
        @DisplayName("声明了可信代理后取转发链第一段")
        void shouldTakeFirstHopWhenProxyTrusted() {
            properties.setTrustForwardedHeaders(true);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");

            assertThat(aspect.resolveClientIp(request)).isEqualTo("203.0.113.7");
        }

        @Test
        @DisplayName("声明了可信代理但没有转发头时仍用连接地址")
        void shouldFallBackToRemoteAddrWhenHeaderMissing() {
            properties.setTrustForwardedHeaders(true);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");

            assertThat(aspect.resolveClientIp(request)).isEqualTo("10.0.0.1");
        }
    }

    @Nested
    @DisplayName("用户身份")
    class Identity {

        @Test
        @DisplayName("请求头里的 X-User-Id 不参与身份判定")
        void shouldIgnoreUserIdHeader() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.0.2.10");
            request.addHeader("X-User-Id", "attacker-123");

            // 之前这里会采信该头，每换一个值就是一个全新的限流桶
            assertThat(aspect.resolveIdentity(request)).isEqualTo("ip:192.0.2.10");
        }

        @Test
        @DisplayName("有认证结果时优先用 userId")
        void shouldPreferResolvedUserId() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.0.2.10");
            request.setAttribute("userId", "42");

            assertThat(aspect.resolveIdentity(request)).isEqualTo("42");
        }

        @Test
        @DisplayName("没有认证结果时按客户端地址分桶")
        void shouldBucketByClientAddressWhenAnonymous() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("198.51.100.8");

            assertThat(aspect.resolveIdentity(request)).isEqualTo("ip:198.51.100.8");
        }
    }
}
