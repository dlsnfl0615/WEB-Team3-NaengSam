import { afterEach, describe, expect, it } from "vitest";
import {
  isInAppBrowser,
  isIos,
  isStandalone,
  urlBase64ToUint8Array,
} from "./pushCapability";

const originalUserAgent = Object.getOwnPropertyDescriptor(
  navigator,
  "userAgent",
);

function setUserAgent(userAgent: string) {
  Object.defineProperty(navigator, "userAgent", {
    configurable: true,
    value: userAgent,
  });
}

afterEach(() => {
  if (originalUserAgent) {
    Object.defineProperty(navigator, "userAgent", originalUserAgent);
  }
});

describe("urlBase64ToUint8Array", () => {
  it("패딩이 생략된 base64url을 원래 바이트로 되돌린다", () => {
    // "hello" = base64 "aGVsbG8=" → 패딩을 뺀 base64url "aGVsbG8"
    const bytes = urlBase64ToUint8Array("aGVsbG8");

    expect(Array.from(bytes)).toEqual([104, 101, 108, 108, 111]);
  });

  it("URL-safe 문자(-, _)를 표준 base64 문자로 되돌린다", () => {
    // 표준 base64 "++//" 에 해당하는 URL-safe 표기
    const urlSafe = urlBase64ToUint8Array("--__");
    const standard = urlBase64ToUint8Array("++//");

    expect(Array.from(urlSafe)).toEqual(Array.from(standard));
  });

  it("VAPID 공개키 길이(65바이트)를 그대로 유지한다", () => {
    // 65바이트 비압축 EC 포인트를 base64url로 만든 뒤 되돌린다.
    const source = new Uint8Array(65).fill(7);
    source[0] = 4; // 비압축 포인트 표식
    const base64Url = btoa(String.fromCharCode(...source))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");

    const restored = urlBase64ToUint8Array(base64Url);

    // 길이나 바이트가 어긋나면 subscribe가 조용히 실패해 원인을 찾기 어렵다.
    expect(restored.length).toBe(65);
    expect(Array.from(restored)).toEqual(Array.from(source));
  });
});

describe("isInAppBrowser", () => {
  it("카카오톡 인앱브라우저를 감지한다", () => {
    setUserAgent(
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) KAKAOTALK 10.0.0",
    );

    expect(isInAppBrowser()).toBe(true);
  });

  it("일반 모바일 브라우저는 인앱브라우저가 아니다", () => {
    setUserAgent(
      "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
    );

    expect(isInAppBrowser()).toBe(false);
  });
});

describe("isIos", () => {
  it("아이폰을 감지한다", () => {
    setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)");

    expect(isIos()).toBe(true);
  });

  it("안드로이드는 iOS가 아니다", () => {
    setUserAgent("Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0");

    expect(isIos()).toBe(false);
  });
});

describe("isStandalone", () => {
  it("브라우저 탭에서는 standalone이 아니다", () => {
    expect(isStandalone()).toBe(false);
  });
});
