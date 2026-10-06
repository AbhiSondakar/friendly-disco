package com.ecoloop.partner;

import com.ecoloop.common.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/partners")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPartnerController {

    private final PartnerRepository partners;
    private final PartnerLifecycleService lifecycleService;

    public AdminPartnerController(PartnerRepository partners, PartnerLifecycleService lifecycleService) {
        this.partners = partners;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    public PageResponse<PartnerDto> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String search) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(Math.max(1, size), 100);
        PageRequest pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Partner> spec = Specification.where(PartnerSpecifications.withStatus(status))
                .and(PartnerSpecifications.withSearch(search));
        Page<Partner> partnerPage = partners.findAll(spec, pageable);
        Page<PartnerDto> dtoPage = partnerPage.map(p -> PartnerDto.from(p, true));
        return PageResponse.of(dtoPage);
    }

    @GetMapping("/{id}")
    public PartnerDto get(@PathVariable UUID id) {
        return partners.findById(id)
            .map(p -> PartnerDto.from(p, true))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found"));
    }

    @PostMapping("/{id}/approve")
    public PartnerDto approve(@PathVariable UUID id) {
        return PartnerDto.from(lifecycleService.approve(id), true);
    }

    @PostMapping("/{id}/reject")
    public PartnerDto reject(@PathVariable UUID id) {
        return PartnerDto.from(lifecycleService.reject(id), true);
    }

    @PostMapping("/{id}/suspend")
    public PartnerDto suspend(@PathVariable UUID id) {
        return PartnerDto.from(lifecycleService.suspend(id), true);
    }
}
