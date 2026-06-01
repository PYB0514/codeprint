package com.codeprint.infrastructure.persistence.project;

import com.codeprint.domain.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUserId(UUID userId);

    int countByUserId(UUID userId);
}
