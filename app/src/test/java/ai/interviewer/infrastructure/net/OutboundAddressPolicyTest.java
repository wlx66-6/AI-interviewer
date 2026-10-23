package ai.interviewer.infrastructure.net;

import ai.interviewer.common.exception.BusinessException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("出站地址策略")
class OutboundAddressPolicyTest {

    private static final Map<String, String> HOSTS = Map.of(
        "dashscope.aliyuncs.com", "203.0.113.7",
        "localhost", "127.0.0.1",
        "loopback", "127.0.0.1",
        "private-a", "10.1.2.3",
        "private-b", "192.168.1.10",
        "metadata", "169.254.169.254",
        "v6-loopback", "::1",
        "v6-unique-local", "fd00::1",
        "cgnat", "100.64.0.1"
    );

    private static final Function<String, InetAddress[]> RESOLVER = host -> {
        String address = HOSTS.get(host);
        if (address == null) {
            throw new IllegalArgumentException("无法解析主机: " + host);
        }
        return new InetAddress[] {inet(address)};
    };

    private static InetAddress inet(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertRejected(String url) {
        assertThatThrownBy(() -> OutboundAddressPolicy.requirePublicTarget(URI.create(url), RESOLVER))
            .isInstanceOf(BusinessException.class);
    }

    private void assertAccepted(String url) {
        assertThatCode(() -> OutboundAddressPolicy.requirePublicTarget(URI.create(url), RESOLVER))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("公网目标放行")
    void shouldAllowPublicTarget() {
        assertAccepted("wss://dashscope.aliyuncs.com/api-ws/v1/realtime");
        assertAccepted("https://dashscope.aliyuncs.com/api/v1");
    }

    @Test
    @DisplayName("本机回环地址被拒绝")
    void shouldRejectLoopback() {
        assertRejected("wss://loopback:6379");
        assertRejected("http://localhost:8080/actuator");
        assertRejected("http://v6-loopback:9000");
    }

    @Test
    @DisplayName("内网地址被拒绝")
    void shouldRejectPrivateNetworks() {
        assertRejected("ws://private-a:8086");
        assertRejected("ws://private-b:5432");
        assertRejected("ws://cgnat:6379");
        assertRejected("ws://v6-unique-local:1234");
    }

    @Test
    @DisplayName("云元数据地址被拒绝")
    void shouldRejectCloudMetadataAddress() {
        assertRejected("http://metadata/latest/meta-data/");
    }

    @Test
    @DisplayName("非网络协议被拒绝")
    void shouldRejectNonNetworkSchemes() {
        assertRejected("file:///C:/Windows/win.ini");
        assertRejected("gopher://dashscope.aliyuncs.com/");
    }

    @Test
    @DisplayName("缺少主机名时被拒绝")
    void shouldRejectMissingHost() {
        assertRejected("http:///latest/meta-data/");
    }

    @Test
    @DisplayName("解析失败时按拒绝处理")
    void shouldRejectWhenResolutionFails() {
        assertRejected("wss://nonexistent.invalid/ws");
    }

    @Test
    @DisplayName("解析出多个地址时只要有一个是内网就拒绝")
    void shouldRejectWhenAnyResolvedAddressIsInternal() {
        Function<String, InetAddress[]> mixed = host -> new InetAddress[] {inet("203.0.113.7"), inet("10.1.2.3")};

        assertThatThrownBy(() ->
            OutboundAddressPolicy.requirePublicTarget(URI.create("wss://dashscope.aliyuncs.com/ws"), mixed))
            .isInstanceOf(BusinessException.class);
    }
}
