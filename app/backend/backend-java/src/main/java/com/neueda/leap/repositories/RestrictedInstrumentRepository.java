package com.neueda.leap.repositories;

import com.neueda.leap.models.RestrictedInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestrictedInstrumentRepository extends JpaRepository<RestrictedInstrument, String> {
}
