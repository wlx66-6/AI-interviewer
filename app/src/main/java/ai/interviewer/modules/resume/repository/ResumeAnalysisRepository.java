package ai.interviewer.modules.resume.repository;

import ai.interviewer.modules.resume.model.ResumeAnalysisEntity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 简历评测Repository
 */
@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysisEntity, Long> {

    /**
     * 根据简历ID查找最新评测记录
     *
     * <p>用 EntityGraph 一并取出关联简历：评测结果的 DTO 里带简历原文
     * （{@code ResumePersistenceService#entityToDTO} 读 {@code entity.getResume().getResumeText()}），
     * 而该转换发生在事务外，懒加载代理脱离持久化上下文后取值会抛
     * {@code LazyInitializationException}（关掉 OSIV 后暴露）。
     */
    @EntityGraph(attributePaths = "resume")
    ResumeAnalysisEntity findFirstByResumeIdOrderByAnalyzedAtDesc(Long resumeId);

    /**
     * 根据简历ID查找所有评测记录
     */
    @EntityGraph(attributePaths = "resume")
    List<ResumeAnalysisEntity> findByResumeIdOrderByAnalyzedAtDesc(Long resumeId);
}
