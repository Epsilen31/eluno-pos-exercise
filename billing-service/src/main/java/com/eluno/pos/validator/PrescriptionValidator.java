package com.eluno.pos.validator;

import com.eluno.pos.dto.EyePrescriptionDto;
import com.eluno.pos.dto.LensLimitsDto;
import com.eluno.pos.dto.PrescriptionDto;
import com.eluno.pos.dto.ProductVariantDto;
import com.eluno.pos.exception.BillingValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PrescriptionValidator {
    public List<String> validate(
            PrescriptionDto prescription, ProductVariantDto lens, LocalDate date) {
        if (prescription == null) {
            throw new BillingValidationException(
                    "No valid prescription. Refer customer to the optometrist before billing lenses.");
        }
        if (date.isBefore(prescription.getDate()) || date.isAfter(prescription.getValidUntil())) {
            throw new BillingValidationException(
                    "Prescription is expired or future-dated. Arrange an eye test.");
        }
        if (lens.getLimits() == null) {
            throw new BillingValidationException(
                    "Lens has no clinical limits; ask the lab to complete the catalog.");
        }
        List<String> warnings = new ArrayList<>();
        validateEye("Right", prescription.getRight(), lens, prescription.getSource(), warnings);
        validateEye("Left", prescription.getLeft(), lens, prescription.getSource(), warnings);
        return warnings;
    }

    private void validateEye(
            String name,
            EyePrescriptionDto eye,
            ProductVariantDto lens,
            String source,
            List<String> warnings) {
        LensLimitsDto limits = lens.getLimits();
        if (eye == null || eye.getSphere() == null || eye.getCylinder() == null) {
            throw new BillingValidationException(
                    name + " eye has incomplete sphere/cylinder; consult optometrist.");
        }
        if (eye.getSphere().compareTo(limits.getSphereMin()) < 0
                || eye.getSphere().compareTo(limits.getSphereMax()) > 0) {
            throw new BillingValidationException(
                    name
                            + " sphere "
                            + eye.getSphere()
                            + " is outside "
                            + lens.getSku()
                            + " range ["
                            + limits.getSphereMin()
                            + ", "
                            + limits.getSphereMax()
                            + "]. Choose a compatible index/lens.");
        }
        if (eye.getCylinder().abs().compareTo(limits.getCylinderMax()) > 0) {
            throw new BillingValidationException(
                    name
                            + " cylinder exceeds "
                            + lens.getSku()
                            + " limit; choose a compatible lens.");
        }
        if (limits.isAddRequired()
                || List.of("progressive", "bifocal").contains(lens.getLensType())) {
            if (eye.getAdd() == null) {
                throw new BillingValidationException(
                        name
                                + " ADD is required for progressive/bifocal lenses; consult optometrist.");
            }
            boolean belowMinimumAdd =
                    limits.getAddMin() != null && eye.getAdd().compareTo(limits.getAddMin()) < 0;
            boolean aboveMaximumAdd =
                    limits.getAddMax() != null && eye.getAdd().compareTo(limits.getAddMax()) > 0;
            if (belowMinimumAdd || aboveMaximumAdd) {
                throw new BillingValidationException(
                        name
                                + " ADD is outside this lens's supported range; consult optometrist/lab.");
            }
        }
        if (eye.getCylinder().signum() != 0 && eye.getAxis() == null) {
            throw new BillingValidationException(
                    name + " axis is required when cylinder is nonzero.");
        }
        if (eye.getAxis() != null) {
            if (eye.getAxis() < 0 || eye.getAxis() > 180) {
                throw new BillingValidationException(name + " axis must be between 0 and 180.");
            }
            if (eye.getAxis() % 5 != 0) {
                if (!"external".equals(source)) {
                    throw new BillingValidationException(
                            name
                                    + " in-store axis must be on a 5-degree grid; consult optometrist.");
                }
                warnings.add(
                        name
                                + " external axis "
                                + eye.getAxis()
                                + " preserved without rounding; optometrist review recommended.");
            }
        }
    }
}
