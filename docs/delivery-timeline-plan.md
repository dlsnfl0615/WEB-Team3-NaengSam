# 배달 진행 타임라인 계획 (부르미 실시간 배송 화면)

## 배경

부르미 실시간 배송 화면(`frontend/src/pages/delivery-detail/ui/RealDeliveryTracking.tsx`)에서
지도 아래 흰색 카드("실시간 상태" + 굵은 상태 텍스트 한 줄)를, 배민 배달 화면처럼
4단계 진행 타임라인으로 교체한다.

## 대상 화면 / 위치

- 파일: `frontend/src/pages/delivery-detail/ui/RealDeliveryTracking.tsx`
- 교체 지점: 404~414번째 줄의 `Card`(핀 아이콘 + "실시간 상태" 라벨 + `{view.title}`)

```tsx
<Card className="flex items-center gap-3">
  <span className="... rounded-pill bg-teal-50 text-teal-700">
    <Icon name="pin" size={18} />
  </span>
  <div className="flex flex-col">
    <span className="text-2xs text-muted">실시간 상태</span>
    <span className="text-md font-bold text-navy-900">{view.title}</span>   {/* ← 이 자리를 4단계 타임라인으로 */}
  </div>
</Card>
```

- 지도 위 `<h1>{view.title}</h1>`(373~380번째 줄)을 그대로 둘지, 없앨지는 미확정 — 결정 필요.

## 단계 구성

픽업 시작 → 픽업 완료 → 배달 시작 → 배달 완료. 완료된 단계는 teal, 아직 안 된 단계는 회색.

## 상태 매핑

백엔드 `DeliveryStatusResponseDtoStatus`를 그대로 재사용(백엔드 변경 없음). 드리미가 픽업사진을
올려서 픽업 완료 처리하는 시점에 "픽업 완료"와 "배달 시작"이 동시에 완료 처리된다(현재 백엔드가
이 둘을 구분하는 별도 이벤트를 갖고 있지 않기 때문).

| 백엔드 status | 픽업 시작 | 픽업 완료 | 배달 시작 | 배달 완료 |
| --- | --- | --- | --- | --- |
| `PICKUP_NORMAL` / `PICKUP_DELAYED` (초기 진입) | ✅ | ⬜ | ⬜ | ⬜ |
| `DELIVERING` (픽업사진 업로드로 완료 처리된 시점) | ✅ | ✅ | ✅ | ⬜ |
| `DELIVERED` | ✅ | ✅ | ✅ | ✅ |

## 구현 개요

1. `frontend/src/shared/ui/`에 새 타임라인 컴포넌트 추가(예: `DeliveryTimeline`) — 4개 점 + 연결선,
   done 여부에 따라 teal/회색.
2. `RealDeliveryTracking.tsx`가 이미 구독 중인 `status` 값으로 4단계 done 배열을 계산.
3. 위 `Card`의 텍스트 자리를 이 컴포넌트로 교체.

## 미확정 사항

- 지도 위 `<h1>{view.title}</h1>`도 없앨지 여부.
- 드리미(배달원) 쪽 추적 화면(`DeliveryTrackScreen.tsx`)도 같은 걸 적용할지 여부 — 이번 스코프는
  부르미 화면만.
