import type { Order, OrderStatus, Role } from "./types";

export interface NextAction {
  to: OrderStatus;
  label: string;
  needsReason?: boolean;
  danger?: boolean;
}

/** Mirrors private.valid_transition() in the database. The server is the source of truth. */
export function nextActions(order: Pick<Order, "orderStatus" | "orderType">, role: Role): NextAction[] {
  const a: NextAction[] = [];
  const s = order.orderStatus;
  const kitchen = role === "KITCHEN";
  if (s === "PLACED" && !kitchen) {
    a.push({ to: "ACCEPTED", label: "Accept" });
    a.push({ to: "REJECTED", label: "Reject", needsReason: true, danger: true });
  }
  if (s === "ACCEPTED") {
    a.push({ to: "PREPARING", label: "Start preparing" });
    if (!kitchen) a.push({ to: "CANCELLED", label: "Cancel", needsReason: true, danger: true });
  }
  if (s === "PREPARING") a.push({ to: "READY", label: "Mark ready" });
  if (s === "READY" && !kitchen) {
    if (order.orderType === "DELIVERY") a.push({ to: "OUT_FOR_DELIVERY", label: "Out for delivery" });
    a.push({ to: "COMPLETED", label: "Mark completed (handed over)" });
  }
  if (s === "OUT_FOR_DELIVERY" && !kitchen) a.push({ to: "COMPLETED", label: "Mark delivered" });
  return a;
}

export const ACTIVE_STATUSES: OrderStatus[] = ["PLACED", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY"];
