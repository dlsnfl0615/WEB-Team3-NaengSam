package com.naengsam.quick.domain.delivery.service;

import com.naengsam.quick.domain.delivery.dto.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 부르미 - 드리미 매칭 로직 스켈레톤.
 * 로직 자체는 원본 그대로 두고, 컴파일/자료구조/네이밍 일관성만 보정한 버전.
 */
@Service
@RequiredArgsConstructor
public class MatchingService implements MatchingContext {

    /**
     * 드리미 응답 제한시간. TODO: 정책 확정 후 조정
     */
    private static final Duration OFFER_TTL = Duration.ofSeconds(30);
    /**
     * 한 주문에 동시에 제안할 최대 드리미 수
     */
    private static final int MAX_OFFER_COUNT = 3;

    // ────────────────────────────── 도메인 타입 ──────────────────────────────


    /**
     * 큐에 쌓이는 액션. 타입별로 필요한 payload가 달라서 sealed interface로 분리
     */


    /**
     * status가 계속 바뀌므로 record가 아닌 가변 클래스로 변경.
     * (record로 유지하려면 withStatus()로 새 인스턴스를 만들어 맵에 다시 넣어야 함)
     */
    public static final class MatchOffer {
        private final UUID offerId;
        private final UUID orderId;
        private final UUID dreamiId;
        private final LocalDateTime expiresAt;
        private MatchOfferStatus status;

        public MatchOffer(UUID offerId, UUID orderId, UUID dreamiId,
                          MatchOfferStatus status, LocalDateTime expiresAt) {
            this.offerId = offerId;
            this.orderId = orderId;
            this.dreamiId = dreamiId;
            this.status = status;
            this.expiresAt = expiresAt;
        }

        public UUID offerId() {
            return offerId;
        }

        public UUID orderId() {
            return orderId;
        }

        public UUID dreamiId() {
            return dreamiId;
        }

        public LocalDateTime expiresAt() {
            return expiresAt;
        }

        public MatchOfferStatus status() {
            return status;
        }

        public void changeStatus(MatchOfferStatus status) {
            this.status = status;
        }
    }

    public enum MatchOfferStatus {
        OFFERED,                        // 드리미에게 제안이 전달되어 응답 대기 중
        PENDING_BOORMI_CONFIRMATION,    // 해당 드리미가 수락하여 부르미의 승낙을 대기중
        MATCHED,                        // 해당 드리미가 수락했고 부르미도 수락해 매칭 후보로 확정됨
        BOORMI_REJECTED,                // 해당 부르미가 명시적으로 거절함
        DREAMI_REJECTED,                // 해당 드리미가 명시적으로 거절함
        BOORMI_EXPIRED,                 // 제한 시간 내 부르미가 응답하지 않아 만료됨
        DREAMI_EXPIRED,                 // 제한 시간 내 드리미가 응답하지 않아 만료됨
        WITHDRAWN                       // 다른 드리미가 먼저 수락했거나 서버가 제안을 회수함
    }

    /**
     * 기다리고 있는 드리미 (콜 대기중인 드리미). 마찬가지로 status가 바뀌므로 가변 클래스
     */
    public static final class WaitingDreami {
        private final UUID dreamiId;
        private GeoPoint location;
        private WaitingDreamiStatus status;
        private LocalDateTime updatedAt;

        public WaitingDreami(UUID dreamiId, GeoPoint location,
                             WaitingDreamiStatus status, LocalDateTime updatedAt) {
            this.dreamiId = dreamiId;
            this.location = location;
            this.status = status;
            this.updatedAt = updatedAt;
        }

        public UUID dreamiId() {
            return dreamiId;
        }

        public GeoPoint location() {
            return location;
        }

        public WaitingDreamiStatus status() {
            return status;
        }

        public LocalDateTime updatedAt() {
            return updatedAt;
        }

        public void changeStatus(WaitingDreamiStatus status) {
            this.status = status;
            this.updatedAt = LocalDateTime.now();
        }
    }

    public enum WaitingDreamiStatus {
        MATCHING,   // 지금 매칭 중
        PROPOSED    // 지금 Match Offer 방 안에 들어감
    }

    // ────────────────────────────── 저장소 ──────────────────────────────

    private final Map<UUID, MatchOffer> offersById = new HashMap<>();           // Map<OfferUUID, MatchOffer>
    private final Map<UUID, Set<UUID>> offerIdsByDreamiId = new HashMap<>();    // Map<DreamiUUID, Set<OfferUUID>>
    private final Map<UUID, Set<UUID>> offerIdsByOrderId = new HashMap<>();     // Map<OrderUUID, Set<OfferUUID>>

    /**
     * 하나의 주문에 대해 동시에 뿌린 제안 묶음 = "방". 원본의 offerMap을 orderId 기준으로 정리
     */
    private final Map<UUID, List<MatchOffer>> offersByOrderId = new HashMap<>();

