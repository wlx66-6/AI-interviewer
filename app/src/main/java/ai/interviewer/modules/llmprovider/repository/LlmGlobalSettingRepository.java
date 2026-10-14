package ai.interviewer.modules.llmprovider.repository;

import ai.interviewer.modules.llmprovider.model.LlmGlobalSettingEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmGlobalSettingRepository extends JpaRepository<LlmGlobalSettingEntity, Long> {
}
