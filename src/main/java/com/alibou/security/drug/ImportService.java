package com.alibou.security.drug;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final DrugService drugService;
    private final ReferenceService referenceService;

    // rollbackFor: иначе при checked-исключении удаление старых данных закоммитится
    @Transactional(rollbackFor = Exception.class)
    public void importDrugsAsync(File file) throws Exception {
        drugService.removeAllDrugs();
        drugService.importExcel(file);
        referenceService.setAll();
    }
}
