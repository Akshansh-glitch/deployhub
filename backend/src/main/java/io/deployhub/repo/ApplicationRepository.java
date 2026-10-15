package io.deployhub.repo;

import io.deployhub.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<Application> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByName(String name);
}
