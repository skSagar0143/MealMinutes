import {describe,expect,it} from "vitest";
import {formatMoney,normalizeSearch} from "./format";
describe("formatMoney",()=>{it("formats INR amounts",()=>expect(formatMoney(149)).toContain("149"));it("handles invalid amounts",()=>expect(formatMoney(Number.NaN)).toBe("—"));});
describe("normalizeSearch",()=>{it("normalizes whitespace",()=>expect(normalizeSearch("  spicy   paneer ")).toBe("spicy paneer"));it("limits length",()=>expect(normalizeSearch("x".repeat(120))).toHaveLength(100));});