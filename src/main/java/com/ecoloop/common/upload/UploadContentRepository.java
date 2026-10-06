package com.ecoloop.common.upload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UploadContentRepository extends JpaRepository<UploadContent, UUID> {}
