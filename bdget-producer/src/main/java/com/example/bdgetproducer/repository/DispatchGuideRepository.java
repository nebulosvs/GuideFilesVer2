package com.example.bdgetproducer.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bdgetproducer.model.DispatchGuide;

public interface DispatchGuideRepository extends JpaRepository<DispatchGuide, Long> {

    List<DispatchGuide> findByTransportistaAndFechaOrderByCreatedAtDesc(String transportista, LocalDate fecha);

    List<DispatchGuide> findByTransportistaOrderByCreatedAtDesc(String transportista);

    List<DispatchGuide> findByFechaOrderByCreatedAtDesc(LocalDate fecha);
}
