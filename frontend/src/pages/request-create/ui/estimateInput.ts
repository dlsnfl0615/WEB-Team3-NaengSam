import type { RequestForm } from "./types";

const ESTIMATE_INPUT_KEYS = [
  "pickup",
  "dropoff",
  "itemType",
  "itemSize",
] as const satisfies readonly (keyof RequestForm)[];

/** 견적을 다시 조회해야 하는 입력값이 실제로 달라졌는지 확인한다. */
export function hasEstimateInputChanged(
  form: RequestForm,
  patch: Partial<RequestForm>,
): boolean {
  return ESTIMATE_INPUT_KEYS.some(
    (key) => patch[key] !== undefined && patch[key] !== form[key],
  );
}
