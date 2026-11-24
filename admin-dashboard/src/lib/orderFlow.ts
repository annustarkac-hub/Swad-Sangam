import type { Order, OrderStatus, Role } from "./types";

export interface NextAction {
  to: OrderStatus;
  label: string;
  needsReason?: boolean;
  danger?: boolean;
  /** Completion goes through the handover dialog: payment (and the delivery code for app deliveries). */
  handover?: boolean;
}

/** Roles that may take money (mirrors the database: waiters and riders' POS access are blocked). */
export const canTakePayment = (role: Role | null) => role != null && !["WAITER", "KITCHEN", "DELIVERY", "ACCOUNTANT"].includes(role);

/** Mirrors private.valid_transition() + the payment/handover rules in the database. The server is the source of truth. */
export function nextActions(order: Pick<Order, "orderStatus" | "orderType"> & Partial<Pick<Order, "source">>, role: Role): NextAction[] {
  const a: NextAction[] = [];
  const s = order.orderStatus;
  const kitchen = role === "KITCHEN";
  const appDelivery = order.orderType === "DELIVERY" && (order.source ?? "APP") === "APP";
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
    // Delivery orders must go out for delivery first — they can never jump to "completed"
    if (order.orderType === "DELIVERY") a.push({ to: "OUT_FOR_DELIVERY", label: "Out for delivery" });
    else if (canTakePayment(role)) a.push({ to: "COMPLETED", label: "Hand over & collect payment", handover: true });
  }
  if (s === "OUT_FOR_DELIVERY" && canTakePayment(role)) {
    a.push({ to: "COMPLETED", label: appDelivery ? "Deliver (code + payment)" : "Mark delivered & collect", handover: true });
  }
  return a;
}

export const ACTIVE_STATUSES: OrderStatus[] = ["PLACED", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY"];
