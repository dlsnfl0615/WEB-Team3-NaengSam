package com.naengsam.quick.domain.delivery.controller;

import com.naengsam.quick.domain.delivery.dto.GeoPoint;
import com.naengsam.quick.domain.delivery.service.MatchingService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final MatchingService matchingService;

    @PostMapping("/dreami")
    public UUID registerDreami(@RequestBody GeoPoint location) {
        UUID dreamiId = UUID.randomUUID();
        matchingService.registerDreami(dreamiId, location);
        return dreamiId;
    }

    @DeleteMapping("/dreami/{dreamiId}")
    public void removeDreami(@PathVariable UUID dreamiId) {
        matchingService.removeDreami(dreamiId);
    }

    @GetMapping("/dreami")
    public List<DreamiView> waitingDreamis() {
        return matchingService.waitingDreamis().stream()
                .map(DreamiView::from)
                .toList();
    }

    record DreamiView(UUID dreamiId, GeoPoint location,
                      MatchingService.WaitingDreamiStatus status, LocalDateTime updatedAt) {

        static DreamiView from(MatchingService.WaitingDreami dreami) {
            return new DreamiView(dreami.dreamiId(), dreami.location(), dreami.status(), dreami.updatedAt());
        }
    }
}