    private final Map<UUID, WaitingDreami> dreamiMap = new HashMap<>();

    private final MatchingEngine matchingEngine;

    // ────────────────────────────── 액션 제출 (public API) ──────────────────────────────

    // 외부에서는 이 메서드로 액션을 큐에 넣기만 한다. 실제 상태 변경은 엔진 스레드에서 apply*가 수행한다.
    public void registerDreami(UUID dreamiId, GeoPoint location) {
        matchingEngine.submit(new DreamiRegister(this, dreamiId, location));
    }

    public void removeDreami(UUID dreamiId) {
        matchingEngine.submit(new DreamiRemove(this, dreamiId));
    }

    void alarmBySocket(String message) {
        // 소켓을 통해 알림보내기
        // 아직 코드 구현X
    }

    @Override
    public void applyRegisterDreami(UUID dreamiId, GeoPoint location) {
        dreamiMap.put(dreamiId,
                new WaitingDreami(dreamiId, location, WaitingDreamiStatus.MATCHING, LocalDateTime.now()));
    }

    @Override
    public void applyRemoveDreami(UUID dreamiId) {
        dreamiMap.remove(dreamiId);
    }

    // 액션 하나 처리
    void startMatching(Order order) {
        List<WaitingDreami> top3List = dreamiMap.values().stream()
                .filter(dreami -> dreami.status() == WaitingDreamiStatus.MATCHING)
                .sorted(orderingComparator())
                .limit(MAX_OFFER_COUNT)
                .toList();

        // 상황 : 상위 3명 알림 보낼 드리미 완성
        LocalDateTime expiresAt = LocalDateTime.now().plus(OFFER_TTL);
        List<MatchOffer> matchOfferList = new ArrayList<>();
        for (WaitingDreami dreami : top3List) {
            UUID offerId = UUID.randomUUID(); // 제안UUID (드리미 1명당 1개)
            MatchOffer offer = new MatchOffer(
                    offerId, order.orderId(), dreami.dreamiId(), MatchOfferStatus.OFFERED, expiresAt);
            matchOfferList.add(offer);

            offersById.put(offerId, offer);
            offerIdsByDreamiId.computeIfAbsent(dreami.dreamiId(), k -> new HashSet<>()).add(offerId);
            offerIdsByOrderId.computeIfAbsent(order.orderId(), k -> new HashSet<>()).add(offerId);
        }

        // 드리미 3명에게 status를 변경 요청
        for (WaitingDreami dreami : top3List) {
            dreamiMap.get(dreami.dreamiId()).changeStatus(WaitingDreamiStatus.PROPOSED);
        }
        offersByOrderId.put(order.orderId(), matchOfferList); // 방안에상위3명넣기 (사실상 방 만들기)
        alarmBySocket("드리미에게_제안_팝업_띄우기");
    }

    // 팝업에서 수락을 눌렀다는 가정
    void acceptByDreami(WaitingDreami dreami, UUID offerId) {
        Optional<MatchOffer> optionalMatchOffer = acceptableOffer(offerId);
        if (optionalMatchOffer.isEmpty()) {
            return;
        }
        MatchOffer matchOffer = optionalMatchOffer.get();
        List<MatchOffer> room = offersByOrderId.get(matchOffer.orderId());
        if (room == null) {
            alarmBySocket("존재하지 않는 주문입니다.");
            return;
        }

        // 수락한 사람의 상태를 PENDING_BOORMI_CONFIRMATION로 변경
        // 나머지 매칭오퍼 상태를 WITHDRAW로 변경
        for (MatchOffer offer : room) {
            // 수락한사람은 PENDING_BOORMI_CONFIRMATION
            // 나머지 사람은 WITHDRAWN
            if (offer.dreamiId().equals(dreami.dreamiId())) {
                offer.changeStatus(MatchOfferStatus.PENDING_BOORMI_CONFIRMATION);
                // 드리미의 status는 PROPOSED 유지
                assert dreami.status() == WaitingDreamiStatus.PROPOSED;
                alarmBySocket("부르미한테_드리미정보_팝업넘기기");
            } else if (offer.status() == MatchOfferStatus.OFFERED) {
                // 아직 응답 대기중(OFFERED)인 오퍼만 회수한다.
                // 이미 거절/만료됐거나 다른 방으로 넘어간 드리미의 상태는 건드리지 않는다.
                offer.changeStatus(MatchOfferStatus.WITHDRAWN);
                // 선착순에서 패배한 드리미를 다시 매칭 수락가능한 상태로 변경
                findDreami(offer.dreamiId())
                        .ifPresent(otherDreami -> otherDreami.changeStatus(WaitingDreamiStatus.MATCHING));
                alarmBySocket("팝업꺼지게(선착순패배)");
            }
        }

    }

