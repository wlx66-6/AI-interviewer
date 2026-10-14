package ai.interviewer.modules.llmprovider.repository;

import ai.interviewer.modules.llmprovider.model.LlmProviderEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LlmProviderRepository extends JpaRepository<LlmProviderEntity, String> {

  List<LlmProviderEntity> findByEnabledTrueOrderByIdAsc();
}
