import { describe, expect, it } from "vitest";
import { nextActions } from "./orderFlow";

const base = { orderType: "PICKUP" as const, source: "APP" as const };

describe("nextActions", () => {
  it("never offers completing a READY delivery order (it must go out first)", () => {
    const a = nextActions({ ...base, orderType: "DELIVERY", orderStatus: "READY" }, "OWNER").map((x) => x.to);
    expect(a).toEqual(["OUT_FOR_DELIVERY"]);
  });
  it("hands over with payment for pickup, but never for waiters", () => {
    expect(nextActions({ ...base, orderStatus: "READY" }, "CASHIER")[0]).toMatchObject({ to: "COMPLETED", handover: true });
    expect(nextActions({ ...base, orderStatus: "READY" }, "WAITER")).toEqual([]);
  });
  it("only shows what the database allows when nextStatuses is present", () => {
    const a = nextActions({ ...base, orderStatus: "PLACED", nextStatuses: ["ACCEPTED"] }, "OWNER").map((x) => x.to);
    expect(a).toEqual(["ACCEPTED"]);
  });
  it("kitchen only moves food through preparation", () => {
    expect(nextActions({ ...base, orderStatus: "PLACED" }, "KITCHEN")).toEqual([]);
    expect(nextActions({ ...base, orderStatus: "PREPARING" }, "KITCHEN").map((x) => x.to)).toEqual(["READY"]);
  });
});
