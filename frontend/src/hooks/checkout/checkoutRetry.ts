const KEY_PREFIX = 'gocook.checkout.';

interface CheckoutLocation {
  path: string;
  search: string;
}

export function rememberCheckout(orderCode: string, location: CheckoutLocation): void {
  try {
    sessionStorage.setItem(KEY_PREFIX + orderCode, JSON.stringify(location));
  } catch {
  }
}

export function recallCheckoutLink(orderCode: string): string | null {
  try {
    const raw = sessionStorage.getItem(KEY_PREFIX + orderCode);
    if (!raw) return null;
    const location = JSON.parse(raw) as CheckoutLocation;
    return `${location.path}${location.search}`;
  } catch {
    return null;
  }
}
