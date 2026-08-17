import { useEffect, useRef } from "react";

export interface UseInfiniteScrollSentinelOptions {
  /** 다음 페이지가 있을 때만 관찰한다. false면 옵저버를 아예 붙이지 않는다. */
  hasNext: boolean;
  /** sentinel이 뷰포트(또는 스크롤 컨테이너)에 들어오면 호출한다. 중복 호출 방지는 호출부(스토어)가 맡는다. */
  onLoadMore: () => void;
}

/**
 * 목록 맨 아래에 둘 sentinel용 ref를 반환한다. sentinel이 화면에 들어오는 순간(더보기 버튼 없이)
 * `onLoadMore`를 호출해 다음 페이지를 이어 받는다 — 무한 스크롤 리스트 공용 훅.
 */
export function useInfiniteScrollSentinel({
  hasNext,
  onLoadMore,
}: UseInfiniteScrollSentinelOptions) {
  const sentinelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!hasNext) return;
    const node = sentinelRef.current;
    if (!node) return;

    // rootMargin으로 바닥에 닿기 조금 전에 미리 다음 페이지를 당겨온다.
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) onLoadMore();
      },
      { rootMargin: "200px" },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [hasNext, onLoadMore]);

  return sentinelRef;
}
