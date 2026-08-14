const PROFILE_IMAGES = ["/profile-dreami.png", "/profile-boormi.png"] as const;

/** 사용자 UUID가 같으면 항상 같은 기본 프로필 이미지를 반환합니다. */
export function getProfileImage(userId: string) {
  let hash = 0;

  for (const character of userId) {
    hash = (hash * 31 + character.charCodeAt(0)) >>> 0;
  }

  return PROFILE_IMAGES[hash % PROFILE_IMAGES.length];
}
