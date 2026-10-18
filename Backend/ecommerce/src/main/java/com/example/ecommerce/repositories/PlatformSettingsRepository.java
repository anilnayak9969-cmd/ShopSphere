package com.example.ecommerce.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ecommerce.entites.PlatformSettings;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {
}
