export type Role = "OWNER" | "MANAGER" | "STAFF" | "KITCHEN" | "CASHIER" | "WAITER" | "ACCOUNTANT";
export type OrderStatus = "PLACED" | "ACCEPTED" | "PREPARING" | "READY" | "OUT_FOR_DELIVERY" | "COMPLETED" | "REJECTED" | "CANCELLED";
export type PaymentStatus = "PENDING" | "PAID" | "FAILED" | "REFUND_PENDING" | "REFUNDED";

export interface StaffProfile {
  user_id: string;
  email: string;
  display_name: string | null;
  role: Role | null;
  is_active: boolean;
  created_at: string;
}

export interface Category {
  id: string;
  name: string;
  name_hi: string | null;
  sort_order: number;
  is_active: boolean;
}

export interface ItemOption {
  id: string;
  name: string;
  pricePaise: number;
  isAvailable?: boolean;
  /** Options in the same group are alternatives (pick one); none = the base variant. */
  group?: string;
}

export interface MenuItem {
  id: string;
  name: string;
  name_hi: string | null;
  category_id: string;
  description: string | null;
  description_hi: string | null;
  price_paise: number;
  food_type: "VEG" | "NON_VEG";
  image_url: string | null;
  image_is_representative?: boolean;
  image_credit?: string | null;
  variant_base_label?: string | null;
  is_available: boolean;
  is_archived: boolean;
  is_popular: boolean;
  preparation_minutes: number | null;
  allergens: string | null;
  portion_info: string | null;
  options: ItemOption[];
  details_verified: boolean;
  sort_order: number;
  updated_at?: string;
}

export interface OrderLine {
  itemId: string;
  nameSnapshot: string;
  foodType: "VEG" | "NON_VEG";
  unitPricePaise: number;
  quantity: number;
  options: { id: string; name: string; pricePaise: number }[];
  lineTotalPaise: number;
}

export interface Order {
  id: string;
  orderNumber: string;
  orderStatus: OrderStatus;
  paymentStatus: PaymentStatus;
  paymentMethod: "CASH" | "UPI_AT_RESTAURANT" | "ONLINE";
  orderType: "PICKUP" | "DINE_IN" | "DELIVERY";
  tableNumber: string | null;
  customerName: string;
  customerPhone?: string;
  deliveryAddress?: string | null;
  deliveryLandmark?: string | null;
  pickupInstructions?: string | null;
  notes?: string | null;
  paymentReference?: string | null;
  subtotalPaise: number;
  taxPaise: number;
  packagingFeePaise: number;
  deliveryFeePaise: number;
  discountPaise: number;
  totalPaise: number;
  couponCode: string | null;
  rejectionReason: string | null;
  cancelReason: string | null;
  cancelledBy: string | null;
  estimatedReadyAt: string | null;
  createdAt: string;
  acceptedAt: string | null;
  readyAt: string | null;
  completedAt: string | null;
  items: OrderLine[];
  history: { status: OrderStatus; at: string }[];
}

export const STATUS_LABEL: Record<OrderStatus, string> = {
  PLACED: "New",
  ACCEPTED: "Accepted",
  PREPARING: "Preparing",
  READY: "Ready",
  OUT_FOR_DELIVERY: "Out for delivery",
  COMPLETED: "Completed",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

export const PAYMENT_LABEL: Record<string, string> = {
  CASH: "Cash",
  UPI_AT_RESTAURANT: "UPI at restaurant",
  ONLINE: "Online",
  PENDING: "Payment pending",
  PAID: "Paid",
  FAILED: "Not paid",
  REFUND_PENDING: "Refund pending",
  REFUNDED: "Refunded",
};

export const ORDER_TYPE_LABEL: Record<string, string> = {
  PICKUP: "Pickup",
  DINE_IN: "Dine-in",
  DELIVERY: "Delivery",
};
