package com.example.bdgetproducer.service;

import java.time.LocalDate;
import java.util.List;

import com.example.bdgetproducer.dto.DispatchGuideRequestDto;
import com.example.bdgetproducer.dto.DispatchGuideResponseDto;
import com.example.bdgetproducer.dto.DispatchGuideUpdateDto;

public interface DispatchGuideService {

    DispatchGuideResponseDto createGuide(DispatchGuideRequestDto request);

    DispatchGuideResponseDto uploadGuideToS3(Long id);

    byte[] downloadGuide(Long id, String transportista);

    DispatchGuideResponseDto updateGuide(Long id, DispatchGuideUpdateDto request, String transportista);

    void deleteGuide(Long id, String transportista);

    List<DispatchGuideResponseDto> getGuideHistory(String transportista, LocalDate fecha);
}
