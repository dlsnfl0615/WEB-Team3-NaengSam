/** 이미 로드했거나 로딩 중인 스크립트를 src 기준으로 캐시(중복 주입 방지). */
const cache = new Map<string, Promise<void>>();

/**
 * 외부 스크립트를 <head>에 1회만 주입하고 로드 완료를 기다린다.
 * 같은 src로 다시 호출하면 최초 로드 Promise를 재사용한다.
 */
export function loadScript(src: string): Promise<void> {
  const cached = cache.get(src);
  if (cached) return cached;

  const promise = new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = src;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      cache.delete(src);
      reject(new Error(`스크립트를 불러오지 못했어요: ${src}`));
    };
    document.head.appendChild(script);
  });

  cache.set(src, promise);
  return promise;
}
