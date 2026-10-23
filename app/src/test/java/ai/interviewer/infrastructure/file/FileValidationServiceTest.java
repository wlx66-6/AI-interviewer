package ai.interviewer.infrastructure.file;

import ai.interviewer.common.exception.BusinessException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("文件类型校验")
class FileValidationServiceTest {

    private final FileValidationService fileValidationService = new FileValidationService();

    /**
     * 与 application.yml 中 app.resume.allowed-types 相同的列表
     */
    private static final List<String> RESUME_TYPES = List.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain"
    );

    @Nested
    @DisplayName("MIME 白名单")
    class MimeWhitelist {

        @Test
        @DisplayName("空字符串不能命中任何白名单项")
        void shouldRejectBlankContentType() {
            // 之前的实现用双向 contains 比较，空字符串是任何字符串的子串，
            // 于是 contentType 为空时任何白名单都会被判定通过
            assertThatThrownBy(() -> fileValidationService.validateContentTypeByList("", RESUME_TYPES, null))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("带 charset 等参数时按主类型比较")
        void shouldIgnoreContentTypeParameters() {
            assertThatCode(() -> fileValidationService.validateContentTypeByList(
                "application/pdf; charset=binary", RESUME_TYPES, null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("大小写不同也视为同一类型")
        void shouldIgnoreCase() {
            assertThatCode(() -> fileValidationService.validateContentTypeByList(
                "APPLICATION/PDF", RESUME_TYPES, null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("白名单里写短类型名时按子串匹配")
        void shouldMatchShortTokenInWhitelist() {
            assertThatCode(() -> fileValidationService.validateContentTypeByList(
                "application/pdf", List.of("pdf"), null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("不在白名单内的类型被拒绝")
        void shouldRejectUnknownType() {
            assertThatThrownBy(() -> fileValidationService.validateContentTypeByList(
                "text/html", RESUME_TYPES, "不支持的文件类型"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
        }
    }

    @Nested
    @DisplayName("MIME 与扩展名的关系")
    class MimeAndExtension {

        @Test
        @DisplayName("MIME 已识别为其他类型时扩展名不能放行")
        void shouldNotLetExtensionOverrideDetectedMime() {
            // Tika 从内容识别出 text/html，改个 .md 后缀不应该变成合法知识库文件
            assertThatThrownBy(() -> fileValidationService.validateContentType(
                "text/html", "笔记.md",
                fileValidationService::isKnowledgeBaseMimeType,
                fileValidationService::isMarkdownExtension,
                null))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("MIME 未知时按扩展名兜底")
        void shouldFallBackToExtensionWhenMimeUnknown() {
            assertThatCode(() -> fileValidationService.validateContentType(
                "application/octet-stream", "笔记.md",
                fileValidationService::isKnowledgeBaseMimeType,
                fileValidationService::isMarkdownExtension,
                null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MIME 缺失时按扩展名兜底")
        void shouldFallBackToExtensionWhenMimeMissing() {
            assertThatCode(() -> fileValidationService.validateContentType(
                null, "笔记.markdown",
                fileValidationService::isKnowledgeBaseMimeType,
                fileValidationService::isMarkdownExtension,
                null))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("MIME 与扩展名都不匹配时拒绝")
        void shouldRejectWhenNeitherMatches() {
            assertThatThrownBy(() -> fileValidationService.validateContentType(
                "application/x-msdownload", "virus.exe",
                fileValidationService::isKnowledgeBaseMimeType,
                fileValidationService::isMarkdownExtension,
                null))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("文件名缺失时不能仅凭 MIME 未知就放行")
        void shouldRejectWhenFileNameMissingAndMimeUnknown() {
            assertThatThrownBy(() -> fileValidationService.validateContentType(
                "application/octet-stream", null,
                fileValidationService::isKnowledgeBaseMimeType,
                fileValidationService::isMarkdownExtension,
                null))
                .isInstanceOf(BusinessException.class);
        }
    }
}
