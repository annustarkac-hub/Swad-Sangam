// Delivery location picker: OpenStreetMap tiles via Leaflet (free, no API key).
// Loaded lazily, only when a customer opens it, so the menu stays fast.
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "./MapPicker.css";
import { Crosshair, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

export interface Pin { lat: number; lng: number }

const pinIcon = (cls: string) => L.divIcon({ className: "", html: `<span class="map-pin ${cls}"></span>`, iconSize: [30, 30], iconAnchor: [15, 30] });

export default function MapPicker({ lang, initial, restaurant, onPick, onClose, title, hint }: {
  lang: "en" | "hi"; initial: Pin | null; restaurant: Pin | null; onPick: (p: Pin) => void; onClose: () => void;
  /** Overrides for other uses (admin: the restaurant's own location). */
  title?: string; hint?: string;
}) {
  const t = (en: string, hi: string) => (lang === "hi" ? hi : en);
  const box = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const marker = useRef<L.Marker | null>(null);
  const [pin, setPin] = useState<Pin | null>(initial);
  const [locating, setLocating] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  function place(p: Pin, zoom?: number) {
    setPin(p);
    if (!map.current) return;
    if (!marker.current) {
      marker.current = L.marker([p.lat, p.lng], { draggable: true, icon: pinIcon("you"), keyboard: true, title: t("Delivery location", "डिलीवरी की जगह") })
        .addTo(map.current)
        .on("dragend", () => { const ll = marker.current!.getLatLng(); setPin({ lat: ll.lat, lng: ll.lng }); });
    } else marker.current.setLatLng([p.lat, p.lng]);
    map.current.setView([p.lat, p.lng], zoom ?? Math.max(map.current.getZoom(), 16));
  }

  useEffect(() => {
    if (!box.current) return;
    const start = initial ?? restaurant ?? { lat: 30.2219, lng: 78.7836 };
    const m = L.map(box.current, { zoomControl: true, attributionControl: true }).setView([start.lat, start.lng], initial ? 17 : 15);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a>',
    }).addTo(m);
    if (restaurant) L.marker([restaurant.lat, restaurant.lng], { icon: pinIcon("shop"), interactive: false }).addTo(m);
    m.on("click", (e: L.LeafletMouseEvent) => place({ lat: e.latlng.lat, lng: e.latlng.lng }));
    map.current = m;
    if (initial) place(initial, 17);
    setTimeout(() => m.invalidateSize(), 50);
    return () => { m.remove(); map.current = null; marker.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function locate() {
    setMsg(null);
    if (!navigator.geolocation) return setMsg(t("This phone cannot share its location. Tap your house on the map.", "यह फ़ोन लोकेशन नहीं दे सकता। नक्शे पर अपने घर पर टैप करें।"));
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (p) => { setLocating(false); place({ lat: p.coords.latitude, lng: p.coords.longitude }, 17); },
      () => { setLocating(false); setMsg(t("Location is off or not allowed. Turn it on, or tap your house on the map.", "लोकेशन बंद है या अनुमति नहीं मिली। उसे चालू करें या नक्शे पर अपने घर पर टैप करें।")); },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 });
  }

  return (
    <div className="map-sheet" role="dialog" aria-modal="true" aria-label={t("Choose delivery location", "डिलीवरी की जगह चुनें")}>
      <div className="map-head">
        <b>{title ?? t("Mark your delivery location", "डिलीवरी की जगह चुनें")}</b>
        <button className="map-close" aria-label={t("Close", "बंद करें")} onClick={onClose}><X size={20} /></button>
      </div>
      <p className="map-hint">{hint ?? t("Tap your house on the map or drag the pin. The red shop pin is Swad Sangam.", "नक्शे पर अपने घर पर टैप करें या पिन खिसकाएँ। लाल दुकान वाला पिन स्वाद संगम है।")}</p>
      <div ref={box} className="map-box" />
      {msg && <p className="map-msg">{msg}</p>}
      <div className="map-actions">
        <button className="map-btn" disabled={locating} onClick={locate}><Crosshair size={18} /> {locating ? t("Finding you…", "ढूँढ रहे हैं…") : t("Use my current location", "मेरी अभी की लोकेशन")}</button>
        <button className="map-btn primary" disabled={!pin} onClick={() => pin && onPick({ lat: +pin.lat.toFixed(6), lng: +pin.lng.toFixed(6) })}>{t("Confirm location", "यही जगह पक्की करें")}</button>
      </div>
    </div>
  );
}
