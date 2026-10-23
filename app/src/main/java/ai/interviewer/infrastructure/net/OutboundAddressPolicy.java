package ai.interviewer.infrastructure.net;

import ai.interviewer.common.exception.BusinessException;
import ai.interviewer.common.exception.ErrorCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * 出站地址策略：只放行公网目标。
 *
 * <p>ASR/TTS 这类地址可以由接口改写，改完再点一次「测试连通性」就是一个内网端口探测器。
 * 这里看的是解析之后的地址，而不是字符串，避免 127.0.0.1 的其它写法绕过去。
 */
public final class OutboundAddressPolicy {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "ws", "wss");

    private OutboundAddressPolicy() {
    }

    /**
     * 校验字符串形式的地址，返回解析并校验过的地址列表
     */
    public static InetAddress[] requirePublicTarget(String url) {
        if (url == null || url.isBlank()) {
            throw reject(null, "地址为空");
        }
        try {
            return requirePublicTarget(URI.create(url.trim()));
        } catch (IllegalArgumentException e) {
            throw reject(null, "地址格式不正确");
        }
    }

    /**
     * 校验 URI，返回解析并校验过的地址列表；调用方应连这个地址，而不是再解析一次主机名
     */
    public static InetAddress[] requirePublicTarget(URI uri) {
        return requirePublicTarget(uri, OutboundAddressPolicy::resolveAll);
    }

    static InetAddress[] requirePublicTarget(URI uri, Function<String, InetAddress[]> resolver) {
        if (uri == null || uri.getScheme() == null) {
            throw reject(null, "地址为空或缺少协议");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw reject(null, "不支持的协议: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw reject(null, "地址缺少主机名");
        }

        InetAddress[] addresses;
        try {
            addresses = resolver.apply(host);
        } catch (RuntimeException e) {
            throw reject(host, "无法解析主机");
        }
        if (addresses == null || addresses.length == 0) {
            throw reject(host, "无法解析主机");
        }

        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw reject(host, "只允许公网地址");
            }
        }

        return addresses;
    }

    private static InetAddress[] resolveAll(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析主机: " + host, e);
        }
    }

    private static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] raw = address.getAddress();
        if (raw.length == 16) {
            // IPv6 唯一本地地址 fc00::/7
            return (raw[0] & 0xFE) == 0xFC;
        }

        int firstOctet = raw[0] & 0xFF;
        int secondOctet = raw[1] & 0xFF;
        // 0.0.0.0/8 与运营商级 NAT 100.64.0.0/10
        return firstOctet == 0 || (firstOctet == 100 && secondOctet >= 64 && secondOctet <= 127);
    }

    private static BusinessException reject(String host, String reason) {
        String message = host == null ? reason : reason + ": " + host;
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
