import { describe, expect, it } from "vitest";
import { fromPaise, rupees, slugify, toPaise } from "./format";
import { nextActions } from "./orderFlow";

describe("money", () => {
  it("formats paise as rupees", () => {
    expect(rupees(5000)).toBe("₹50");
    expect(rupees(12550)).toBe("₹125.50");
  });
  it("parses rupee input to integer paise", () => {
    expect(toPaise("50")).toBe(5000);
    expect(toPaise("12.5")).toBe(1250);
    expect(toPaise("12.05")).toBe(1205);
    expect(toPaise("-1")).toBeNull();
    expect(toPaise("abc")).toBeNull();
    expect(fromPaise(1250)).toBe("12.50");
  });
  it("slugifies category ids", () => {
    expect(slugify("South Indian!")).toBe("south_indian");
  });
});

describe("order flow", () => {
  it("kitchen cannot accept or reject", () => {
    expect(nextActions({ orderStatus: "PLACED", orderType: "PICKUP" }, "KITCHEN")).toEqual([]);
  });
  it("staff can accept or reject new orders", () => {
    expect(nextActions({ orderStatus: "PLACED", orderType: "PICKUP" }, "STAFF").map((a) => a.to)).toEqual(["ACCEPTED", "REJECTED"]);
  });
  it("delivery orders can go out for delivery", () => {
    expect(nextActions({ orderStatus: "READY", orderType: "DELIVERY" }, "STAFF").map((a) => a.to)).toContain("OUT_FOR_DELIVERY");
  });
});
