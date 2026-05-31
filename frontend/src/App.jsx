import React, { useEffect, useRef, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Circle } from 'react-leaflet';
import L from 'leaflet';
import { api } from './api.js';
import { connectStomp, subscribe } from './ws.js';

const CITY_CENTER = [12.9716, 77.5946]; // Bengaluru

const driverIcon = new L.DivIcon({ className: 'pin pin-driver', html: '🚗' });
const riderIcon = new L.DivIcon({ className: 'pin pin-rider', html: '🧍' });

export default function App() {
  const [log, setLog] = useState([]);
  const pushLog = (entry) =>
    setLog((l) => [{ ...entry, ts: new Date().toLocaleTimeString() }, ...l].slice(0, 200));

  return (
    <div className="app">
      <header>
        <h1>Dispatch Engine — Live Simulator</h1>
        <span className="subtitle">Redis GEO · Consistent Hashing · CQRS · Surge · WebSockets</span>
      </header>
      <div className="layout">
        <div className="map-col">
          <MapView pushLog={pushLog} />
        </div>
        <div className="side-col">
          <RiderPanel pushLog={pushLog} />
          <DriverPanel pushLog={pushLog} />
          <MetricsPanel />
        </div>
      </div>
      <EventLog log={log} />
    </div>
  );
}

