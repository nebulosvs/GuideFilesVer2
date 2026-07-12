package com.example.bdgetconsumer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.bdgetconsumer.model.DispatchGuideSummary;

public interface DispatchGuideSummaryRepository extends JpaRepository<DispatchGuideSummary, Long> {
}