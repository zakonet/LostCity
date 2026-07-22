package common.cn.kafei.simukraft.medical;

import common.cn.kafei.simukraft.citizen.CitizenData;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalServiceTest {
    @Test
    void ignoresNullMedicalBedIdsWhenMatchingPatients() {
        UUID bedId = UUID.randomUUID();
        Set<UUID> bedIds = Set.of(bedId);

        assertFalse(MedicalService.containsMedicalBed(bedIds, null));
        assertFalse(MedicalService.containsMedicalBed(bedIds, UUID.randomUUID()));
        assertTrue(MedicalService.containsMedicalBed(bedIds, bedId));
    }

    @Test
    void diseaseBypassesResidentialCoverageForAdmission() {
        CitizenData citizen = new CitizenData(UUID.randomUUID());

        assertFalse(MedicalService.canBypassResidentialCoverage(citizen));
        citizen.setDisease(DiseaseType.COLD, 1L);
        assertTrue(MedicalService.canBypassResidentialCoverage(citizen));
    }
}
