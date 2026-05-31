// Thin REST client for the dispatch engine. All calls go through the API
// gateway (proxied by Vite in dev).

const base = '';

async function post(path, body) {
  const res = await fetch(base + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  });
  return res.json();
}

async function get(path) {
  const res = await fetch(base + path);
  return res.json();
}

export const api = {
  quoteRide: (req) => post('/api/v1/rides/quote', req),
  requestRide: (req) => post('/api/v1/rides', req),
  registerDriver: (driverId, vehicleType) =>
    post(`/api/v1/drivers/${driverId}/register?vehicleType=${vehicleType}`),
  setDriverStatus: (driverId, status) =>
    fetch(`/api/v1/drivers/${driverId}/status?status=${status}`, { method: 'PUT' }),
  updateDriverLocation: (driverId, body) =>
    post(`/api/v1/drivers/${driverId}/location`, body),
  acceptOffer: (driverId, offerId) =>
    post(`/api/v1/drivers/${driverId}/ride-offer/${offerId}/accept`),
  rejectOffer: (driverId, offerId) =>
    post(`/api/v1/drivers/${driverId}/ride-offer/${offerId}/reject`),
  startTrip: (tripId, lat, lng) =>
    post(`/api/v1/trips/${tripId}/start?startLat=${lat}&startLng=${lng}`),
  arrived: (tripId, driverId) =>
    post(`/api/v1/trips/${tripId}/arrived?driverId=${driverId}`),
  completeTrip: (tripId, body) => post(`/api/v1/trips/${tripId}/complete`, body),
  citySummary: (cityId) => get(`/api/v1/analytics/city/${cityId}/summary`),
  surgeMap: () => get('/api/v1/analytics/surge/map'),
  token: (userId, role) => get(`/api/v1/auth/token?userId=${userId}&role=${role}`)
};
