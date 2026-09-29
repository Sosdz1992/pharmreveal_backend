package com.alibou.security.drug;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DrugService {

    private final DrugRepository drugRepository;
    private final EntityManager entityManager;


    @Transactional
    public void removeAllDrugs() {
        drugRepository.deleteAllFast();
        log.info("Drugs removed");
    }


    private static final int BATCH_SIZE = 1000;

    public void importExcel(File file) throws Exception {
        List<Drug> batch = new ArrayList<>(BATCH_SIZE);
        int[] saved = {0};

        List<String> errors = DrugExcelReader.read(file, drug -> {
            batch.add(drug);
            if (batch.size() == BATCH_SIZE) {
                saved[0] += saveBatch(batch);
            }
        });

        // Сохраняем оставшиеся записи
        if (!batch.isEmpty()) {
            saved[0] += saveBatch(batch);
        }

        log.info("Импорт завершён. Сохранено: {}, ошибок: {}", saved[0], errors.size());
        errors.forEach(log::warn);
    }

    private int saveBatch(List<Drug> batch) {
        int size = batch.size();
        drugRepository.saveAll(batch);
        // не копим сохранённые сущности в persistence context на весь импорт
        entityManager.flush();
        entityManager.clear();
        batch.clear();
        return size;
    }

    public Page<Drug> fetchAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return drugRepository.findAll(pageRequest);
    }

    public List<DrugExportDto> fetchAllWithFilters(DrugFilterRequest request) {
        Specification<Drug> spec = Specification.where(null);

        spec = addListFilter(spec, request.getYear(), "year");
        spec = addListFilter(spec, request.getInn(), "inn");
        spec = addListFilter(spec, request.getSegment(), "segment");
        spec = addListFilter(spec, request.getTradeName(), "tradeName");
        spec = addListFilter(spec, request.getManufacturingCompany(), "manufacturingCompany");
        spec = addListFilter(spec, request.getDrugForm(), "drugForm");
        spec = addListFilter(spec, request.getDosage(), "dosage");
        spec = addListFilter(spec, request.getPackQuantity(), "packQuantity");
        spec = addListFilter(spec, request.getAtc1(), "atc1");
        spec = addListFilter(spec, request.getAtc2(), "atc2");
        spec = addListFilter(spec, request.getAtc3(), "atc3");

        spec = addListFilter(spec, request.getPersonWithTradingLicense(), "personWithTradingLicense");
        spec = addListFilter(spec, request.getPersonInterestedInRegistrationGeorgiaStand(), "personInterestedInRegistrationGeorgiaStand");
        spec = addListFilter(spec, request.getInterestedParty(), "interestedParty");
        spec = addListFilter(spec, request.getRxOtc(), "rxOtc");
        spec = addListFilter(spec, request.getModeOfRegistration(), "modeOfRegistration");
        spec = addListFilter(spec, request.getSku(), "sku");
        spec = addListFilter(spec, request.getPriceSource(), "priceSource");


        if (request.getDateFrom() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("importDate"), request.getDateFrom()));
        }

        if (request.getDateTo() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("importDate"), request.getDateTo()));
        }

        List<Drug> drugs = drugRepository.findAll(spec);

        return drugs.stream()
                .map(d -> new DrugExportDto(
                        d.getSegment(),
                        d.getTradeName(),
                        d.getManufacturingCompany(),
                        d.getDrugForm(),
                        d.getDosage(),
                        d.getPackQuantity(),
                        d.getInn(),
                        d.getAtc1(),
                        d.getAtc2(),
                        d.getAtc3(),
                        d.getImportDate(),
                        d.getYear(),
                        d.getPersonWithTradingLicense(),
                        d.getPersonInterestedInRegistrationGeorgiaStand(),
                        d.getInterestedParty(),
                        d.getRxOtc(),
                        d.getModeOfRegistration(),
                        d.getSku(),
                        d.getVolumeInUnits(),
                        d.getPricePerUnitLari(),
                        d.getPricePerUnitUsd(),
                        d.getValueInGel(),
                        d.getValueInUsd(),
                        d.getVolumeInSU(),
                        d.getPriceSource()
                ))
                .toList();
    }


    private Specification<Drug> addListFilter(Specification<Drug> spec, List<String> values, String field) {
        if (values != null && !values.isEmpty()) {
            return spec.and((root, query, cb) -> root.get(field).in(values));
        }
        return spec;
    }


    public List<NameValueDto> getTopMolecules(DrugFilterRequest filter, String metric) {
        Pageable topFive = PageRequest.of(0, 5);
        return drugRepository.findTopByGroupFieldWithFilters(filter, metric, "inn", topFive);
    }

    public List<NameValueDto> getTopProducts(DrugFilterRequest filter, String metric) {
        Pageable topFive = PageRequest.of(0, 5);
        return drugRepository.findTopByGroupFieldWithFilters(filter, metric, "tradeName", topFive);
    }

    public List<NameValueDto> getTopCompanies(DrugFilterRequest filter, String metric) {
        Pageable topFive = PageRequest.of(0, 5);
        return drugRepository.findTopByGroupFieldWithFilters(filter, metric, "personWithTradingLicense", topFive);
    }

    public List<NameValueDto> getTopAtc1(DrugFilterRequest filter, String metric) {
        Pageable topFive = PageRequest.of(0, 3);
        return drugRepository.findTopByGroupFieldWithFilters(filter, metric, "atc1", topFive);
    }

    public List<NameValueDto> getTopSegment(DrugFilterRequest filter, String metric) {
        Pageable topFive = PageRequest.of(0, 3);
        return drugRepository.findTopByGroupFieldWithFilters(filter, metric, "segment", topFive);
    }


//
//    public List<NameValueDto> getTopCompaniesFiltered(DrugFilterRequest request, String currency) {
//        return drugRepository.findTopCompaniesWithFilters(request, currency.toLowerCase(), 5);
//    }


}


