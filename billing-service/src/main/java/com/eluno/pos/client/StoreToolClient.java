package com.eluno.pos.client;

import com.eluno.pos.dto.PrescriptionDto;
import com.eluno.pos.dto.StaffDto;
import com.eluno.pos.dto.StoreDto;
import java.util.List;

public interface StoreToolClient {
    List<PrescriptionDto> getPrescriptionsByPhone(String phone);

    List<StaffDto> getStaffByStore(StoreDto store);
}
