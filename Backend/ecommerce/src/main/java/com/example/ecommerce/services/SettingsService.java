package com.example.ecommerce.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.admin.PlatformSettingsDto;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.repositories.PlatformSettingsRepository;

import lombok.RequiredArgsConstructor;

/** Reads and updates the single platform_settings row (created with defaults on first start). */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final PlatformSettingsRepository repository;

    @Transactional
    public PlatformSettings get() {
        return repository.findById(PlatformSettings.SINGLETON_ID)
                .orElseGet(() -> repository.saveAndFlush(new PlatformSettings()));
    }

    @Transactional
    public PlatformSettingsDto update(PlatformSettingsDto dto) {
        PlatformSettings s = get();
        s.setCommissionPercent(dto.commissionPercent());
        s.setFreeShippingThreshold(dto.freeShippingThreshold());
        s.setShippingFee(dto.shippingFee());
        s.setCodEnabled(dto.codEnabled());
        s.setCodMaxAmount(dto.codMaxAmount());
        s.setReturnWindowDays(dto.returnWindowDays());
        s.setLowStockThreshold(dto.lowStockThreshold());
        return PlatformSettingsDto.from(repository.saveAndFlush(s));
    }
}
