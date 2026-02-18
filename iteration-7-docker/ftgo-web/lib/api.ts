const BASE = "";

export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...options?.headers },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || res.statusText);
  }
  return res.json();
}

// ---- Order Service ----
export interface OrderItem {
  menuItemName: string;
  price: number;
  quantity: number;
}

export interface OrderResponse {
  id: number;
  consumerName: string;
  restaurantId: number;
  restaurantName: string;
  deliveryAddress: string;
  status: string;
  totalAmount: number;
  items: OrderItem[];
  createdAt: string;
}

export interface CreateOrderRequest {
  consumerId: number;
  consumerName: string;
  consumerContact: string;
  restaurantId: number;
  deliveryAddress: string;
  paymentMethod: string;
  items: { menuItemId: number; quantity: number }[];
}

export const ordersApi = {
  getAll: () => apiFetch<OrderResponse[]>("/api/orders"),
  getById: (id: number) => apiFetch<OrderResponse>(`/api/orders/${id}`),
  create: (req: CreateOrderRequest) =>
    apiFetch<OrderResponse>("/api/orders", { method: "POST", body: JSON.stringify(req) }),
  cancel: (id: number) =>
    apiFetch<OrderResponse>(`/api/orders/${id}/cancel`, { method: "PUT" }),
};

// ---- Restaurant Service ----
export interface MenuItem {
  id: number;
  name: string;
  description: string;
  price: number;
}

export interface Restaurant {
  id: number;
  name: string;
  address: string;
  phone: string;
  isOpen: boolean;
  menuItems: MenuItem[];
}

export const restaurantsApi = {
  getAll: () => apiFetch<Restaurant[]>("/api/restaurants"),
  getById: (id: number) => apiFetch<Restaurant>(`/api/restaurants/${id}`),
  getMenu: (id: number) => apiFetch<MenuItem[]>(`/api/restaurants/${id}/menu`),
};

// ---- Kitchen Service ----
export interface KitchenTicket {
  id: number;
  orderId: number;
  restaurantId: number;
  items: string;
  status: string;
  createdAt: string;
  acceptedAt: string | null;
  readyAt: string | null;
}

export const kitchenApi = {
  getTickets: (restaurantId?: number) =>
    apiFetch<KitchenTicket[]>(
      restaurantId ? `/api/kitchen/tickets?restaurantId=${restaurantId}` : "/api/kitchen/tickets"
    ),
  accept: (id: number) =>
    apiFetch<KitchenTicket>(`/api/kitchen/tickets/${id}/accept`, { method: "PUT" }),
  preparing: (id: number) =>
    apiFetch<KitchenTicket>(`/api/kitchen/tickets/${id}/preparing`, { method: "PUT" }),
  ready: (id: number) =>
    apiFetch<KitchenTicket>(`/api/kitchen/tickets/${id}/ready`, { method: "PUT" }),
};

// ---- Delivery Service ----
export interface Delivery {
  id: number;
  orderId: number;
  courierId: number | null;
  pickupAddress: string;
  deliveryAddress: string;
  status: string;
  createdAt: string;
  deliveredAt: string | null;
}

export interface Courier {
  id: number;
  name: string;
  phone: string;
  available: boolean;
}

export const deliveriesApi = {
  getAll: () => apiFetch<Delivery[]>("/api/deliveries"),
  getByCourier: (courierId: number) =>
    apiFetch<Delivery[]>(`/api/deliveries/courier/${courierId}`),
  assign: (id: number, courierId: number) =>
    apiFetch<Delivery>(`/api/deliveries/${id}/assign/${courierId}`, { method: "PUT" }),
  pickup: (id: number) =>
    apiFetch<Delivery>(`/api/deliveries/${id}/pickup`, { method: "PUT" }),
  deliver: (id: number) =>
    apiFetch<Delivery>(`/api/deliveries/${id}/deliver`, { method: "PUT" }),
  getCouriers: () => apiFetch<Courier[]>("/api/couriers"),
  getCourier: (id: number) => apiFetch<Courier>(`/api/couriers/${id}`),
};

// ---- Accounting Service ----
export interface Payment {
  id: number;
  orderId: number;
  amount: number;
  paymentMethod: string;
  status: string;
  transactionId: string;
  createdAt: string;
}

export const paymentsApi = {
  getByOrder: (orderId: number) => apiFetch<Payment>(`/api/payments/order/${orderId}`),
};

// ---- Notification Service ----
export interface Notification {
  id: number;
  orderId: number;
  type: string;
  recipient: string;
  message: string;
  sentAt: string;
}

export const notificationsApi = {
  getByOrder: (orderId: number) =>
    apiFetch<Notification[]>(`/api/notifications/order/${orderId}`),
};