function MapView({ pushLog }) {
  const [drivers, setDrivers] = useState({});

  useEffect(() => {
    // Drivers are rendered from a shared window store updated by the panels.
    const t = setInterval(() => setDrivers({ ...window.__drivers }), 1000);
    return () => clearInterval(t);
  }, []);

  return (
    <MapContainer center={CITY_CENTER} zoom={13} style={{ height: '100%', width: '100%' }}>
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution="&copy; OpenStreetMap"
      />
      {Object.entries(drivers).map(([id, d]) => (
        <Marker key={id} position={[d.lat, d.lng]} icon={driverIcon}>
          <Popup>
            {id}<br />{d.vehicleType} · {d.status}
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  );
}

window.__drivers = window.__drivers || {};

function RiderPanel({ pushLog }) {
  const [riderId, setRiderId] = useState('rider-1');
  const [pickup, setPickup] = useState('12.9716,77.5946');
  const [drop, setDrop] = useState('12.9352,77.6245');
  const [vehicleType, setVehicleType] = useState('ECONOMY');
  const [quote, setQuote] = useState(null);
  const [trip, setTrip] = useState(null);
  const [status, setStatus] = useState('idle');
  const clientRef = useRef(null);

  const parse = (s) => s.split(',').map(Number);

  const subscribeRider = async () => {
    const { token } = await api.token(riderId, 'RIDER');
    const client = connectStomp(token, (c) => {
      subscribe(c, `/topic/rider/${riderId}/ride-status`, (m) => {
        setStatus(m.status || JSON.stringify(m));
        pushLog({ type: 'RIDE_STATUS', message: JSON.stringify(m) });
      });
      subscribe(c, `/topic/rider/${riderId}/driver-location`, (m) => {
        pushLog({ type: 'DRIVER_LOC', message: `${m.driverId} @ ${m.lat?.toFixed?.(4)},${m.lng?.toFixed?.(4)}` });
      });
    }, pushLog);
    clientRef.current = client;
  };

  const getQuote = async () => {
    const [pLat, pLng] = parse(pickup);
    const [dLat, dLng] = parse(drop);
    const q = await api.quoteRide({ riderId, pickupLat: pLat, pickupLng: pLng, dropLat: dLat, dropLng: dLng, vehicleType });
    setQuote(q);
  };

  const requestRide = async () => {
    if (!clientRef.current) await subscribeRider();
    const [pLat, pLng] = parse(pickup);
    const [dLat, dLng] = parse(drop);
    const res = await api.requestRide({ riderId, pickupLat: pLat, pickupLng: pLng, dropLat: dLat, dropLng: dLng, vehicleType });
    setTrip(res.tripId);
    setStatus(res.status);
    pushLog({ type: 'RIDE_REQUESTED', message: `${res.tripId} fare≈₹${res.quote?.estimatedFare}` });
  };

  return (
    <section className="panel">
      <h2>🧍 Rider</h2>
      <label>Rider ID<input value={riderId} onChange={(e) => setRiderId(e.target.value)} /></label>
      <label>Pickup (lat,lng)<input value={pickup} onChange={(e) => setPickup(e.target.value)} /></label>
      <label>Drop (lat,lng)<input value={drop} onChange={(e) => setDrop(e.target.value)} /></label>
      <label>Vehicle
        <select value={vehicleType} onChange={(e) => setVehicleType(e.target.value)}>
          <option>ECONOMY</option><option>PREMIUM</option><option>AUTO</option>
        </select>
      </label>
      <div className="row">
        <button onClick={getQuote}>Get Quote</button>
        <button className="primary" onClick={requestRide}>Request Ride</button>
      </div>
      {quote && (
        <div className="quote">
          Fare ≈ <b>₹{quote.estimatedFare}</b> · {(quote.distanceKm).toFixed(2)} km · ETA {Math.round(quote.etaSeconds / 60)} min
          {quote.surgeMultiplier > 1 && <span className="surge"> ⚡ {quote.surgeMultiplier}x surge</span>}
        </div>
      )}
      {trip && <div className="status">Trip <code>{trip}</code> — <b>{status}</b></div>}
    </section>
  );
}

function DriverPanel({ pushLog }) {
  const [driverId, setDriverId] = useState('driver-1');
  const [vehicleType, setVehicleType] = useState('ECONOMY');
  const [online, setOnline] = useState(false);
  const [offer, setOffer] = useState(null);
  const [countdown, setCountdown] = useState(0);
  const [pos, setPos] = useState('12.9716,77.5946');
  const clientRef = useRef(null);
  const tickRef = useRef(null);

  const parse = (s) => s.split(',').map(Number);

  useEffect(() => {
    if (countdown <= 0) return;
    const t = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [countdown]);

  const goOnline = async () => {
    await api.registerDriver(driverId, vehicleType);
    await api.setDriverStatus(driverId, 'AVAILABLE');
    const [lat, lng] = parse(pos);
    await api.updateDriverLocation(driverId, { lat, lng, cityId: 'default' });
    window.__drivers[driverId] = { lat, lng, vehicleType, status: 'AVAILABLE' };

    const { token } = await api.token(driverId, 'DRIVER');
    clientRef.current = connectStomp(token, (c) => {
      subscribe(c, `/topic/driver/${driverId}/ride-offer`, (m) => {
        setOffer(m);
        setCountdown(m.offerTimeoutSeconds || 15);
        pushLog({ type: 'RIDE_OFFER', message: `offer ${m.offerId} fare≈₹${m.estimatedFare}` });
      });
      subscribe(c, `/topic/driver/${driverId}/trip-updates`, (m) =>
        pushLog({ type: 'TRIP_UPDATE', message: JSON.stringify(m) }));
    }, pushLog);

    // heartbeat every 4s
    tickRef.current = setInterval(async () => {
      const [la, ln] = parse(pos);
      await api.updateDriverLocation(driverId, { lat: la, lng: ln, cityId: 'default' });
    }, 4000);
    setOnline(true);
  };

  const goOffline = async () => {
    await api.setDriverStatus(driverId, 'OFFLINE');
    clearInterval(tickRef.current);
    delete window.__drivers[driverId];
    setOnline(false);
  };

  const accept = async () => {
    await api.acceptOffer(driverId, offer.offerId);
    pushLog({ type: 'ACCEPT', message: offer.offerId });
    setOffer(null);
  };
  const reject = async () => {
    await api.rejectOffer(driverId, offer.offerId);
    setOffer(null);
  };

  return (
    <section className="panel">
      <h2>🚗 Driver</h2>
      <label>Driver ID<input value={driverId} onChange={(e) => setDriverId(e.target.value)} /></label>
      <label>Position (lat,lng)<input value={pos} onChange={(e) => setPos(e.target.value)} /></label>
      <label>Vehicle
        <select value={vehicleType} onChange={(e) => setVehicleType(e.target.value)}>
          <option>ECONOMY</option><option>PREMIUM</option><option>AUTO</option>
        </select>
      </label>
      <div className="row">
        {!online
          ? <button className="primary" onClick={goOnline}>Go Online</button>
          : <button onClick={goOffline}>Go Offline</button>}
      </div>
      {offer && (
        <div className="offer">
          <div>New ride offer · <b>₹{offer.estimatedFare}</b> · ETA {Math.round(offer.etaSeconds / 60)}m</div>
          <div className="countdown">{countdown}s</div>
          <div className="row">
            <button className="primary" onClick={accept}>Accept</button>
            <button onClick={reject}>Reject</button>
          </div>
        </div>
      )}
    </section>
  );
}

function MetricsPanel() {
  const [summary, setSummary] = useState({});
  const [surge, setSurge] = useState({});

  useEffect(() => {
    const t = setInterval(async () => {
      try {
        setSummary(await api.citySummary('default'));
        setSurge(await api.surgeMap());
      } catch { /* services may be starting */ }
    }, 5000);
    return () => clearInterval(t);
  }, []);

  return (
    <section className="panel">
      <h2>📊 City Metrics</h2>
      <div className="metrics">
        <Metric label="Active drivers" value={summary.activeDrivers ?? '—'} />
        <Metric label="Active trips" value={summary.activeTrips ?? '—'} />
        <Metric label="Completed" value={summary.completedTrips ?? '—'} />
        <Metric label="Cancelled" value={summary.cancelledTrips ?? '—'} />
      </div>
      <h3>Surge zones</h3>
      <div className="surge-zones">
        {Object.entries(surge).length === 0 && <span className="muted">no surge</span>}
        {Object.entries(surge).map(([z, m]) => (
          <span key={z} className={m > 1 ? 'zone hot' : 'zone'}>{z}: {m}x</span>
        ))}
      </div>
    </section>
  );
}

function Metric({ label, value }) {
  return (
    <div className="metric">
      <div className="metric-value">{value}</div>
      <div className="metric-label">{label}</div>
    </div>
  );
}

function EventLog({ log }) {
  return (
    <section className="event-log">
      <h2>Event Stream</h2>
      <div className="log-body">
        {log.map((e, i) => (
          <div className="log-line" key={i}>
            <span className="log-ts">{e.ts}</span>
            <span className={`log-type t-${e.type}`}>{e.type}</span>
            <span className="log-msg">{e.message}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
