package com.example.patientvault.patient.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.patientvault.audit.AuditAction;
import com.example.patientvault.audit.AuditEvent;
import com.example.patientvault.audit.AuditService;
import com.example.patientvault.search.PatientSearch;
import com.example.patientvault.search.QueryType;
import com.example.patientvault.search.SearchResult;
import com.example.patientvault.search.TextNormalizer;
import com.example.patientvault.shared.Permissions;

/**
 * The patient list / search: asks the search module for candidate ids, then loads one page of ACTIVE patients
 * in a single query, decrypts it in memory and attaches each patient's latest visit. The query text is never
 * logged or audited; only its kind and the number of results are.
 */
@Service
public class PatientSearchService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final int DIAGNOSIS_SNIPPET = 120;

    /** One result card. {@code toString()} prints the id only. */
    public record PatientCard(UUID id, String firstName, String lastName, String email, String mobile, String amka,
                              String referralSource, boolean consentGiven, LocalDate latestVisitDate,
                              String latestDiagnosis) {
        public String fullName() {
            return firstName + " " + lastName;
        }

        @Override
        public String toString() {
            return "PatientCard[id=" + id + "]";
        }
    }

    /**
     * @param page 1-based page number actually shown
     * @param messageKey set when the query was rejected (explains what to change)
     * @param truncated more matches exist than the search returns; the query should be narrowed
     */
    public record SearchPage(List<PatientCard> cards, long total, int page, int size, int totalPages, QueryType type,
                             String messageKey, Object messageArg, boolean truncated) {
        public boolean hasPrevious() {
            return page > 1;
        }

        public boolean hasNext() {
            return page < totalPages;
        }
    }

    private final PatientSearch search;
    private final PatientRepository patients;
    private final VisitRepository visits;
    private final PatientCodec codec;
    private final AuditService audit;

    PatientSearchService(PatientSearch search, PatientRepository patients, VisitRepository visits,
                         PatientCodec codec, AuditService audit) {
        this.search = search;
        this.patients = patients;
        this.visits = visits;
        this.codec = codec;
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('" + Permissions.PATIENT_READ + "')")
    @Transactional(readOnly = true)
    public SearchPage search(String query, int page, int size) {
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 1);
        SearchResult result = search.find(query);

        Page<PatientEntity> found = switch (result.type()) {
            case EMPTY -> patients.findRecentPage(PageRequest.of(pageNumber - 1, pageSize));
            case INVALID -> Page.empty(PageRequest.of(0, pageSize));
            default -> result.candidateIds().isEmpty() ? Page.empty(PageRequest.of(0, pageSize))
                    : patients.findActiveByIds(result.candidateIds(), PageRequest.of(pageNumber - 1, pageSize));
        };

        List<PatientCard> cards = toCards(found.getContent());
        audit.record(AuditEvent.success(AuditAction.PATIENT_SEARCHED)
                .withDetail("type", result.type() == QueryType.EMPTY ? "RECENT" : result.type())
                .withDetail("count", found.getTotalElements()));
        return new SearchPage(cards, found.getTotalElements(), pageNumber, pageSize, found.getTotalPages(),
                result.type(), result.messageKey(), result.messageArg(), result.truncated());
    }

    private List<PatientCard> toCards(List<PatientEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<UUID, VisitEntity> latest = new HashMap<>();
        visits.findByPatientIdInOrderByVisitDateDescCreatedAtDesc(entities.stream().map(PatientEntity::getId).toList())
                .forEach(v -> latest.putIfAbsent(v.getPatientId(), v));

        List<PatientCard> cards = new ArrayList<>(entities.size());
        for (PatientEntity entity : entities) {
            PatientData data = codec.decode(entity);
            VisitEntity visit = latest.get(entity.getId());
            cards.add(new PatientCard(entity.getId(), data.firstName(), data.lastName(), data.email(), data.mobile(),
                    data.amka(), data.referralSource(), data.consentGiven(),
                    visit == null ? null : visit.getVisitDate(),
                    visit == null ? null : snippet(codec.decodeDiagnosis(visit, entity))));
        }
        // the page itself is chosen by recent activity; within it people are listed by name
        cards.sort(Comparator.comparing((PatientCard c) -> TextNormalizer.normalize(c.lastName()))
                .thenComparing(c -> TextNormalizer.normalize(c.firstName())));
        return cards;
    }

    private static String snippet(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        if (flat.length() <= DIAGNOSIS_SNIPPET) {
            return flat;
        }
        int cut = flat.lastIndexOf(' ', DIAGNOSIS_SNIPPET);
        return flat.substring(0, cut > DIAGNOSIS_SNIPPET / 2 ? cut : DIAGNOSIS_SNIPPET) + "…";
    }
}
