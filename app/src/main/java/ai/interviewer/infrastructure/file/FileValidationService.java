package ai.interviewer.infrastructure.file;

import ai.interviewer.common.exception.BusinessException;
import ai.interviewer.common.exception.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件验证服务
 * 提供通用的文件验证功能
 */
@Slf4j
@Service
public class FileValidationService {

    /**
     * 验证文件基本属性（是否为空、文件大小）
     *
     * @param file 上传的文件
     * @param maxSizeBytes 最大文件大小（字节）
     * @param fileTypeName 文件类型名称（用于错误消息，如"简历"、"知识库"）
     */
    public void validateFile(MultipartFile file, long maxSizeBytes, String fileTypeName) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                String.format("请选择要上传的%s文件", fileTypeName));
        }

        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件大小超过限制");
        }
    }

    /**
     * 验证文件类型（基于MIME类型）
     *
     * @param contentType 文件的MIME类型
     * @param allowedTypes 允许的MIME类型列表（支持部分匹配，如"pdf"会匹配"application/pdf"）
     * @param errorMessage 验证失败时的错误消息
     */
    public void validateContentTypeByList(String contentType, List<String> allowedTypes, String errorMessage) {
        if (!isAllowedType(contentType, allowedTypes)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                errorMessage != null ? errorMessage : "不支持的文件类型: " + contentType);
        }
    }

    /**
     * 验证文件类型（基于MIME类型和文件扩展名）
     *
     * @param contentType 文件的MIME类型
     * @param fileName 文件名（用于扩展名检查）
     * @param mimeTypeChecker MIME类型检查器
     * @param extensionChecker 文件扩展名检查器
     * @param errorMessage 验证失败时的错误消息
     */
    public void validateContentType(String contentType, String fileName,
                                   Predicate<String> mimeTypeChecker,
                                   Predicate<String> extensionChecker,
                                   String errorMessage) {
        // 先检查MIME类型
        if (mimeTypeChecker.test(contentType)) {
            return;
        }

        // MIME 是按内容识别出来的，只有它给不出信息时才允许用扩展名兜底。
        // 否则把 HTML 改名为 .md 就能混过这里。
        if (isUnknownMimeType(contentType) && fileName != null && extensionChecker.test(fileName)) {
            return;
        }

        throw new BusinessException(ErrorCode.BAD_REQUEST,
            errorMessage != null ? errorMessage : "不支持的文件类型: " + contentType);
    }

    /**
     * 检查文件类型是否在允许列表中
     */
    private boolean isAllowedType(String contentType, List<String> allowedTypes) {
        if (contentType == null || allowedTypes == null || allowedTypes.isEmpty()) {
            return false;
        }

        String baseType = normalize(contentType);
        if (baseType.isEmpty()) {
            return false;
        }

        return allowedTypes.stream()
            .map(this::normalize)
            .filter(allowed -> !allowed.isEmpty())
            .anyMatch(allowed -> matches(baseType, allowed));
    }

    /**
     * 判断 MIME 是否给不出类型信息：缺失、空串，或 HTTP 里的通用兜底值。
     */
    private boolean isUnknownMimeType(String contentType) {
        String baseType = normalize(contentType);
        return baseType.isEmpty() || "application/octet-stream".equals(baseType);
    }

    /**
     * 去掉 MIME 参数（如 ; charset=utf-8）并统一小写
     */
    private String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }

        int parameterStart = contentType.indexOf(';');
        String baseType = parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType;
        return baseType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(String baseType, String allowedType) {
        if (allowedType.endsWith("/*")) {
            return baseType.startsWith(allowedType.substring(0, allowedType.length() - 1));
        }

        // 配置里既有完整 MIME（application/pdf），也允许只写关键字（pdf）
        return allowedType.contains("/") ? baseType.equals(allowedType) : baseType.contains(allowedType);
    }

    /**
     * 检查文件扩展名是否为Markdown格式
     */
    public boolean isMarkdownExtension(String fileName) {
        if (fileName == null) {
            return false;
        }

        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".md") ||
               lowerFileName.endsWith(".markdown") ||
               lowerFileName.endsWith(".mdown");
    }

    /**
     * 检查MIME类型是否为知识库支持的格式
     */
    public boolean isKnowledgeBaseMimeType(String contentType) {
        if (contentType == null) {
            return false;
        }

        String lowerContentType = contentType.toLowerCase();
        return lowerContentType.contains("pdf") ||
               lowerContentType.contains("msword") ||
               lowerContentType.contains("wordprocessingml") ||
               lowerContentType.contains("text/plain") ||
               lowerContentType.contains("text/markdown") ||
               lowerContentType.contains("text/x-markdown") ||
               lowerContentType.contains("text/x-web-markdown") ||
               lowerContentType.contains("application/rtf");
    }
}