    // 드리미가 거절하면, DREAMI_REJECTED로 변경 및 다시 대기상태로
    void rejectByDreami(WaitingDreami dreami, UUID offerId) {
        findOffer(offerId).ifPresentOrElse(
                offer -> {
                    dreami.changeStatus(WaitingDreamiStatus.MATCHING);
                    offer.changeStatus(MatchOfferStatus.DREAMI_REJECTED);
                    alarmBySocket("거절했으니 팝업끄면됨");
                },
                () -> alarmBySocket("존재하지 않는 제안입니다.")
        );
    }

    void acceptByBoormi(UUID offerId) {
        findOffer(offerId).ifPresentOrElse(
                matchOffer -> {
                    assert matchOffer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION;
                    matchOffer.changeStatus(MatchOfferStatus.MATCHED); // 부르미까지 수락 완료
                    proceedToDelivery(matchOffer);
                },
                () -> alarmBySocket("존재하지 않는 제안입니다.")
        );
    }

    void rejectByBoormi(UUID offerId) {
        findOffer(offerId).ifPresentOrElse(
                matchOffer -> {
                    assert matchOffer.status() == MatchOfferStatus.PENDING_BOORMI_CONFIRMATION;
                    matchOffer.changeStatus(MatchOfferStatus.BOORMI_REJECTED);

                    // 거절당한 드리미의 상태를 배달가능 상태로 변경
                    alarmBySocket("거절당한_드리미에게_부르미가_거절했다고_알려주기");
                    findDreami(matchOffer.dreamiId())
                            .ifPresent(dreami -> dreami.changeStatus(WaitingDreamiStatus.MATCHING));
                },
                () -> alarmBySocket("존재하지 않는 제안입니다.")
        );
    }

    // 이건 액션에 의해 실행 되어야함
    void expireDreamiOffer(UUID offerId) {
        // 해당 match가 OFFERED 상태가 아니라면 다른 로직에 의해서 처리가 된거임
        findOffer(offerId)
                .filter(matchOffer -> matchOffer.status() == MatchOfferStatus.OFFERED)
                .ifPresent(matchOffer -> {
                    matchOffer.changeStatus(MatchOfferStatus.DREAMI_EXPIRED);
                    findDreami(matchOffer.dreamiId())
                            .ifPresent(dreami -> dreami.changeStatus(WaitingDreamiStatus.MATCHING));
                });
    }

    void expireBoormiOffer(UUID offerId) {
        // TODO: 드리미쪽과 달리 status 가드가 없음. PENDING_BOORMI_CONFIRMATION 체크 필요 여부 확인
        findOffer(offerId).ifPresent(matchOffer -> {
            // 드리미가 다시 배달이 가능하게 바꿔야함
            matchOffer.changeStatus(MatchOfferStatus.BOORMI_EXPIRED);
            findDreami(matchOffer.dreamiId())
                    .ifPresent(dreami -> dreami.changeStatus(WaitingDreamiStatus.MATCHING));
        });
    }

    // ────────────────────────────── 미구현 ──────────────────────────────

    /**
     * TODO: 거리순 등 실제 정렬 기준 확정 전까지는 대기 오래한 순
     */
    private Comparator<WaitingDreami> orderingComparator() {
        return Comparator.comparing(WaitingDreami::updatedAt);
    }

    // ────────────────────────────── 조회 헬퍼 ──────────────────────────────

    private Optional<MatchOffer> findOffer(UUID offerId) {
        return Optional.ofNullable(offersById.get(offerId));
    }

    private Optional<WaitingDreami> findDreami(UUID dreamiId) {
        return Optional.ofNullable(dreamiMap.get(dreamiId));
    }

    /**
     * 드리미가 정상적으로 수락 가능한 오퍼만 반환한다.
     * 없거나 이미 종료된 상태면 실패 알림을 보내고 empty를 반환한다.
     */
    private Optional<MatchOffer> acceptableOffer(UUID offerId) {
        MatchOffer offer = offersById.get(offerId);
        if (offer == null) {
            alarmBySocket("존재하지 않는 제안입니다.");
            return Optional.empty();
        }
        // 이미 자신 matchOffer상태가 WITHDRAWN이면? -> 실패메시지
        if (offer.status() == MatchOfferStatus.WITHDRAWN) {
            alarmBySocket("이미 다른 드리미가 수락한 주문입니다.");
            return Optional.empty();
        }
        // 정상적으로 수락 가능한 상태는 OFFERED 뿐. (거절/만료된 제안은 수락 불가)
        if (offer.status() != MatchOfferStatus.OFFERED) {
            alarmBySocket("이미 종료된 제안입니다.");
            return Optional.empty();
        }
        return Optional.of(offer);
    }

    private void proceedToDelivery(MatchOffer matchOffer) {
        // 아직 코드 구현X
    }
}