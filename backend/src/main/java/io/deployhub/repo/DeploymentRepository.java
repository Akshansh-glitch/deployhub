package io.deployhub.repo;

import io.deployhub.domain.DeployState;
import io.deployhub.domain.DeploymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<DeploymentRecord, Long> {
    List<DeploymentRecord> findByApplicationIdOrderByIdDesc(Long applicationId);
    Optional<DeploymentRecord> findFirstByApplicationIdOrderByIdDesc(Long applicationId);
    List<DeploymentRecord> findByStatusIn(Collection<DeployState> states);
    List<DeploymentRecord> findByApplicationIdAndStatus(Long applicationId, DeployState status);
    boolean existsByApplicationIdAndStatusIn(Long applicationId, Collection<DeployState> states);
}
